package com.example

import com.example.engine.ai.MotionKeyframe
import com.example.engine.ai.MotionTrackingEvaluator
import com.example.engine.ai.NormalizedRect
import com.example.engine.ai.TrackingResult
import com.example.engine.integration.KeyframeAnimationEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MotionTrackingEngineTest {

  @Test
  fun testNormalizedRectGeometry() {
    val rect = NormalizedRect(left = 0.2f, top = 0.3f, right = 0.6f, bottom = 0.7f)
    assertEquals(0.4f, rect.width, 0.0001f)
    assertEquals(0.4f, rect.height, 0.0001f)
    assertEquals(0.4f, rect.centerX, 0.0001f)
    assertEquals(0.5f, rect.centerY, 0.0001f)

    val rectF = rect.toRectF(1080f, 1920f)
    assertEquals(216f, rectF.left, 0.01f)
    assertEquals(576f, rectF.top, 0.01f)
    assertEquals(648f, rectF.right, 0.01f)
    assertEquals(1344f, rectF.bottom, 0.01f)
  }

  @Test
  fun testMotionTrackingEvaluationAndInterpolation() {
    val keyframes = listOf(
      MotionKeyframe(timestampUs = 0L, centerX = 0.2f, centerY = 0.3f, scaleX = 1.0f, scaleY = 1.0f),
      MotionKeyframe(timestampUs = 1_000_000L, centerX = 0.8f, centerY = 0.7f, scaleX = 1.5f, scaleY = 1.5f)
    )

    val result = TrackingResult(
      targetId = "test_target",
      clipId = "video_clip_1",
      startTimestampUs = 0L,
      endTimestampUs = 1_000_000L,
      keyframes = keyframes
    )

    val evaluator = MotionTrackingEvaluator(result)

    val mid = evaluator.evaluate(500_000L)
    assertEquals(0.5f, mid.centerX, 0.001f)
    assertEquals(0.5f, mid.centerY, 0.001f)
    assertEquals(1.25f, mid.scaleX, 0.001f)
    assertEquals(1.25f, mid.scaleY, 0.001f)

    val startVal = evaluator.evaluate(-100_000L)
    assertEquals(0.2f, startVal.centerX, 0.001f)

    val endVal = evaluator.evaluate(2_000_000L)
    assertEquals(0.8f, endVal.centerX, 0.001f)
  }

  @Test
  fun testTransformMatrixGeneration() {
    val keyframes = listOf(
      MotionKeyframe(timestampUs = 0L, centerX = 0.5f, centerY = 0.5f, scaleX = 1.0f, scaleY = 1.0f, rotationDeg = 0f)
    )

    val result = TrackingResult(
      targetId = "test_target",
      clipId = "video_clip_1",
      startTimestampUs = 0L,
      endTimestampUs = 1_000_000L,
      keyframes = keyframes
    )

    val evaluator = MotionTrackingEvaluator(result)
    val matrix = evaluator.computeTransformMatrix(
      timestampUs = 0L,
      viewportWidth = 1080f,
      viewportHeight = 1920f,
      contentWidth = 200f,
      contentHeight = 100f
    )

    assertNotNull(matrix)
    val values = FloatArray(9)
    matrix.getValues(values)
    
    assertEquals(440f, values[android.graphics.Matrix.MTRANS_X], 0.01f)
    assertEquals(910f, values[android.graphics.Matrix.MTRANS_Y], 0.01f)
  }

  @Test
  fun testConvertTrackingResultToClipKeyframes() {
    val keyframes = listOf(
      MotionKeyframe(timestampUs = 0L, centerX = 0.5f, centerY = 0.5f, scaleX = 1.0f, scaleY = 1.0f),
      MotionKeyframe(timestampUs = 500_000L, centerX = 1.0f, centerY = 0.0f, scaleX = 2.0f, scaleY = 2.0f)
    )

    val result = TrackingResult(
      targetId = "test_target",
      clipId = "video_clip_1",
      startTimestampUs = 0L,
      endTimestampUs = 500_000L,
      keyframes = keyframes
    )

    val clipKeyframes = KeyframeAnimationEngine.convertTrackingResultToClipKeyframes(result)
    assertEquals(2, clipKeyframes.size)

    assertEquals(0L, clipKeyframes[0].timeMs)
    assertEquals(0.0f, clipKeyframes[0].posX, 0.001f)
    assertEquals(0.0f, clipKeyframes[0].posY, 0.001f)

    assertEquals(500L, clipKeyframes[1].timeMs)
    assertEquals(1.0f, clipKeyframes[1].posX, 0.001f)
    assertEquals(-1.0f, clipKeyframes[1].posY, 0.001f)
    assertEquals(2.0f, clipKeyframes[1].scaleX, 0.001f)
  }
}
