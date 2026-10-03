package com.ahstudio.composition.graph

sealed class CompositionError {
    data class CircularParenting(val chain: List<LayerId>) : CompositionError()
    data class CircularMatte(val chain: List<LayerId>) : CompositionError()
    data class MissingParent(val layer: LayerId, val parent: LayerId) : CompositionError()
    data class MissingMatteLayer(val layer: LayerId, val matte: LayerId) : CompositionError()
}

class CompositionGraph(val compositionId: CompositionId) {
    private val layers = LinkedHashMap<Long, CompositionLayer>()
    var structureVersion: Long = 0; private set
    var propertyVersions = HashMap<Long, Long>()
    var listener: ((CompositionGraph) -> Unit)? = null

    val allLayers: List<CompositionLayer> get() = layers.values.toList()
    fun layer(id: LayerId): CompositionLayer? = layers[id.value]

    fun addLayer(l: CompositionLayer) { layers[l.id.value] = l; bump() }
    fun removeLayer(id: LayerId) {
        layers.remove(id.value)
        layers.values.filter { it.parentId == id }.forEach { layers[it.id.value] = it.copy(parentId = null) }
        layers.values.filter { it.trackMatteLayer == id }.forEach { layers[it.id.value] = it.copy(trackMatteLayer = null, trackMatteMode = TrackMatteMode.NONE) }
        bump()
    }
    fun replaceLayer(l: CompositionLayer) { layers[l.id.value] = l; bump() }
    fun touchProperty(id: LayerId) { propertyVersions[id.value] = (propertyVersions[id.value] ?: 0) + 1; listener?.invoke(this) }
    private fun bump() { structureVersion++; listener?.invoke(this) }

    /** Deterministic draw order: ascending z, insertion index tie-break. Index 0 = bottom. */
    fun drawOrderAt(timeUs: Long): List<CompositionLayer> =
        layers.values.filter { it.activeAt(timeUs) }
            .sortedWith(compareBy({ it.zOrder }, { it.insertionIndex }))

    /** Active layers whose matte reference resolves to this layer (they must not double-render). */
    fun matteConsumersOf(id: LayerId, timeUs: Long): List<CompositionLayer> =
        drawOrderAt(timeUs).filter { it.trackMatteLayer == id && it.trackMatteMode != TrackMatteMode.NONE }

    fun validate(): List<CompositionError> {
        val errs = mutableListOf<CompositionError>()
        for (l in layers.values) {
            l.parentId?.let { p ->
                if (!layers.containsKey(p.value)) errs += CompositionError.MissingParent(l.id, p)
                else if (reachable(l.id, p)) errs += CompositionError.CircularParenting(listOf(l.id, p))
            }
            if (l.trackMatteMode != TrackMatteMode.NONE) {
                val m = l.trackMatteLayer
                if (m == null || !layers.containsKey(m.value)) errs += CompositionError.MissingMatteLayer(l.id, m ?: LayerId(-1))
                else if (reachable(l.id, m)) errs += CompositionError.CircularMatte(listOf(l.id, m))
            }
        }
        return errs
    }

    private fun reachable(from: LayerId, target: LayerId): Boolean {
        val seen = HashSet<Long>(); val stack = ArrayDeque<Long>()
        layers.values.filter { it.parentId == from || it.trackMatteLayer == from }.forEach { stack.addLast(it.id.value) }
        while (stack.isNotEmpty()) {
            val cur = stack.removeFirst()
            if (cur == target.value) return true
            if (!seen.add(cur)) continue
            layers.values.filter { it.parentId == LayerId(cur) || it.trackMatteLayer == LayerId(cur) }.forEach { stack.addLast(it.id.value) }
        }
        return false
    }
}

object GraphValidator {
    /** Would making child→parent introduce a cycle? (walk up from candidate parent) */
    fun wouldCreateParentCycle(graph: CompositionGraph, child: LayerId, parent: LayerId): Boolean {
        var cur: LayerId? = parent; val seen = HashSet<Long>()
        while (cur != null && seen.add(cur.value)) {
            if (cur == child) return true
            cur = graph.layer(cur)?.parentId
        }
        return false
    }
}
