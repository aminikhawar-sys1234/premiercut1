package com.ahstudio.composition.edit

import com.ahstudio.composition.graph.*
import org.json.JSONObject

interface CompositionCommand {
    val label: String
    fun apply()
    fun revert()
    fun toJson(): JSONObject
}

/** Main-thread-only history (UI commands). */
class CommandHistory {
    private val undoStack = ArrayDeque<CompositionCommand>()
    private val redoStack = ArrayDeque<CompositionCommand>()
    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    fun execute(cmd: CompositionCommand) { cmd.apply(); undoStack.addLast(cmd); redoStack.clear() }
    fun undo(): CompositionCommand? = undoStack.removeLastOrNull()?.also { it.revert(); redoStack.addLast(it) }
    fun redo(): CompositionCommand? = redoStack.removeLastOrNull()?.also { it.apply(); undoStack.addLast(it) }
    fun clear() { undoStack.clear(); redoStack.clear() }
}

class AddLayerCommand(private val graph: CompositionGraph, private val layer: CompositionLayer) : CompositionCommand {
    override val label = "AddLayer"
    override fun apply() = graph.addLayer(layer)
    override fun revert() = graph.removeLayer(layer.id)
    override fun toJson() = JSONObject().put("op", "ADD_LAYER").put("comp", graph.compositionId.value)
        .put("layer", com.ahstudio.composition.io.CompositionJson.layerToJson(layer))
}

class RemoveLayerCommand(private val graph: CompositionGraph, private val id: LayerId) : CompositionCommand {
    override val label = "RemoveLayer"
    private val removed = graph.layer(id)
    private val childParentRestore = graph.allLayers.filter { it.parentId == id }.map { it.id to it.parentId }
    private val matteRestore = graph.allLayers.filter { it.trackMatteLayer == id }.map { it.id to (it.trackMatteLayer to it.trackMatteMode) }
    override fun apply() = graph.removeLayer(id)
    override fun revert() {
        val r = removed ?: return
        graph.addLayer(r)
        for ((cid, pid) in childParentRestore) graph.layer(cid)?.let { graph.replaceLayer(it.copy(parentId = pid)) }
        for ((cid, pair) in matteRestore) graph.layer(cid)?.let {
            graph.replaceLayer(it.copy(trackMatteLayer = pair.first, trackMatteMode = pair.second))
        }
    }
    override fun toJson() = JSONObject().put("op", "REMOVE_LAYER").put("comp", graph.compositionId.value)
        .put("layerId", removed?.id?.value ?: -1)
}

class TransformLayerCommand(
    private val graph: CompositionGraph, private val id: LayerId, private val newT: Transform2D,
) : CompositionCommand {
    override val label = "TransformLayer"
    private val old = graph.layer(id)?.transform
    override fun apply() { graph.layer(id)?.let { graph.replaceLayer(it.copy(transform = newT)); graph.touchProperty(id) } }
    override fun revert() { old?.let { o -> graph.layer(id)?.let { graph.replaceLayer(it.copy(transform = o)); graph.touchProperty(id) } } }
    override fun toJson() = JSONObject().put("op", "TRANSFORM").put("comp", graph.compositionId.value).put("layerId", id.value)
        .put("new", com.ahstudio.composition.io.CompositionJson.transformToJson(newT))
}

class SetOpacityCommand(
    private val graph: CompositionGraph, private val id: LayerId,
    private val newOp: PropertyTrack<Float>,
) : CompositionCommand {
    override val label = "SetOpacity"
    private val old = graph.layer(id)?.opacity
    override fun apply() { graph.layer(id)?.let { graph.replaceLayer(it.copy(opacity = newOp)); graph.touchProperty(id) } }
    override fun revert() { old?.let { o -> graph.layer(id)?.let { graph.replaceLayer(it.copy(opacity = o)); graph.touchProperty(id) } } }
    override fun toJson() = JSONObject().put("op", "SET_OPACITY").put("comp", graph.compositionId.value).put("layerId", id.value)
        .put("new", com.ahstudio.composition.io.CompositionJson.floatTrackToJson(newOp))
}

class SetBlendModeCommand(
    private val graph: CompositionGraph, private val id: LayerId, private val newMode: BlendMode,
) : CompositionCommand {
    override val label = "SetBlendMode"
    private val old = graph.layer(id)?.blendMode
    override fun apply() { graph.layer(id)?.let { graph.replaceLayer(it.copy(blendMode = newMode)); graph.touchProperty(id) } }
    override fun revert() { old?.let { o -> graph.layer(id)?.let { graph.replaceLayer(it.copy(blendMode = o)); graph.touchProperty(id) } } }
    override fun toJson() = JSONObject().put("op", "SET_BLEND").put("comp", graph.compositionId.value)
        .put("layerId", id.value).put("new", newMode.name)
}

