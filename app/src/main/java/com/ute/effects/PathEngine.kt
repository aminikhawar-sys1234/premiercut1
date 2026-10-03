package com.ute.effects

/**
 * Text-on-path. Real arc-length parameterized path evaluation.
 * Path is specified in normalized layer space, scaled to px at render time.
 */
data class PathSpec(
    val type: PathType,
    /** Cubic bezier control points (normalized). For BEZIER and as segments for CUSTOM. */
    val segments: List<CubicSegment> = emptyList(),
    /** For ARC: radius in px; for CIRCLE: radius; sweep in degrees. */
    val radiusPx: Float = 300f,
    val startAngleDeg: Float = 180f,
    val sweepDeg: Float = 360f,
    val offsetPx: Float = 0f,          // perpendicular offset off the path
    val alignToPath: Boolean = true,   // rotate glyphs with path tangent
    /** Animation: glyph-position phase along the path, in px per second. */
    val flowPxPerSec: Float = 0f,
)

data class CubicSegment(val p0x: Float, val p0y: Float, val p1x: Float, val p1y: Float,
                        val p2x: Float, val p2y: Float, val p3x: Float, val p3y: Float)

enum class PathType { LINE, ARC, CIRCLE, BEZIER, CUSTOM }

/** Arc-length table over a cubic bezier — real numeric parameterization. */
class PathCursor(private val segments: List<CubicSegment>) {
    private val samples = 64
    private val lengths = FloatArray(segments.size * samples)

    init {
        var acc = 0f
        var prev = evaluate(0, 0f)
        for (s in segments.indices) for (i in 1..samples) {
            val p = evaluate(s, i / samples.toFloat())
            acc += kotlin.math.hypot((p[0] - prev[0]).toDouble(), (p[1] - prev[1]).toDouble()).toFloat()
            lengths[s * samples + i - 1] = acc
            prev = p
        }
    }

    val totalLength: Float get() = lengths.lastOrNull() ?: 0f

    /** Returns [x, y, tangentX, tangentY] at arc length s. */
    fun at(sRaw: Float): FloatArray {
        val s = sRaw.coerceIn(0f, totalLength)
        var lo = 0; var hi = lengths.size - 1
        while (lo < hi) { val mid = (lo + hi) / 2; if (lengths[mid] < s) lo = mid + 1 else hi = mid }
        val idx = lo
        val seg = idx / samples
        val t = (idx % samples + 1).toFloat() / samples
        val p = evaluate(seg, t)
        val q = evaluate(seg, (t + 0.01f).coerceAtMost(1f))
        val tx = q[0] - p[0]; val ty = q[1] - p[1]
        val len = kotlin.math.hypot(tx.toDouble(), ty.toDouble()).toFloat().coerceAtLeast(1e-5f)
        return floatArrayOf(p[0], p[1], tx / len, ty / len)
    }

    private fun evaluate(seg: Int, t: Float): FloatArray {
        val s = segments.getOrElse(seg) { segments.last() }
        val u = 1f - t
        fun bez(a: Float, b: Float, c: Float, d: Float) =
            u*u*u*a + 3*u*u*t*b + 3*u*t*t*c + t*t*t*d
        return floatArrayOf(bez(s.p0x, s.p1x, s.p2x, s.p3x), bez(s.p0y, s.p1y, s.p2y, s.p3y))
    }
}
