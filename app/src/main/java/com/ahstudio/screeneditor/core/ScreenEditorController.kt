package com.ahstudio.screeneditor.core

import android.graphics.PointF
import com.ahstudio.screeneditor.bridge.ScreenEditorCommandUndoRedoBridge
import com.ahstudio.screeneditor.bridge.ScreenEditorKeyframeBridge
import com.ahstudio.screeneditor.bridge.ScreenEditorTimelineBridge
import com.ahstudio.screeneditor.composition.ScreenEditorCompositionEngine
import com.ahstudio.screeneditor.layers.ScreenEditorLayerManager
import com.ahstudio.screeneditor.ports.DeleteLayerCommand
import com.ahstudio.screeneditor.ports.EditTextCommand
import com.ahstudio.screeneditor.ports.EffectChainRef
import com.ahstudio.screeneditor.ports.MoveClipCommand
import com.ahstudio.screeneditor.ports.PlaybackPort
import com.ahstudio.screeneditor.ports.ReorderLayerCommand
import com.ahstudio.screeneditor.ports.SetCropCommand
import com.ahstudio.screeneditor.ports.SetEffectParamCommand
import com.ahstudio.screeneditor.ports.SetLayerLockCommand
import com.ahstudio.screeneditor.ports.SetLayerVisibilityCommand
import com.ahstudio.screeneditor.ports.SetOpacityCommand
import com.ahstudio.screeneditor.ports.TextStateRef
import com.ahstudio.screeneditor.ports.TimelinePort
import com.ahstudio.screeneditor.ports.TrimClipCommand
import com.ahstudio.screeneditor.ports.TrimEdge
import com.ahstudio.screeneditor.transform.CropRect
import com.ahstudio.screeneditor.transform.Transform2D
import com.ahstudio.screeneditor.viewport.ScreenEditorViewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScreenEditorController(
    val viewport: ScreenEditorViewport,
    val timelineBridge: ScreenEditorTimelineBridge,
    val layers: ScreenEditorLayerManager,
    val keyframes: ScreenEditorKeyframeBridge,
    val composition: ScreenEditorCompositionEngine,
    val undo: ScreenEditorCommandUndoRedoBridge,
    val timelinePort: TimelinePort,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ScreenEditorState())
    val state: StateFlow<ScreenEditorState> = _state.asStateFlow()

    val selectedLayerId: StateFlow<String?> = _state.map { it.selectedLayerId }
        .distinctUntilChanged()
        .let { flow ->
            val flowState = MutableStateFlow(_state.value.selectedLayerId)
            scope.launch { flow.collect { flowState.value = it } }
            flowState.asStateFlow()
        }

    val timelinePositionUs: StateFlow<Long> = _state.map { it.currentTimelinePositionUs }
        .distinctUntilChanged()
        .let { flow ->
            val flowState = MutableStateFlow(_state.value.currentTimelinePositionUs)
            scope.launch { flow.collect { flowState.value = it } }
            flowState.asStateFlow()
        }

    fun initialize(playback: PlaybackPort) {
        scope.launch {
            timelineBridge.observedPositionUs.collect { us ->
                _state.update { it.copy(currentTimelinePositionUs = us) }
                composition.onPositionChanged(us)
                val clipsAtNow = timelinePort.clipsAt(us)
                val ctxClip = clipsAtNow.firstOrNull { it.enabled }?.id
                _state.update { s -> s.copy(contextClipId = ctxClip ?: s.contextClipId) }
            }
        }

        scope.launch {
            timelineBridge.isScrubbing.collect { isScrub ->
                _state.update { it.copy(isScrubbing = isScrub) }
            }
        }

        scope.launch {
            playback.isPlaying.collect { isPlaying ->
                _state.update { it.copy(isPlaying = isPlaying) }
            }
        }

        scope.launch {
            viewport.state.collect { v ->
                _state.update {
                    it.copy(
                        viewportScale = v.scale,
                        viewportTranslation = PointF(v.tx, v.ty)
                    )
                }
            }
        }
    }

    // ---- Selection ----
    fun selectLayer(id: String?) {
        val clipId = if (id != null) {
            val clip = timelinePort.clipsAt(state.value.currentTimelinePositionUs).firstOrNull { it.layerId == id }
                ?: timelinePort.clipById(id)
            clip?.id ?: id
        } else null
        _state.update { it.copy(selectedLayerId = id, selectedClipId = clipId) }
    }

    fun selectClip(id: String?) {
        val layerId = if (id != null) {
            val clip = timelinePort.clipById(id)
            clip?.layerId ?: id
        } else null
        _state.update { it.copy(selectedClipId = id, selectedLayerId = layerId) }
    }

    fun setTool(tool: EditorTool) = _state.update { it.copy(activeTool = tool) }
    fun setSnapping(on: Boolean) = _state.update { it.copy(snappingEnabled = on) }
    fun setGuides(on: Boolean) = _state.update { it.copy(guidesEnabled = on) }

    // ---- Transform lifecycle: preview-live -> commit-as-command ----
    fun beginTransform(layerId: String, snapshot: Transform2D) {
        _state.update { it.copy(isTransforming = true, interactionMode = InteractionMode.TRANSFORMING) }
        undo.beginCoalescedTransform(layerId, snapshot)
    }

    fun updateTransformLive(layerId: String, t: Transform2D) {
        layers.setTransformLive(layerId, t)
        composition.invalidate()
    }

    fun commitTransform(layerId: String, final: Transform2D) {
        layers.setTransformLive(layerId, final)
        undo.commitCoalescedTransform(layerId, final)
        _state.update { it.copy(isTransforming = false, interactionMode = InteractionMode.IDLE) }
    }

    fun cancelTransform(layerId: String, original: Transform2D) {
        layers.setTransformLive(layerId, original)
        composition.invalidate()
        undo.cancelCoalesced()
        _state.update { it.copy(isTransforming = false, interactionMode = InteractionMode.IDLE) }
    }

    // Property edits - always via undo bridge
    fun setOpacity(layerId: String, opacity: Float) = undo.execute(SetOpacityCommand(layerId, opacity))
    fun setLayerVisible(id: String, v: Boolean) = undo.execute(SetLayerVisibilityCommand(id, v))
    fun setLayerLocked(id: String, v: Boolean) = undo.execute(SetLayerLockCommand(id, v))
    fun reorderLayers(from: Int, to: Int) = undo.execute(ReorderLayerCommand(from, to))
    fun deleteLayer(id: String) = undo.execute(DeleteLayerCommand(id))
    fun trimClip(clipId: String, edge: TrimEdge, us: Long) = undo.execute(TrimClipCommand(clipId, edge, us))
    fun moveClip(clipId: String, startUs: Long, trackId: String?) = undo.execute(MoveClipCommand(clipId, startUs, trackId))
    fun setCrop(layerId: String, from: CropRect, to: CropRect) = undo.execute(SetCropCommand(layerId, from, to))
    fun setEffectParam(layerId: String, chain: EffectChainRef, paramId: String, value: Float) =
        undo.execute(SetEffectParamCommand(layerId, chain, paramId, value))
    fun editText(layerId: String, mutate: (TextStateRef) -> TextStateRef) = undo.execute(EditTextCommand(layerId, mutate))

    fun undo() = undo.undo()
    fun redo() = undo.redo()
}
