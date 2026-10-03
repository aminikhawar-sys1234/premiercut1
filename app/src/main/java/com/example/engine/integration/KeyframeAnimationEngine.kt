package com.example.engine.integration

import com.example.domain.model.ClipKeyframe
import com.example.domain.model.KeyframeInterpolation
import kotlin.math.abs

/**
 * Compatibility facade over Ah Studio's existing KeyframeInterpolator.
 * The project model remains authoritative; this adds explicit CRUD/evaluation
 * entry points matching the Autocut animation-engine boundary.
 */
object KeyframeAnimationEngine {
  data class Value(
    val positionX: Float,
    val positionY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
    val opacity: Float,
    val volume: Float = 1f,
    val blur: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val effectParam: Float = 0f
  )

  fun evaluate(keyframes: List<ClipKeyframe>, timeMs: Long): Value {
    if (keyframes.isEmpty()) return Value(0f, 0f, 1f, 1f, 0f, 1f)
    val sorted = keyframes.sortedBy { it.timeMs }
    if (timeMs <= sorted.first().timeMs) return value(sorted.first())
    if (timeMs >= sorted.last().timeMs) return value(sorted.last())

    val afterIndex = sorted.indexOfFirst { it.timeMs >= timeMs }
    val before = sorted[afterIndex - 1]
    val after = sorted[afterIndex]
    val span = (after.timeMs - before.timeMs).coerceAtLeast(1L)
    val raw = ((timeMs - before.timeMs).toFloat() / span).coerceIn(0f, 1f)
    val t = ease(before.interpolation, before.customCurvePoints, raw)

    fun lerp(a: Float, b: Float) = a + (b - a) * t
    return Value(
      lerp(before.posX, after.posX),
      lerp(before.posY, after.posY),
      lerp(before.scaleX, after.scaleX),
      lerp(before.scaleY, after.scaleY),
      lerp(before.rotation, after.rotation),
      lerp(before.opacity, after.opacity).coerceIn(0f, 1f),
      lerp(before.volume, after.volume).coerceAtLeast(0f),
      lerp(before.blur, after.blur).coerceIn(0f, 1f),
      lerp(before.brightness, after.brightness).coerceIn(-1f, 1f),
      lerp(before.contrast, after.contrast).coerceAtLeast(0f),
      lerp(before.saturation, after.saturation).coerceAtLeast(0f),
      lerp(before.effectParam, after.effectParam).coerceIn(0f, 1f)
    )
  }

  fun insertOrUpdate(
    keyframes: List<ClipKeyframe>,
    keyframe: ClipKeyframe,
    toleranceMs: Long = 1L
  ): List<ClipKeyframe> =
    (keyframes.filterNot { it.id == keyframe.id || abs(it.timeMs - keyframe.timeMs) <= toleranceMs } + keyframe)
      .sortedBy { it.timeMs }

  fun delete(keyframes: List<ClipKeyframe>, id: String): List<ClipKeyframe> =
    keyframes.filterNot { it.id == id }

  private fun value(k: ClipKeyframe) = Value(
    k.posX, k.posY, k.scaleX, k.scaleY, k.rotation, k.opacity,
    k.volume, k.blur, k.brightness, k.contrast, k.saturation, k.effectParam
  )

  private fun ease(kind: KeyframeInterpolation, points: List<Float>, t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return when (kind) {
      KeyframeInterpolation.HOLD -> 0f
      KeyframeInterpolation.EASE_IN -> clamped * clamped * clamped
      KeyframeInterpolation.EASE_OUT -> 1f - (1f - clamped) * (1f - clamped) * (1f - clamped)
      KeyframeInterpolation.EASE_IN_OUT ->
        if (clamped < .5f) 4f * clamped * clamped * clamped
        else 1f - (-2f * clamped + 2f).let { it * it * it } / 2f
      KeyframeInterpolation.CUBIC_BEZIER, KeyframeInterpolation.CUSTOM_CURVE ->
        solveCubicBezier(
          points.getOrNull(0) ?: .42f,
          points.getOrNull(1) ?: 0f,
          points.getOrNull(2) ?: .58f,
          points.getOrNull(3) ?: 1f,
          clamped
        )
      KeyframeInterpolation.LINEAR -> clamped
    }
  }

  fun solveCubicBezier(
    p1x: Float,
    p1y: Float,
    p2x: Float,
    p2y: Float,
    targetX: Float
  ): Float {
    if (targetX <= 0f) return 0f
    if (targetX >= 1f) return 1f
    var low = 0f
    var high = 1f
    var t = targetX
    repeat(14) {
      val x = bezier(p1x, p2x, t)
      if (abs(x - targetX) < .001f) return@repeat
      if (x < targetX) low = t else high = t
      t = (low + high) * .5f
    }
    return bezier(p1y, p2y, t).coerceIn(0f, 1f)
  }

  private fun bezier(p1: Float, p2: Float, t: Float): Float {
    val u = 1f - t
    return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t
  }

  /**
   * Converts a MotionTrackerEngine TrackingResult into baked Timeline ClipKeyframes
   * normalized to the target clip coordinate space, respecting attachment transform toggles and offsets.
   */
  fun convertTrackingResultToClipKeyframes(
    trackingResult: com.example.engine.ai.TrackingResult,
    clipStartTimelineMs: Long = 0L,
    followPosition: Boolean = true,
    followScale: Boolean = true,
    followRotation: Boolean = true,
    offsetX: Float = 0f,
    offsetY: Float = 0f
  ): List<ClipKeyframe> {
    return trackingResult.keyframes.map { kf ->
      val timeMs = (kf.timestampUs / 1000L).coerceAtLeast(0L)
      val relTimeMs = (timeMs - clipStartTimelineMs).coerceAtLeast(0L)
      val normPosX = if (followPosition) ((kf.centerX - 0.5f) * 2f + offsetX).coerceIn(-2f, 2f) else offsetX
      val normPosY = if (followPosition) ((kf.centerY - 0.5f) * 2f + offsetY).coerceIn(-2f, 2f) else offsetY
      val sX = if (followScale) kf.scaleX else 1.0f
      val sY = if (followScale) kf.scaleY else 1.0f
      val rot = if (followRotation) kf.rotationDeg else 0.0f

      ClipKeyframe(
        timeMs = relTimeMs,
        posX = normPosX,
        posY = normPosY,
        scaleX = sX,
        scaleY = sY,
        rotation = rot
      )
    }
  }
}
