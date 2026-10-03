package com.example

import com.example.domain.model.AudioClip
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.TimelineEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TimelineMultiSelectTest {

  private lateinit var timelineEngine: TimelineEngine

  @Before
  fun setUp() {
    timelineEngine = TimelineEngine()
    val initialTimeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Video 1", uri = "file:///v1.mp4", durationMs = 3000L, timelineStartMs = 0L),
        VideoClip(id = "v2", name = "Video 2", uri = "file:///v2.mp4", durationMs = 4000L, timelineStartMs = 3000L),
        VideoClip(id = "v3", name = "Video 3", uri = "file:///v3.mp4", durationMs = 5000L, timelineStartMs = 7000L)
      ),
      audioClips = listOf(
        AudioClip(id = "a1", title = "Audio 1", uri = "file:///a1.mp3", durationMs = 6000L, timelineStartMs = 1000L)
      ),
      overlayClips = listOf(
        VideoClip(id = "o1", name = "Overlay 1", uri = "file:///o1.png", durationMs = 2000L, timelineStartMs = 2000L)
      )
    )
    timelineEngine.loadTimeline(initialTimeline)
  }

  @Test
  fun testSelectMultipleClipsAndToggle() {
    timelineEngine.selectClip("v1")
    assertEquals(setOf("v1"), timelineEngine.selectedClipIds.value)
    assertFalse(timelineEngine.isMultiSelectMode.value)

    timelineEngine.toggleSelectClip("v2")
    assertEquals(setOf("v1", "v2"), timelineEngine.selectedClipIds.value)
    assertTrue(timelineEngine.isMultiSelectMode.value)

    timelineEngine.toggleSelectClip("v1")
    assertEquals(setOf("v2"), timelineEngine.selectedClipIds.value)

    timelineEngine.selectAllClips()
    assertEquals(5, timelineEngine.selectedClipIds.value.size)
    assertTrue(timelineEngine.isMultiSelectMode.value)

    timelineEngine.clearSelection()
    assertTrue(timelineEngine.selectedClipIds.value.isEmpty())
    assertFalse(timelineEngine.isMultiSelectMode.value)
  }

  @Test
  fun testBulkMoveSelectedClips() {
    timelineEngine.selectMultipleClips(setOf("v1", "v2"))
    assertTrue(timelineEngine.isMultiSelectMode.value)

    // Move both clips right by 1500ms
    timelineEngine.moveClipByDelta("v1", 1500L, snap = false)

    val videoClips = timelineEngine.timeline.value.videoClips
    val v1 = videoClips.find { it.id == "v1" }!!
    val v2 = videoClips.find { it.id == "v2" }!!
    val v3 = videoClips.find { it.id == "v3" }!!

    assertEquals(1500L, v1.timelineStartMs)
    assertEquals(4500L, v2.timelineStartMs)
    assertEquals(7000L, v3.timelineStartMs) // untouched
  }

  @Test
  fun testBulkMoveClampedToZero() {
    timelineEngine.selectMultipleClips(setOf("v1", "v2"))
    // v1 is at 0ms, moving by -1000ms should clamp delta to 0 or minStart
    timelineEngine.moveClipByDelta("v1", -1000L, snap = false)

    val videoClips = timelineEngine.timeline.value.videoClips
    val v1 = videoClips.find { it.id == "v1" }!!
    val v2 = videoClips.find { it.id == "v2" }!!

    assertEquals(0L, v1.timelineStartMs)
    assertEquals(3000L, v2.timelineStartMs)
  }

  @Test
  fun testBulkNormalDelete() {
    timelineEngine.selectMultipleClips(setOf("v1", "a1"))
    val success = timelineEngine.normalDelete()
    assertTrue(success)

    val videoClips = timelineEngine.timeline.value.videoClips
    val audioClips = timelineEngine.timeline.value.audioClips

    assertEquals(2, videoClips.size)
    assertFalse(videoClips.any { it.id == "v1" })
    assertTrue(audioClips.isEmpty())
    assertTrue(timelineEngine.selectedClipIds.value.isEmpty())
  }

  @Test
  fun testBulkRippleDelete() {
    timelineEngine.selectMultipleClips(setOf("v1", "v2"))
    val success = timelineEngine.rippleDelete()
    assertTrue(success)

    val videoClips = timelineEngine.timeline.value.videoClips
    assertEquals(1, videoClips.size)
    assertEquals("v3", videoClips[0].id)
    assertEquals(0L, videoClips[0].timelineStartMs)
  }
}
