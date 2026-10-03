package com.vfx.engine.core.curve

/**
 * Monotone cubic (Fritsch–Carlson) spline through control points.
 * Used by the Curves effect; sampled into a 256x1 GL LUT at upload.
 * Never overshoots — always stays within the control-point hull.
 */
class Curve(val points: List<Pair<Float, Float>>) {

    private val xs: FloatArray
    private val ys: FloatArray
    private val ms: FloatArray        // interval slopes
    private val tangents: FloatArray  // point tangents

    init {
        require(points.size >= 2) { "Curve needs >= 2 points" }
        xs = points.map { it.first }.toFloatArray()
        ys = points.map { it.second }.toFloatArray()
        for (i in 0 until xs.size - 1) require(xs[i] < xs[i + 1]) { "Curve x must be strictly increasing" }
        ms = FloatArray(points.size - 1)
        tangents = FloatArray(points.size)
        for (i in ms.indices) ms[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i])
        // Endpoints: one-sided slopes
        tangents[0] = ms[0]
        tangents[points.size - 1] = ms[ms.size - 1]
        // Interior: Fritsch–Carlson weighted harmonic mean (avoids overshoot)
        for (i in 1 until points.size - 1) {
            tangents[i] = if (ms[i - 1] * ms[i] <= 0f) 0f
            else {
                val d1 = ms[i - 1]; val d2 = ms[i]
                val denom = (2f * d2 / d1 + 1f) + (2f * d1 / d2 + 1f)
                val safeDenom = if (denom == 0f) 1e-6f else denom
                3f * (d1 + d2) / safeDenom
            }
        }
    }

    fun evaluate(x: Float): Float {
        val cx = x.coerceIn(xs.first(), xs.last())
        // Binary search for segment
        var lo = 0; var hi = xs.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (xs[mid] <= cx) lo = mid else hi = mid
        }
        val h = xs[hi] - xs[lo]
        if (h == 0f) return ys[lo]
        val t = (cx - xs[lo]) / h
        // Hermite basis
        val t2 = t * t; val t3 = t2 * t
        val h00 = 2f * t3 - 3f * t2 + 1f
        val h10 = t3 - 2f * t2 + t
        val h01 = -2f * t3 + 3f * t2
        val h11 = t3 - t2
        val y = h00 * ys[lo] + h10 * h * tangents[lo] + h01 * ys[hi] + h11 * h * tangents[hi]
        return if (y.isFinite()) y else ys[lo]
    }

    /** Samples to a 256-entry normalized LUT row (values clamped 0..1). */
    fun sample256(): FloatArray = FloatArray(256) { i -> evaluate(i / 255f).coerceIn(0f, 1f) }

    companion object {
        val LINEAR = Curve(listOf(0f to 0f, 1f to 1f))
        fun gamma(g: Float) = Curve(listOf(0f to 0f, 0.5f to Math.pow(0.5, 1.0 / g.toDouble()).toFloat(), 1f to 1f))
    }
}
