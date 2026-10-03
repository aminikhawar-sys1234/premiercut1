package com.example.engine

import com.example.domain.model.*
import kotlin.math.*

data class InterpolatedClipTransform(
  val scaleX: Float,
  val scaleY: Float,
  val rotation: Float,
  val posX: Float,
  val posY: Float,
  val opacity: Float,
  val volume: Float = 1.0f,
  val blur: Float = 0.0f,
  val brightness: Float = 0.0f,
  val contrast: Float = 1.0f,
  val saturation: Float = 1.0f,
  val effectParam: Float = 0.0f
) {
  // Legacy / convenience uniform scale
  val scale: Float get() = (scaleX + scaleY) / 2f

  constructor(
    scale: Float,
    rotation: Float,
    posX: Float,
    posY: Float,
    opacity: Float
  ) : this(
    scaleX = scale,
    scaleY = scale,
    rotation = rotation,
    posX = posX,
    posY = posY,
    opacity = opacity,
    volume = 1.0f,
    blur = 0.0f,
    brightness = 0.0f,
    contrast = 1.0f,
    saturation = 1.0f,
    effectParam = 0.0f
  )
}

object KeyframeInterpolator {

  fun interpolate(clip: VideoClip, relTimeMs: Long): InterpolatedClipTransform {
    val base = interpolateBaseKeyframes(clip, relTimeMs)
    return if (clip.animation.hasAnimation) {
      applyAnimation(base, clip, relTimeMs)
    } else {
      base
    }
  }

  fun interpolate(clip: StickerClip, relTimeMs: Long): InterpolatedClipTransform {
    val keyframes = clip.keyframes.sortedBy { it.timeMs }
    if (keyframes.isEmpty()) {
      return InterpolatedClipTransform(
        scaleX = clip.scale,
        scaleY = clip.scale,
        rotation = clip.rotation,
        posX = clip.posX,
        posY = clip.posY,
        opacity = clip.opacity,
        volume = 1.0f,
        blur = 0.0f,
        brightness = 0.0f,
        contrast = 1.0f,
        saturation = 1.0f,
        effectParam = 0.0f
      )
    }

    if (relTimeMs <= keyframes.first().timeMs) {
      return keyframeToTransform(keyframes.first())
    }
    if (relTimeMs >= keyframes.last().timeMs) {
      return keyframeToTransform(keyframes.last())
    }

    var before = keyframes.first()
    var after = keyframes.last()
    for (i in 0 until keyframes.size - 1) {
      if (relTimeMs >= keyframes[i].timeMs && relTimeMs <= keyframes[i + 1].timeMs) {
        before = keyframes[i]
        after = keyframes[i + 1]
        break
      }
    }

    val range = (after.timeMs - before.timeMs).toFloat().coerceAtLeast(1f)
    val rawT = ((relTimeMs - before.timeMs) / range).coerceIn(0f, 1f)
    val factor = computeFactor(before.interpolation, before.customCurvePoints, rawT)

    return InterpolatedClipTransform(
      scaleX = lerp(before.scaleX, after.scaleX, factor),
      scaleY = lerp(before.scaleY, after.scaleY, factor),
      rotation = lerp(before.rotation, after.rotation, factor),
      posX = lerp(before.posX, after.posX, factor),
      posY = lerp(before.posY, after.posY, factor),
      opacity = lerp(before.opacity, after.opacity, factor).coerceIn(0f, 1f),
      volume = 1.0f,
      blur = 0.0f,
      brightness = 0.0f,
      contrast = 1.0f,
      saturation = 1.0f,
      effectParam = 0.0f
    )
  }

