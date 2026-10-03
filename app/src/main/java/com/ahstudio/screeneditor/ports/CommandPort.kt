package com.ahstudio.screeneditor.ports

import com.ahstudio.screeneditor.transform.CropRect
import com.ahstudio.screeneditor.transform.Transform2D
import kotlinx.coroutines.flow.StateFlow

sealed interface EditorCommand
data class TransformLayerCommand(val layerId: String, val from: Transform2D, val to: Transform2D) : EditorCommand
data class SetOpacityCommand(val layerId: String, val value: Float) : EditorCommand
data class SetLayerVisibilityCommand(val id: String, val visible: Boolean) : EditorCommand
data class SetLayerLockCommand(val id: String, val locked: Boolean) : EditorCommand
data class ReorderLayerCommand(val from: Int, val to: Int) : EditorCommand
data class DeleteLayerCommand(val id: String) : EditorCommand
data class TrimClipCommand(val clipId: String, val edge: TrimEdge, val boundaryUs: Long) : EditorCommand
data class MoveClipCommand(val clipId: String, val startUs: Long, val trackId: String?) : EditorCommand
data class SetEffectParamCommand(val layerId: String, val chain: EffectChainRef, val paramId: String, val value: Float) : EditorCommand
data class EditTextCommand(val layerId: String, val mutate: (TextStateRef) -> TextStateRef) : EditorCommand
data class SetKeyframeCommand(val layerId: String, val prop: KeyableProperty, val timeUs: Long, val value: Float) : EditorCommand
data class RemoveKeyframeCommand(val layerId: String, val prop: KeyableProperty, val timeUs: Long) : EditorCommand
data class SetCropCommand(val layerId: String, val from: CropRect, val to: CropRect) : EditorCommand

interface CommandPort {
    fun execute(command: EditorCommand)
    fun preview(command: EditorCommand)
    fun undo()
    fun redo()
    val canUndo: StateFlow<Boolean>
    val canRedo: StateFlow<Boolean>
}
