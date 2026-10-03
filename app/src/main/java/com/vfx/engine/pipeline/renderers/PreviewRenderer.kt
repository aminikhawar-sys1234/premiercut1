package com.vfx.engine.pipeline.renderers

import com.vfx.engine.core.Microseconds
import com.vfx.engine.pipeline.RenderRequest

class PreviewRenderer {
  fun renderFrame(request: RenderRequest) {
    // VSync aligned preview frame render
  }
}

class ExportRenderer {
  fun renderExportFrame(request: RenderRequest) {
    // Offscreen exact PTS deterministic renderer
  }
}

class ScrubbingOptimizer {
  private var lastSeekTime: Long = 0L

  fun isRapidSeeking(currentPts: Microseconds): Boolean {
    val now = System.currentTimeMillis()
    val delta = now - lastSeekTime
    lastSeekTime = now
    return delta < 50 // less than 50ms between seeks
  }
}
