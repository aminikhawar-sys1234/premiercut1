package com.example.engine.composition

import com.example.engine.composition.gpu.PreviewGpuRenderer
import com.example.engine.composition.gpu.NativeLayer
import com.example.engine.composition.ComposedFrame

/**
 * PreviewRenderer bridges the NLE Timeline engine state and the rendering surface.
 * It coordinates the GPU composition graph to produce frame-accurate preview outputs.
 */
class PreviewRenderer(
    private val gpuRenderer: PreviewGpuRenderer
) {
    private var viewportWidth = 0
    private var viewportHeight = 0

    fun resize(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        gpuRenderer.resize(width, height)
    }

    fun render(layers: List<NativeLayer>) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        gpuRenderer.render(layers)
    }

    fun release() {
        gpuRenderer.release()
    }
}
