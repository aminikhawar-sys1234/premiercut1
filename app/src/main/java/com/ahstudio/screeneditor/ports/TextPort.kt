package com.ahstudio.screeneditor.ports

interface TextPort {
    fun stateFor(layerId: String): TextStateRef?
    fun update(layerId: String, mutate: (TextStateRef) -> TextStateRef)
}
