package com.ahstudio.composition.core

import com.ahstudio.composition.graph.*

/** Pure planner: Timeline state → deterministic Op list. NO GL, NO Android deps → unit-testable. */
object CompositionPlanner {

    sealed class Op {
        data class DrawLayer(val layer: CompositionLayer, val hasMatte: Boolean, val skipReason: String? = null) : Op()
        data class Adjust(val layer: CompositionLayer) : Op()
        data class Skip(val layer: CompositionLayer, val reason: String) : Op()
    }

    data class Plan(
        val timeUs: Long,
        val ops: List<Op>,
        val hiddenMatteLayerIds: Set<Long>,
        val errors: List<CompositionError>,
    )

    fun plan(graph: CompositionGraph, timeUs: Long): Plan {
        val errors = graph.validate()
        val order = graph.drawOrderAt(timeUs)
        val activeIds = order.map { it.id.value }.toHashSet()

        // Matte consumers: matte source is consumed (hidden) if active; if matte inactive → consumer draws normally (AE semantics).
        val consumers = order.filter { it.trackMatteMode != TrackMatteMode.NONE && it.trackMatteLayer != null }
        val hidden = consumers.mapNotNull { c ->
            val m = c.trackMatteLayer!!.value
            if (activeIds.contains(m)) m else null
        }.toSet()

        val ops = ArrayList<Op>(order.size)
        for (l in order) {
            if (l.id.value in hidden) continue // consumed as matte — never double-composited
            if (!l.visible) { ops += Op.Skip(l, "invisible"); continue }
            if (!l.enabled) { ops += Op.Skip(l, "disabled"); continue }
            when (l.type) {
                LayerType.NULL -> ops += Op.Skip(l, "null layer (transform holder only)")
                LayerType.CAMERA -> ops += Op.Skip(l, "camera: consumed via parenting (3D extension point)")
                LayerType.ADJUSTMENT -> ops += Op.Adjust(l)
                else -> {
                    val hasMatte = l.trackMatteMode != TrackMatteMode.NONE && l.trackMatteLayer != null && activeIds.contains(l.trackMatteLayer.value)
                    ops += Op.DrawLayer(l, hasMatte)
                }
            }
        }
        return Plan(timeUs, ops, hidden, errors)
    }
}
