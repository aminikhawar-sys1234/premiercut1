package com.ahstudio.integration

import com.ahstudio.screeneditor.composition.CompositionFrame
import com.ahstudio.screeneditor.ports.RendererPort

class AhRendererAdapter(
    private val onFrameRenderCallback: ((CompositionFrame) -> Unit)? = null
) : RendererPort {

    override fun submit(frame: CompositionFrame) {
        onFrameRenderCallback?.invoke(frame)
    }
}
