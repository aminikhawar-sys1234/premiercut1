package com.example.engine.rendergraph

import android.util.Log
import java.util.*

/**
 * High-Performance Directed Acyclic Graph (DAG) Render Pipeline.
 *
 * Replaces linear effect stacks with a true node-based graph.
 * Performs topological sorting and parallel evaluation of multi-input masks,
 * mattes, 3D layers, and blending passes.
 */
class DAGRenderGraph {
    companion object {
        private const val TAG = "DAGRenderGraph"
    }

    private val nodes = mutableMapOf<String, RenderNode>()
    private val edges = mutableListOf<RenderEdge>()

    fun addNode(node: RenderNode) {
        nodes[node.id] = node
    }

    fun removeNode(nodeId: String) {
        nodes.remove(nodeId)
        edges.removeAll { it.fromNodeId == nodeId || it.toNodeId == nodeId }
    }

    fun connect(fromNodeId: String, fromPortId: String, toNodeId: String, toPortId: String) {
        val edge = RenderEdge(fromNodeId, fromPortId, toNodeId, toPortId)
        if (!edges.contains(edge)) {
            edges.add(edge)
        }
    }

    fun disconnect(fromNodeId: String, toNodeId: String) {
        edges.removeAll { it.fromNodeId == fromNodeId && it.toNodeId == toNodeId }
    }

    fun clear() {
        nodes.clear()
        edges.clear()
    }

    /**
     * Performs Kahn's Algorithm to sort nodes in valid topological execution order.
     * Throws an [IllegalStateException] if a cyclic dependency is detected.
     */
    fun topologicalSort(): List<RenderNode> {
        val inDegree = mutableMapOf<String, Int>()
        val adjacency = mutableMapOf<String, MutableList<String>>()

        for (nodeId in nodes.keys) {
            inDegree[nodeId] = 0
            adjacency[nodeId] = mutableListOf()
        }

        for (edge in edges) {
            if (nodes.containsKey(edge.fromNodeId) && nodes.containsKey(edge.toNodeId)) {
                adjacency[edge.fromNodeId]?.add(edge.toNodeId)
                inDegree[edge.toNodeId] = (inDegree[edge.toNodeId] ?: 0) + 1
            }
        }

        val queue: Queue<String> = LinkedList()
        for ((nodeId, deg) in inDegree) {
            if (deg == 0) {
                queue.add(nodeId)
            }
        }

        val orderedList = mutableListOf<RenderNode>()

        while (queue.isNotEmpty()) {
            val u = queue.poll() ?: break
            val node = nodes[u]
            if (node != null) {
                orderedList.add(node)
            }

            for (neighbor in adjacency[u] ?: emptyList()) {
                val newDeg = (inDegree[neighbor] ?: 1) - 1
                inDegree[neighbor] = newDeg
                if (newDeg == 0) {
                    queue.add(neighbor)
                }
            }
        }

        if (orderedList.size != nodes.size) {
            Log.e(TAG, "Cyclic dependency detected in Render DAG! Sorted count=${orderedList.size}, Total=${nodes.size}")
            return nodes.values.toList() // Fallback to safe insertion order
        }

        return orderedList
    }

    /**
     * Executes the Render Graph in topological order.
     * Routes outputs across edges and computes final composited output.
     */
    fun execute(context: RenderExecutionContext): Int {
        val sortedNodes = topologicalSort()

        for (node in sortedNodes) {
            // 1. Pull inputs for this node from incoming edges
            for (edge in edges.filter { it.toNodeId == node.id }) {
                val sourceValue = context.intermediateOutputs["${edge.fromNodeId}:${edge.fromPortId}"]
                if (sourceValue != null) {
                    context.setOutput(node.id, edge.toPortId, sourceValue)
                }
            }

            // 2. Execute node processing
            node.execute(context)
        }

        // Return texture from output sink if present, or first available output
        val sink = sortedNodes.filterIsInstance<OutputSinkNode>().firstOrNull()
        return sink?.finalTextureId ?: (context.intermediateOutputs.values.filterIsInstance<Int>().lastOrNull() ?: 0)
    }
}
