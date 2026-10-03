package com.ahstudio.color.curves

import com.ahstudio.color.core.CurvePoint
import kotlin.math.*

/**
 * Fritsch–Carlson monotone cubic interpolation (§16):
 * mathematically CANNOT overshoot between control points — pro-grade stability.
 */
object MonotoneCubicSpline {

    fun bakeLut(points: List<CurvePoint>, size: Int = 256): FloatArray {
        require(points.size >= 2) { "Curve needs >= 2 points" }
        val xs = FloatArray(points.size); val ys = FloatArray(points.size)
        points.forEachIndexed { i, p -> xs[i] = p.x; ys[i] = p.y }
        // enforce strictly increasing x
        for (i in 1 until xs.size) require(xs[i] > xs[i - 1]) { "Curve x must increase" }
        val n = points.size

        val d = FloatArray(n - 1)
        for (i in 0 until n - 1) d[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i])
        val m = FloatArray(n)
        m[0] = d[0]; m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
            val a = m[i] / d[i]; val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) { val t = 3f / sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
        }

        val lut = FloatArray(size)
        var seg = 0
        for (j in 0 until size) {
            val x = j.toFloat() / (size - 1)
            while (seg < n - 2 && x > xs[seg + 1]) seg++
            val h = xs[seg + 1] - xs[seg]
            val t = ((x - xs[seg]) / h).coerceIn(0f, 1f)
            val t2 = t * t; val t3 = t2 * t
            val h00 = 2 * t3 - 3 * t2 + 1f; val h10 = t3 - 2 * t2 + t
            val h01 = -2 * t3 + 3 * t2;     val h11 = t3 - t2
            lut[j] = (h00 * ys[seg] + h10 * h * m[seg] + h01 * ys[seg + 1] + h11 * h * m[seg + 1])
                .coerceIn(-0.5f, 1.5f)
        }
        return lut
    }
}

/**
 * Baked RGBA curve texture data (256x1):
 *   R = redCurve(x), G = greenCurve(x), B = blueCurve(x), A = masterCurve(x)
 * Luma curve baked separately into its own texture (single channel used).
 */
object CurveBaker {
    const val TEX_SIZE = 256

    fun bakeCombined(curves: com.ahstudio.color.core.CurveSet): Pair<FloatArray, FloatArray> {
        val main = FloatArray(TEX_SIZE * 4)
        val r = MonotoneCubicSpline.bakeLut(curves.red, TEX_SIZE)
        val g = MonotoneCubicSpline.bakeLut(curves.green, TEX_SIZE)
        val b = MonotoneCubicSpline.bakeLut(curves.blue, TEX_SIZE)
        val m = MonotoneCubicSpline.bakeLut(curves.master, TEX_SIZE)
        for (i in 0 until TEX_SIZE) {
            main[i * 4 + 0] = r[i]; main[i * 4 + 1] = g[i]; main[i * 4 + 2] = b[i]; main[i * 4 + 3] = m[i]
        }
        val luma = MonotoneCubicSpline.bakeLut(curves.luma, TEX_SIZE)
        return main to luma
    }

    fun isIdentity(c: List<CurvePoint>) = c.size == 2 && c[0] == CurvePoint(0f, 0f) && c[1] == CurvePoint(1f, 1f)
}
