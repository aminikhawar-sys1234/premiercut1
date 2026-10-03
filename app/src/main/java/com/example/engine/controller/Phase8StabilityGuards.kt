package com.example.engine.controller

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Small dependency-free guards for final integration/lifecycle validation. */
class Phase8PlaybackGuard {
    private val released = AtomicBoolean(false)
    private val generation = AtomicLong(0L)

    fun invalidate(): Long = generation.incrementAndGet()
    fun isCurrent(token: Long): Boolean = !released.get() && token == generation.get()
    fun release() { released.set(true); generation.incrementAndGet() }
    fun isReleased(): Boolean = released.get()
}

object Phase8TimelineMath {
    fun clamp(positionMs: Long, durationMs: Long): Long {
        val safeDurationMs = durationMs.coerceAtLeast(0L)
        return when {
            positionMs <= 0L -> 0L
            positionMs >= safeDurationMs -> safeDurationMs
            else -> positionMs
        }
    }

    fun sourceToTimeline(
        sourcePositionMs: Long,
        sourceStartMs: Long,
        timelineStartMs: Long,
        speed: Float
    ): Long {
        // Keep timeline conversion deterministic across JVMs: do the scale in Double
        // and truncate only once, after the complete calculation.
        val safeSpeed = if (speed.isFinite() && speed > 0f) speed.toDouble() else 0.01
        val sourceOffsetMs = if (sourcePositionMs > sourceStartMs) {
            sourcePositionMs - sourceStartMs
        } else {
            0L
        }
        val timelineOffsetMs = (sourceOffsetMs.toDouble() / safeSpeed).toLong()
        return timelineStartMs + timelineOffsetMs
    }
}
