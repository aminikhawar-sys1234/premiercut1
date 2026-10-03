package com.ahstudio.composition.edit

import com.ahstudio.composition.graph.*

/** All Screen Editor gestures go through here → commands → history. UI never mutates engine state directly. */
class CompositionEditorFacade(private val graph: CompositionGraph, private val history: CommandHistory) {

    fun addLayer(layer: CompositionLayer) = history.execute(AddLayerCommand(graph, layer))
    fun removeLayer(id: LayerId) = history.execute(RemoveLayerCommand(graph, id))
    fun setTransform(id: LayerId, t: Transform2D) = history.execute(TransformLayerCommand(graph, id, t))
    fun setOpacity(id: LayerId, track: PropertyTrack<Float>) = history.execute(SetOpacityCommand(graph, id, track))
    fun setBlendMode(id: LayerId, mode: BlendMode) = history.execute(SetBlendModeCommand(graph, id, mode))
    fun reorder(id: LayerId, newZ: Int) = history.execute(ReorderLayerCommand(graph, id, newZ))
    fun setParent(id: LayerId, parent: LayerId?) = history.execute(SetParentCommand(graph, id, parent))
    fun addMask(id: LayerId, mask: MaskInstance) = history.execute(AddMaskCommand(graph, id, mask))
    fun removeMask(id: LayerId, maskId: Long) = history.execute(RemoveMaskCommand(graph, id, maskId))
    fun setTrackMatte(id: LayerId, matte: LayerId?, mode: TrackMatteMode) = history.execute(SetTrackMatteCommand(graph, id, matte, mode))

    /** Gesture convenience: live drag uses this, final drop commits one undoable command. */
    fun dragPreview(id: LayerId, dxPx: Float, dyPx: Float): Transform2D? {
        val cur = graph.layer(id)?.transform ?: return null
        return cur.copy(positionX = PropertyTrack(cur.positionX.staticValue + dxPx), positionY = PropertyTrack(cur.positionY.staticValue + dyPx))
    }
    fun undo() = history.undo()
    fun redo() = history.redo()
}
