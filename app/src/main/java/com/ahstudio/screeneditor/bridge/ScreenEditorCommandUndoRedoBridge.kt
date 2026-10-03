package com.ahstudio.screeneditor.bridge

import com.ahstudio.screeneditor.ports.CommandPort
import com.ahstudio.screeneditor.ports.EditorCommand
import com.ahstudio.screeneditor.ports.TransformLayerCommand
import com.ahstudio.screeneditor.transform.Transform2D
import kotlinx.coroutines.flow.StateFlow

class ScreenEditorCommandUndoRedoBridge(private val port: CommandPort) {
    // Gesture coalescing: one drag gesture = ONE undo entry
    private var coalesceKey: String? = null
    private var coalescedFrom: Transform2D? = null
    private var coalescedLayer: String? = null

    fun execute(cmd: EditorCommand) = port.execute(cmd)
    fun preview(cmd: EditorCommand) = port.preview(cmd)
    fun undo() = port.undo()
    fun redo() = port.redo()
    val canUndo: StateFlow<Boolean> get() = port.canUndo
    val canRedo: StateFlow<Boolean> get() = port.canRedo

    fun beginCoalescedTransform(layerId: String, from: Transform2D) {
        coalesceKey = "$layerId-transform-${System.currentTimeMillis()}"
        coalescedFrom = from
        coalescedLayer = layerId
    }

    fun commitCoalescedTransform(layerId: String, to: Transform2D) {
        val f = coalescedFrom
        if (f != null && coalescedLayer == layerId && f != to) {
            port.execute(TransformLayerCommand(layerId, f, to))
        }
        coalesceKey = null
        coalescedFrom = null
        coalescedLayer = null
    }

    fun cancelCoalesced() {
        coalesceKey = null
        coalescedFrom = null
        coalescedLayer = null
    }
}