  fun interpolate(clip: TextClip, relTimeMs: Long): InterpolatedClipTransform {
    val keyframes = clip.keyframes.sortedBy { it.timeMs }
    if (keyframes.isEmpty()) {
      return InterpolatedClipTransform(
        scaleX = clip.scale,
        scaleY = clip.scale,
        rotation = clip.rotation,
        posX = clip.posX,
        posY = clip.posY,
        opacity = clip.opacity,
        volume = 1.0f,
        blur = 0.0f,
        brightness = 0.0f,
        contrast = 1.0f,
        saturation = 1.0f,
        effectParam = 0.0f
      )
    }

    if (relTimeMs <= keyframes.first().timeMs) {
      return keyframeToTransform(keyframes.first())
    }
    if (relTimeMs >= keyframes.last().timeMs) {
      return keyframeToTransform(keyframes.last())
    }

    var before = keyframes.first()
    var after = keyframes.last()
    for (i in 0 until keyframes.size - 1) {
      if (relTimeMs >= keyframes[i].timeMs && relTimeMs <= keyframes[i + 1].timeMs) {
        before = keyframes[i]
        after = keyframes[i + 1]
        break
      }
    }

    val range = (after.timeMs - before.timeMs).toFloat().coerceAtLeast(1f)
    val rawT = ((relTimeMs - before.timeMs) / range).coerceIn(0f, 1f)
    val factor = computeFactor(before.interpolation, before.customCurvePoints, rawT)

    return InterpolatedClipTransform(
      scaleX = lerp(before.scaleX, after.scaleX, factor),
      scaleY = lerp(before.scaleY, after.scaleY, factor),
      rotation = lerp(before.rotation, after.rotation, factor),
      posX = lerp(before.posX, after.posX, factor),
      posY = lerp(before.posY, after.posY, factor),
      opacity = lerp(before.opacity, after.opacity, factor).coerceIn(0f, 1f),
      volume = 1.0f,
      blur = 0.0f,
      brightness = 0.0f,
      contrast = 1.0f,
      saturation = 1.0f,
      effectParam = 0.0f
    )
  }

  private fun interpolateBaseKeyframes(clip: VideoClip, relTimeMs: Long): InterpolatedClipTransform {
    val keyframes = clip.keyframes.sortedBy { it.timeMs }
    if (keyframes.isEmpty()) {
      return InterpolatedClipTransform(
        scaleX = clip.cropScale,
        scaleY = clip.cropScale,
        rotation = clip.rotationDegrees.toFloat(),
        posX = clip.cropOffsetX,
        posY = clip.cropOffsetY,
        opacity = clip.opacity,
        volume = clip.volume,
        blur = 0.0f,
        brightness = 0.0f,
        contrast = 1.0f,
        saturation = 1.0f,
        effectParam = 0.0f
      )
    }

    if (relTimeMs <= keyframes.first().timeMs) {
      val first = keyframes.first()
      return keyframeToTransform(first)
    }
    if (relTimeMs >= keyframes.last().timeMs) {
      val last = keyframes.last()
      return keyframeToTransform(last)
    }

    // Find bounding keyframes
    var before = keyframes.first()
    var after = keyframes.last()
    for (i in 0 until keyframes.size - 1) {
      if (relTimeMs >= keyframes[i].timeMs && relTimeMs <= keyframes[i + 1].timeMs) {
        before = keyframes[i]
        after = keyframes[i + 1]
        break
      }
    }

    val range = (after.timeMs - before.timeMs).toFloat().coerceAtLeast(1f)
    val rawT = ((relTimeMs - before.timeMs) / range).coerceIn(0f, 1f)
    val factor = computeFactor(before.interpolation, before.customCurvePoints, rawT)

    return InterpolatedClipTransform(
      scaleX = lerp(before.scaleX, after.scaleX, factor),
      scaleY = lerp(before.scaleY, after.scaleY, factor),
      rotation = lerp(before.rotation, after.rotation, factor),
      posX = lerp(before.posX, after.posX, factor),
      posY = lerp(before.posY, after.posY, factor),
      opacity = lerp(before.opacity, after.opacity, factor).coerceIn(0f, 1f),
      volume = lerp(before.volume, after.volume, factor).coerceAtLeast(0f),
      blur = lerp(before.blur, after.blur, factor).coerceIn(0f, 1f),
      brightness = lerp(before.brightness, after.brightness, factor).coerceIn(-1f, 1f),
      contrast = lerp(before.contrast, after.contrast, factor).coerceAtLeast(0f),
      saturation = lerp(before.saturation, after.saturation, factor).coerceAtLeast(0f),
      effectParam = lerp(before.effectParam, after.effectParam, factor).coerceIn(0f, 1f)
    )
  }

