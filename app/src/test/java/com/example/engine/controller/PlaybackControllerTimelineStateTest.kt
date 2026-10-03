package com.example.engine.controller

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackControllerTimelineStateTest {

  private lateinit var context: Context
  private lateinit var playbackController: PlaybackController
  private var lastReportedTimelinePos: Long = -1L
  private var playbackEndedCalled: Boolean = false

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    lastReportedTimelinePos = -1L
    playbackEndedCalled = false
    try {
      playbackController = PlaybackController(
        context = context,
        onTimelinePositionChanged = { pos -> lastReportedTimelinePos = pos },
        onPlaybackEnded = { playbackEndedCalled = true }
      )
    } catch (t: Throwable) {
      assumeNoException("Media3 is unavailable in Robolectric environment", t)
    }
  }

  @Test
  fun testInitialTimelineState() {
    val state = playbackController.timelineState.value
    assertEquals(0L, state.positionMs)
    assertEquals(0L, state.positionUs)
    assertFalse(state.isPlaying)
    assertFalse(state.isScrubbing)
    assertFalse(state.isSeeking)
    assertNull(state.activeClip)
    assertEquals(EnginePlaybackState.IDLE, state.playbackState)
    assertEquals(0L, state.totalDurationMs)
  }

  @Test
  fun testTimelineUpdateSyncsState() {
    val clip1 = VideoClip(
      id = "clip_1",
      name = "vid1.mp4",
      uri = "content://media/vid1.mp4",
      durationMs = 4000L,
      timelineStartMs = 0L,
      sourceStartMs = 0L,
      sourceEndMs = 4000L,
      isVideo = true
    )
    val clip2 = VideoClip(
      id = "clip_2",
      name = "vid2.mp4",
      uri = "content://media/vid2.mp4",
      durationMs = 6000L,
      timelineStartMs = 4000L,
      sourceStartMs = 0L,
      sourceEndMs = 6000L,
      isVideo = true
    )
    val timeline = Timeline(videoClips = listOf(clip1, clip2))
    playbackController.updateTimeline(timeline)

    val state = playbackController.timelineState.value
    assertEquals(10000L, state.totalDurationMs)
    assertEquals(0L, state.positionMs)
    assertNotNull(state.activeClip)
    assertEquals("clip_1", state.activeClip?.id)
  }

  @Test
  fun testSeekToTimelineSynchronizesPlayheadAndClip() {
    val clip1 = VideoClip(
      id = "clip_1",
      name = "vid1.mp4",
      uri = "content://media/vid1.mp4",
      durationMs = 5000L,
      timelineStartMs = 0L,
      sourceStartMs = 0L,
      sourceEndMs = 5000L,
      isVideo = true
    )
    val clip2 = VideoClip(
      id = "clip_2",
      name = "vid2.mp4",
      uri = "content://media/vid2.mp4",
      durationMs = 5000L,
      timelineStartMs = 5000L,
      sourceStartMs = 0L,
      sourceEndMs = 5000L,
      isVideo = true
    )
    playbackController.updateTimeline(Timeline(videoClips = listOf(clip1, clip2)))

    playbackController.seekToTimeline(2500L, resumeAfter = false, exact = true)
    var state = playbackController.timelineState.value
    assertEquals(2500L, state.positionMs)
    assertEquals(2500000L, state.positionUs)
    assertEquals("clip_1", state.activeClip?.id)
    assertEquals(2500L, lastReportedTimelinePos)
    assertFalse(state.isPlaying)
    assertFalse(state.isSeeking)

    // Seek across clip boundary into clip_2
    playbackController.seekToTimeline(7200L, resumeAfter = false, exact = true)
    state = playbackController.timelineState.value
    assertEquals(7200L, state.positionMs)
    assertEquals(7200000L, state.positionUs)
    assertEquals("clip_2", state.activeClip?.id)
    assertEquals(7200L, lastReportedTimelinePos)
  }

  @Test
  fun testScrubbingLifecycle() {
    val clip = VideoClip(
      id = "clip_1",
      name = "vid1.mp4",
      uri = "content://media/vid1.mp4",
      durationMs = 10000L,
      timelineStartMs = 0L,
      sourceStartMs = 0L,
      sourceEndMs = 10000L,
      isVideo = true
    )
    playbackController.updateTimeline(Timeline(videoClips = listOf(clip)))

    playbackController.startScrubbing()
    assertTrue(playbackController.timelineState.value.isScrubbing)

    playbackController.scrubToTimeline(4500L)
    assertEquals(4500L, playbackController.timelineState.value.positionMs)
    assertEquals(4500L, lastReportedTimelinePos)

    playbackController.stopScrubbingTimeline(6000L, resumeAfter = false)
    assertFalse(playbackController.timelineState.value.isScrubbing)
    assertEquals(6000L, playbackController.timelineState.value.positionMs)
    assertEquals(6000L, lastReportedTimelinePos)
  }

  @Test
  fun testPlayPauseCycle() {
    val clip = VideoClip(
      id = "clip_1",
      name = "vid1.mp4",
      uri = "content://media/vid1.mp4",
      durationMs = 8000L,
      timelineStartMs = 0L,
      sourceStartMs = 0L,
      sourceEndMs = 8000L,
      isVideo = true
    )
    playbackController.updateTimeline(Timeline(videoClips = listOf(clip)))

    playbackController.playTimeline(1000L)
    var state = playbackController.timelineState.value
    assertTrue(state.isPlaying)
    assertEquals(1000L, state.positionMs)
    assertEquals(EnginePlaybackState.PLAYING, state.playbackState)

    playbackController.pauseTimeline()
    state = playbackController.timelineState.value
    assertFalse(state.isPlaying)
    assertEquals(EnginePlaybackState.PAUSED, state.playbackState)
  }

  @Test
  fun testPlayTimelineWrapsAtEnd() {
    val clip = VideoClip(
      id = "clip_1",
      name = "vid1.mp4",
      uri = "content://media/vid1.mp4",
      durationMs = 5000L,
      timelineStartMs = 0L,
      sourceStartMs = 0L,
      sourceEndMs = 5000L,
      isVideo = true
    )
    playbackController.updateTimeline(Timeline(videoClips = listOf(clip)))

    // Start at or beyond end of timeline -> should wrap to 0
    playbackController.playTimeline(5000L)
    val state = playbackController.timelineState.value
    assertEquals(0L, state.positionMs)
    assertTrue(state.isPlaying)
  }
}
