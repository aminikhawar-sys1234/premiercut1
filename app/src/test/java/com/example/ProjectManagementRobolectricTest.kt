package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CrashRecoveryEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.TimelineSerializer
import com.example.data.repository.ProjectRepository
import com.example.domain.model.*
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectManagementRobolectricTest {

  private lateinit var context: Context
  private lateinit var database: AppDatabase
  private lateinit var repository: ProjectRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = ProjectRepository(database)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `test project serialization preserves all settings, trim ranges, keyframes, and chroma key`() {
    val keyframes = listOf(
      ClipKeyframe(id = "kf_1", timeMs = 0L, posX = 0f, posY = 0f, scale = 1.0f, rotation = 0f, opacity = 1.0f),
      ClipKeyframe(id = "kf_2", timeMs = 2000L, posX = 0.5f, posY = -0.2f, scale = 1.2f, rotation = 15f, opacity = 0.8f)
    )

    val videoClip = VideoClip(
      id = "video_1",
      uri = "file:///data/user/0/com.example/files/sample.mp4",
      name = "Main Clip",
      durationMs = 4000L,
      sourceStartMs = 1000L,
      sourceEndMs = 5000L,
      timelineStartMs = 0L,
      speed = 1.25f,
      volume = 0.8f,
      keyframes = keyframes
    )

    val textClip = TextClip(
      id = "text_1",
      text = "Pro Studio Title",
      durationMs = 3000L,
      timelineStartMs = 500L,
      fontSizeSp = 28f,
      textColor = 0xFFFFFFFF
    )

    val timeline = Timeline(
      videoClips = listOf(videoClip),
      textClips = listOf(textClip),
      chromaKey = ChromaKeySettings(enabled = true, targetColor = 0xFF00FF00, similarity = 0.45f, smoothness = 0.15f),
      canvasBackgroundColor = 0xFF1A1A1A,
      aspectRatio = AspectRatio.RATIO_9_16
    )

    val pkg = ProjectPackage(
      projectId = "proj_test_123",
      projectName = "4K Cinematic Reel",
      timeline = timeline,
      settings = ProjectSettings(
        aspectRatio = AspectRatio.RATIO_9_16,
        resolution = Resolution.RES_1080P,
        fps = FrameRate.FPS_30,
        sampleRateHz = 48000,
        canvasBackgroundColor = 0xFF1A1A1A
      )
    )
    val serializedJson = TimelineSerializer.toPackageJson(pkg)

    assertNotNull(serializedJson)
    assertTrue(serializedJson.contains("\"version\""))
    assertTrue(serializedJson.contains("4K Cinematic Reel"))
    assertTrue(serializedJson.contains("mediaReferences"))

    // Deserialize back into Timeline
    val deserializedTimeline = TimelineSerializer.fromJson(serializedJson)
    assertEquals(1, deserializedTimeline.videoClips.size)
    val deserializedClip = deserializedTimeline.videoClips.first()
    assertEquals("video_1", deserializedClip.id)
    assertEquals(1000L, deserializedClip.sourceStartMs)
    assertEquals(5000L, deserializedClip.sourceEndMs)
    assertEquals(1.25f, deserializedClip.speed, 0.01f)
    assertEquals(0.8f, deserializedClip.volume, 0.01f)
    assertEquals(2, deserializedClip.keyframes.size)

    assertEquals(1, deserializedTimeline.textClips.size)
    assertEquals("Pro Studio Title", deserializedTimeline.textClips.first().text)

    assertTrue(deserializedTimeline.chromaKey.enabled)
    assertEquals(0.45f, deserializedTimeline.chromaKey.similarity, 0.01f)
  }

  @Test
  fun `test backward compatibility with legacy timeline json`() {
    val legacyJson = """
      {
        "videoClips": [
          {
            "id": "legacy_clip",
            "uri": "content://media/legacy.mp4",
            "name": "Legacy Clip",
            "durationMs": 3000,
            "sourceStartMs": 0,
            "sourceEndMs": 3000,
            "timelineStartMs": 0,
            "speed": 1.0,
            "volume": 1.0,
            "isVideo": true
          }
        ],
        "overlayClips": [],
        "audioClips": [],
        "textClips": [],
        "stickerClips": [],
        "effectClips": [],
        "transitions": [],
        "adjustments": {},
        "filter": {},
        "chromaKey": { "enabled": false }
      }
    """.trimIndent()

    val parsed = TimelineSerializer.fromJson(legacyJson)
    assertEquals(1, parsed.videoClips.size)
    assertEquals("legacy_clip", parsed.videoClips.first().id)
    assertEquals("Legacy Clip", parsed.videoClips.first().name)
  }

  @Test
  fun `test crash recovery session lifecycle`() = runBlocking {
    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "c1", uri = "file:///test.mp4", name = "Emergency Clip", durationMs = 2500L)
      )
    )

    // Initially, no recovery session exists
    val initialSession = repository.getActiveRecoverySession()
    assertNull(initialSession)

    // Save crash recovery session
    repository.saveCrashRecoverySession(
      projectId = "proj_crash_1",
      projectName = "Unsaved Work",
      timeline = timeline
    )

    val session = repository.getActiveRecoverySession()
    assertNotNull(session)
    assertEquals("proj_crash_1", session?.projectId)
    assertEquals("Unsaved Work", session?.projectName)
    assertTrue(session!!.isDirty)

    val recoveredTimeline = TimelineSerializer.fromJson(session.timelineJson)
    assertEquals(1, recoveredTimeline.videoClips.size)
    assertEquals("c1", recoveredTimeline.videoClips.first().id)

    // Saving project should clear recovery session
    repository.saveProject(
      id = "proj_crash_1",
      name = "Unsaved Work",
      durationMs = 2500L,
      thumbnailPath = "",
      aspectRatio = "9:16",
      resolution = "1080p",
      fps = 30,
      timeline = timeline,
      isDraft = false
    )

    val sessionAfterSave = repository.getActiveRecoverySession()
    assertNull(sessionAfterSave)
  }

  @Test
  fun `test project CRUD duplicate rename delete`() = runBlocking {
    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "orig_c", uri = "file:///video.mp4", name = "Scene 1", durationMs = 3000L)
      )
    )

    val projId = "proj_alpha"
    repository.saveProject(
      id = projId,
      name = "Alpha Project",
      durationMs = 3000L,
      thumbnailPath = "",
      aspectRatio = "16:9",
      resolution = "1080p",
      fps = 60,
      timeline = timeline,
      isDraft = false,
      sampleRate = 44100,
      canvasColor = 0xFFFFFFFF
    )

    val stored = repository.getProjectById(projId)
    assertNotNull(stored)
    assertEquals("Alpha Project", stored?.name)
    assertEquals("16:9", stored?.aspectRatio)
    assertEquals(44100, stored?.sampleRate)
    assertEquals(0xFFFFFFFF, stored?.canvasColor)

    // Rename
    repository.renameProject(projId, "Alpha Remastered")
    assertEquals("Alpha Remastered", repository.getProjectById(projId)?.name)

    // Duplicate
    repository.duplicateProject(projId)
    val all = repository.allProjects.first()
    assertEquals(2, all.size)
    val duplicated = all.first { it.id != projId }
    assertEquals("Alpha Remastered (Copy)", duplicated.name)
    assertEquals("16:9", duplicated.aspectRatio)

    // Delete original
    repository.deleteProject(projId)
    assertNull(repository.getProjectById(projId))
    assertEquals(1, repository.allProjects.first().size)
  }

  @Test
  fun `test missing media detection and relink clip`() {
    val tempDir = File(context.cacheDir, "test_media_${System.currentTimeMillis()}").apply { mkdirs() }
    val existingFile = File(tempDir, "existing.mp4").apply { writeText("fake video bytes") }
    val missingFile = File(tempDir, "non_existent.mp4")

    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "clip_ok", uri = existingFile.toURI().toString(), name = "OK Clip", durationMs = 2000L),
        VideoClip(id = "clip_missing", uri = missingFile.toURI().toString(), name = "Lost Clip", durationMs = 3000L)
      )
    )

    val missingItems = MediaRelinkManager.detectMissingMedia(context, timeline)
    assertEquals(1, missingItems.size)
    assertEquals("clip_missing", missingItems.first().clipId)
    assertEquals("non_existent.mp4", missingItems.first().originalFilename)

    // Relink clip with the existing file
    val relinkedTimeline = MediaRelinkManager.relinkClip(
      context = context,
      timeline = timeline,
      clipId = "clip_missing",
      newUri = existingFile.toURI().toString()
    )

    val afterRelinkMissing = MediaRelinkManager.detectMissingMedia(context, relinkedTimeline)
    assertEquals(0, afterRelinkMissing.size)
  }

  @Test
  fun `test comprehensive project serialization with captions stickers effects filters transitions and chroma`() {
    val wordCaptions = listOf(
      WordTiming("Welcome", 0L, 500L),
      WordTiming("to", 500L, 200L),
      WordTiming("AH", 700L, 400L),
      WordTiming("Studio", 1100L, 600L)
    )

    val keyframes = listOf(
      ClipKeyframe(id = "kf_scale", timeMs = 0L, posX = 0.1f, posY = 0.2f, scale = 1.0f, rotation = 0f, opacity = 1.0f),
      ClipKeyframe(id = "kf_end", timeMs = 3000L, posX = 0.5f, posY = -0.1f, scale = 1.5f, rotation = 45f, opacity = 0.9f)
    )

    val complexTimeline = Timeline(
      videoClips = listOf(
        VideoClip(
          id = "vid_main",
          uri = "file:///storage/video.mp4",
          name = "Main Video Clip",
          timelineStartMs = 0L,
          durationMs = 4000L,
          sourceStartMs = 500L,
          sourceEndMs = 4500L,
          speed = 1.5f,
          volume = 0.9f,
          isMuted = false,
          isReversed = false,
          keyframes = keyframes
        )
      ),
      overlayClips = listOf(
        VideoClip(
          id = "overlay_pip",
          uri = "file:///storage/pip.mp4",
          name = "PIP Overlay",
          timelineStartMs = 1000L,
          durationMs = 2500L,
          sourceStartMs = 0L,
          sourceEndMs = 2500L,
          opacity = 0.85f
        )
      ),
      audioClips = listOf(
        AudioClip(
          id = "audio_bgm",
          uri = "file:///storage/bgm.mp3",
          title = "Background Score",
          timelineStartMs = 0L,
          durationMs = 5000L,
          sourceStartMs = 1000L,
          sourceEndMs = 6000L,
          volume = 0.75f,
          speed = 1.0f,
          fadeInMs = 600L,
          fadeOutMs = 800L,
          gainDb = 2.5f
        )
      ),
      textClips = listOf(
        TextClip(
          id = "caption_1",
          text = "Welcome to AH Studio",
          timelineStartMs = 0L,
          durationMs = 3000L,
          fontFamily = "Roboto",
          fontSizeSp = 26f,
          textColor = 0xFFFFFFFF,
          hasGradient = true,
          gradientColorStart = 0xFF00E5FF,
          gradientColorEnd = 0xFF8B5CF6,
          strokeWidth = 2f,
          strokeColor = 0xFF000000,
          animationType = "Pop",
          subtitleStyle = "HighlightWord",
          words = wordCaptions
        )
      ),
      stickerClips = listOf(
        StickerClip(
          id = "sticker_fire",
          emojiOrAsset = "🔥",
          timelineStartMs = 1200L,
          durationMs = 2000L,
          posX = 0.4f,
          posY = -0.3f,
          scale = 1.4f,
          rotation = 12f,
          opacity = 1f
        )
      ),
      effectClips = listOf(
        EffectClip(
          id = "effect_glitch",
          effectType = EffectType.GLITCH,
          timelineStartMs = 2000L,
          durationMs = 1500L,
          intensity = 0.85f
        )
      ),
      transitions = listOf(
        Transition(
          id = "trans_wipe",
          clipIndexBefore = 0,
          type = TransitionType.WIPE,
          durationMs = 450L
        )
      ),
      adjustments = VideoAdjustments(
        brightness = 0.1f,
        contrast = 1.2f,
        saturation = 1.15f,
        temperature = 0.05f,
        vignette = 0.2f
      ),
      filter = FilterSettings(
        type = FilterType.CINEMATIC,
        intensity = 0.9f
      ),
      chromaKey = ChromaKeySettings(
        enabled = true,
        targetColor = 0xFF00FF00,
        similarity = 0.42f,
        smoothness = 0.18f,
        spillSuppression = 0.6f,
        backgroundType = "SolidColor",
        backgroundColor = 0xFF000000
      ),
      canvasBackgroundColor = 0xFF121212,
      aspectRatio = AspectRatio.RATIO_16_9
    )

    val settings = ProjectSettings(
      aspectRatio = AspectRatio.RATIO_16_9,
      resolution = Resolution.RES_4K,
      fps = FrameRate.FPS_60,
      sampleRateHz = 48000,
      canvasBackgroundColor = 0xFF121212
    )

    val pkg = TimelineSerializer.buildProjectPackage(
      projectId = "proj_full_feature",
      projectName = "4K Cinematic Master",
      settings = settings,
      timeline = complexTimeline,
      isDraft = false
    )

    val json = TimelineSerializer.toPackageJson(pkg)
    assertNotNull(json)

    // Deserialize back into ProjectPackage
    val restoredPkg = TimelineSerializer.fromPackageJson(json)
    assertNotNull(restoredPkg)
    assertEquals("proj_full_feature", restoredPkg?.projectId)
    assertEquals("4K Cinematic Master", restoredPkg?.projectName)
    assertEquals(AspectRatio.RATIO_16_9, restoredPkg?.settings?.aspectRatio)
    assertEquals(Resolution.RES_4K, restoredPkg?.settings?.resolution)
    assertEquals(FrameRate.FPS_60, restoredPkg?.settings?.fps)
    assertEquals(48000, restoredPkg?.settings?.sampleRateHz)

    // Verify timeline tracks
    val t = restoredPkg!!.timeline
    assertEquals(1, t.videoClips.size)
    assertEquals(500L, t.videoClips.first().sourceStartMs)
    assertEquals(4500L, t.videoClips.first().sourceEndMs)
    assertEquals(1.5f, t.videoClips.first().speed, 0.01f)
    assertEquals(2, t.videoClips.first().keyframes.size)

    assertEquals(1, t.overlayClips.size)
    assertEquals("overlay_pip", t.overlayClips.first().id)

    assertEquals(1, t.audioClips.size)
    assertEquals(600L, t.audioClips.first().fadeInMs)
    assertEquals(800L, t.audioClips.first().fadeOutMs)
    assertEquals(2.5f, t.audioClips.first().gainDb, 0.01f)

    assertEquals(1, t.textClips.size)
    assertEquals(4, t.textClips.first().words.size)
    assertEquals("Welcome", t.textClips.first().words.first().word)

    assertEquals(1, t.stickerClips.size)
    assertEquals("🔥", t.stickerClips.first().emojiOrAsset)

    assertEquals(1, t.effectClips.size)
    assertEquals(EffectType.GLITCH, t.effectClips.first().effectType)

    assertEquals(1, t.transitions.size)
    assertEquals(TransitionType.WIPE, t.transitions.first().type)

    assertEquals(0.1f, t.adjustments.brightness, 0.01f)
    assertEquals(FilterType.CINEMATIC, t.filter.type)
    assertTrue(t.chromaKey.enabled)
    assertEquals(0.42f, t.chromaKey.similarity, 0.01f)

    // Verify media references and source metadata extracted
    assertEquals(3, restoredPkg.mediaReferences.size) // video, overlay, audio
    assertTrue(restoredPkg.mediaReferences.any { it.clipId == "vid_main" && it.mediaType == "VIDEO" })
    assertTrue(restoredPkg.mediaReferences.any { it.clipId == "audio_bgm" && it.mediaType == "AUDIO" })
    assertEquals(3, restoredPkg.sourceMetadata.size)
  }
}