  private fun applyAnimation(
    base: InterpolatedClipTransform,
    clip: VideoClip,
    relTimeMs: Long
  ): InterpolatedClipTransform {
    val anim = clip.animation
    var curScaleX = base.scaleX
    var curScaleY = base.scaleY
    var curRotation = base.rotation
    var curPosX = base.posX
    var curPosY = base.posY
    var curOpacity = base.opacity
    var curBlur = base.blur
    val intensity = anim.intensity

    // 1. In-Animation (Entrance)
    if (anim.inType != InAnimationType.NONE && relTimeMs < anim.inDurationMs) {
      val rawT = (relTimeMs.toFloat() / anim.inDurationMs.coerceAtLeast(50L).toFloat()).coerceIn(0f, 1f)
      val t = evaluateEasing(rawT, anim.easing)

      when (anim.inType) {
        InAnimationType.NONE -> {}
        InAnimationType.FADE_IN -> {
          curOpacity *= t
        }
        InAnimationType.ZOOM_IN -> {
          val s = (0.15f + 0.85f * t)
          curScaleX *= s
          curScaleY *= s
          curOpacity *= min(1f, t * 1.5f)
        }
        InAnimationType.ZOOM_OUT -> {
          val s = (1.85f - 0.85f * t)
          curScaleX *= s
          curScaleY *= s
          curOpacity *= min(1f, t * 1.5f)
        }
        InAnimationType.SLIDE_UP -> {
          curPosY += (1f - t) * 0.8f * intensity
          curOpacity *= min(1f, t * 2f)
        }
        InAnimationType.SLIDE_DOWN -> {
          curPosY -= (1f - t) * 0.8f * intensity
          curOpacity *= min(1f, t * 2f)
        }
        InAnimationType.SLIDE_LEFT -> {
          curPosX += (1f - t) * 0.8f * intensity
          curOpacity *= min(1f, t * 2f)
        }
        InAnimationType.SLIDE_RIGHT -> {
          curPosX -= (1f - t) * 0.8f * intensity
          curOpacity *= min(1f, t * 2f)
        }
        InAnimationType.SPIN_IN -> {
          curRotation += (1f - t) * 360f * intensity
          curScaleX *= t
          curScaleY *= t
          curOpacity *= t
        }
        InAnimationType.BOUNCE_IN -> {
          val b = 1f - cos(t * PI.toFloat() * 2.5f) * exp(-t * 3.5f)
          curScaleX *= b
          curScaleY *= b
          curOpacity *= min(1f, t * 2f)
        }
        InAnimationType.POP_IN -> {
          val pop = if (t < 0.7f) (t / 0.7f * 1.15f) else (1.15f - (t - 0.7f) / 0.3f * 0.15f)
          curScaleX *= pop
          curScaleY *= pop
          curOpacity *= min(1f, t * 2f)
        }
        InAnimationType.FLIP_X -> {
          curScaleX *= abs(cos((1f - t) * PI.toFloat() * 0.5f))
          curOpacity *= min(1f, t * 2f)
        }
        InAnimationType.FLIP_Y -> {
          curScaleY *= abs(cos((1f - t) * PI.toFloat() * 0.5f))
          curOpacity *= min(1f, t * 2f)
        }
        InAnimationType.SWING_IN -> {
          curRotation += sin((1f - t) * 6f) * 25f * (1f - t) * intensity
        }
        InAnimationType.ELASTIC_IN -> {
          val p = t - 1f
          val el = (p * p * (2.70158f * p + 1.70158f) + 1f).coerceAtLeast(0f)
          curScaleX *= el
          curScaleY *= el
        }
        InAnimationType.GLITCH_IN -> {
          curPosX += sin(relTimeMs * 0.08f) * 0.04f * (1f - t) * intensity
          curPosY += cos(relTimeMs * 0.09f) * 0.04f * (1f - t) * intensity
          curOpacity *= if ((relTimeMs / 50L) % 2L == 0L) 0.4f else 1.0f
        }
        InAnimationType.WIPE_IN -> {
          curScaleX *= t
          curOpacity *= min(1f, t * 2f)
        }
        InAnimationType.BLUR_IN -> {
          curBlur = max(curBlur, (1f - t) * 0.8f * intensity)
          curOpacity *= t
        }
      }
    }

    // 2. Out-Animation (Exit)
    val outStartMs = (clip.durationMs - anim.outDurationMs).coerceAtLeast(0L)
    if (anim.outType != OutAnimationType.NONE && relTimeMs > outStartMs) {
      val rawT = ((relTimeMs - outStartMs).toFloat() / anim.outDurationMs.coerceAtLeast(50L).toFloat()).coerceIn(0f, 1f)
      val t = evaluateEasing(rawT, anim.easing)

      when (anim.outType) {
        OutAnimationType.NONE -> {}
        OutAnimationType.FADE_OUT -> {
          curOpacity *= (1f - t)
        }
        OutAnimationType.ZOOM_OUT -> {
          val s = (1f - 0.85f * t)
          curScaleX *= s
          curScaleY *= s
          curOpacity *= (1f - t)
        }
        OutAnimationType.ZOOM_IN_OUT -> {
          val s = (1f + 1.2f * t)
          curScaleX *= s
          curScaleY *= s
          curOpacity *= (1f - t)
        }
        OutAnimationType.SLIDE_UP_OUT -> {
          curPosY -= t * 0.8f * intensity
          curOpacity *= (1f - t)
        }
        OutAnimationType.SLIDE_DOWN_OUT -> {
          curPosY += t * 0.8f * intensity
          curOpacity *= (1f - t)
        }
        OutAnimationType.SLIDE_LEFT_OUT -> {
          curPosX -= t * 0.8f * intensity
          curOpacity *= (1f - t)
        }
        OutAnimationType.SLIDE_RIGHT_OUT -> {
          curPosX += t * 0.8f * intensity
          curOpacity *= (1f - t)
        }
        OutAnimationType.SPIN_OUT -> {
          curRotation += t * 360f * intensity
          curScaleX *= (1f - t)
          curScaleY *= (1f - t)
          curOpacity *= (1f - t)
        }
        OutAnimationType.BOUNCE_OUT -> {
          val b = (1f - t) * (1f + sin(t * 8f) * 0.15f)
          curScaleX *= b
          curScaleY *= b
          curOpacity *= (1f - t)
        }
        OutAnimationType.POP_OUT -> {
          val pop = (1f - t * 0.9f)
          curScaleX *= pop
          curScaleY *= pop
          curOpacity *= (1f - t)
        }
        OutAnimationType.FLIP_X_OUT -> {
          curScaleX *= abs(cos(t * PI.toFloat() * 0.5f))
          curOpacity *= (1f - t)
        }
        OutAnimationType.SWING_OUT -> {
          curRotation += sin(t * 6f) * 25f * t * intensity
          curOpacity *= (1f - t)
        }
        OutAnimationType.GLITCH_OUT -> {
          curPosX += sin(relTimeMs * 0.08f) * 0.04f * t * intensity
          curPosY += cos(relTimeMs * 0.09f) * 0.04f * t * intensity
          curOpacity *= (1f - t) * if ((relTimeMs / 50L) % 2L == 0L) 0.4f else 1.0f
        }
        OutAnimationType.WIPE_OUT -> {
          curScaleX *= (1f - t)
          curOpacity *= (1f - t)
        }
        OutAnimationType.BLUR_OUT -> {
          curBlur = max(curBlur, t * 0.8f * intensity)
          curOpacity *= (1f - t)
        }
      }
    }

    // 3. Combo / Loop Animation
    if (anim.comboType != ComboAnimationType.NONE) {
      val cycleMs = (relTimeMs * anim.speed).toLong()
      when (anim.comboType) {
        ComboAnimationType.NONE -> {}
        ComboAnimationType.PULSE -> {
          val pulse = 1.0f + 0.12f * sin(cycleMs * 0.006f) * intensity
          curScaleX *= pulse
          curScaleY *= pulse
        }
        ComboAnimationType.HEARTBEAT -> {
          val ph = (cycleMs % 1000L) / 1000f
          val beat = if (ph < 0.2f) sin(ph / 0.2f * PI.toFloat()) * 0.22f else if (ph in 0.25f..0.45f) sin((ph - 0.25f) / 0.2f * PI.toFloat()) * 0.14f else 0f
          val s = 1.0f + beat * intensity
          curScaleX *= s
          curScaleY *= s
        }
        ComboAnimationType.PENDULUM -> {
          curRotation += sin(cycleMs * 0.004f) * 14f * intensity
        }
        ComboAnimationType.FLOAT -> {
          curPosY += sin(cycleMs * 0.003f) * 0.04f * intensity
          curPosX += cos(cycleMs * 0.002f) * 0.025f * intensity
        }
        ComboAnimationType.SHAKE -> {
          curPosX += sin(cycleMs * 0.035f) * 0.02f * intensity
          curPosY += cos(cycleMs * 0.042f) * 0.02f * intensity
          curRotation += sin(cycleMs * 0.028f) * 3.5f * intensity
        }
        ComboAnimationType.JITTER -> {
          curPosX += (((cycleMs % 73L) / 73f) - 0.5f) * 0.04f * intensity
          curPosY += (((cycleMs % 59L) / 59f) - 0.5f) * 0.04f * intensity
        }
        ComboAnimationType.FLASH_PULSE -> {
          val flash = 0.6f + 0.4f * (0.5f + 0.5f * sin(cycleMs * 0.015f)) * intensity
          curOpacity *= flash.coerceIn(0f, 1f)
        }
        ComboAnimationType.WAVE -> {
          curRotation += sin(cycleMs * 0.005f) * 8f * intensity
          curScaleY *= (1.0f + sin(cycleMs * 0.008f) * 0.08f * intensity)
        }
        ComboAnimationType.SPIN_360 -> {
          curRotation += (cycleMs * 0.15f * intensity) % 360f
        }
        ComboAnimationType.BREATHE -> {
          val br = 1.0f + sin(cycleMs * 0.003f) * 0.08f * intensity
          curScaleX *= br
          curScaleY *= br
        }
        ComboAnimationType.ZOOM_PULSE -> {
          val zp = abs(sin(cycleMs * 0.005f))
          val s = 0.95f + 0.18f * zp * intensity
          curScaleX *= s
          curScaleY *= s
        }
      }
    }

    return base.copy(
      scaleX = curScaleX,
      scaleY = curScaleY,
      rotation = curRotation,
      posX = curPosX,
      posY = curPosY,
      opacity = curOpacity.coerceIn(0f, 1f),
      blur = curBlur.coerceIn(0f, 1f)
    )
  }

