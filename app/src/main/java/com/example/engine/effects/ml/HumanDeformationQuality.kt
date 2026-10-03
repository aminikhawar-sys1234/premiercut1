package com.example.engine.effects.ml

/** Device-quality policy for adaptive human deformation. */
enum class HumanDeformationQuality(val grid: Int, val analysisIntervalMs: Long, val useSegmentation: Boolean) {
  LOW(16, 100L, false),
  MEDIUM(24, 66L, true),
  HIGH(32, 33L, true);

  companion object {
    fun fromCapability(supportsGles3: Boolean, memoryClassMb: Int): HumanDeformationQuality =
      when {
        supportsGles3 && memoryClassMb >= 256 -> HIGH
        memoryClassMb >= 128 -> MEDIUM
        else -> LOW
      }
  }
}
