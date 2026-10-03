package com.ute.animation

import kotlin.math.*

/** Interpolation curves. CubicBezier is solved for t by Newton-Raphson (CSS-style). */
sealed class Easing {
    object Linear : Easing()
    object EaseIn : Easing()          // bezier(0.42, 0, 1, 1)
    object EaseOut : Easing()         // bezier(0, 0, 0.58, 1)
    object EaseInOut : Easing()       // bezier(0.42, 0, 0.58, 1)
    data class CubicBezier(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Easing()
    /** Underdamped spring on unit progress 0→1. zeta<1 → overshoot. */
    data class Spring(val zeta: Float = 0.35f, val omega: Float = 12f) : Easing()

    fun transform(t: Float): Float {
        return when (this) {
            Linear -> t.coerceIn(0f, 1f)
            EaseIn -> CubicBezier(0.42f, 0f, 1f, 1f).transform(t)
            EaseOut -> CubicBezier(0f, 0f, 0.58f, 1f).transform(t)
            EaseInOut -> CubicBezier(0.42f, 0f, 0.58f, 1f).transform(t)
            is CubicBezier -> {
                val x = t.coerceIn(0f, 1f)
                if (x <= 0f) return 0f
                if (x >= 1f) return 1f
                var u = x
                var found = false
                for (i in 0 until 8) {
                    val xu = bezierX(u, x1, x2)
                    val dx = bezierXDeriv(u, x1, x2)
                    if (kotlin.math.abs(dx) < 1e-6f) break
                    val next = u - (xu - x) / dx
                    if (next < 0f || next > 1f) break
                    if (kotlin.math.abs(bezierX(next, x1, x2) - x) < 1e-5f) { u = next; found = true; break }
                    u = next
                }
                if (!found) {
                    var lo = 0f; var hi = 1f; u = x
                    for (i in 0 until 24) {
                        u = (lo + hi) / 2f
                        if (bezierX(u, x1, x2) < x) lo = u else hi = u
                    }
                }
                bezierY(u, y1, y2)
            }
            is Spring -> {
                val time = t.coerceIn(0f, 1f)
                if (zeta < 1f) {
                    val wd = omega * sqrt(1f - zeta * zeta)
                    1f - Math.exp((-zeta * omega * time).toDouble()).toFloat() *
                        (cos(wd * time) + (zeta * omega / wd) * sin(wd * time))
                } else {
                    1f - Math.exp((-omega * time).toDouble()).toFloat() * (1f + omega * time)
                }
            }
        }
    }

    private fun bezierX(t: Float, x1: Float, x2: Float) =
        3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t
    private fun bezierY(t: Float, y1: Float, y2: Float) =
        3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t
    private fun bezierXDeriv(t: Float, x1: Float, x2: Float) =
        3 * (1 - t) * (1 - t) * x1 + 6 * (1 - t) * t * (x2 - x1) + 3 * t * t * (1 - x2)
}
