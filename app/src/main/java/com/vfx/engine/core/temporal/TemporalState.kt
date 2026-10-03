package com.vfx.engine.core.temporal

import com.vfx.engine.core.Microseconds

data class FrameWindow(
  val centerPts: Microseconds,
  val requiredFrameOffsets: List<Int> = listOf(-1, 0, 1)
)

class TemporalState<T> {
  private val history = mutableMapOf<Long, T>()

  fun store(pts: Microseconds, frameData: T) {
    history[pts.value] = frameData
  }

  fun get(pts: Microseconds): T? = history[pts.value]

  fun purgeBefore(pts: Microseconds) {
    history.entries.removeIf { it.key < pts.value }
  }
}
