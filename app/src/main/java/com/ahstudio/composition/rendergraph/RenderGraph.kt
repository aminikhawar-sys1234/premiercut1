package com.ahstudio.composition.rendergraph

/** Explicit dependency-ordered render graph — never "for(layer) draw(layer)". */
class RenderGraph {
    open class Node(val id: String, val deps: List<String>) {
        class Source(layerId: Long, val kind: Kind) : Node("L$layerId:src", emptyList()) {
            enum class Kind { VIDEO, IMAGE, TEXT, SHAPE, STICKER, PRECOMP, WHITE }
        }
        class TransformPass(layerId: Long) : Node("L$layerId:xf", listOf("L$layerId:src"))
        class EffectPassNode(layerId: Long, val passIndex: Int, prev: String) : Node("L$layerId:fx$passIndex", listOf(prev))
        class MaskPassNode(layerId: Long, prev: String) : Node("L$layerId:mask", listOf(prev))
        class MattePassNode(layerId: Long, prev: String, val matteLayerId: Long) : Node("L$layerId:matte", listOf(prev, "L$matteLayerId:matteSrc"))
        class BlendNode(layerId: Long, prev: String, val mode: Int, val opacity: Float, val op: Int) : Node("L$layerId:blend", listOf(prev, "backdrop"))
        class AdjustmentNode(layerId: Long, prev: String) : Node("L$layerId:adj", listOf(prev, "backdrop"))
        class Output(prev: String) : Node("out", listOf(prev))
    }

    private val nodes = LinkedHashMap<String, Node>()
    fun add(n: Node) { nodes[n.id] = n }
    fun clear() = nodes.clear()

    /** Kahn topological sort with cycle detection. */
    fun topoOrder(): List<Node> {
        val indeg = HashMap<String, Int>(); val outEdges = HashMap<String, MutableList<String>>()
        nodes.values.forEach { indeg.putIfAbsent(it.id, 0) }
        nodes.values.forEach { n ->
            n.deps.forEach { d ->
                if (nodes.containsKey(d)) {
                    outEdges.getOrPut(d) { mutableListOf() }.add(n.id)
                    indeg[n.id] = (indeg[n.id] ?: 0) + 1
                } else if (d != "backdrop") {
                    throw IllegalStateException("RenderGraph missing dependency $d for ${n.id}")
                }
            }
        }
        val q = ArrayDeque(nodes.keys.filter { (indeg[it] ?: 0) == 0 })
        val order = mutableListOf<Node>()
        while (q.isNotEmpty()) {
            val id = q.removeFirst(); order += nodes[id]!!
            outEdges[id]?.forEach { t -> val d = (indeg[t] ?: 1) - 1; indeg[t] = d; if (d == 0) q.addLast(t) }
        }
        if (order.size != nodes.size) throw IllegalStateException("RenderGraph cycle detected")
        return order
    }
}
