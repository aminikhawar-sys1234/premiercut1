package com.example

import com.example.domain.model.*
import com.example.engine.SelectedTrackElement
import com.example.engine.TimelineEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TimelineEngineUpgradeTest {

  private lateinit var engine: TimelineEngine

  @Before
  fun setUp() {
    engine = TimelineEngine()
  }

  @Test
  fun testFpsAndTimeConversionsAreAccurate() {
    engine.setTimelineFps(30)
    assertEquals(30, engine.timelineFps.value)

    // 1000ms at 30fps -> 30 frames
    assertEquals(30L, engine.timeToFrame(1000L, 30))
    assertEquals(1000L, engine.frameToTime(30L, 30))

    // 500ms at 30fps -> 15 frames
    assertEquals(15L, engine.timeToFrame(500L, 30))
    assertEquals(500L, engine.frameToTime(15L, 30))

    // Microseconds conversions
    assertEquals(1_000_000L, engine.timeMsToMicros(1000L))
    assertEquals(1000L, engine.microsToTimeMs(1_000_000L))
    assertEquals(1_000_000L, engine.frameToMicros(30L, 30))
    assertEquals(30L, engine.microsToFrame(1_000_000L, 30))

    // Frame duration
    assertEquals(33L, engine.frameDurationMs(30))

    // Frame alignment
    assertEquals(0L, engine.alignToFrame(10L, 30))
    assertEquals(33L, engine.alignToFrame(35L, 30))
    assertEquals(1000L, engine.alignToFrame(1005L, 30))
  }

  @Test
  fun testMultiTrackVideoAndAudioManagement() {
    // Add primary video clip on track 0
    val v1Id = engine.addVideoClip(
      uri = "file:///video1.mp4",
      name = "Main Video 1",
      durationMs = 4000L
    )

    // Add overlay video on track 1
    val vOverlay1Id = engine.addVideoClipToTrack(
      trackIndex = 1,
      uri = "file:///overlay1.mp4",
      name = "Overlay 1",
      startMs = 1000L,
      durationMs = 2000L
    )

    // Add overlay video on track 2
    val vOverlay2Id = engine.addVideoClipToTrack(
      trackIndex = 2,
      uri = "file:///overlay2.mp4",
      name = "Overlay 2",
      startMs = 1500L,
      durationMs = 2500L
    )

    // Add audio tracks (Track 0 = BGM, Track 1 = Voiceover)
    val bgmId = engine.addAudioClipToTrack(
      trackIndex = 0,
      uri = "file:///bgm.mp3",
      title = "Background Music",
      startMs = 0L,
      durationMs = 6000L,
      volume = 0.8f
    )
    val voId = engine.addAudioClipToTrack(
      trackIndex = 1,
      uri = "file:///voiceover.m4a",
      title = "Voiceover",
      startMs = 1000L,
      durationMs = 3000L,
      volume = 1.0f
    )

    // Verify track indices
    val videoTracks = engine.getVideoTrackIndices()
    assertTrue(videoTracks.contains(0))
    assertTrue(videoTracks.contains(1))
    assertTrue(videoTracks.contains(2))

    val audioTracks = engine.getAudioTrackIndices()
    assertTrue(audioTracks.contains(0))
    assertTrue(audioTracks.contains(1))

    // Verify track clip queries
    assertEquals(1, engine.getVideoClipsForTrack(0).size)
    assertEquals(1, engine.getVideoClipsForTrack(1).size)
    assertEquals(1, engine.getVideoClipsForTrack(2).size)
    assertEquals(1, engine.getAudioClipsForTrack(0).size)
    assertEquals(1, engine.getAudioClipsForTrack(1).size)

    assertEquals("Main Video 1", engine.getVideoClipsForTrack(0).first().name)
    assertEquals("Overlay 1", engine.getVideoClipsForTrack(1).first().name)
    assertEquals("Overlay 2", engine.getVideoClipsForTrack(2).first().name)
  }

  @Test
  fun testSplitClipAtTimeAndKeyframePreservation() {
    val clipId = engine.addVideoClip(
      uri = "file:///video.mp4",
      name = "Video Clip",
      durationMs = 6000L
    )

    // Add keyframes before and after split position
    val kf1 = ClipKeyframe(timeMs = 1000L, scale = 1.2f, posX = 0.1f)
    val kf2 = ClipKeyframe(timeMs = 5000L, scale = 1.5f, posX = 0.3f)
    engine.addKeyframeToClip(clipId, kf1)
    engine.addKeyframeToClip(clipId, kf2)

    val splitResult = engine.splitClipAtTime(clipId, 3000L)
    assertNotNull(splitResult)
    val (part1Id, part2Id) = splitResult!!

    val timeline = engine.timeline.value
    val part1 = timeline.videoClips.find { it.id == part1Id }!!
    val part2 = timeline.videoClips.find { it.id == part2Id }!!

    assertEquals(0L, part1.timelineStartMs)
    assertEquals(3000L, part1.durationMs)
    assertEquals(3000L, part2.timelineStartMs)
    assertEquals(3000L, part2.durationMs)

    // Part 1 should have keyframe at 1000ms
    assertEquals(1, part1.keyframes.size)
    assertEquals(1000L, part1.keyframes[0].timeMs)

    // Part 2 should have keyframe shifted to 5000 - 3000 = 2000ms
    assertEquals(1, part2.keyframes.size)
    assertEquals(2000L, part2.keyframes[0].timeMs)
    assertEquals(1.5f, part2.keyframes[0].scale, 0.001f)
  }

  @Test
  fun testAudioSynchronizationAndFades() {
    val audioId = engine.addAudioClipToTrack(
      trackIndex = 0,
      uri = "file:///song.mp3",
      title = "Song",
      startMs = 1000L,
      durationMs = 4000L,
      volume = 1.0f,
      fadeInMs = 1000L,
      fadeOutMs = 1000L
    )

    val clip = engine.timeline.value.audioClips.find { it.id == audioId }!!

    // Before start -> volume 0
    assertEquals(0f, engine.getEffectiveAudioVolumeAt(clip, 500L), 0.001f)

    // At start (1000ms) -> volume 0 (fade in start)
    assertEquals(0f, engine.getEffectiveAudioVolumeAt(clip, 1000L), 0.001f)

    // Midway through fade in (1500ms, 500ms in) -> (0.5)^2 = 0.25
    assertEquals(0.25f, engine.getEffectiveAudioVolumeAt(clip, 1500L), 0.01f)

    // Middle of clip (3000ms) -> volume 1.0
    assertEquals(1.0f, engine.getEffectiveAudioVolumeAt(clip, 3000L), 0.001f)

    // Midway through fade out (4500ms, 500ms remaining) -> (0.5)^2 = 0.25
    assertEquals(0.25f, engine.getEffectiveAudioVolumeAt(clip, 4500L), 0.01f)

    // After end (5500ms) -> volume 0
    assertEquals(0f, engine.getEffectiveAudioVolumeAt(clip, 5500L), 0.001f)
  }

  @Test
  fun testActiveClipsSnapshotAtTimelinePosition() {
    engine.addVideoClip(
      uri = "file:///main.mp4",
      name = "Main",
      durationMs = 5000L
    )
    engine.addOverlayClip(
      uri = "file:///pip.mp4",
      name = "PIP",
      durationMs = 3000L,
      startTimeMs = 0L
    )
    engine.addAudioClip(
      uri = "file:///music.mp3",
      title = "Music",
      durationMs = 5000L,
      startTimeMs = 0L
    )
    engine.addTextClip(
      text = "Title Text",
      durationMs = 2000L,
      timelineStartMs = 0L
    )

    // Query active elements at 1000ms
    val snapshot = engine.getActiveClipsAt(1000L)
    assertNotNull(snapshot.primaryVideoClip)
    assertEquals("Main", snapshot.primaryVideoClip?.name)
    assertEquals(1, snapshot.activeOverlays.size)
    assertEquals(1, snapshot.activeAudioClips.size)
    assertEquals(1, snapshot.activeTextClips.size)

    // Query active elements at 4000ms (text was 2000ms starting at 0, so it should be inactive)
    val snapshotLate = engine.getActiveClipsAt(4000L)
    assertNotNull(snapshotLate.primaryVideoClip)
    assertEquals(0, snapshotLate.activeTextClips.size)
  }

  @Test
  fun testTransformationsAndKeyframeInterpolation() {
    val clipId = engine.addVideoClip(
      uri = "file:///video.mp4",
      name = "Video",
      durationMs = 4000L
    )

    engine.updateClipTransformation(
      clipId = clipId,
      posX = 0.2f,
      posY = -0.1f,
      scale = 1.25f,
      opacity = 0.9f
    )

    val updatedClip = engine.timeline.value.videoClips.find { it.id == clipId }!!
    assertEquals(0.2f, updatedClip.cropOffsetX, 0.001f)
    assertEquals(-0.1f, updatedClip.cropOffsetY, 0.001f)
    assertEquals(1.25f, updatedClip.cropScale, 0.001f)
    assertEquals(0.9f, updatedClip.opacity, 0.001f)
  }
}
