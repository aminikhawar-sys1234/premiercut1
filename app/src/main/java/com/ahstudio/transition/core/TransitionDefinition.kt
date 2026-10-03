package com.ahstudio.transition.core

enum class TransitionFamily { DISSOLVE, ZOOM, SLIDE, PUSH, WIPE, RADIAL, SHAPE, BLUR, GLITCH,
    DISTORTION, RIPPLE, LIGHT, PARTICLE, PIXEL, MASK, FILM, CAMERA, PROCEDURAL, OTHER }

enum class AlphaMode { OPAQUE, PREMULTIPLIED, STRAIGHT }

/**
 * Shader source. [fragment] must NOT contain "#version" — the compiler injects it.
 * A custom [vertexOverride] must declare `in vec2 aPosition; in vec2 aUv; out vec2 vUv;`
 * (the engine binds those attribute locations and feeds fullscreen-quad geometry).
 */
data class ShaderSource(
    val id: String,
    val fragment: String,
    val vertexOverride: String? = null,
    val maxSourceBytes: Int = TransitionEngineMetadata.DEFAULT_SHADER_SIZE_CAP_BYTES,
)

data class TransitionDefinition(
    val id: String,
    val name: String,
    val family: TransitionFamily,
    val version: Int,
    val minEngineVersion: Int,
    val parameters: List<TransitionParameterDefinition>,
    val shaders: Map<String, ShaderSource>,
    val graph: TransitionRenderGraphSpec,
    val defaultDurationMs: Long = 800,
    val alphaMode: AlphaMode = AlphaMode.OPAQUE,
) {
    init {
        require(graph.passes.all { it.shaderId in shaders }) {
            "Graph references shader ids missing from definition: ${graph.passes.map { it.shaderId }}"
        }
        require(defaultDurationMs in 1..TransitionEngineMetadata.MAX_DURATION_MS) {
            "defaultDurationMs out of range: $defaultDurationMs"
        }
    }

    /** Definition defaults + clamped overrides. Warnings for unknown/mismatched overrides. */
    fun resolveParameters(overrides: Map<String, ParamValue>):
            Pair<Map<String, ResolvedParameter>, List<String>> {
        val out = HashMap<String, ResolvedParameter>(parameters.size)
        val warnings = mutableListOf<String>()
        for (def in parameters) {
            val raw = overrides[def.id] ?: def.defaultValue
            val (coerced, warn) = ParameterMath.coerce(raw, def)
            if (warn != null) warnings += warn
            out[def.id] = ResolvedParameter(def.type, coerced ?: def.defaultValue)
        }
        overrides.keys.filter { k -> parameters.none { it.id == k } }
            .forEach { warnings += "Unknown parameter override '$it' ignored." }
        return out to warnings
    }
}

/** A parameter value with its type pinned — the renderer needs the type to pick the GL setter. */
data class ResolvedParameter(val type: ParamType, val value: ParamValue)
