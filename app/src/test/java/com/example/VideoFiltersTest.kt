package com.example

import com.example.domain.model.*
import com.example.engine.SelectedTrackElement
import com.example.engine.TimelineEngine
import com.example.engine.composition.ColorFilterGenerator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoFiltersTest {

  private lateinit var timelineEngine: TimelineEngine

  @Before
  fun setUp() {
    timelineEngine = TimelineEngine()
    timelineEngine.loadTimeline(Timeline())
  }

  @Test
  fun testAllRequestedFilterTypesExist() {
    val requestedNames = listOf(
      "4K",
      "Blacklight Fix",
      "Enhance",
      "HDR",
      "Glow",
      "Focus",
      "Quality Restoration",
      "Golden Autumn",
      "Oceanic View",
      "Almond",
      "Sunlight Orange Blue"
    )

    val actualDisplayNames = FilterType.values().map { it.displayName }
    for (name in requestedNames) {
      assertTrue("Filter $name should be present in FilterType", actualDisplayNames.contains(name))
    }
  }

  @Test
  fun testColorFilterGeneratorProducesValidMatrices() {
    val requestedFilters = listOf(
      FilterType.FOUR_K,
      FilterType.BLACKLIGHT_FIX,
      FilterType.ENHANCE,
      FilterType.HDR,
      FilterType.GLOW,
      FilterType.FOCUS,
      FilterType.QUALITY_RESTORATION,
      FilterType.GOLDEN_AUTUMN,
      FilterType.OCEANIC_VIEW,
      FilterType.ALMOND,
      FilterType.SUNLIGHT_ORANGE_BLUE
    )

    for (filter in requestedFilters) {
      val matrix = ColorFilterGenerator.getFilterMatrix(filter, 1.0f)
      assertNotNull("Matrix for ${filter.displayName} must not be null", matrix)
      assertEquals("Matrix array length must be 20", 20, matrix!!.array.size)

      val matrix50 = ColorFilterGenerator.getFilterMatrix(filter, 0.5f)
      assertNotNull("Matrix at 50% intensity for ${filter.displayName} must not be null", matrix50)

      val array = ColorFilterGenerator.getFilterMatrixArray(filter, 1.0f)
      assertEquals("Matrix array helper must return 20 floats", 20, array.size)
    }

    // Original / NONE returns null
    assertNull("Matrix for NONE should be null", ColorFilterGenerator.getFilterMatrix(FilterType.NONE, 1.0f))
  }

  @Test
  fun testCombinedMatrixIntegration() {
    val adjustments = VideoAdjustments(
      brightness = 0.1f,
      contrast = 1.2f,
      saturation = 1.3f
    )
    val filterSettings = FilterSettings(type = FilterType.FOUR_K, intensity = 0.85f)
    val clipFilter = FilterSettings(type = FilterType.GOLDEN_AUTUMN, intensity = 1.0f)

    val globalCombined = ColorFilterGenerator.createCombinedMatrix(adjustments, filterSettings)
    assertNotNull(globalCombined)
    assertEquals(20, globalCombined.array.size)

    val clipCombined = ColorFilterGenerator.createCombinedMatrix(adjustments, filterSettings, clipFilter)
    assertNotNull(clipCombined)
    assertEquals(20, clipCombined.array.size)
  }

  @Test
  fun testFilterApplicationIsNonDestructiveToOtherTracks() {
    // Setup timeline with video, audio, text, sticker, and effect
    val initialTimeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "v1", uri = "uri1", durationMs = 5000L, timelineStartMs = 0L),
        VideoClip(id = "v2", name = "v2", uri = "uri2", durationMs = 5000L, timelineStartMs = 5000L)
      ),
      audioClips = listOf(
        AudioClip(id = "a1", title = "a1", uri = "a_uri", durationMs = 10000L, timelineStartMs = 0L)
      ),
      textClips = listOf(
        TextClip(id = "t1", text = "Title", timelineStartMs = 0L, durationMs = 4000L)
      ),
      stickerClips = listOf(
        StickerClip(id = "s1", emojiOrAsset = "🔥", timelineStartMs = 1000L, durationMs = 2000L)
      ),
      effectClips = listOf(
        EffectClip(id = "e1", effectType = EffectType.GLITCH, timelineStartMs = 0L, durationMs = 2000L)
      )
    )
    timelineEngine.loadTimeline(initialTimeline)

    val initialDuration = timelineEngine.timeline.value.totalDurationMs
    assertEquals(10000L, initialDuration)

    // Select video1 and apply 4K filter
    timelineEngine.selectElement(SelectedTrackElement.Video("v1"))
    timelineEngine.updateFilter(FilterSettings(type = FilterType.FOUR_K, intensity = 0.9f))

    val afterFilter = timelineEngine.timeline.value
    // Verify tracks are intact and unchanged in position/duration
    assertEquals(2, afterFilter.videoClips.size)
    assertEquals(1, afterFilter.audioClips.size)
    assertEquals(1, afterFilter.textClips.size)
    assertEquals(1, afterFilter.stickerClips.size)
    assertEquals(1, afterFilter.effectClips.size)
    assertEquals(initialDuration, afterFilter.totalDurationMs)

    // Verify clip 1 has the filter
    val updatedV1 = afterFilter.videoClips.find { it.id == "v1" }!!
    assertEquals(FilterType.FOUR_K, updatedV1.filter?.type)
    assertEquals(0.9f, updatedV1.filter?.intensity)

    // Verify clip 2 has not been disturbed
    val v2 = afterFilter.videoClips.find { it.id == "v2" }!!
    assertEquals(5000L, v2.timelineStartMs)
    assertEquals(5000L, v2.durationMs)

    // Filters are intentionally clip-local; there is no project-wide "apply all" path.
    timelineEngine.selectElement(SelectedTrackElement.Video("v2"))
    timelineEngine.updateFilter(FilterSettings(type = FilterType.GOLDEN_AUTUMN, intensity = 1.0f), "v2")
    val afterSecondFilter = timelineEngine.timeline.value
    assertEquals(FilterType.GOLDEN_AUTUMN, afterSecondFilter.videoClips.find { it.id == "v2" }!!.filter?.type)
    assertEquals(FilterType.FOUR_K, afterSecondFilter.videoClips.find { it.id == "v1" }!!.filter?.type)

    // Remove v1's filter through the same clip-local API.
    timelineEngine.updateFilter(FilterSettings(type = FilterType.NONE, intensity = 1.0f), "v1")
    val afterRemove = timelineEngine.timeline.value
    assertEquals(FilterType.NONE, afterRemove.videoClips.find { it.id == "v1" }!!.filter?.type)
    assertEquals(FilterType.GOLDEN_AUTUMN, afterRemove.videoClips.find { it.id == "v2" }!!.filter?.type)
  }
}
