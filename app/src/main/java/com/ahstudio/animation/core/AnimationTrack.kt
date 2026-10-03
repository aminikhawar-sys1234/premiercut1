package com.ahstudio.animation.core

import com.ahstudio.animation.keyframes.*
import com.ahstudio.animation.math.Vec2
import com.ahstudio.animation.properties.BindingKey
import com.ahstudio.animation.properties.PropertyType
import com.ahstudio.animation.time.LoopMode
import com.ahstudio.animation.time.TimeRemapper
import java.util.concurrent.atomic.AtomicReference

enum class Boundary { HOLD, LOOP, CONTINUE }

class AnimationTrack internal constructor(
    val key: BindingKey,
    initial: State
) {
    /** Immutable state -- copy-on-write. Bumping [version] invalidates all caches for this track. */
    data class State(
        val type: PropertyType,
        val data: KeyframeTrackData = KeyframeTrackData.EMPTY,
        val enabled: Boolean = true,
        val spatial: Boolean = false,                    // VEC2: honor spatial Bézier handles
        val rangeStartMs: Long? = null,                  // null = first keyframe
        val rangeEndMs: Long? = null,                    // null = last keyframe
        val beforeRange: Boundary = Boundary.HOLD,
        val afterRange: Boundary = Boundary.HOLD,
        val loop: LoopMode = LoopMode.NONE,
        val repeatCount: Int = Int.MAX_VALUE,
        val speed: Double = 1.0,
        val customTimeCurve: KeyframeTrackData? = null
    ) {
        val remapper: TimeRemapper by lazy {
            TimeRemapper(speed, loop, repeatCount, customTimeCurve)
        }
    }

    private val state = AtomicReference(initial)
    fun get(): State = state.get()
    internal fun forceState(s: State) { state.set(s) }

    val hasKeyframes: Boolean get() = state.get().data.keyframes.isNotEmpty()
    val keyframeCount: Int get() = state.get().data.keyframes.size

    /**
     * Seek-safe evaluation at absolute timeline time [timeMs].
     * Order: remap/loop -> range boundary -> keyframe segment -> value + velocity.
     * Returns null if disabled/empty (host keeps static value).
     */
    fun evaluate(timeMs: Long, cache: TrackEvalCache): EvaluatedValue? {
        val st = state.get()
        val kfs = st.data.keyframes
        if (!st.enabled || kfs.isEmpty()) return null
        val first = kfs.first(); val last = kfs.last()
        val start = st.rangeStartMs ?: first.timeMs
        val end = st.rangeEndMs ?: last.timeMs
        val duration = end - start
        if (duration <= 0L) return valueOf(first, st.type)
        val local = timeMs - start
        val t = start + st.remapper.remap(local, duration)
        return when {
            t < first.timeMs -> beforeBoundary(st, t, cache)
            t > last.timeMs -> afterBoundary(st, t, cache)
            else -> interior(st, t, cache)
        }
    }

    private fun interior(st: State, t: Long, cache: TrackEvalCache): EvaluatedValue? {
        val kfs = st.data.keyframes
        val seg = cache.segmentFor(st.data, t)
        return when (st.type) {
            PropertyType.FLOAT ->
                KeyframeEvaluator.evalScalar(kfs, seg, t, cache, st.data.version)
            PropertyType.VEC2 ->
                KeyframeEvaluator.evalVec2(kfs, seg, t, st.spatial, cache, st.data.version)
        }
    }

    private fun beforeBoundary(st: State, t: Long, cache: TrackEvalCache): EvaluatedValue? {
        val kfs = st.data.keyframes
        val first = kfs.first(); val last = kfs.last()
        return when (st.beforeRange) {
            Boundary.HOLD -> valueOf(first, st.type)
            Boundary.CONTINUE -> {
                val v = if (kfs.size >= 2) {
                    when (st.type) {
                        PropertyType.FLOAT -> KeyframeEvaluator.evalScalar(kfs, 0, first.timeMs + 1, cache, st.data.version)?.let {
                            EvaluatedValue.FloatV(first.value, it.velocityPerSec)
                        }
                        PropertyType.VEC2 -> KeyframeEvaluator.evalVec2(kfs, 0, first.timeMs + 1, st.spatial, cache, st.data.version)?.let {
                            EvaluatedValue.Vec2V(vecOf(first), it.velocityPerSec)
                        }
                    }
                } else interior(st, first.timeMs, cache) ?: return null
                if (v == null) return null
                extrapolate(v, (t - first.timeMs) / 1000.0)
            }
            Boundary.LOOP -> {
                val span = last.timeMs - first.timeMs
                if (span <= 0) valueOf(first, st.type)
                else {
                    val phase = (first.timeMs - t).mod(span)
                    interior(st, last.timeMs - phase, cache)
                }
            }
        }
    }

    private fun afterBoundary(st: State, t: Long, cache: TrackEvalCache): EvaluatedValue? {
        val kfs = st.data.keyframes
        val first = kfs.first(); val last = kfs.last()
        return when (st.afterRange) {
            Boundary.HOLD -> valueOf(last, st.type)
            Boundary.CONTINUE -> {
                val v = if (kfs.size >= 2) {
                    when (st.type) {
                        PropertyType.FLOAT -> KeyframeEvaluator.evalScalar(kfs, kfs.size - 2, last.timeMs - 1, cache, st.data.version)?.let {
                            EvaluatedValue.FloatV(last.value, it.velocityPerSec)
                        }
                        PropertyType.VEC2 -> KeyframeEvaluator.evalVec2(kfs, kfs.size - 2, last.timeMs - 1, st.spatial, cache, st.data.version)?.let {
                            EvaluatedValue.Vec2V(vecOf(last), it.velocityPerSec)
                        }
                    }
                } else interior(st, last.timeMs, cache) ?: return null
                if (v == null) return null
                extrapolate(v, (t - last.timeMs) / 1000.0)
            }
            Boundary.LOOP -> {
                val span = last.timeMs - first.timeMs
                if (span <= 0) valueOf(last, st.type)
                else {
                    val phase = (t - last.timeMs).mod(span)
                    interior(st, first.timeMs + phase, cache)
                }
            }
        }
    }

    private fun vecOf(kf: Keyframe): Vec2 = kf.vecValue ?: Vec2(kf.value, kf.value)

    private fun valueOf(kf: Keyframe, type: PropertyType): EvaluatedValue = when (type) {
        PropertyType.FLOAT -> EvaluatedValue.FloatV(kf.value, 0.0)
        PropertyType.VEC2 -> EvaluatedValue.Vec2V(kf.vecValue ?: Vec2(kf.value, kf.value), Vec2.ZERO)
    }

    private fun extrapolate(v: EvaluatedValue, dtSec: Double): EvaluatedValue = when (v) {
        is EvaluatedValue.FloatV -> EvaluatedValue.FloatV(v.value + v.velocityPerSec * dtSec, v.velocityPerSec)
        is EvaluatedValue.Vec2V -> EvaluatedValue.Vec2V(v.value + v.velocityPerSec * dtSec, v.velocityPerSec)
    }
}
