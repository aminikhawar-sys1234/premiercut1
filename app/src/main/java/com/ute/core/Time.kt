package com.ute.core

/** Composition time is always microseconds since composition start — never wall clock. */
object Time {
    const val MICROS_PER_SEC = 1_000_000.0
    fun toSeconds(micros: Long): Double = micros / MICROS_PER_SEC
}

data class TimeRange(val startSec: Double, val durationSec: Double) {
    val endSec: Double get() = startSec + durationSec
    fun contains(t: Double) = t >= startSec && t < endSec
    fun local(t: Double) = (t - startSec).coerceIn(0.0, durationSec)
}
