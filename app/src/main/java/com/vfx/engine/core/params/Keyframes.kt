package com.vfx.engine.core.params

import com.vfx.engine.core.curve.Curve
import kotlin.math.abs

/** Maps linear progress [0,1] to eased progress [0,1]. */
fun interface Easing { fun apply(t: Float): Float }

object Easings {
    val LINEAR = Easing { it }
    val EASE_IN = Easing { it * it }
    val EASE_OUT = Easing { 1f - (1f - it) * (1f - it) }
    val EASE_IN_OUT = Easing { t -> t * t * (3f - 2f * t) }          // smoothstep
    val CUBIC_IN = Easing { it * it * it }
    val CUBIC_OUT = Easing { t -> 1f - (1f - t).let { u -> u * u * u } }
    val CUBIC_IN_OUT = Easing { t ->
        if (t < 0.5f) 4f * t * t * t
        else 1f - (-2f * t + 2f).let { u -> u * u * u } / 2f
    }

    /**
     * Cubic-bezier easing with numeric solving (CSS-style).
     * x1, x2 must be in [0,1]; y1, y2 may exceed for overshoot (clamped here).
     */
    class Bezier(
        private val x1: Float, private val y1: Float,
        private val x2: Float, private val y2: Float
    ) : Easing {
        private fun sampleX(t: Float) = 3f * t * (1f - t) * (1f - t) * x1 + 3f * t * t * (1f - t) * x2 + t * t * t
        private fun sampleY(t: Float) = 3f * t * (1f - t) * (1f - t) * y1 + 3f * t * t * (1f - t) * y2 + t * t * t
        private fun sampleDX(t: Float) = 3f * (1f - t) * (1f - t) * x1 + 6f * t * (1f - t) * (x2 - x1) + 3f * t * t * (1f - x2)

        override fun apply(x: Float): Float {
            val target = x.coerceIn(0f, 1f)
            var t = target
            // Newton–Raphson: solve sampleX(t) = target
            repeat(8) {
                val diff = sampleX(t) - target
                if (abs(diff) < 1e-5f) return sampleY(t).coerceIn(0f, 1f)
                val d = sampleDX(t)
                if (abs(d) < 1e-6f) return@repeat
                t = (t - diff / d).coerceIn(0f, 1f)
            }
            // Bisection fallback (sampleX is monotonic for x1,x2 in [0,1])
            var lo = 0f; var hi = 1f
            repeat(24) {
                val mid = (lo + hi) / 2f
                if (sampleX(mid) < target) lo = mid else hi = mid
            }
            return sampleY((lo + hi) / 2f).coerceIn(0f, 1f)
        }
    }

    fun bezier(x1: Float, y1: Float, x2: Float, y2: Float) = Bezier(x1, y1, x2, y2)

    fun named(name: String): Easing = when (name) {
        "linear" -> LINEAR; "easeIn" -> EASE_IN; "easeOut" -> EASE_OUT; "easeInOut" -> EASE_IN_OUT
        "cubicIn" -> CUBIC_IN; "cubicOut" -> CUBIC_OUT; "cubicInOut" -> CUBIC_IN_OUT
        else -> LINEAR
    }

    fun nameOf(e: Easing): String = when (e) {
        LINEAR -> "linear"; EASE_IN -> "easeIn"; EASE_OUT -> "easeOut"; EASE_IN_OUT -> "easeInOut"
        CUBIC_IN -> "cubicIn"; CUBIC_OUT -> "cubicOut"; CUBIC_IN_OUT -> "cubicInOut"
        is Bezier -> "bezier"; else -> "linear"
    }
}

/**
 * Generic keyframe track over MEDIA timestamps (microseconds).
 * Deterministic: binary search + stable interpolation.
 * All times are presentation timestamps — never wall-clock.
 */
class KeyframeTrack<T>(private val lerp: (T, T, Float) -> T) {
    data class Keyframe(val timeUs: Long, val value: Any, val easing: Easing)

    private val frames = ArrayList<Keyframe>()

    val isEmpty: Boolean get() = frames.isEmpty()
    val size: Int get() = frames.size
    val firstTimeUs: Long get() = frames.first().timeUs
    val lastTimeUs: Long get() = frames.last().timeUs
    fun keyframes(): List<Keyframe> = frames.toList()

    /** Insert or replace a keyframe; keeps list sorted by time. */
    fun set(timeUs: Long, value: T, easing: Easing = Easings.LINEAR) {
        frames.removeAll { it.timeUs == timeUs }
        frames.add(Keyframe(timeUs, value as Any, easing))
        frames.sortBy { it.timeUs }
    }

    fun removeAt(timeUs: Long) { frames.removeAll { it.timeUs == timeUs } }
    fun clear() = frames.clear()

    @Suppress("UNCHECKED_CAST")
    fun evaluate(timeUs: Long): T {
        if (frames.isEmpty()) throw IllegalStateException("Empty keyframe track")
        if (timeUs <= frames.first().timeUs) return frames.first().value as T
        if (timeUs >= frames.last().timeUs) return frames.last().value as T
        // Binary search for surrounding pair
        var lo = 0; var hi = frames.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (frames[mid].timeUs <= timeUs) lo = mid else hi = mid
        }
        val a = frames[lo]; val b = frames[hi]
        val span = (b.timeUs - a.timeUs).coerceAtLeast(1L)
        val t = (timeUs - a.timeUs).toFloat() / span.toFloat()
        val eased = a.easing.apply(t.coerceIn(0f, 1f))
        return lerp(a.value as T, b.value as T, eased)
    }
}

object KeyframeTracks {
    fun floats() = KeyframeTrack<Float> { a, b, t -> a + (b - a) * t }
    fun ints() = KeyframeTrack<Int> { a, b, t -> (a + (b - a) * t).toInt() }
    fun booleans() = KeyframeTrack<Boolean> { a, _, _ -> a }
    fun enums() = KeyframeTrack<String> { a, _, _ -> a }
    fun colors() = KeyframeTrack<FloatArray> { a, b, t -> FloatArray(4) { a[it] + (b[it] - a[it]) * t } }
    fun vecs(n: Int) = KeyframeTrack<FloatArray> { a, b, t -> FloatArray(n) { a[it] + (b[it] - a[it]) * t } }
    fun curves() = KeyframeTrack<Curve> { a, _, _ -> a }
}