  private fun evaluateEasing(t: Float, easing: AnimationEasing): Float {
    val clamped = t.coerceIn(0f, 1f)
    return when (easing) {
      AnimationEasing.LINEAR -> clamped
      AnimationEasing.EASE_IN -> clamped * clamped
      AnimationEasing.EASE_OUT -> 1f - (1f - clamped) * (1f - clamped)
      AnimationEasing.EASE_IN_OUT -> {
        if (clamped < 0.5f) 2f * clamped * clamped
        else 1f - (-2f * clamped + 2f) * (-2f * clamped + 2f) / 2f
      }
      AnimationEasing.OVERSHOOT -> {
        val s = 1.70158f
        val p = clamped - 1f
        (p * p * ((s + 1f) * p + s) + 1f).coerceIn(0f, 1.5f)
      }
      AnimationEasing.BOUNCE -> {
        (1f - cos(clamped * PI.toFloat() * 2.5f) * exp(-clamped * 3.5f)).coerceIn(0f, 1.3f)
      }
      AnimationEasing.ELASTIC -> {
        val p = clamped - 1f
        (-2.0.pow(10.0 * p) * sin((p - 0.075f) * (2f * PI.toFloat()) / 0.3f)).toFloat().coerceIn(0f, 1.4f)
      }
    }
  }

