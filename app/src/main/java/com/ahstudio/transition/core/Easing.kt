package com.ahstudio.transition.core

import java.util.Objects
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic CSS-style cubic Bézier solver. Pure function of x; no state, no
 * per-call allocation (coefficients precomputed in init). Newton–Raphson refined
 * by fixed bisection — no early exits, reproducible run-to-run on the same device.
 */
class CubicBezier(private val x1: Float, private val y1: Float,
                  private val x2: Float, private val y2: Float) {
    init {
        require(x1 in 0f..1f && x2 in 0f..1f) {
            "Bézier x control values must be in [0,1], got x1=$x1 x2=$x2"
        }
    }

    private val cx = 3f * x1
    private val bx = 3f * (x2 - x1) - cx
    private val ax = 1f - cx - bx
    private val cy = 3f * y1
    private val by = 3f * (y2 - y1) - cy
    private val ay = 1f - cy - by

    private fun sampleX(t: Float) = ((ax * t + bx) * t + cx) * t
    private fun sampleY(t: Float) = ((ay * t + by) * t + cy) * t
    private fun slopeX(t: Float) = 3f * ax * t * t + 2f * bx * t

    fun solve(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var t = x
        repeat(NEWTON) {                                    // Newton–Raphson
            val d = slopeX(t)
            if (abs(d) >= 1e-6f) {
                t -= (sampleX(t) - x) / d
                t = t.coerceIn(0f, 1f)
            }
        }
        var lo = 0f; var hi = 1f
        repeat(BISECT) {                                    // guaranteed refinement
            val tx = sampleX(t)
            if (abs(tx - x) < EPS) return sampleY(t)
            if (tx < x) lo = t else hi = t
            t = (lo + hi) * 0.5f
        }
        return sampleY(t)
    }

    companion object { private const val NEWTON = 8; private const val BISECT = 18; private const val EPS = 1e-5f }
}

class Easing private constructor(
    val type: Type,
    val bx1: Float = 0f, val by1: Float = 0f, val bx2: Float = 1f, val by2: Float = 1f,
) {
    enum class Type {
        LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT,
        CUBIC_IN, CUBIC_OUT, CUBIC_IN_OUT,
        QUARTIC_IN_OUT, QUINTIC_IN_OUT, SINE_IN_OUT,
        EXPO_IN, EXPO_OUT, EXPO_IN_OUT,
        CIRC_IN, CIRC_OUT, CIRC_IN_OUT,
        BACK_IN, BACK_OUT, BACK_IN_OUT,
        ELASTIC_OUT, ELASTIC_IN_OUT, BOUNCE_OUT,
        CUBIC_BEZIER
    }

    private val bezier: CubicBezier? =
        if (type == Type.CUBIC_BEZIER) CubicBezier(bx1, by1, bx2, by2) else null

    /** f(0)=0, f(1)=1 for every type; pure; input clamped. */
    fun evaluate(xRaw: Float): Float {
        val x = xRaw.coerceIn(0f, 1f)
        return when (type) {
            Type.LINEAR -> x
            Type.EASE_IN -> x * x
            Type.EASE_OUT -> 1f - (1f - x) * (1f - x)
            Type.EASE_IN_OUT -> if (x < 0.5f) 2f * x * x else 1f - 2f * (1f - x) * (1f - x)
            Type.CUBIC_IN -> x * x * x
            Type.CUBIC_OUT -> 1f - (1f - x).pow(3)
            Type.CUBIC_IN_OUT -> if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
            Type.QUARTIC_IN_OUT -> if (x < 0.5f) 8f * x.pow(4) else 1f - (-2f * x + 2f).pow(4) / 2f
            Type.QUINTIC_IN_OUT -> if (x < 0.5f) 16f * x.pow(5) else 1f - (-2f * x + 2f).pow(5) / 2f
            Type.SINE_IN_OUT -> -(cos(PI.toFloat() * x) - 1f) / 2f
            Type.EXPO_IN -> if (x == 0f) 0f else 2f.pow(10f * x - 10f)
            Type.EXPO_OUT -> if (x == 1f) 1f else 1f - 2f.pow(-10f * x)
            Type.EXPO_IN_OUT -> when {
                x == 0f -> 0f
                x == 1f -> 1f
                x < 0.5f -> 2f.pow(20f * x - 10f) / 2f
                else -> (2f - 2f.pow(-20f * x + 10f)) / 2f
            }
            Type.CIRC_IN -> 1f - sqrt(1f - x * x)
            Type.CIRC_OUT -> sqrt(1f - (x - 1f) * (x - 1f))
            Type.CIRC_IN_OUT -> if (x < 0.5f) (1f - sqrt(1f - (2f * x).pow(2))) / 2f
                else (sqrt(1f - (-2f * x + 2f).pow(2)) + 1f) / 2f
            Type.BACK_IN -> C3 * x * x * x - C1 * x * x
            Type.BACK_OUT -> 1f + C3 * (x - 1f).pow(3) + C1 * (x - 1f).pow(2)
            Type.BACK_IN_OUT -> {
                val c2 = C1 * 1.525f
                if (x < 0.5f) ((2f * x).pow(2) * ((c2 + 1f) * 2f * x - c2)) / 2f
                else ((2f * x - 2f).pow(2) * ((c2 + 1f) * (x * 2f - 2f) + c2) + 2f) / 2f
            }
            Type.ELASTIC_OUT -> when {
                x == 0f -> 0f
                x == 1f -> 1f
                else -> 2f.pow(-10f * x) * sin((x * 10f - 0.75f) * C4) + 1f
            }
            Type.ELASTIC_IN_OUT -> when {
                x == 0f -> 0f
                x == 1f -> 1f
                x < 0.5f -> -(2f.pow(20f * x - 10f) * sin((20f * x - 11.125f) * C5)) / 2f
                else -> (2f.pow(-20f * x + 10f) * sin((20f * x - 11.125f) * C5)) / 2f + 1f
            }
            Type.BOUNCE_OUT -> bounceOut(x)
            Type.CUBIC_BEZIER -> bezier!!.solve(x)
        }
    }

    override fun equals(other: Any?): Boolean = other is Easing &&
        other.type == type && other.bx1 == bx1 && other.by1 == by1 &&
        other.bx2 == bx2 && other.by2 == by2
    override fun hashCode(): Int = Objects.hash(type, bx1, by1, bx2, by2)

    companion object {
        private val C1 = 1.70158f
        private val C3 = C1 + 1f
        private val C4 = (2.0 * PI / 3.0).toFloat()
        private val C5 = (2.0 * PI / 4.5).toFloat()

        private fun bounceOut(x: Float): Float = when {
            x < 1f / 2.75f -> 7.5625f * x * x
            x < 2f / 2.75f -> 7.5625f * (x - 1.5f / 2.75f) * (x - 1.5f / 2.75f) + 0.75f
            x < 2.5f / 2.75f -> 7.5625f * (x - 2.25f / 2.75f) * (x - 2.25f / 2.75f) + 0.9375f
            else -> 7.5625f * (x - 2.625f / 2.75f) * (x - 2.625f / 2.75f) + 0.984375f
        }

        fun linear() = Easing(Type.LINEAR)
        fun of(type: Type) = Easing(type)
        fun bezier(x1: Float, y1: Float, x2: Float, y2: Float) =
            Easing(Type.CUBIC_BEZIER, x1, y1, x2, y2)
    }
}
