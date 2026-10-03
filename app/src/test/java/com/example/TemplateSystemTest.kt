package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TimelineSerializer
import com.example.data.presets.PlaceholderType
import com.example.data.presets.TemplatesCatalog
import com.example.data.repository.ProjectRepository
import com.example.domain.model.AspectRatio
import com.example.domain.model.Resolution
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemplateSystemTest {

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
  fun `verify all 11 required categories exist in TemplatesCatalog`() {
    val requiredCategories = listOf(
      "Reels",
      "TikTok-style short videos",
      "YouTube",
      "YouTube Shorts",
      "Instagram",
      "Business",
      "Product Ads",
      "Birthday",
      "Wedding",
      "Travel",
      "Cinematic"
    )

    val availableCategories = TemplatesCatalog.templates.map { it.category }.toSet()

    for (required in requiredCategories) {
      assertTrue("TemplatesCatalog must contain category '$required'", availableCategories.contains(required))
    }
  }

  @Test
  fun `verify templates have reusable structure with placeholders keyframes effects and audio`() {
    val templates = TemplatesCatalog.templates
    assertTrue(templates.size >= 11)

    templates.forEach { template ->
      assertNotNull("Template title should not be null", template.title)
      assertTrue("Template must have a valid duration", template.durationMs > 0)
      assertTrue("Template must have media placeholders", template.mediaPlaceholders.isNotEmpty())
      assertTrue("Template must have text placeholders", template.textPlaceholders.isNotEmpty())

      // Verify template creates valid timeline
      val defaultTimeline = template.createDefaultTimeline()
      assertTrue("Timeline for '${template.title}' must have video clips", defaultTimeline.videoClips.isNotEmpty())
      assertTrue("Timeline for '${template.title}' must have text clips", defaultTimeline.textClips.isNotEmpty())
      assertTrue("Timeline for '${template.title}' must have an audio track", defaultTimeline.audioClips.isNotEmpty())
    }
  }

  @Test
  fun `test media and text placeholder replacement generates customized editable timeline`() {
    val reelsTemplate = TemplatesCatalog.templates.first { it.category == "Reels" }

    val userMedia = mapOf(
      "v1" to "content://media/user_hook.mp4",
      "v2" to "content://media/user_action.mp4",
      "i1" to "content://media/user_photo.jpg",
      "v3" to "content://media/user_climax.mp4"
    )

    val userTexts = mapOf(
      "t1" to "SUMMER SPECIAL 2026",
      "t2" to "EXCLUSIVE LOOK ONLY TODAY"
    )

    val customizedTimeline = reelsTemplate.createTimeline(userMedia, userTexts)

    // Verify media placeholders replaced
    val v1 = customizedTimeline.videoClips.first { it.id == "reels_vid_1" }
    assertEquals("content://media/user_hook.mp4", v1.uri)
    assertEquals(2000L, v1.durationMs)

    val img = customizedTimeline.videoClips.first { it.id == "reels_img_1" }
    assertEquals("content://media/user_photo.jpg", img.uri)
    assertFalse(img.isVideo)
    assertTrue("Image must have zoom keyframes", img.keyframes.isNotEmpty())

    // Verify text placeholders replaced
    val t1 = customizedTimeline.textClips.first { it.id == "reels_txt_1" }
    assertEquals("SUMMER SPECIAL 2026", t1.text)
    assertEquals("Pop", t1.animationType)

    val t2 = customizedTimeline.textClips.first { it.id == "reels_txt_2" }
    assertEquals("EXCLUSIVE LOOK ONLY TODAY", t2.text)

    // Verify transitions, effects, and audio preserved
    assertTrue("Transitions should be preserved", customizedTimeline.transitions.isNotEmpty())
    assertTrue("Effects should be preserved", customizedTimeline.effectClips.isNotEmpty())
    assertEquals(1, customizedTimeline.audioClips.size)
  }

  @Test
  fun `test template project can be saved and reloaded with full editability`() = runBlocking {
    val weddingTemplate = TemplatesCatalog.templates.first { it.category == "Wedding" }
    val timeline = weddingTemplate.createTimeline(
      mapOf("v1" to "file:///couple_dance.mp4"),
      mapOf("t1" to "Emma & Oliver", "t2" to "JUNE 20, 2026")
    )

    val saved = repository.saveProject(
      id = "proj_wedding_custom",
      name = "${weddingTemplate.title} Project",
      durationMs = weddingTemplate.durationMs,
      thumbnailPath = "",
      aspectRatio = weddingTemplate.aspectRatio.label,
      resolution = weddingTemplate.resolution.label,
      fps = weddingTemplate.fps.fps,
      timeline = timeline
    )

    assertNotNull(saved)
    assertEquals("proj_wedding_custom", saved.id)

    // Retrieve and deserialize
    val fetched = repository.getProjectById("proj_wedding_custom")
    assertNotNull(fetched)
    val restored = TimelineSerializer.fromJson(fetched!!.timelineJson)

    assertEquals(2, restored.videoClips.size)
    assertEquals("file:///couple_dance.mp4", restored.videoClips.first().uri)
    assertEquals("Emma & Oliver", restored.textClips.first().text)
    assertEquals("Emotional Piano & Orchestra", restored.audioClips.first().title)

    // Verify user can modify everything (e.g. trim clip, change speed)
    val editedClip = restored.videoClips.first().copy(durationMs = 5000L, speed = 1.5f)
    val editedTimeline = restored.copy(videoClips = listOf(editedClip))
    assertEquals(5000L, editedTimeline.videoClips.first().durationMs)
    assertEquals(1.5f, editedTimeline.videoClips.first().speed, 0.01f)
  }
}
