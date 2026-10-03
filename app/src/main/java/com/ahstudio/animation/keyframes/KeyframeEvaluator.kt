package com.ahstudio.animation.keyframes

import com.ahstudio.animation.curves.Bezier2D
import com.ahstudio.animation.curves.CubicBezierTiming
import com.ahstudio.animation.easing.Easing
import com.ahstudio.animation.easing.EasingType
import com.ahstudio.animation.math.Vec2
import com.ahstudio.animation.math.isFinite
import com.ahstudio.animation.math.lerp

sealed class EvaluatedValue {
    data class FloatV(val value: Double, val velocityPerSec: Double) : EvaluatedValue()
    data class Vec2V(val value: Vec2, val velocityPerSec: Vec2) : EvaluatedValue()
}

/** Segment-index + timing-object cache with temporal locality. Race-safe. */
class TrackEvalCache {
    @Volatile private var version = Long.MIN_VALUE
    @Volatile private var seg = -1
    @Volatile private var timing: CubicBezierTiming? = null

    fun segmentFor(data: KeyframeTrackData, t: Long): Int {
        val kfs = data.keyframes; val n = kfs.size
        if (n < 2) return if (n == 1) 0 else -2
        val v = data.version; val s = seg
        if (v == version && s in 0 until n - 1) {
            if (t >= kfs[s].timeMs && t < kfs[s + 1].timeMs) return s
        }
        val idx = KeyframeTrackData.segmentIndex(kfs, t)
        version = v; seg = idx; timing = null
        return idx
    }

    fun timingFor(segIndex: Int, dataVersion: Long, factory: () -> CubicBezierTiming): CubicBezierTiming {
        if (version == dataVersion && seg == segIndex) timing?.let { return it }
        val t = factory()
        version = dataVersion; seg = segIndex; timing = t
        return t
    }
}

data class TimedProgress(val p: Double, val slope: Double)

object KeyframeEvaluator {
    val DEFAULT_EASE_IN = CubicBezierTiming(0.42, 0.0, 1.0, 1.0)
    val DEFAULT_EASE_OUT = CubicBezierTiming(0.0, 0.0, 0.58, 1.0)
    val DEFAULT_EASE_IN_OUT = CubicBezierTiming(0.42, 0.0, 0.58, 1.0)

    // ---------- segment-level ----------

    fun evalScalar(kfs: List<Keyframe>, seg: Int, t: Long, cache: TrackEvalCache?, dataVersion: Long): EvaluatedValue.FloatV? {
        val n = kfs.size
        if (n == 0) return null
        if (t <= kfs.first().timeMs || seg < 0) return EvaluatedValue.FloatV(kfs.first().value, 0.0)
        if (t >= kfs.last().timeMs) return EvaluatedValue.FloatV(kfs.last().value, 0.0)
        val s = if (seg >= n - 1) n - 2 else seg
        val a = kfs[s]; val b = kfs[s + 1]
        if (a.interpolation == InterpolationType.HOLD) return EvaluatedValue.FloatV(a.value, 0.0)
        val dtMs = b.timeMs - a.timeMs
        if (dtMs <= 0) return EvaluatedValue.FloatV(b.value, 0.0)
        val u = ((t - a.timeMs).toDouble() / dtMs).coerceIn(0.0, 1.0)
        val tp = timedProgress(a, b, u, dtMs, cache, s, dataVersion)
        val v = lerp(a.value, b.value, tp.p)
        var vel = (b.value - a.value) * tp.slope / (dtMs / 1000.0)
        if (!vel.isFinite()) vel = 0.0
        return EvaluatedValue.FloatV(if (v.isFinite()) v else a.value, vel)
    }

