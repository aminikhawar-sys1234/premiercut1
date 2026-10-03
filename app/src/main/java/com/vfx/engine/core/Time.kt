package com.vfx.engine.core

import java.util.Locale

/** Media timestamp in microseconds. NEVER wall-clock. */
@JvmInline
value class TimeUs(val value: Long) : Comparable<TimeUs> {
    override fun compareTo(other: TimeUs) = value.compareTo(other.value)
    val seconds: Float get() = value / 1_000_000f
    val nanos: Long get() = value * 1_000L
    companion object {
        val ZERO = TimeUs(0)
        fun ofSeconds(s: Double) = TimeUs((s * 1_000_000).toLong())
    }
}

@JvmInline
value class Microseconds(val value: Long) {
  val seconds: Double get() = value / 1_000_000.0
  val milliseconds: Long get() = value / 1_000L

  companion object {
    val ZERO = Microseconds(0L)
    fun fromSeconds(sec: Double): Microseconds = Microseconds((sec * 1_000_000.0).toLong())
    fun fromMillis(ms: Long): Microseconds = Microseconds(ms * 1_000L)
  }
}

data class TimeCode(
  val hours: Int,
  val minutes: Int,
  val seconds: Int,
  val frames: Int,
  val fps: Int = 30
) {
  override fun toString(): String {
    return String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
  }

  fun toMicroseconds(): Microseconds {
    val totalSec = (hours * 3600) + (minutes * 60) + seconds
    val frameMicros = ((frames.toDouble() / fps) * 1_000_000.0).toLong()
    return Microseconds((totalSec * 1_000_000L) + frameMicros)
  }
}

data class TimelineRange(
  val start: Microseconds = Microseconds(0),
  val duration: Microseconds = Microseconds(0),
  val startUs: TimeUs = TimeUs(start.value),
  val durationUs: Long = duration.value
) {
  val end: Microseconds get() = Microseconds(start.value + duration.value)
  val endUs: TimeUs get() = TimeUs(startUs.value + durationUs)

  fun contains(pts: Microseconds): Boolean {
    return pts.value >= start.value && pts.value < end.value
  }

  fun contains(t: TimeUs): Boolean {
    return t.value >= startUs.value && t.value < endUs.value
  }
}
