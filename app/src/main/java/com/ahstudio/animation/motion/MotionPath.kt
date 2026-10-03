package com.ahstudio.animation.motion

import com.ahstudio.animation.curves.Bezier2D
import com.ahstudio.animation.keyframes.*
import com.ahstudio.animation.math.Vec2
import com.ahstudio.animation.core.AnimationEngine
import com.ahstudio.animation.properties.BindingKey

/**
 * Multi-segment spatial motion path with arc-length parameterization.
 * Deterministic; built once per data version by the caller.
 */
class MotionPath(kfs: List<Keyframe>, samplesPerSegment: Int = 24) {
    private class Seg(val p0: Vec2, val p1: Vec2, val p2: Vec2, val p3: Vec2)
    private val segs = ArrayList<Seg>()
    private val cum: DoubleArray
    val totalLength: Double

    init {
        var prev: Keyframe? = null
        for (kf in kfs) {
            prev?.let { a ->
                val va = a.vecValue ?: Vec2(a.value, a.value)
                val vb = kf.vecValue ?: Vec2(kf.value, kf.value)
                segs.add(Seg(va,
                    va + (a.spatialOutHandle ?: (vb - va) / 3.0),
                    vb + (kf.spatialInHandle ?: (va - vb) / 3.0), vb))
            }
            prev = kf
        }
        cum = DoubleArray(segs.size + 1)
        var total = 0.0
        for (i in segs.indices) { total += segmentLength(segs[i], samplesPerSegment); cum[i + 1] = total }
        totalLength = total
    }

    private fun locate(d: Double): Pair<Int, Double> {
        val dist = d.coerceIn(0.0, totalLength)
        var lo = 0; var hi = segs.size - 1
        while (lo < hi) { val mid = (lo + hi + 1) ushr 1; if (cum[mid + 1] < dist) lo = mid + 1 else hi = mid }
        val segStart = cum[lo]; val segLen = (cum[lo + 1] - segStart).coerceAtLeast(1e-9)
        return lo to ((dist - segStart) / segLen).coerceIn(0.0, 1.0)
    }
    fun positionAtDistance(d: Double): Vec2 {
        if (segs.isEmpty()) return Vec2.ZERO
        val (i, t) = locate(d); val s = segs[i]
        return Bezier2D.sample(s.p0, s.p1, s.p2, s.p3, t)
    }
    fun tangentAtDistance(d: Double): Vec2 {
        if (segs.isEmpty()) return Vec2(1.0, 0.0)
        val (i, t) = locate(d); val s = segs[i]
        return Bezier2D.derivative(s.p0, s.p1, s.p2, s.p3, t).normalized()
    }
    private fun segmentLength(s: Seg, n: Int): Double {
        var len = 0.0; var prevP = Bezier2D.sample(s.p0, s.p1, s.p2, s.p3, 0.0)
        for (i in 1..n) {
            val p = Bezier2D.sample(s.p0, s.p1, s.p2, s.p3, i.toDouble() / n)
            len += (p - prevP).length(); prevP = p
        }
        return len
    }
}

/**
 * Motion blur helper -- subframe sampling THROUGH the same evaluate() path,
 * guaranteeing preview/export parity. No second renderer.
 */
object MotionBlurSampler {
    fun subframePositions(
        engine: AnimationEngine, key: BindingKey,
        timeMs: Long, shutterMs: Double, samples: Int
    ): List<Vec2> {
        if (samples <= 0) return emptyList()
        val out = ArrayList<Vec2>(samples)
        for (i in 0 until samples) {
            val f = if (samples == 1) 0.0 else i.toDouble() / (samples - 1)
            val t = timeMs + ((f - 0.5) * shutterMs).toLong()
            when (val v = engine.evaluateKey(key, t)) {
                is EvaluatedValue.Vec2V -> out.add(v.value)
                is EvaluatedValue.FloatV -> out.add(Vec2(v.value, v.value))
                null -> {}
            }
        }
        return out
    }
}
