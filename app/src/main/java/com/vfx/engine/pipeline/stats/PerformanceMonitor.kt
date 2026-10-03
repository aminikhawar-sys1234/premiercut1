package com.vfx.engine.pipeline.stats

class PerformanceMonitor {
  var lastFrameGpuTimeMs: Float = 0f
    private set
  var droppedFramesCount: Int = 0
    private set

  fun recordFrameTime(timeMs: Float) {
    lastFrameGpuTimeMs = timeMs
    if (timeMs > 16.67f) {
      droppedFramesCount++
    }
  }
}

enum class QualityLevel {
  FULL,
  HALF,
  QUARTER
}

class FrameDropStrategy {
  fun evaluateQuality(avgFrameTimeMs: Float): QualityLevel {
    return when {
      avgFrameTimeMs > 33f -> QualityLevel.QUARTER
      avgFrameTimeMs > 18f -> QualityLevel.HALF
      else -> QualityLevel.FULL
    }
  }
}