class ReorderLayerCommand(
    private val graph: CompositionGraph, private val id: LayerId, private val newZ: Int,
) : CompositionCommand {
    override val label = "ReorderLayer"
    private val old = graph.layer(id)?.zOrder
    override fun apply() { graph.layer(id)?.let { graph.replaceLayer(it.copy(zOrder = newZ)); graph.touchProperty(id) } }
    override fun revert() { old?.let { o -> graph.layer(id)?.let { graph.replaceLayer(it.copy(zOrder = o)); graph.touchProperty(id) } } }
    override fun toJson() = JSONObject().put("op", "REORDER").put("comp", graph.compositionId.value)
        .put("layerId", id.value).put("newZ", newZ)
}

class SetParentCommand(
    private val graph: CompositionGraph, private val id: LayerId, private val newParent: LayerId?,
) : CompositionCommand {
    override val label = "SetParent"
    private val old = graph.layer(id)?.parentId
    override fun apply() {
        if (newParent != null && com.ahstudio.composition.graph.GraphValidator.wouldCreateParentCycle(graph, id, newParent))
            throw IllegalArgumentException("circular parenting rejected")
        graph.layer(id)?.let { graph.replaceLayer(it.copy(parentId = newParent)); graph.touchProperty(id) }
    }
    override fun revert() { graph.layer(id)?.let { graph.replaceLayer(it.copy(parentId = old)); graph.touchProperty(id) } }
    override fun toJson() = JSONObject().put("op", "SET_PARENT").put("comp", graph.compositionId.value)
        .put("layerId", id.value).put("new", newParent?.value ?: -1)
}

class AddMaskCommand(private val graph: CompositionGraph, private val id: LayerId, private val mask: MaskInstance) : CompositionCommand {
    override val label = "AddMask"
    override fun apply() { graph.layer(id)?.let { graph.replaceLayer(it.copy(masks = it.masks + mask)); graph.touchProperty(id) } }
    override fun revert() { graph.layer(id)?.let { graph.replaceLayer(it.copy(masks = it.masks.filterNot { m -> m.id == mask.id })); graph.touchProperty(id) } }
    override fun toJson() = JSONObject().put("op", "ADD_MASK").put("comp", graph.compositionId.value).put("layerId", id.value)
        .put("mask", com.ahstudio.composition.io.CompositionJson.maskToJson(mask))
}

class RemoveMaskCommand(private val graph: CompositionGraph, private val id: LayerId, private val maskId: Long) : CompositionCommand {
    override val label = "RemoveMask"
    private val removed = graph.layer(id)?.masks?.firstOrNull { it.id == maskId }
    override fun apply() { graph.layer(id)?.let { graph.replaceLayer(it.copy(masks = it.masks.filterNot { m -> m.id == maskId })); graph.touchProperty(id) } }
    override fun revert() { removed?.let { m -> graph.layer(id)?.let { graph.replaceLayer(it.copy(masks = it.masks + m)); graph.touchProperty(id) } } }
    override fun toJson() = JSONObject().put("op", "REMOVE_MASK").put("comp", graph.compositionId.value)
        .put("layerId", id.value).put("maskId", maskId)
}

class SetTrackMatteCommand(
    private val graph: CompositionGraph, private val id: LayerId,
    private val newLayer: LayerId?, private val newMode: TrackMatteMode,
) : CompositionCommand {
    override val label = "SetTrackMatte"
    private val oldL = graph.layer(id)?.trackMatteLayer; private val oldM = graph.layer(id)?.trackMatteMode
    override fun apply() { graph.layer(id)?.let { graph.replaceLayer(it.copy(trackMatteLayer = newLayer, trackMatteMode = newMode)); graph.touchProperty(id) } }
    override fun revert() { graph.layer(id)?.let { graph.replaceLayer(it.copy(trackMatteLayer = oldL, trackMatteMode = oldM ?: TrackMatteMode.NONE)); graph.touchProperty(id) } }
    override fun toJson() = JSONObject().put("op", "SET_MATTE").put("comp", graph.compositionId.value).put("layerId", id.value)
        .put("newLayer", newLayer?.value ?: -1).put("newMode", newMode.name)
}