  fun interpolateVolume(keyframes: List<ClipKeyframe>, relTimeMs: Long, defaultVolume: Float = 1.0f): Float {
    if (keyframes.isEmpty()) return defaultVolume
    val sorted = keyframes.sortedBy { it.timeMs }
    if (relTimeMs <= sorted.first().timeMs) return sorted.first().volume
    if (relTimeMs >= sorted.last().timeMs) return sorted.last().volume
    var before = sorted.first()
    var after = sorted.last()
    for (i in 0 until sorted.size - 1) {
      if (relTimeMs >= sorted[i].timeMs && relTimeMs <= sorted[i + 1].timeMs) {
        before = sorted[i]
        after = sorted[i + 1]
        break
      }
    }
    val range = (after.timeMs - before.timeMs).toFloat().coerceAtLeast(1f)
    val rawT = ((relTimeMs - before.timeMs) / range).coerceIn(0f, 1f)
    val factor = computeFactor(before.interpolation, before.customCurvePoints, rawT)
    return lerp(before.volume, after.volume, factor).coerceAtLeast(0f)
  }

  fun interpolateVolume(clip: AudioClip, relTimeMs: Long): Float {
    var vol = if (clip.keyframes.isNotEmpty()) {
      interpolateVolume(clip.keyframes, relTimeMs, clip.volume)
    } else {
      clip.volume
    }
    if (clip.fadeInMs > 0L && relTimeMs < clip.fadeInMs) {
      vol *= (relTimeMs.toFloat() / clip.fadeInMs.toFloat()).coerceIn(0f, 1f)
    }
    if (clip.fadeOutMs > 0L && (clip.durationMs - relTimeMs) < clip.fadeOutMs) {
      vol *= ((clip.durationMs - relTimeMs).toFloat() / clip.fadeOutMs.toFloat()).coerceIn(0f, 1f)
    }
    return vol.coerceAtLeast(0f)
  }

