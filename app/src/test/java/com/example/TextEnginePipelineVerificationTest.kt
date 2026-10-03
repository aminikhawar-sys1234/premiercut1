package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.*
import com.example.engine.KeyframeInterpolator
import com.example.engine.TimelineEngine
import com.example.engine.composition.VideoCompositionEngine
import com.example.engine.text.TextLayerRenderer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextEnginePipelineVerificationTest {

  private lateinit var timelineEngine: TimelineEngine
  private lateinit var compositionEngine: VideoCompositionEngine

  @Before
  fun setUp() {
    timelineEngine = TimelineEngine()
    compositionEngine = VideoCompositionEngine(ApplicationProvider.getApplicationContext())

    val initialTimeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Main Video", uri = "video.mp4", durationMs = 10000L, timelineStartMs = 0L)
      )
    )
    timelineEngine.loadTimeline(initialTimeline)
  }

  @Test
  fun testMultipleTextLayersTimingAndStackOrdering() {
    val text1 = TextClip(
      id = "t1",
      text = "Title Layer",
      timelineStartMs = 0L,
      durationMs = 5000L,
      trackIndex = 0,
      posX = 0f,
      posY = -0.3f,
      scale = 1.2f,
      textColor = 0xFF00E5FF
    )

    val text2 = TextClip(
      id = "t2",
      text = "Subtitle Layer",
      timelineStartMs = 2000L,
      durationMs = 4000L,
      trackIndex = 1,
      posX = 0f,
      posY = 0.4f,
      scale = 0.9f,
      textColor = 0xFFFFFFFF
    )

    val timeline = timelineEngine.timeline.value.copy(
      textClips = listOf(text1, text2)
    )

    // At t = 1000ms -> Only Text 1 is active
    val frame1s = compositionEngine.evaluateFrame(timeline, 1000L)
    assertEquals(1, frame1s.activeTexts.size)
    assertEquals("t1", frame1s.activeTexts[0].clip.id)

    // At t = 3000ms -> Both Text 1 and Text 2 are active, ordered by trackIndex
    val frame3s = compositionEngine.evaluateFrame(timeline, 3000L)
    assertEquals(2, frame3s.activeTexts.size)
    assertEquals("t1", frame3s.activeTexts[0].clip.id)
    assertEquals("t2", frame3s.activeTexts[1].clip.id)

    // At t = 5500ms -> Only Text 2 is active
    val frame5s = compositionEngine.evaluateFrame(timeline, 5500L)
    assertEquals(1, frame5s.activeTexts.size)
    assertEquals("t2", frame5s.activeTexts[0].clip.id)
  }

  @Test
  fun testTextKeyframeInterpolation() {
    val keyframes = listOf(
      ClipKeyframe(timeMs = 0L, posX = -0.8f, posY = -0.8f, scaleX = 0.5f, scaleY = 0.5f, rotation = 0f, opacity = 0.2f),
      ClipKeyframe(timeMs = 1000L, posX = 0f, posY = 0f, scaleX = 1.5f, scaleY = 1.5f, rotation = 180f, opacity = 1.0f)
    )

    val textClip = TextClip(
      id = "kf_text",
      text = "Animated Keyframes",
      timelineStartMs = 0L,
      durationMs = 3000L,
      keyframes = keyframes
    )

    // Test at t = 0ms
    val kfStart = KeyframeInterpolator.interpolate(textClip, 0L)
    assertEquals(-0.8f, kfStart.posX, 0.01f)
    assertEquals(0.5f, kfStart.scale, 0.01f)
    assertEquals(0.2f, kfStart.opacity, 0.01f)

    // Test at midpoint t = 500ms
    val kfMid = KeyframeInterpolator.interpolate(textClip, 500L)
    assertEquals(-0.4f, kfMid.posX, 0.05f)
    assertEquals(1.0f, kfMid.scale, 0.05f)
    assertEquals(90f, kfMid.rotation, 2.0f)
    assertEquals(0.6f, kfMid.opacity, 0.05f)

    // Verify composition frame evaluates interpolated clip
    val timeline = timelineEngine.timeline.value.copy(textClips = listOf(textClip))
    val frame = compositionEngine.evaluateFrame(timeline, 500L)
    assertEquals(1, frame.activeTexts.size)
    val active = frame.activeTexts[0]
    assertEquals(-0.4f, active.clip.posX, 0.05f)
    assertEquals(1.0f, active.clip.scale, 0.05f)
  }

  @Test
  fun testTextAnimationsEvaluation() {
    val textFade = TextClip(
      id = "anim_fade",
      text = "Fade Text",
      animationType = "Fade",
      animDurationMs = 1000L,
      timelineStartMs = 0L,
      durationMs = 3000L
    )

    val stateStart = TextLayerRenderer.evaluateAnimation(textFade, 0L)
    assertTrue("Initial alpha should be <= 0.3f for Fade animation", stateStart.opacity <= 0.3f)

    val stateEnd = TextLayerRenderer.evaluateAnimation(textFade, 1000L)
    assertEquals(1.0f, stateEnd.opacity, 0.05f)
  }

  @Test
  fun testTextResolutionIndependentRendering() {
    val textClip = TextClip(
      id = "res_text",
      text = "Resolution Scaling",
      fontSizeSp = 30f,
      posX = 0.1f,
      posY = 0.2f
    )

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // Render at 360p, 720p, 1080p, 4K
    val bmp360 = TextLayerRenderer.renderToBitmap(textClip, 0L, 360, 640, context)
    val bmp720 = TextLayerRenderer.renderToBitmap(textClip, 0L, 720, 1280, context)
    val bmp1080 = TextLayerRenderer.renderToBitmap(textClip, 0L, 1080, 1920, context)
    val bmp4k = TextLayerRenderer.renderToBitmap(textClip, 0L, 2160, 3840, context)

    assertNotNull(bmp360)
    assertNotNull(bmp720)
    assertNotNull(bmp1080)
    assertNotNull(bmp4k)

    assertEquals(360, bmp360.width)
    assertEquals(720, bmp720.width)
    assertEquals(1080, bmp1080.width)
    assertEquals(2160, bmp4k.width)
  }
}
