package com.vfx.engine.core.keyframe

import com.vfx.engine.core.Microseconds
import com.vfx.engine.core.math.MathUtils

enum class EasingPreset {
  LINEAR,
  EASE_IN,
  EASE_OUT,
  EASE_IN_OUT,
  BOUNCE
}

data class Keyframe<T>(
  val time: Microseconds,
  val value: T,
  val easing: EasingPreset = EasingPreset.LINEAR,
  val controlPoint1: Pair<Float, Float>? = null,
  val controlPoint2: Pair<Float, Float>? = null
)

class KeyframeTrack<T>(
  val paramName: String,
  val keyframes: List<Keyframe<T>> = emptyList(),
  val interpolator: (a: T, b: T, progress: Float) -> T
) {
  fun evaluateAt(time: Microseconds): T? {
    if (keyframes.isEmpty()) return null
    if (keyframes.size == 1 || time.value <= keyframes.first().time.value) {
      return keyframes.first().value
    }
    if (time.value >= keyframes.last().time.value) {
      return keyframes.last().value
    }

    // Fast binary search
    var low = 0
    var high = keyframes.size - 1
    while (low <= high) {
      val mid = (low + high) ushr 1
      if (keyframes[mid].time.value <= time.value) {
        low = mid + 1
      } else {
        high = mid - 1
      }
    }

    val k0 = keyframes[high]
    val k1 = keyframes[low]
    val duration = (k1.time.value - k0.time.value).toFloat()
    val elapsed = (time.value - k0.time.value).toFloat()
    val linearProgress = if (duration > 0f) MathUtils.clamp(elapsed / duration, 0f, 1f) else 1f

    val easedProgress = if (k0.controlPoint1 != null && k0.controlPoint2 != null) {
      com.vfx.engine.core.curve.CubicBezierEvaluator.evaluate(
        k0.controlPoint1.first, k0.controlPoint1.second,
        k0.controlPoint2.first, k0.controlPoint2.second,
        linearProgress
      )
    } else {
      when (k0.easing) {
        EasingPreset.LINEAR -> linearProgress
        EasingPreset.EASE_IN -> linearProgress * linearProgress
        EasingPreset.EASE_OUT -> 1f - (1f - linearProgress) * (1f - linearProgress)
        EasingPreset.EASE_IN_OUT -> MathUtils.smoothstep(0f, 1f, linearProgress)
        EasingPreset.BOUNCE -> MathUtils.smoothstep(0f, 1f, linearProgress)
      }
    }

    return interpolator(k0.value, k1.value, easedProgress)
  }
}