  fun computeFactor(
    interpolation: KeyframeInterpolation,
    customCurvePoints: List<Float>?,
    t: Float
  ): Float {
    val clampedT = t.coerceIn(0f, 1f)
    return when (interpolation) {
      KeyframeInterpolation.LINEAR -> clampedT
      KeyframeInterpolation.EASE_IN -> clampedT * clampedT * clampedT
      KeyframeInterpolation.EASE_OUT -> 1f - (1f - clampedT) * (1f - clampedT) * (1f - clampedT)
      KeyframeInterpolation.EASE_IN_OUT -> {
        if (clampedT < 0.5f) {
          4f * clampedT * clampedT * clampedT
        } else {
          (1.0 - Math.pow((-2.0 * clampedT + 2.0), 3.0) / 2.0).toFloat()
        }
      }
      KeyframeInterpolation.HOLD -> {
        if (clampedT >= 1f) 1f else 0f
      }
      KeyframeInterpolation.CUBIC_BEZIER,
      KeyframeInterpolation.CUSTOM_CURVE -> {
        val pts = if (customCurvePoints != null && customCurvePoints.size >= 4) {
          customCurvePoints
        } else {
          listOf(0.42f, 0.0f, 0.58f, 1.0f)
        }
        solveCubicBezier(pts[0], pts[1], pts[2], pts[3], clampedT)
      }
    }
  }

