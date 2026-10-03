package com.example.engine.integration

import kotlin.math.roundToLong

data class CompoundTimeMapping(
  val parentUs: Long,
  val localUs: Long,
  val sourceUs: Long
)

/**
 * Bidirectional compound/nested timeline mapper adapted to Ah Studio's
 * timeline-time conventions. Instances can be chained for nested timelines.
 */
class CompoundTimelineTimeMapper(
  private val timelineStartUs: Long,
  private val timelineDurationUs: Long,
  private val sourceInUs: Long,
  private val sourceOutUs: Long,
  private val speed: Double = 1.0,
  private val reversed: Boolean = false
) {
  init {
    require(timelineDurationUs >= 0) { "Timeline duration must be non-negative" }
    require(sourceOutUs >= sourceInUs) { "Source range must be non-negative" }
    require(speed > 0.0) { "Speed must be greater than zero" }
  }

  fun parentToLocal(parentUs: Long): Long? {
    if (parentUs !in timelineStartUs until (timelineStartUs + timelineDurationUs)) return null
    return parentUs - timelineStartUs
  }

  fun localToSource(localUs: Long): Long {
    val clamped = localUs.coerceIn(0L, timelineDurationUs)
    val delta = (clamped * speed).roundToLong()
    return if (reversed) {
      (sourceOutUs - delta).coerceIn(sourceInUs, sourceOutUs)
    } else {
      (sourceInUs + delta).coerceIn(sourceInUs, sourceOutUs)
    }
  }

  fun parentToSource(parentUs: Long): Long? =
    parentToLocal(parentUs)?.let(::localToSource)

  fun sourceToLocal(sourceUs: Long): Long {
    val delta = if (reversed) sourceOutUs - sourceUs else sourceUs - sourceInUs
    return (delta / speed).roundToLong().coerceIn(0L, timelineDurationUs)
  }

  fun sourceToParent(sourceUs: Long): Long =
    timelineStartUs + sourceToLocal(sourceUs)

  fun map(parentUs: Long): CompoundTimeMapping? =
    parentToLocal(parentUs)?.let { local ->
      CompoundTimeMapping(parentUs, local, localToSource(local))
    }
}
