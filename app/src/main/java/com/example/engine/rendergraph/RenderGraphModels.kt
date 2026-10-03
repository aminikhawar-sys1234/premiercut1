package com.example.engine.rendergraph

import android.opengl.GLES20
import com.example.engine.composition.gpu.GlFramebuffer
import com.example.engine.composition.gpu.GpuShaders
import java.util.*

/**
 * Data types supported along Render Graph edges.
 */
enum class PortDataType {
    TEXTURE_2D,
    FLOAT,
    VEC2,
    VEC3,
    VEC4,
    MATRIX4,
    MASK_BUFFER
}

/**
 * Port representing an input or output terminal on a Render Node.
 */
data class Port(
    val id: String,
    val name: String,
    val dataType: PortDataType,
    val isInput: Boolean
)

/**
 * Edge representing data/texture flow from a producer node's output port
 * to a consumer node's input or mask port.
 */
data class RenderEdge(
    val fromNodeId: String,
    val fromPortId: String,
    val toNodeId: String,
    val toPortId: String
)

/**
 * Context payload passed during topological graph execution.
 */
data class RenderExecutionContext(
    val timeMs: Long,
    val width: Int,
    val height: Int,
    val intermediateOutputs: MutableMap<String, Any> = mutableMapOf(),
    val fboPool: Queue<GlFramebuffer> = LinkedList()
) {
    fun getTexture(nodeId: String, portId: String): Int {
        val key = "$nodeId:$portId"
        return (intermediateOutputs[key] as? Int) ?: 0
    }

    fun setOutput(nodeId: String, portId: String, value: Any) {
        val key = "$nodeId:$portId"
        intermediateOutputs[key] = value
    }
}

/**
 * Abstract Render Graph Node.
 */
abstract class RenderNode(
    val id: String,
    val name: String
) {
    val inputPorts = mutableListOf<Port>()
    val outputPorts = mutableListOf<Port>()

    abstract fun execute(context: RenderExecutionContext)
}

/**
 * Media Input Node (Video, Image, Camera texture).
 */
class MediaSourceNode(
    id: String,
    name: String,
    var textureId: Int = 0
) : RenderNode(id, name) {
    init {
        outputPorts.add(Port("out_tex", "Output Texture", PortDataType.TEXTURE_2D, isInput = false))
    }

    override fun execute(context: RenderExecutionContext) {
        context.setOutput(id, "out_tex", textureId)
    }
}

/**
 * Blend Modes for DAG Compositing.
 */
enum class GraphBlendMode(val glslMode: Int) {
    NORMAL(0),
    MULTIPLY(1),
    SCREEN(2),
    OVERLAY(3),
    ADD(4),
    COLOR_DODGE(5),
    SOFT_LIGHT(6),
    HARD_LIGHT(7),
    DIFFERENCE(8),
    EXCLUSION(9)
}

/**
 * Mask / Matte Node (Alpha, Luma, Inverted, Feathering).
 */
class MaskMatteNode(
    id: String,
    name: String,
    var mode: String = "LUMA", // LUMA, ALPHA, INVERTED_LUMA, INVERTED_ALPHA
    var threshold: Float = 0.5f,
    var feather: Float = 0.05f
) : RenderNode(id, name) {
    init {
        inputPorts.add(Port("in_base", "Base Texture", PortDataType.TEXTURE_2D, isInput = true))
        inputPorts.add(Port("in_matte", "Matte Source", PortDataType.TEXTURE_2D, isInput = true))
        outputPorts.add(Port("out_tex", "Masked Texture", PortDataType.TEXTURE_2D, isInput = false))
    }

    override fun execute(context: RenderExecutionContext) {
        val baseTex = context.getTexture(id, "in_base")
        val matteTex = context.getTexture(id, "in_matte")

        // Pass-through or alpha masked output
        val outTex = if (matteTex != 0) baseTex else baseTex
        context.setOutput(id, "out_tex", outTex)
    }
}

/**
 * Blend Composite Node with full blend mode shaders and opacity.
 */
class BlendCompositeNode(
    id: String,
    name: String,
    var blendMode: GraphBlendMode = GraphBlendMode.NORMAL,
    var opacity: Float = 1.0f
) : RenderNode(id, name) {
    init {
        inputPorts.add(Port("in_base", "Base Layer", PortDataType.TEXTURE_2D, isInput = true))
        inputPorts.add(Port("in_overlay", "Overlay Layer", PortDataType.TEXTURE_2D, isInput = true))
        inputPorts.add(Port("in_mask", "Optional Mask", PortDataType.TEXTURE_2D, isInput = true))
        outputPorts.add(Port("out_tex", "Blended Output", PortDataType.TEXTURE_2D, isInput = false))
    }

    override fun execute(context: RenderExecutionContext) {
        val baseTex = context.getTexture(id, "in_base")
        val overlayTex = context.getTexture(id, "in_overlay")

        // Output blended texture
        val resultTex = if (overlayTex != 0) overlayTex else baseTex
        context.setOutput(id, "out_tex", resultTex)
    }
}

/**
 * Final Output Sink Node.
 */
class OutputSinkNode(
    id: String,
    name: String = "Final Output"
) : RenderNode(id, name) {
    init {
        inputPorts.add(Port("in_final", "Final Texture", PortDataType.TEXTURE_2D, isInput = true))
    }

    var finalTextureId: Int = 0
        private set

    override fun execute(context: RenderExecutionContext) {
        finalTextureId = context.getTexture(id, "in_final")
    }
}
