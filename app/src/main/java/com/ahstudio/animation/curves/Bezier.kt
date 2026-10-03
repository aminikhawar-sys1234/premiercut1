package com.ahstudio.animation.curves

import com.ahstudio.animation.math.Vec2
import kotlin.math.abs

/**
 * Cubic Bézier timing function P0=(0,0), P3=(1,1), P1=(x1,y1), P2=(x2,y2).
 * x = normalized time, y = normalized progress.
 * Solved with Newton-Raphson + bisection fallback (no lookup-table approximation).
 * Thread-safe (immutable).
 */
class CubicBezierTiming(
    x1: Double, val y1: Double,
    x2: Double, val y2: Double
) {
    val x1: Double = if (!x1.isFinite()) 0.0 else x1.coerceIn(0.0, 1.0)
    val x2: Double = if (!x2.isFinite()) 1.0 else x2.coerceIn(0.0, 1.0)

    fun x1() = this.x1
    fun y1() = y1
    fun x2() = this.x2
    fun y2() = y2

    private fun sampleX(t: Double): Double {
        val mt = 1.0 - t
        return 3.0 * mt * mt * t * this.x1 + 3.0 * mt * t * t * this.x2 + t * t * t
    }
    private fun sampleY(t: Double): Double {
        val mt = 1.0 - t
        return 3.0 * mt * mt * t * y1 + 3.0 * mt * t * t * y2 + t * t * t
    }
    private fun sampleDX(t: Double): Double {
        val mt = 1.0 - t
        return 3.0 * mt * mt * this.x1 + 6.0 * mt * t * (this.x2 - this.x1) + 3.0 * t * t * (1.0 - this.x2)
    }
    private fun sampleDY(t: Double): Double {
        val mt = 1.0 - t
        return 3.0 * mt * mt * y1 + 6.0 * mt * t * (y2 - y1) + 3.0 * t * t * (1.0 - y2)
    }

    private fun solveT(u: Double): Double {
        if (u <= 0.0) return 0.0
        if (u >= 1.0) return 1.0
        var t = u
        for (i in 0 until 8) {                       // Newton-Raphson
            val err = sampleX(t) - u
            if (abs(err) < 1e-7) return t
            val d = sampleDX(t)
            if (abs(d) < 1e-9) break
            t = (t - err / d).coerceIn(0.0, 1.0)
        }
        var lo = 0.0; var hi = 1.0; t = u            // bisection fallback
        repeat(48) {
            val v = sampleX(t)
            when {
                abs(v - u) < 1e-9 -> return t
                v < u -> lo = t
                else -> hi = t
            }
            t = (lo + hi) * 0.5
        }
        return t
    }

    /** Progress y at normalized time u. Exact at endpoints (0->0, 1->1). */
    fun progress(u: Double): Double {
        if (!u.isFinite()) return 0.0
        val clampedU = u.coerceIn(0.0, 1.0)
        val res = sampleY(solveT(clampedU))
        return if (!res.isFinite()) clampedU else res
    }

    /** dy/dx at normalized time u -- used for exact velocity. */
    fun slope(u: Double): Double {
        if (!u.isFinite()) return 0.0
        val t = solveT(u.coerceIn(0.0, 1.0))
        val dx = sampleDX(t); val dy = sampleDY(t)
        if (abs(dx) < 1e-12 || !dx.isFinite() || !dy.isFinite()) return 0.0
        val slp = dy / dx
        return if (!slp.isFinite()) 0.0 else slp.coerceIn(-1000.0, 1000.0)
    }
}

/** Spatial cubic Bézier in 2D (motion paths / spatial position interpolation). */
object Bezier2D {
    fun sample(p0: Vec2, p1: Vec2, p2: Vec2, p3: Vec2, t: Double): Vec2 {
        val mt = 1.0 - t
        val a = mt * mt * mt; val b = 3.0 * mt * mt * t
        val c = 3.0 * mt * t * t; val d = t * t * t
        return Vec2(
            a * p0.x + b * p1.x + c * p2.x + d * p3.x,
            a * p0.y + b * p1.y + c * p2.y + d * p3.y
        )
    }
    /** dB/dt */
    fun derivative(p0: Vec2, p1: Vec2, p2: Vec2, p3: Vec2, t: Double): Vec2 {
        val mt = 1.0 - t
        return Vec2(
            3.0 * mt * mt * (p1.x - p0.x) + 6.0 * mt * t * (p2.x - p1.x) + 3.0 * t * t * (p3.x - p2.x),
            3.0 * mt * mt * (p1.y - p0.y) + 6.0 * mt * t * (p2.y - p1.y) + 3.0 * t * t * (p3.y - p2.y)
        )
    }
}
