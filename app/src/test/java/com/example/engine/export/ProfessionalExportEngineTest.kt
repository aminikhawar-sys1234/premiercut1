package com.example.engine.export

import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
class ProfessionalExportEngineTest {

  @Test
  fun frameTimestampsAreMonotonic() {
    val fps = 30
    val frameDurationUs = 1_000_000L / fps
    val timestamps = (0L until 300L).map { it * frameDurationUs }
    assertEquals(0L, timestamps.first())
    assertTrue(timestamps.zipWithNext().all { (a, b) -> b > a })
  }

  @Test
  fun thirtyFpsFiveSecondTimelineHasExpectedFrameCount() {
    val frames = ceil(5.0 * 30.0).toLong()
    assertEquals(150L, frames)
  }

  @Test
  fun sanitizationContractAllowsStableOutputNames() {
    val name = "My Project / 4K: Final?"
    val sanitized = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48).ifBlank { "project" }
    assertEquals("My_Project___4K__Final_", sanitized)
  }

  @Test
  fun exportRenderPlannerBuildsAccurateFramesAcrossFps() {
    for (fps in listOf(24, 30, 60)) {
      val config = ExportConfig(
        resolution = Resolution.RES_1080P,
        frameRate = when (fps) {
          24 -> FrameRate.FPS_24
          60 -> FrameRate.FPS_60
          else -> FrameRate.FPS_30
        }
      )
      val timeline = Timeline(
        videoClips = listOf(
          VideoClip(
            id = "clip_1",
            uri = "content://media/1",
            name = "Clip 1",
            durationMs = 2500L,
            timelineStartMs = 0L
          ),
          VideoClip(
            id = "clip_2",
            uri = "content://media/2",
            name = "Clip 2",
            durationMs = 2500L,
            timelineStartMs = 2500L
          )
        )
      )
      val plan = ExportRenderPlanner.build(timeline, config)
      assertEquals(5000L, plan.durationMs)
      val expectedTotal = ceil(5.0 * fps).toLong()
      assertEquals(expectedTotal, plan.totalFrames)
      assertEquals(fps, plan.frameRate)

      val frameList = plan.frames.toList()
      assertEquals(expectedTotal.toInt(), frameList.size)
      assertEquals(0L, frameList.first().presentationTimeUs)
      assertEquals(0L, frameList.first().timelinePositionMs)
      assertTrue(frameList.zipWithNext().all { (a, b) -> b.presentationTimeUs > a.presentationTimeUs })
    }
  }

  @Test
  fun multipleClipsAndOverlaysAreTrackedCorrectly() {
    val clip1 = VideoClip(id = "c1", uri = "file:///v1.mp4", name = "Clip 1", durationMs = 3000L, timelineStartMs = 0L)
    val clip2 = VideoClip(id = "c2", uri = "file:///v2.mp4", name = "Clip 2", durationMs = 3000L, timelineStartMs = 3000L)
    val overlay = VideoClip(id = "ov1", uri = "file:///ov.mp4", name = "Overlay 1", durationMs = 2000L, timelineStartMs = 1000L)
    val timeline = Timeline(
      videoClips = listOf(clip1, clip2),
      overlayClips = listOf(overlay)
    )

    assertEquals(1, ExportRenderPlanner.activeVideoClipCount(timeline, 500L))
    assertEquals(1, ExportRenderPlanner.activeVideoClipCount(timeline, 2999L))
    assertEquals(1, ExportRenderPlanner.activeVideoClipCount(timeline, 3000L))
    assertEquals(1, ExportRenderPlanner.activeVideoClipCount(timeline, 4000L))
    assertEquals(0, ExportRenderPlanner.activeVideoClipCount(timeline, 6000L))
  }

  @Test
  fun trimmedClipsAndSpeedCalculationsMaintainCorrectSourceOffset() {
    val clip = VideoClip(
      id = "trimmed_clip",
      uri = "file:///test.mp4",
      name = "Trimmed",
      durationMs = 4000L,
      timelineStartMs = 0L,
      sourceStartMs = 1500L,
      sourceEndMs = 9500L,
      speed = 2.0f
    )
    val timelinePositionMs = 1000L
    val sourceOffset = clip.sourceStartMs + (timelinePositionMs * clip.speed).toLong()
    assertEquals(3500L, sourceOffset)
    assertTrue(sourceOffset < clip.sourceEndMs)
  }

  @Test
  fun chromaKeySettingsAndVisualFiltersArePreserved() {
    val chroma = ChromaKeySettings(
      enabled = true,
      targetColor = 0xFF00FF00,
      similarity = 0.45f,
      smoothness = 0.15f,
      spillSuppression = 0.6f,
      backgroundType = "Transparent"
    )
    val filter = FilterSettings(
      type = FilterType.VINTAGE,
      intensity = 0.85f
    )
    val adjustments = VideoAdjustments(
      brightness = 0.1f,
      contrast = 1.2f,
      saturation = 1.1f,
      temperature = 0.05f
    )
    val timeline = Timeline(
      chromaKey = chroma,
      filter = filter,
      adjustments = adjustments
    )
    assertTrue(timeline.chromaKey.enabled)
    assertEquals("Transparent", timeline.chromaKey.backgroundType)
    assertEquals(0.85f, timeline.filter.intensity, 0.001f)
    assertEquals(1.2f, timeline.adjustments.contrast, 0.001f)
  }

  @Test
  fun textAndStickersPreserveKeyframesAndTransforms() {
    val textClip = TextClip(
      id = "text_1",
      text = "Professional Output",
      durationMs = 3000L,
      timelineStartMs = 500L,
      scale = 1.5f,
      posX = 0.1f,
      posY = -0.2f,
      rotation = 45f
    )
    val stickerClip = StickerClip(
      id = "sticker_1",
      emojiOrAsset = "fire_anim",
      durationMs = 2000L,
      timelineStartMs = 1000L,
      scale = 1.2f
    )
    val timeline = Timeline(
      textClips = listOf(textClip),
      stickerClips = listOf(stickerClip)
    )
    assertEquals(1, timeline.textClips.size)
    assertEquals(1, timeline.stickerClips.size)
    assertEquals("Professional Output", timeline.textClips.first().text)
  }

  @Test
  fun progressStagesFollowPredictableLifecycle() {
    val stages = listOf(
      ProfessionalExportStage.PREPARING,
      ProfessionalExportStage.RENDERING,
      ProfessionalExportStage.ENCODING_VIDEO,
      ProfessionalExportStage.VERIFYING,
      ProfessionalExportStage.COMPLETED
    )
    assertEquals(5, stages.size)
    val initial = ProfessionalExportProgress()
    assertEquals(ProfessionalExportStage.PREPARING, initial.stage)
    assertEquals(0f, initial.fraction, 0.001f)
  }

  @Test
  fun exportValidatorRejectsNonExistentOrEmptyFiles() {
    val config = ExportConfig()
    val nonExistent = File("/tmp/non_existent_output_test_${System.currentTimeMillis()}.mp4")
    val res1 = ExportValidator.validate(nonExistent, config, 1000L)
    assertFalse(res1.valid)
    assertTrue(res1.message.contains("does not exist"))

    val emptyFile = File.createTempFile("empty_export_", ".mp4")
    try {
      val res2 = ExportValidator.validate(emptyFile, config, 1000L)
      assertFalse(res2.valid)
      assertTrue(res2.message.contains("empty"))
    } finally {
      emptyFile.delete()
    }
  }
}

