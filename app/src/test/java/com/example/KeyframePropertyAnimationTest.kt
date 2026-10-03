package com.example

import com.example.domain.model.ClipKeyframe
import com.example.domain.model.KeyframeInterpolation
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.KeyframeInterpolator
import com.example.engine.SelectedTrackElement
import com.example.engine.TimelineEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyframePropertyAnimationTest {

  private lateinit var timelineEngine: TimelineEngine

  @Before
  fun setUp() {
    timelineEngine = TimelineEngine()
  }

  @Test
  fun testKeyframeInterpolator_animatesScalePositionOpacityRotation() {
    val clip = VideoClip(
      id = "video_clip_1",
      uri = "asset:///video1.mp4",
      name = "Test Clip",
      timelineStartMs = 0L,
      durationMs = 4000L,
      sourceStartMs = 0L,
      sourceEndMs = 4000L,
      keyframes = listOf(
        ClipKeyframe(
          id = "kf1",
          timeMs = 0L,
          posX = -0.5f,
          posY = -0.2f,
          scaleX = 1.0f,
          scaleY = 1.0f,
          rotation = 0f,
          opacity = 0.2f,
          interpolation = KeyframeInterpolation.LINEAR
        ),
        ClipKeyframe(
          id = "kf2",
          timeMs = 4000L,
          posX = 0.5f,
          posY = 0.6f,
          scaleX = 2.0f,
          scaleY = 2.0f,
          rotation = 180f,
          opacity = 1.0f,
          interpolation = KeyframeInterpolation.LINEAR
        )
      )
    )

    // At t = 0ms
    val t0 = KeyframeInterpolator.interpolate(clip, 0L)
    assertEquals(-0.5f, t0.posX, 0.01f)
    assertEquals(-0.2f, t0.posY, 0.01f)
    assertEquals(1.0f, t0.scaleX, 0.01f)
    assertEquals(0f, t0.rotation, 0.01f)
    assertEquals(0.2f, t0.opacity, 0.01f)

    // At t = 2000ms (Midpoint)
    val tMid = KeyframeInterpolator.interpolate(clip, 2000L)
    assertEquals(0.0f, tMid.posX, 0.01f)
    assertEquals(0.2f, tMid.posY, 0.01f)
    assertEquals(1.5f, tMid.scaleX, 0.01f)
    assertEquals(90f, tMid.rotation, 0.01f)
    assertEquals(0.6f, tMid.opacity, 0.01f)

    // At t = 4000ms (End)
    val tEnd = KeyframeInterpolator.interpolate(clip, 4000L)
    assertEquals(0.5f, tEnd.posX, 0.01f)
    assertEquals(0.6f, tEnd.posY, 0.01f)
    assertEquals(2.0f, tEnd.scaleX, 0.01f)
    assertEquals(180f, tEnd.rotation, 0.01f)
    assertEquals(1.0f, tEnd.opacity, 0.01f)
  }

  @Test
  fun testTimelineEngine_addUpdateDeleteKeyframe() {
    val clip = VideoClip(
      id = "video_clip_test",
      uri = "asset:///video.mp4",
      name = "Main Video",
      timelineStartMs = 0L,
      durationMs = 5000L,
      sourceStartMs = 0L,
      sourceEndMs = 5000L,
      keyframes = emptyList()
    )

    timelineEngine.loadTimeline(Timeline(videoClips = listOf(clip)))
    timelineEngine.selectElement(SelectedTrackElement.Video("video_clip_test"))

    // Add keyframe at playhead 1500ms
    timelineEngine.setPosition(1500L)
    timelineEngine.addKeyframeToSelectedClip(
      ClipKeyframe(
        id = "kf_custom_1",
        timeMs = 1500L,
        scaleX = 1.3f,
        scaleY = 1.3f,
        rotation = 45f,
        opacity = 0.8f
      )
    )

    val currentClip = timelineEngine.timeline.value.videoClips.first()
    assertEquals(1, currentClip.keyframes.size)
    val kf = currentClip.keyframes.first()
    assertEquals(1500L, kf.timeMs)
    assertEquals(1.3f, kf.scaleX, 0.01f)
    assertEquals(45f, kf.rotation, 0.01f)

    // Update keyframe
    timelineEngine.updateKeyframe(kf.id) {
      it.copy(scaleX = 1.8f, rotation = 90f)
    }

    val updatedClip = timelineEngine.timeline.value.videoClips.first()
    val updatedKf = updatedClip.keyframes.first()
    assertEquals(1.8f, updatedKf.scaleX, 0.01f)
    assertEquals(90f, updatedKf.rotation, 0.01f)

    // Delete keyframe
    timelineEngine.selectKeyframe(updatedKf.id)
    timelineEngine.deleteSelectedKeyframes()
    val emptyKfClip = timelineEngine.timeline.value.videoClips.first()
    assertTrue("Keyframes list should be empty after deletion", emptyKfClip.keyframes.isEmpty())
  }

  @Test
  fun testTimelineEngine_applyMotionPreset() {
    val clip = VideoClip(
      id = "video_preset_test",
      uri = "asset:///video.mp4",
      name = "Preset Video",
      timelineStartMs = 0L,
      durationMs = 3000L,
      sourceStartMs = 0L,
      sourceEndMs = 3000L,
      keyframes = emptyList()
    )

    timelineEngine.loadTimeline(Timeline(videoClips = listOf(clip)))
    timelineEngine.selectElement(SelectedTrackElement.Video("video_preset_test"))

    // Apply Zoom In motion preset (1.0x to 1.4x)
    timelineEngine.applyMotionPresetToSelectedClip(
      scaleStart = 1.0f,
      scaleEnd = 1.4f,
      interpolation = KeyframeInterpolation.EASE_IN_OUT
    )

    val updated = timelineEngine.timeline.value.videoClips.first()
    assertEquals(2, updated.keyframes.size)

    val startKf = updated.keyframes[0]
    val endKf = updated.keyframes[1]

    assertEquals(0L, startKf.timeMs)
    assertEquals(1.0f, startKf.scaleX, 0.01f)
    assertEquals(3000L, endKf.timeMs)
    assertEquals(1.4f, endKf.scaleX, 0.01f)
    assertEquals(KeyframeInterpolation.EASE_IN_OUT, startKf.interpolation)
  }

  @Test
  fun testTimelineEngine_jumpBetweenKeyframes() {
    val clip = VideoClip(
      id = "video_jump_test",
      uri = "asset:///video.mp4",
      name = "Jump Video",
      timelineStartMs = 0L,
      durationMs = 6000L,
      sourceStartMs = 0L,
      sourceEndMs = 6000L,
      keyframes = listOf(
        ClipKeyframe(id = "kf_1", timeMs = 1000L),
        ClipKeyframe(id = "kf_2", timeMs = 3000L),
        ClipKeyframe(id = "kf_3", timeMs = 5000L)
      )
    )

    timelineEngine.loadTimeline(Timeline(videoClips = listOf(clip)))
    timelineEngine.selectElement(SelectedTrackElement.Video("video_jump_test"))

    // Set position at 0ms and jump next
    timelineEngine.setPosition(0L)
    timelineEngine.jumpToNextKeyframe()
    assertEquals(1000L, timelineEngine.currentPositionMs.value)

    timelineEngine.jumpToNextKeyframe()
    assertEquals(3000L, timelineEngine.currentPositionMs.value)

    // Jump previous
    timelineEngine.jumpToPreviousKeyframe()
    assertEquals(1000L, timelineEngine.currentPositionMs.value)
  }
}
