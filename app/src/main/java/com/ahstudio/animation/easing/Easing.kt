package com.ahstudio.animation.easing

import com.ahstudio.animation.curves.CubicBezierTiming
import com.ahstudio.animation.math.clamp01
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class EasingType {
    LINEAR,
    SINE_IN, SINE_OUT, SINE_INOUT,
    CUBIC_IN, CUBIC_OUT, CUBIC_INOUT,
    QUART_IN, QUART_OUT, QUART_INOUT,
    QUINT_IN, QUINT_OUT, QUINT_INOUT,
    EXPO_IN, EXPO_OUT, EXPO_INOUT,
    CIRC_IN, CIRC_OUT, CIRC_INOUT,
    BACK_IN, BACK_OUT, BACK_INOUT,
    ELASTIC_IN, ELASTIC_OUT, ELASTIC_INOUT,
    BOUNCE_IN, BOUNCE_OUT, BOUNCE_INOUT,
    CUSTOM
}

object Easing {
    private const val C1 = 1.70158
    private const val C3 = C1 + 1.0
    private const val ELASTIC_P = (2.0 * Math.PI) / 3.0

    /** Deterministic pure function of normalized progress u in [0,1]. */
    fun apply(type: EasingType, u: Double, custom: CubicBezierTiming? = null): Double {
        val x = clamp01(u)
        return when (type) {
            EasingType.LINEAR -> x
            EasingType.SINE_IN -> 1.0 - cos(x * Math.PI / 2.0)
            EasingType.SINE_OUT -> sin(x * Math.PI / 2.0)
            EasingType.SINE_INOUT -> -(cos(Math.PI * x) - 1.0) / 2.0
            EasingType.CUBIC_IN -> x * x * x
            EasingType.CUBIC_OUT -> 1.0 - (1.0 - x).pow(3)
            EasingType.CUBIC_INOUT -> if (x < 0.5) 4.0 * x * x * x else 1.0 - (-2.0 * x + 2.0).pow(3) / 2.0
            EasingType.QUART_IN -> x.pow(4)
            EasingType.QUART_OUT -> 1.0 - (1.0 - x).pow(4)
            EasingType.QUART_INOUT -> if (x < 0.5) 8.0 * x.pow(4) else 1.0 - (-2.0 * x + 2.0).pow(4) / 2.0
            EasingType.QUINT_IN -> x.pow(5)
            EasingType.QUINT_OUT -> 1.0 - (1.0 - x).pow(5)
            EasingType.QUINT_INOUT -> if (x < 0.5) 16.0 * x.pow(5) else 1.0 - (-2.0 * x + 2.0).pow(5) / 2.0
            EasingType.EXPO_IN -> if (x == 0.0) 0.0 else 2.0.pow(10.0 * x - 10.0)
            EasingType.EXPO_OUT -> if (x == 1.0) 1.0 else 1.0 - 2.0.pow(-10.0 * x)
            EasingType.EXPO_INOUT -> when {
                x == 0.0 -> 0.0; x == 1.0 -> 1.0
                x < 0.5 -> 2.0.pow(20.0 * x - 10.0) / 2.0
                else -> (2.0 - 2.0.pow(-20.0 * x + 10.0)) / 2.0
            }
            EasingType.CIRC_IN -> 1.0 - sqrt(1.0 - x * x)
            EasingType.CIRC_OUT -> sqrt(1.0 - (x - 1.0).pow(2))
            EasingType.CIRC_INOUT -> if (x < 0.5)
                (1.0 - sqrt(1.0 - (2.0 * x).pow(2))) / 2.0
            else (sqrt(1.0 - (-2.0 * x + 2.0).pow(2)) + 1.0) / 2.0
            EasingType.BACK_IN -> C3 * x * x * x - C1 * x * x
            EasingType.BACK_OUT -> 1.0 + C3 * (x - 1.0).pow(3) + C1 * (x - 1.0).pow(2)
            EasingType.BACK_INOUT -> if (x < 0.5)
                ((2.0 * x).pow(2) * ((C3 + 1.0) * 2.0 * x - C1)) / 2.0
            else ((2.0 * x - 2.0).pow(2) * ((C3 + 1.0) * (x * 2.0 - 2.0) + C1) + 2.0) / 2.0
            EasingType.ELASTIC_IN -> when {
                x == 0.0 -> 0.0; x == 1.0 -> 1.0
                else -> -2.0.pow(10.0 * x - 10.0) * sin((10.0 * x - 10.75) * ELASTIC_P)
            }
            EasingType.ELASTIC_OUT -> when {
                x == 0.0 -> 0.0; x == 1.0 -> 1.0
                else -> 2.0.pow(-10.0 * x) * sin((10.0 * x - 0.75) * ELASTIC_P) + 1.0
            }
            EasingType.ELASTIC_INOUT -> when {
                x == 0.0 -> 0.0; x == 1.0 -> 1.0
                x < 0.5 -> -(2.0.pow(20.0 * x - 10.0) * sin((20.0 * x - 11.125) * ELASTIC_P)) / 2.0
                else -> (2.0.pow(-20.0 * x + 10.0) * sin((20.0 * x - 11.125) * ELASTIC_P)) / 2.0 + 1.0
            }
            EasingType.BOUNCE_IN -> 1.0 - bounceOut(1.0 - x)
            EasingType.BOUNCE_OUT -> bounceOut(x)
            EasingType.BOUNCE_INOUT -> if (x < 0.5)
                (1.0 - bounceOut(1.0 - 2.0 * x)) / 2.0
            else (1.0 + bounceOut(2.0 * x - 1.0)) / 2.0
            EasingType.CUSTOM -> custom?.progress(x) ?: x
        }
    }

    /** dy/du -- stable central difference (h = 1e-5), one-sided at boundaries. Deterministic. */
    fun slope(type: EasingType, u: Double, custom: CubicBezierTiming? = null): Double {
        val h = 1e-5
        val a = clamp01(u - h); val b = clamp01(u + h)
        val denom = b - a
        if (denom <= 0.0) return 0.0
        return (apply(type, b, custom) - apply(type, a, custom)) / denom
    }

    private fun bounceOut(x: Double): Double {
        val n1 = 7.5625; val d1 = 2.75
        return when {
            x < 1.0 / d1 -> n1 * x * x
            x < 2.0 / d1 -> { val y = x - 1.5 / d1; n1 * y * y + 0.75 }
            x < 2.5 / d1 -> { val y = x - 2.25 / d1; n1 * y * y + 0.9375 }
            else -> { val y = x - 2.625 / d1; n1 * y * y + 0.984375 }
        }
    }

    fun isFiniteProgress(type: EasingType, u: Double, custom: CubicBezierTiming? = null): Boolean {
        val v = apply(type, u, custom)
        return !v.isNaN() && !v.isInfinite() && abs(v) < 1e9
    }
}