    fun evalVec2(
        kfs: List<Keyframe>, seg: Int, t: Long, spatial: Boolean,
        cache: TrackEvalCache?, dataVersion: Long
    ): EvaluatedValue.Vec2V? {
        val n = kfs.size
        if (n == 0) return null
        if (t <= kfs.first().timeMs || seg < 0) return EvaluatedValue.Vec2V(vecOf(kfs.first()), Vec2.ZERO)
        if (t >= kfs.last().timeMs) return EvaluatedValue.Vec2V(vecOf(kfs.last()), Vec2.ZERO)
        val s = if (seg >= n - 1) n - 2 else seg
        val a = kfs[s]; val b = kfs[s + 1]
        val va = vecOf(a); val vb = vecOf(b)
        if (a.interpolation == InterpolationType.HOLD) return EvaluatedValue.Vec2V(va, Vec2.ZERO)
        val dtMs = b.timeMs - a.timeMs
        if (dtMs <= 0) return EvaluatedValue.Vec2V(vb, Vec2.ZERO)
        val dtSec = dtMs / 1000.0
        val u = ((t - a.timeMs).toDouble() / dtMs).coerceIn(0.0, 1.0)
        val useSpatial = spatial && (a.spatialOutHandle != null || b.spatialInHandle != null)
        val tp = if (useSpatial) {
            timedProgress(a, b, u, dtMs, cache, s, dataVersion)
        } else if (a.interpolation == InterpolationType.BEZIER) {
            val dMag = (vb - va).length()
            if (dMag < 1e-12) TimedProgress(0.0, 0.0)
            else {
                val k = dtSec / dMag / 3.0
                var y1 = a.outTangent * k; var y2 = 1.0 - b.inTangent * k
                if (!isFinite(y1)) y1 = 0.0
                if (!isFinite(y2)) y2 = 1.0
                val bez = (cache?.timingFor(seg, dataVersion) { CubicBezierTiming(1.0 / 3.0, y1, 2.0 / 3.0, y2) })
                    ?: CubicBezierTiming(1.0 / 3.0, y1, 2.0 / 3.0, y2)
                TimedProgress(bez.progress(u), bez.slope(u))
            }
        } else timedProgress(a, b, u, dtMs, cache, seg, dataVersion)

        return if (useSpatial) {
            val p0 = va
            val p1 = va + (a.spatialOutHandle ?: (vb - va) / 3.0)
            val p2 = vb + (b.spatialInHandle ?: (va - vb) / 3.0)
            val pos = Bezier2D.sample(p0, p1, p2, vb, tp.p)
            val vel = Bezier2D.derivative(p0, p1, p2, vb, tp.p) * (tp.slope / dtSec)
            EvaluatedValue.Vec2V(pos, vel)
        } else {
            EvaluatedValue.Vec2V(va.lerp(vb, tp.p), (vb - va) * (tp.slope / dtSec))
        }
    }

    /** Whole-track evaluation with hard clamp at first/last keyframe. */
    fun evalScalarFull(kfs: List<Keyframe>, t: Long, cache: TrackEvalCache = TrackEvalCache()): EvaluatedValue.FloatV? {
        if (kfs.isEmpty()) return null
        return evalScalar(kfs, KeyframeTrackData.segmentIndex(kfs, t), t, cache, 0L)
    }
    fun evalVec2Full(kfs: List<Keyframe>, t: Long, spatial: Boolean, cache: TrackEvalCache = TrackEvalCache()): EvaluatedValue.Vec2V? {
        if (kfs.isEmpty()) return null
        return evalVec2(kfs, KeyframeTrackData.segmentIndex(kfs, t), t, spatial, cache, 0L)
    }

    // ---------- timing ----------

    private fun timedProgress(
        a: Keyframe, b: Keyframe, u: Double, dtMs: Long,
        cache: TrackEvalCache?, seg: Int, dataVersion: Long
    ): TimedProgress {
        if (a.easing != EasingType.LINEAR && a.interpolation != InterpolationType.HOLD) {
            return TimedProgress(Easing.apply(a.easing, u), Easing.slope(a.easing, u))
        }
        return when (a.interpolation) {
            InterpolationType.HOLD -> TimedProgress(0.0, 0.0)
            InterpolationType.LINEAR -> TimedProgress(u, 1.0)
            InterpolationType.EASE_IN -> bezTP(cache, seg, dataVersion, DEFAULT_EASE_IN, u)
            InterpolationType.EASE_OUT -> bezTP(cache, seg, dataVersion, DEFAULT_EASE_OUT, u)
            InterpolationType.EASE_IN_OUT -> bezTP(cache, seg, dataVersion, DEFAULT_EASE_IN_OUT, u)
            InterpolationType.CUSTOM_CURVE ->
                a.bezier?.let { TimedProgress(it.progress(u), it.slope(u)) } ?: TimedProgress(u, 1.0)
            InterpolationType.BEZIER -> {
                val dv = b.value - a.value
                if (abs(dv) < 1e-12) return TimedProgress(0.0, 0.0)
                val k = (dtMs / 1000.0) / dv / 3.0
                var y1 = a.outTangent * k
                var y2 = 1.0 - b.inTangent * k
                if (!isFinite(y1)) y1 = 0.0
                if (!isFinite(y2)) y2 = 1.0
                val bez = (cache?.timingFor(seg, dataVersion) {
                    CubicBezierTiming(1.0 / 3.0, y1, 2.0 / 3.0, y2)
                }) ?: CubicBezierTiming(1.0 / 3.0, y1, 2.0 / 3.0, y2)
                TimedProgress(bez.progress(u), bez.slope(u))
            }
        }
    }

    private fun bezTP(cache: TrackEvalCache?, seg: Int, dv: Long, bez: CubicBezierTiming, u: Double) =
        TimedProgress(bez.progress(u), bez.slope(u))

    private fun vecOf(kf: Keyframe): Vec2 = kf.vecValue ?: Vec2(kf.value, kf.value)
    private fun abs(v: Double) = kotlin.math.abs(v)
}
