package com.ahstudio.face.temporal

import kotlin.math.PI
import kotlin.math.abs

/** Classic One Euro filter (Casiez et al.) — passes through first sample, no startup lag. */
class OneEuroFilter(
    private var minCutoff: Double,
    private var beta: Double,
    private val dCutoff: Double = 1.0,
) {
    private var xPrev = Double.NaN
    private var dxPrev = 0.0
    val isSeeded get() = !xPrev.isNaN()

    fun retune(minCutoff: Double, beta: Double) { this.minCutoff = minCutoff; this.beta = beta }
    fun reset() { xPrev = Double.NaN; dxPrev = 0.0 }

    fun filter(x: Double, dt: Double): Double {
        require(dt > 0.0)
        if (!isSeeded) { xPrev = x; dxPrev = 0.0; return x }
        val dx = (x - xPrev) / dt
        val aD = alpha(dCutoff, dt)
        val dxHat = aD * dx + (1 - aD) * dxPrev
        val cutoff = minCutoff + beta * abs(dxHat)
        val a = alpha(cutoff, dt)
        val xHat = a * x + (1 - a) * xPrev
        xPrev = xHat; dxPrev = dxHat
        return xHat
    }

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }
}

/** Minimal scalar Kalman — prediction during detection gaps. */
class KalmanFilter1D(private val q: Double = 1e-3, private val r: Double = 1e-2) {
    private var x = 0.0; private var v = 0.0; private var p = 1.0; private var seeded = false
    fun reset() { seeded = false }
    fun update(z: Double?, dt: Double): Double {
        if (!seeded) { if (z == null) return x; x = z; v = 0.0; p = r; seeded = true; return x }
        x += v * dt; p += q * dt
        if (z != null) {
            val k = p / (p + r)
            x += k * (z - x); v += k * ((z - x) / dt) * 0.1
            p *= (1 - k)
        }
        return x
    }
}

fun unwrapAngle(prev: Float, next: Float): Float {
    var d = next - prev
    while (d > 180f) d -= 360f
    while (d < -180f) d += 360f
    return prev + d
}