  /**
   * Evaluates cubic bezier Y for a given target X in range [0, 1].
   * P0=(0,0), P1=(p1x, p1y), P2=(p2x, p2y), P3=(1,1).
   */
  fun solveCubicBezier(p1x: Float, p1y: Float, p2x: Float, p2y: Float, targetX: Float): Float {
    if (targetX <= 0f) return 0f
    if (targetX >= 1f) return 1f
    var low = 0f
    var high = 1f
    var t = targetX
    // Binary search / bisection for root x(t) = targetX
    for (i in 0 until 14) {
      val currentX = evaluateBezier1D(p1x, p2x, t)
      if (abs(currentX - targetX) < 0.001f) break
      if (currentX < targetX) {
        low = t
      } else {
        high = t
      }
      t = (low + high) * 0.5f
    }
    return evaluateBezier1D(p1y, p2y, t).coerceIn(0f, 1f)
  }

  private fun evaluateBezier1D(p1: Float, p2: Float, t: Float): Float {
    val oneMinusT = 1f - t
    return 3f * oneMinusT * oneMinusT * t * p1 + 3f * oneMinusT * t * t * p2 + t * t * t
  }

  private fun keyframeToTransform(kf: ClipKeyframe): InterpolatedClipTransform {
    return InterpolatedClipTransform(
      scaleX = kf.scaleX,
      scaleY = kf.scaleY,
      rotation = kf.rotation,
      posX = kf.posX,
      posY = kf.posY,
      opacity = kf.opacity,
      volume = kf.volume,
      blur = kf.blur,
      brightness = kf.brightness,
      contrast = kf.contrast,
      saturation = kf.saturation,
      effectParam = kf.effectParam
    )
  }

  fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
  }

  private fun getEffectKeyframeVal(kf: ClipKeyframe): Float {
    return when {
      kf.opacity < 1.0f -> kf.opacity
      kf.effectParam > 0.0f -> kf.effectParam
      else -> kf.opacity
    }
  }

  fun interpolateEffectIntensity(clip: EffectClip, relTimeMs: Long): Float {
    if (clip.keyframes.isEmpty()) return clip.intensity
    val keyframes = clip.keyframes.sortedBy { it.timeMs }
    if (relTimeMs <= keyframes.first().timeMs) {
      val first = keyframes.first()
      return (getEffectKeyframeVal(first) * clip.intensity).coerceIn(0f, 1f)
    }
    if (relTimeMs >= keyframes.last().timeMs) {
      val last = keyframes.last()
      return (getEffectKeyframeVal(last) * clip.intensity).coerceIn(0f, 1f)
    }
    var before = keyframes.first()
    var after = keyframes.last()
    for (i in 0 until keyframes.size - 1) {
      if (relTimeMs >= keyframes[i].timeMs && relTimeMs <= keyframes[i + 1].timeMs) {
        before = keyframes[i]
        after = keyframes[i + 1]
        break
      }
    }
    val range = (after.timeMs - before.timeMs).toFloat().coerceAtLeast(1f)
    val rawT = ((relTimeMs - before.timeMs) / range).coerceIn(0f, 1f)
    val factor = computeFactor(before.interpolation, before.customCurvePoints, rawT)
    val vBefore = getEffectKeyframeVal(before)
    val vAfter = getEffectKeyframeVal(after)
    val interpolatedVal = lerp(vBefore, vAfter, factor)
    return (interpolatedVal * clip.intensity).coerceIn(0f, 1f)
  }
}

