package com.ahstudio.animation.time

import com.ahstudio.animation.keyframes.KeyframeTrackData
import com.ahstudio.animation.keyframes.KeyframeEvaluator
import kotlin.math.roundToLong

enum class LoopMode { NONE, LOOP, PINGPONG, REVERSE }

/**
 * Maps track-relative local time (ms) -> authored local time (ms).
 * Pure function of inputs => random-access/seek-safe by construction.
 */
class TimeRemapper(
    val speed: Double = 1.0,
    val loop: LoopMode = LoopMode.NONE,
    val repeatCount: Int = Int.MAX_VALUE,
    val customCurve: KeyframeTrackData? = null   // keyframe values are interpreted as ms
) {
    fun remap(localMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        customCurve?.let { c ->
            val v = KeyframeEvaluator.evalScalarFull(c.keyframes, localMs.coerceIn(0L, durationMs))?.value
                ?: 0.0
            return v.roundToLong().coerceIn(0L, durationMs)
        }
        val s = if (speed.isFinite() && speed > 0.0) speed else 1.0
        var t = localMs.toDouble() * s   // authored-domain time
        when (loop) {
            LoopMode.NONE -> Unit
            LoopMode.LOOP -> {
                val maxLen = durationMs.toDouble() * repeatCount.coerceAtLeast(1)
                if (t >= maxLen) t = maxLen - 1e-6
                t = t.mod(durationMs.toDouble())
            }
            LoopMode.PINGPONG -> {
                val cycle = durationMs * 2.0
                val maxLen = cycle * repeatCount.coerceAtLeast(1)
                if (t >= maxLen) t = maxLen - 1e-6
                val m = t.mod(cycle)
                t = if (m <= durationMs) m else cycle - m
            }
            LoopMode.REVERSE -> t = durationMs - t
        }
        return if (loop == LoopMode.NONE) t.roundToLong() else t.roundToLong().coerceIn(0L, durationMs)
    }
}
