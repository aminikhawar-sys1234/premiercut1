package com.example

import com.example.domain.model.AudioClip
import com.example.domain.model.TextClip
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.SelectedTrackElement
import com.example.engine.TimelineEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TimelineSynchronizationTest {

  private lateinit var timelineEngine: TimelineEngine

  @Before
  fun setUp() {
    timelineEngine = TimelineEngine()
    val initialTimeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Video 1", uri = "file:///v1.mp4", durationMs = 4000L, timelineStartMs = 0L),
        VideoClip(id = "v2", name = "Video 2", uri = "file:///v2.mp4", durationMs = 4000L, timelineStartMs = 4000L)
      ),
      audioClips = listOf(
        AudioClip(id = "a1", title = "Audio 1", uri = "file:///a1.mp3", durationMs = 8000L, timelineStartMs = 0L)
      ),
      overlayClips = listOf(
        VideoClip(id = "o1", name = "Overlay 1", uri = "file:///o1.png", durationMs = 4000L, timelineStartMs = 1000L)
      ),
      textClips = listOf(
        TextClip(id = "t1", text = "Text 1", durationMs = 3000L, timelineStartMs = 500L)
      )
    )
    timelineEngine.loadTimeline(initialTimeline)
  }

  @Test
  fun testDirectClipMovementDoesNotMoveUnrelatedTracks() {
    timelineEngine.setTracksSyncEnabled(true)
    assertTrue(timelineEngine.isTracksSyncEnabled.value)

    // Normal clip dragging must remain isolated even when the optional
    // synchronized-track mode is enabled.
    timelineEngine.moveClipByDelta("v1", 1000L, snap = false)

    val currentTimeline = timelineEngine.timeline.value
    assertEquals(1000L, currentTimeline.videoClips.find { it.id == "v1" }!!.timelineStartMs)
    assertEquals(4000L, currentTimeline.videoClips.find { it.id == "v2" }!!.timelineStartMs)
    assertEquals(0L, currentTimeline.audioClips.find { it.id == "a1" }!!.timelineStartMs)
    assertEquals(1000L, currentTimeline.overlayClips.find { it.id == "o1" }!!.timelineStartMs)
    assertEquals(500L, currentTimeline.textClips.find { it.id == "t1" }!!.timelineStartMs)
  }

  @Test
  fun testExplicitSynchronizedMovementStillMovesAllUnlockedTracks() {
    timelineEngine.setTracksSyncEnabled(true)
    assertTrue(timelineEngine.moveSynchronizedTracksByDelta(1000L, snap = false))

    val currentTimeline = timelineEngine.timeline.value
    assertEquals(1000L, currentTimeline.videoClips.find { it.id == "v1" }!!.timelineStartMs)
    assertEquals(5000L, currentTimeline.videoClips.find { it.id == "v2" }!!.timelineStartMs)
    assertEquals(1000L, currentTimeline.audioClips.find { it.id == "a1" }!!.timelineStartMs)
    assertEquals(2000L, currentTimeline.overlayClips.find { it.id == "o1" }!!.timelineStartMs)
    assertEquals(1500L, currentTimeline.textClips.find { it.id == "t1" }!!.timelineStartMs)
  }

  @Test
  fun testPlayheadUsesExactMillisecondPosition() {
    timelineEngine.setPosition(5500L, snap = false)
    assertEquals(5500L, timelineEngine.currentPositionMs.value)

    timelineEngine.setPosition(7250L, snap = false)
    assertEquals(7250L, timelineEngine.currentPositionMs.value)
  }

  @Test
  fun testSplitAllTracksAtPlayhead() {
    // Position CTI at 2000ms:
    // - v1 spans 0..4000ms -> should split at 2000ms
    // - a1 spans 0..8000ms -> should split at 2000ms
    // - o1 spans 1000..5000ms -> should split at 2000ms
    // - t1 spans 500..3500ms -> should split at 2000ms
    timelineEngine.setPosition(2000L)

    val splitResult = timelineEngine.splitAllTracksAtPlayhead()
    assertTrue(splitResult)

    val currentTimeline = timelineEngine.timeline.value
    assertEquals(3, currentTimeline.videoClips.size) // v1 was split into 2 + v2 = 3
    assertEquals(2, currentTimeline.audioClips.size) // a1 was split into 2
    assertEquals(2, currentTimeline.overlayClips.size) // o1 was split into 2
    assertEquals(2, currentTimeline.textClips.size) // t1 was split into 2

    // Check first part of v1 ends at 2000ms, second part starts at 2000ms
    val vPart1 = currentTimeline.videoClips[0]
    val vPart2 = currentTimeline.videoClips[1]
    assertEquals(0L, vPart1.timelineStartMs)
    assertEquals(2000L, vPart1.durationMs)
    assertEquals(2000L, vPart2.timelineStartMs)
    assertEquals(2000L, vPart2.durationMs)
  }

  @Test
  fun testTrimClipRightToPlayheadEndsExactlyAtCti() {
    timelineEngine.selectElement(SelectedTrackElement.Video("v1"))
    // CTI at 2500ms
    timelineEngine.setPosition(2500L)
    val trimmed = timelineEngine.trimClipRightToPlayhead("v1")
    assertTrue(trimmed)

    val v1 = timelineEngine.timeline.value.videoClips.find { it.id == "v1" }!!
    assertEquals(2500L, v1.timelineStartMs + v1.durationMs)
  }

  @Test
  fun testMoveSelectedClipToPlayheadAlignsStartAtCti() {
    timelineEngine.setTracksSyncEnabled(false)
    timelineEngine.selectElement(SelectedTrackElement.Overlay("o1"))
    // CTI at 3000ms
    timelineEngine.setPosition(3000L)
    val moved = timelineEngine.moveSelectedClipToPlayhead()
    assertTrue(moved)

    val o1 = timelineEngine.timeline.value.overlayClips.find { it.id == "o1" }!!
    assertEquals(3000L, o1.timelineStartMs)
  }
}
