package com.ahstudio.integration

import com.ahstudio.screeneditor.ports.CommandPort
import com.ahstudio.screeneditor.ports.DeleteLayerCommand
import com.ahstudio.screeneditor.ports.EditTextCommand
import com.ahstudio.screeneditor.ports.EditorCommand
import com.ahstudio.screeneditor.ports.MoveClipCommand
import com.ahstudio.screeneditor.ports.ReorderLayerCommand
import com.ahstudio.screeneditor.ports.SetCropCommand
import com.ahstudio.screeneditor.ports.SetEffectParamCommand
import com.ahstudio.screeneditor.ports.SetKeyframeCommand
import com.ahstudio.screeneditor.ports.SetLayerLockCommand
import com.ahstudio.screeneditor.ports.SetLayerVisibilityCommand
import com.ahstudio.screeneditor.ports.SetOpacityCommand
import com.ahstudio.screeneditor.ports.TransformLayerCommand
import com.ahstudio.screeneditor.ports.TrimClipCommand
import com.ahstudio.screeneditor.ports.TrimEdge
import com.ahstudio.screeneditor.transform.Transform2D
import com.example.engine.TimelineEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AhCommandAdapter(
    private val timelineEngine: TimelineEngine
) : CommandPort {

    private val undoHistory = ArrayDeque<EditorCommand>()
    private val redoHistory = ArrayDeque<EditorCommand>()

    private val _canUndo = MutableStateFlow(false)
    override val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    override val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    override fun execute(command: EditorCommand) {
        applyCommand(command)
        undoHistory.addLast(command)
        redoHistory.clear()
        updateFlows()
    }

    override fun preview(command: EditorCommand) {
        applyCommand(command)
    }

    override fun undo() {
        if (undoHistory.isEmpty()) return
        val cmd = undoHistory.removeLast()
        invertCommand(cmd)
        redoHistory.addLast(cmd)
        updateFlows()
    }

    override fun redo() {
        if (redoHistory.isEmpty()) return
        val cmd = redoHistory.removeLast()
        applyCommand(cmd)
        undoHistory.addLast(cmd)
        updateFlows()
    }

    private fun updateFlows() {
        _canUndo.value = undoHistory.isNotEmpty()
        _canRedo.value = redoHistory.isNotEmpty()
    }

    private fun applyTransform(id: String, t: Transform2D) {
        val cur = timelineEngine.timeline.value
        val newOverlay = cur.overlayClips.map {
            if (it.id == id) {
                it.copy(
                    cropScale = t.scaleX,
                    rotationDegrees = t.rotationDeg.toInt(),
                    cropOffsetX = t.translationX,
                    cropOffsetY = t.translationY,
                    flipHorizontal = t.flipH,
                    flipVertical = t.flipV
                )
            } else it
        }
        val newText = cur.textClips.map {
            if (it.id == id) {
                it.copy(
                    scale = t.scaleX,
                    rotation = t.rotationDeg,
                    posX = t.translationX,
                    posY = t.translationY
                )
            } else it
        }
        val newSticker = cur.stickerClips.map {
            if (it.id == id) {
                it.copy(
                    scale = t.scaleX,
                    rotation = t.rotationDeg,
                    posX = t.translationX,
                    posY = t.translationY
                )
            } else it
        }
        val newVideo = cur.videoClips.map {
            if (it.id == id) {
                it.copy(
                    cropScale = t.scaleX,
                    rotationDegrees = t.rotationDeg.toInt(),
                    cropOffsetX = t.translationX,
                    cropOffsetY = t.translationY,
                    flipHorizontal = t.flipH,
                    flipVertical = t.flipV
                )
            } else it
        }
        timelineEngine.loadTimeline(cur.copy(
            videoClips = newVideo,
            overlayClips = newOverlay,
            textClips = newText,
            stickerClips = newSticker
        ))
    }

    private fun applyCommand(cmd: EditorCommand) {
        when (cmd) {
            is TransformLayerCommand -> {
                applyTransform(cmd.layerId, cmd.to)
            }
            is SetOpacityCommand -> {
                val cur = timelineEngine.timeline.value
                val op = cmd.value.coerceIn(0f, 1f)
                timelineEngine.loadTimeline(cur.copy(
                    videoClips = cur.videoClips.map { if (it.id == cmd.layerId) it.copy(opacity = op) else it },
                    overlayClips = cur.overlayClips.map { if (it.id == cmd.layerId) it.copy(opacity = op) else it },
                    textClips = cur.textClips.map { if (it.id == cmd.layerId) it.copy(opacity = op) else it },
                    stickerClips = cur.stickerClips.map { if (it.id == cmd.layerId) it.copy(opacity = op) else it }
                ))
            }
            is SetLayerVisibilityCommand -> {
                val cur = timelineEngine.timeline.value
                val isHidden = !cmd.visible
                timelineEngine.loadTimeline(cur.copy(
                    videoClips = cur.videoClips.map { if (it.id == cmd.id) it.copy(isHidden = isHidden) else it },
                    overlayClips = cur.overlayClips.map { if (it.id == cmd.id) it.copy(isHidden = isHidden) else it },
                    textClips = cur.textClips.map { if (it.id == cmd.id) it.copy(isHidden = isHidden) else it },
                    stickerClips = cur.stickerClips.map { if (it.id == cmd.id) it.copy(isHidden = isHidden) else it }
                ))
            }
            is SetLayerLockCommand -> {
                val cur = timelineEngine.timeline.value
                timelineEngine.loadTimeline(cur.copy(
                    videoClips = cur.videoClips.map { if (it.id == cmd.id) it.copy(isLocked = cmd.locked) else it },
                    overlayClips = cur.overlayClips.map { if (it.id == cmd.id) it.copy(isLocked = cmd.locked) else it },
                    textClips = cur.textClips.map { if (it.id == cmd.id) it.copy(isLocked = cmd.locked) else it },
                    stickerClips = cur.stickerClips.map { if (it.id == cmd.id) it.copy(isLocked = cmd.locked) else it }
                ))
            }
            is ReorderLayerCommand -> {
                val cur = timelineEngine.timeline.value
                val overlays = cur.overlayClips.toMutableList()
                if (cmd.from in overlays.indices && cmd.to in overlays.indices) {
                    val item = overlays.removeAt(cmd.from)
                    overlays.add(cmd.to, item)
                    val updated = overlays.mapIndexed { idx, clip -> clip.copy(trackIndex = idx) }
                    timelineEngine.loadTimeline(cur.copy(overlayClips = updated))
                }
            }
            is DeleteLayerCommand -> {
                timelineEngine.normalDelete(setOf(cmd.id))
            }
            is TrimClipCommand -> {
                val boundaryMs = cmd.boundaryUs / 1000L
                when (cmd.edge) {
                    TrimEdge.START -> timelineEngine.trimClipLeft(cmd.clipId, boundaryMs, snap = false)
                    TrimEdge.END -> timelineEngine.trimClipRight(cmd.clipId, boundaryMs, snap = false)
                }
            }
            is MoveClipCommand -> {
                val startMs = cmd.startUs / 1000L
                timelineEngine.moveClip(cmd.clipId, startMs, snap = false)
            }
            else -> {}
        }
    }

    private fun invertCommand(cmd: EditorCommand) {
        when (cmd) {
            is TransformLayerCommand -> {
                applyTransform(cmd.layerId, cmd.from)
            }
            is SetLayerVisibilityCommand -> {
                val cur = timelineEngine.timeline.value
                timelineEngine.loadTimeline(cur.copy(
                    videoClips = cur.videoClips.map { if (it.id == cmd.id) it.copy(isHidden = cmd.visible) else it },
                    overlayClips = cur.overlayClips.map { if (it.id == cmd.id) it.copy(isHidden = cmd.visible) else it },
                    textClips = cur.textClips.map { if (it.id == cmd.id) it.copy(isHidden = cmd.visible) else it },
                    stickerClips = cur.stickerClips.map { if (it.id == cmd.id) it.copy(isHidden = cmd.visible) else it }
                ))
            }
            is SetLayerLockCommand -> {
                val cur = timelineEngine.timeline.value
                timelineEngine.loadTimeline(cur.copy(
                    videoClips = cur.videoClips.map { if (it.id == cmd.id) it.copy(isLocked = !cmd.locked) else it },
                    overlayClips = cur.overlayClips.map { if (it.id == cmd.id) it.copy(isLocked = !cmd.locked) else it },
                    textClips = cur.textClips.map { if (it.id == cmd.id) it.copy(isLocked = !cmd.locked) else it },
                    stickerClips = cur.stickerClips.map { if (it.id == cmd.id) it.copy(isLocked = !cmd.locked) else it }
                ))
            }
            is ReorderLayerCommand -> {
                val cur = timelineEngine.timeline.value
                val overlays = cur.overlayClips.toMutableList()
                if (cmd.to in overlays.indices && cmd.from in overlays.indices) {
                    val item = overlays.removeAt(cmd.to)
                    overlays.add(cmd.from, item)
                    val updated = overlays.mapIndexed { idx, clip -> clip.copy(trackIndex = idx) }
                    timelineEngine.loadTimeline(cur.copy(overlayClips = updated))
                }
            }
            else -> {}
        }
    }
}
