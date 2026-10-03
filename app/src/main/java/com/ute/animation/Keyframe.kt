package com.ute.animation

import com.ute.color.ColorMath
import com.ute.core.MathUtil

data class KeyframeSpec(
    val timeSec: Double,
    val value: Float,
    val easing: Easing = Easing.EaseInOut,
)

/** A scalar track with sorted keyframes. All evaluation is timestamp-based. */
class Track(keys: List<KeyframeSpec>) {

    private val sorted = keys.sortedBy { it.timeSec }

    fun isEmpty() = sorted.isEmpty()
    val durationSec: Double get() = sorted.lastOrNull()?.timeSec ?: 0.0

    fun evaluate(tSec: Double): Float {
        if (sorted.isEmpty()) return 0f
        if (tSec <= sorted.first().timeSec) return MathUtil.sanitize(sorted.first().value)
        if (tSec >= sorted.last().timeSec) return MathUtil.sanitize(sorted.last().value)
        var i = 0
        while (i < sorted.size - 1 && sorted[i + 1].timeSec <= tSec) i++
        val a = sorted[i]; val b = sorted[i + 1]
        val span = (b.timeSec - a.timeSec).coerceAtLeast(1e-9)
        val raw = ((tSec - a.timeSec) / span).toFloat()
        val f = MathUtil.sanitize(a.easing.transform(raw), raw).coerceIn(0f, 1f)
        return MathUtil.sanitize(a.value + (b.value - a.value) * f)
    }
}

enum class AnimatableProperty {
    POSITION_X, POSITION_Y, SCALE, SCALE_X, SCALE_Y, ROTATION,
    OPACITY, FONT_SIZE, TRACKING, LINE_SPACING,
    FILL_COLOR, OUTLINE_WIDTH, OUTLINE_COLOR, SHADOW_DISTANCE, SHADOW_OPACITY,
    GLOW_RADIUS, GLOW_STRENGTH, BLUR_RADIUS,
    EXTRUSION_DEPTH, BEVEL_WIDTH, LIGHT_INTENSITY,
    CAMERA_X, CAMERA_Y, CAMERA_Z, CAMERA_ROTATION, CAMERA_FOV,
    EFFECT_INTENSITY,
}

/**
 * Color channels are animated as scalar RGB triplets evaluated in OKLab for perceptual correctness.
 */
object ColorTrack {
    fun evaluate(r: Track?, g: Track?, b: Track?, t: Double, fallback: Int): Int {
        if (r == null && g == null && b == null) return fallback
        val base = ColorMath.argbToOklab(fallback)
        val l = r?.evaluate(t)?.coerceIn(0f, 1f) ?: base[0]
        return ColorMath.oklabToArgb(l, base[1], base[2], fallback ushr 24)
    }
}
