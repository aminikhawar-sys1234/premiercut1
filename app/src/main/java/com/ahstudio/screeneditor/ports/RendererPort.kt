package com.ahstudio.screeneditor.ports

import com.ahstudio.screeneditor.composition.CompositionFrame

interface RendererPort {
    fun submit(frame: CompositionFrame)
}
