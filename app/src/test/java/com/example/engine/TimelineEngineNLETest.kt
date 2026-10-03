package com.example.engine

import com.example.domain.model.AudioClip
import com.example.domain.model.TextClip
import com.example.domain.model.Timeline
import com.example.domain.model.TrackType
import com.example.domain.model.Transition
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TimelineEngineNLETest {

  private lateinit var engine: TimelineEngine

  @Before
  fun setUp() {
    engine = TimelineEngine()
  }

  @Test
  fun testFrameAccurateMath() {
    engine.setTimelineFps(30)
    assertEquals(30, engine.fps)

    // At 30 FPS, 1000ms = 30 frames, 500ms = 15 frames, 33ms ~ 1 frame
    assertEquals(0L, engine.timeToFrame(0L))
    assertEquals(15L, engine.timeToFrame(500L))
    assertEquals(30L, engine.timeToFrame(1000L))

    assertEquals(0L, engine.frameToTime(0L))
    assertEquals(500L, engine.frameToTime(15L))
    assertEquals(1000L, engine.frameToTime(30L))

    // Microsecond timing
    assertEquals(1_000_000L, engine.timeMsToMicros(1000L))
    assertEquals(1000L, engine.microsToTimeMs(1_000_000L))
    assertEquals(30L, engine.microsToFrame(1_000_000L, 30))
    assertEquals(1_000_000L, engine.frameToMicros(30L, 30))

    // 60 FPS
    assertEquals(60L, engine.timeToFrame(1000L, 60))
    assertEquals(1000L, engine.frameToTime(60L, 60))
  }

  @Test
  fun testMultiTrackClipsAndOrdering() {
    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Clip 1", uri = "uri1", timelineStartMs = 0L, durationMs = 2000L, trackIndex = 0),
        VideoClip(id = "v2", name = "Clip 2", uri = "uri2", timelineStartMs = 2000L, durationMs = 3000L, trackIndex = 0)
      ),
      overlayClips = listOf(
        VideoClip(id = "ov1", name = "Overlay 1", uri = "ov_uri1", timelineStartMs = 500L, durationMs = 1500L, trackIndex = 1),
        VideoClip(id = "ov2", name = "Overlay 2", uri = "ov_uri2", timelineStartMs = 1000L, durationMs = 2000L, trackIndex = 2)
      ),
      audioClips = listOf(
        AudioClip(id = "a1", title = "Audio 1", uri = "a_uri1", timelineStartMs = 0L, durationMs = 2500L, trackIndex = 0),
        AudioClip(id = "a2", title = "Audio 2", uri = "a_uri2", timelineStartMs = 1000L, durationMs = 2000L, trackIndex = 1)
      ),
      textClips = listOf(
        TextClip(id = "t1", text = "Hello", timelineStartMs = 200L, durationMs = 1000L, trackIndex = 0),
        TextClip(id = "t2", text = "World", timelineStartMs = 800L, durationMs = 1500L, trackIndex = 1)
      )
    )
    engine.loadTimeline(timeline)

    // Verify track counts and indices
    val videoIndices = engine.getVideoTrackIndices()
    assertTrue(videoIndices.contains(0))
    assertTrue(videoIndices.contains(1))
    assertTrue(videoIndices.contains(2))
    assertEquals(3, engine.getVideoTrackCount())

    val audioIndices = engine.getAudioTrackIndices()
    assertTrue(audioIndices.contains(0))
    assertTrue(audioIndices.contains(1))
    assertEquals(2, engine.getAudioTrackCount())

    assertEquals(2, engine.getTextTrackCount())
  }

  @Test
  fun testActiveClipsAtTimelinePositions() {
    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Clip 1", uri = "uri1", timelineStartMs = 0L, durationMs = 2000L, trackIndex = 0),
        VideoClip(id = "v2", name = "Clip 2", uri = "uri2", timelineStartMs = 2000L, durationMs = 2000L, trackIndex = 0)
      ),
      overlayClips = listOf(
        VideoClip(id = "ov1", name = "Overlay 1", uri = "ov1", timelineStartMs = 500L, durationMs = 1500L, trackIndex = 2),
        VideoClip(id = "ov2", name = "Overlay 2", uri = "ov2", timelineStartMs = 800L, durationMs = 1000L, trackIndex = 1)
      ),
      textClips = listOf(
        TextClip(id = "t1", text = "Title", timelineStartMs = 500L, durationMs = 1000L, trackIndex = 0)
      )
    )
    engine.loadTimeline(timeline)

    // Query active clips at 1000ms
    val activeAt1000 = engine.getActiveClipsAt(1000L)
    assertNotNull(activeAt1000.primaryVideoClip)
    assertEquals("v1", activeAt1000.primaryVideoClip?.id)

    // Overlays should be sorted by trackIndex ascending (track 1 before track 2)
    assertEquals(2, activeAt1000.activeOverlays.size)
    assertEquals("ov2", activeAt1000.activeOverlays[0].id)
    assertEquals(1, activeAt1000.activeOverlays[0].trackIndex)
    assertEquals("ov1", activeAt1000.activeOverlays[1].id)
    assertEquals(2, activeAt1000.activeOverlays[1].trackIndex)

    // Text clip active
    assertEquals(1, activeAt1000.activeTextClips.size)
    assertEquals("t1", activeAt1000.activeTextClips[0].id)

    // Frame-based query at 30 FPS (1000ms = frame 30)
    val frameSnapshot = engine.getActiveClipsAtFrame(30L, 30)
    assertEquals(30L, frameSnapshot.frameIndex)
    assertEquals(1000L, frameSnapshot.timelinePosMs)
    assertEquals("v1", frameSnapshot.primaryVideoClip?.id)
  }

  @Test
  fun testOverlapDetectionAndResolution() {
    val timeline = Timeline(
      overlayClips = listOf(
        VideoClip(id = "ovA", name = "Overlay A", uri = "ovA", timelineStartMs = 0L, durationMs = 2000L, trackIndex = 1),
        VideoClip(id = "ovB", name = "Overlay B", uri = "ovB", timelineStartMs = 1000L, durationMs = 2000L, trackIndex = 1)
      )
    )
    engine.loadTimeline(timeline)

    val overlaps = engine.getTrackOverlaps(TrackType.OVERLAY, 1)
    assertEquals(1, overlaps.size)
    assertEquals("ovA", overlaps[0].clipAId)
    assertEquals("ovB", overlaps[0].clipBId)
    assertEquals(1000L, overlaps[0].overlapStartMs)
    assertEquals(2000L, overlaps[0].overlapEndMs)
    assertEquals(1000L, overlaps[0].overlapDurationMs)

    assertTrue(engine.hasOverlapOnTrack(TrackType.OVERLAY, 1, 500L, 1000L))

    // Resolve by ripple
    val resolved = engine.resolveOverlapByRipple("ovA", "ovB")
    assertTrue(resolved)

    // After ripple, ovB should start at 2000ms, eliminating the overlap
    val overlapsAfter = engine.getTrackOverlaps(TrackType.OVERLAY, 1)
    assertEquals(0, overlapsAfter.size)
  }

  @Test
  fun testClipTrimmingAndSplitting() {
    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Clip 1", uri = "uri1", timelineStartMs = 0L, durationMs = 4000L, sourceStartMs = 0L, sourceEndMs = 4000L, trackIndex = 0)
      )
    )
    engine.loadTimeline(timeline)

    val trimmedRangeBefore = engine.getClipTrimmedRange("v1")
    assertNotNull(trimmedRangeBefore)
    assertEquals(4000L, trimmedRangeBefore?.durationMs)
    assertEquals(0L, trimmedRangeBefore?.timelineStartMs)
    assertEquals(4000L, trimmedRangeBefore?.timelineEndMs)

    // Split at 1500ms
    val splitResult = engine.splitClipAtTime("v1", 1500L)
    assertNotNull(splitResult)
    val (leftId, rightId) = splitResult!!

    val leftRange = engine.getClipTrimmedRange(leftId)
    val rightRange = engine.getClipTrimmedRange(rightId)

    assertNotNull(leftRange)
    assertNotNull(rightRange)
    assertEquals(1500L, leftRange?.durationMs)
    assertEquals(2500L, rightRange?.durationMs)
    assertEquals(0L, leftRange?.timelineStartMs)
    assertEquals(1500L, rightRange?.timelineStartMs)
    assertEquals(leftRange!!.durationMs + rightRange!!.durationMs, 4000L)
  }

  @Test
  fun testTransitions() {
    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Clip 1", uri = "uri1", timelineStartMs = 0L, durationMs = 2000L, trackIndex = 0),
        VideoClip(id = "v2", name = "Clip 2", uri = "uri2", timelineStartMs = 2000L, durationMs = 2000L, trackIndex = 0)
      ),
      transitions = listOf(
        Transition(
          id = "tr1",
          type = TransitionType.DISSOLVE,
          durationMs = 500L,
          clipIndexBefore = 0
        )
      )
    )
    engine.loadTimeline(timeline)

    // Cut point is at 2000ms. Transition center is at 2000ms, start = 1750ms, end = 2250ms.
    val timingAt1700 = engine.getTransitionTimingAt(1700L)
    assertTrue(timingAt1700 == null)

    val timingAt2000 = engine.getTransitionTimingAt(2000L)
    assertNotNull(timingAt2000)
    assertEquals(1750L, timingAt2000?.startMs)
    assertEquals(2250L, timingAt2000?.endMs)
    assertEquals(0.5f, timingAt2000!!.progress, 0.01f)
    assertEquals("v1", timingAt2000.clipBefore.id)
    assertEquals("v2", timingAt2000.clipAfter.id)
  }

  @Test
  fun testAudioSyncInfoAndTracks() {
    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Clip 1", uri = "v_uri1", timelineStartMs = 0L, durationMs = 3000L, hasAudio = true, volume = 0.8f, trackIndex = 0)
      ),
      audioClips = listOf(
        AudioClip(id = "a1", title = "Audio 1", uri = "a_uri1", timelineStartMs = 500L, durationMs = 2000L, volume = 0.9f, trackIndex = 0)
      )
    )
    engine.loadTimeline(timeline)

    val syncInfo = engine.getActiveAudioSyncInfoAt(1000L)
    assertEquals(2, syncInfo.size)

    val audioTracks = engine.toTimelineAudioTracks()
    assertTrue(audioTracks.isNotEmpty())
    val mainAudioTrack = audioTracks.find { it.id == "track_main_video_audio" }
    assertNotNull(mainAudioTrack)
    assertEquals(1, mainAudioTrack?.clips?.size)

    val standaloneAudioTrack = audioTracks.find { it.id == "track_audio_0" }
    assertNotNull(standaloneAudioTrack)
    assertEquals(1, standaloneAudioTrack?.clips?.size)
  }

  @Test
  fun testUnifiedTimelineIntegration() {
    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Clip 1", uri = "v_uri1", timelineStartMs = 0L, durationMs = 2000L, trackIndex = 0),
        VideoClip(id = "v2", name = "Clip 2", uri = "v_uri2", timelineStartMs = 2000L, durationMs = 3000L, trackIndex = 0)
      )
    )
    engine.loadTimeline(timeline)

    val unified = engine.asUnifiedTimeline()
    assertEquals(5000L, unified.totalDurationMs())
    assertEquals(30, unified.fps)

    val snapPoints = unified.snapPoints()
    assertTrue(snapPoints.contains(0L))
    assertTrue(snapPoints.contains(2000L))
    assertTrue(snapPoints.contains(5000L))

    assertEquals(engine.timeline.value, unified.timelineSnapshot)
  }

  @Test
  fun testAdvancedNLEEditOperations() {
    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Clip 1", uri = "uri1", timelineStartMs = 0L, durationMs = 2000L, trackIndex = 0),
        VideoClip(id = "v2", name = "Clip 2", uri = "uri2", timelineStartMs = 2000L, durationMs = 3000L, trackIndex = 0)
      )
    )
    engine.loadTimeline(timeline)

    // Test Roll Edit
    val rolled = engine.rollEditClip("v1", "v2", 500L)
    assertTrue(rolled)
    val curTimeline = engine.timeline.value
    assertEquals(2500L, curTimeline.videoClips[0].durationMs)
    assertEquals(2500L, curTimeline.videoClips[1].timelineStartMs)
    assertEquals(2500L, curTimeline.videoClips[1].durationMs)

    // Test Slip Edit
    val slipped = engine.slipEditClip("v1", 200L)
    assertTrue(slipped)
    assertEquals(200L, engine.timeline.value.videoClips[0].sourceStartMs)

    // Test Slide Edit
    val slided = engine.slideEditClip("v2", 300L)
    assertTrue(slided)
    assertEquals(2800L, engine.timeline.value.videoClips[1].timelineStartMs)
  }

  @Test
  fun testInsertClipAtAuthoritativeCTI() {
    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Clip 1", uri = "uri1", timelineStartMs = 0L, durationMs = 2000L, trackIndex = 0)
      )
    )
    engine.loadTimeline(timeline)
    engine.setPosition(1500L, snap = false)
    assertEquals(1500L, engine.currentPositionMs.value)

    // Add overlay clip at current CTI
    val overlay = VideoClip(id = "ov1", name = "PIP", uri = "ov_uri", timelineStartMs = 1500L, durationMs = 1000L, trackIndex = 1)
    engine.addOverlayClip(overlay)

    val added = engine.timeline.value.overlayClips.find { it.id == "ov1" }
    assertNotNull(added)
    assertEquals(1500L, added?.timelineStartMs)
    assertEquals(1000L, added?.durationMs)
  }

  @Test
  fun testTrackLockingEnforcement() {
    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Clip 1", uri = "uri1", timelineStartMs = 0L, durationMs = 2000L, trackIndex = 0)
      ),
      trackSettings = mapOf(
        TrackType.MAIN_VIDEO to com.example.domain.model.TrackSettings(TrackType.MAIN_VIDEO, isLocked = true)
      )
    )
    engine.loadTimeline(timeline)

    // Move, trim, split, slip should fail when track is locked
    val splitFail = engine.splitClipAtTime("v1", 1000L)
    assertTrue(splitFail == null)

    val slipFail = engine.slipClip("v1", 500L)
    assertTrue(!slipFail)

    // Unlock and verify split succeeds
    engine.setTrackLocked(TrackType.MAIN_VIDEO, false)
    val splitSuccess = engine.splitClipAtTime("v1", 1000L)
    assertNotNull(splitSuccess)
  }

  @Test
  fun testRippleDeleteAndKeyframePruning() {
    val v1 = VideoClip(id = "v1", name = "C1", uri = "u1", timelineStartMs = 0L, durationMs = 2000L, trackIndex = 0)
    val v2 = VideoClip(id = "v2", name = "C2", uri = "u2", timelineStartMs = 2000L, durationMs = 2000L, trackIndex = 0)
    val v3 = VideoClip(id = "v3", name = "C3", uri = "u3", timelineStartMs = 4000L, durationMs = 2000L, trackIndex = 0)
    engine.loadTimeline(Timeline(videoClips = listOf(v1, v2, v3)))

    // Ripple delete middle clip v2
    engine.rippleDeleteClip("v2")
    val current = engine.timeline.value.videoClips
    assertEquals(2, current.size)
    assertEquals("v1", current[0].id)
    assertEquals("v3", current[1].id)
    assertEquals(0L, current[0].timelineStartMs)
    // v3 should be shifted left by v2's duration (2000ms)
    assertEquals(2000L, current[1].timelineStartMs)
  }

  @Test
  fun testMultiSelectionAndBatchDelete() {
    val v1 = VideoClip(id = "v1", name = "C1", uri = "u1", timelineStartMs = 0L, durationMs = 1000L, trackIndex = 0)
    val v2 = VideoClip(id = "v2", name = "C2", uri = "u2", timelineStartMs = 1000L, durationMs = 1000L, trackIndex = 0)
    val v3 = VideoClip(id = "v3", name = "C3", uri = "u3", timelineStartMs = 2000L, durationMs = 1000L, trackIndex = 0)
    engine.loadTimeline(Timeline(videoClips = listOf(v1, v2, v3)))

    engine.selectClips(setOf("v1", "v3"))
    assertTrue(engine.isClipSelected("v1"))
    assertTrue(engine.isClipSelected("v3"))
    assertTrue(!engine.isClipSelected("v2"))

    engine.deleteClips(setOf("v1", "v3"))
    assertEquals(1, engine.timeline.value.videoClips.size)
    assertEquals("v2", engine.timeline.value.videoClips[0].id)
  }

  @Test
  fun testUndoRedoRestoresExactState() {
    val v1 = VideoClip(id = "v1", name = "C1", uri = "u1", timelineStartMs = 0L, durationMs = 2000L, trackIndex = 0)
    engine.loadTimeline(Timeline(videoClips = listOf(v1)))
    val originalState = engine.timeline.value

    engine.splitClipAtTime("v1", 1000L)
    assertEquals(2, engine.timeline.value.videoClips.size)

    engine.undo()
    assertEquals(1, engine.timeline.value.videoClips.size)
    assertEquals("v1", engine.timeline.value.videoClips[0].id)
    assertEquals(originalState.videoClips[0].durationMs, engine.timeline.value.videoClips[0].durationMs)

    engine.redo()
    assertEquals(2, engine.timeline.value.videoClips.size)
  }

  @Test
  fun testTimelineEvaluatorFrameStateParity() {
    val evaluator = com.example.engine.timeline.TimelineEvaluator()
    val timeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Clip 1", uri = "uri1", timelineStartMs = 0L, durationMs = 3000L, trackIndex = 0)
      ),
      overlayClips = listOf(
        VideoClip(id = "ov1", name = "Overlay 1", uri = "ov1", timelineStartMs = 1000L, durationMs = 1500L, trackIndex = 1)
      ),
      textClips = listOf(
        TextClip(id = "t1", text = "Caption", timelineStartMs = 500L, durationMs = 2000L, trackIndex = 0)
      )
    )

    val frameState = evaluator.evaluate(timeline, 1500L)
    assertEquals(1500L, frameState.timestampMs)
    assertEquals("v1", frameState.activeVideoClip?.id)
    assertEquals(1, frameState.activeOverlays.size)
    assertEquals("ov1", frameState.activeOverlays[0].clip.id)
    assertEquals(1, frameState.activeTexts.size)
    assertEquals("t1", frameState.activeTexts[0].clip.id)
  }
}

