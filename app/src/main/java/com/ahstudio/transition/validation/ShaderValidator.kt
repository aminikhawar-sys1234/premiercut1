package com.ahstudio.transition.validation

import com.ahstudio.transition.core.AlphaMode
import com.ahstudio.transition.core.PassInput
import com.ahstudio.transition.core.ShaderSource
import com.ahstudio.transition.core.TransitionDefinition
import com.ahstudio.transition.core.TransitionEngineMetadata as Meta

object ShaderValidator {
    private val BANNED = listOf(
        "imageLoad", "imageStore", "imageAtomic", "atomicAdd", "atomicCompSwap",
        "sampler3D", "usampler", "isampler", "buffer ", "layout(local_size",
        "#include", "shared ", "gl_FragDepth")
    private val ALLOWED_EXTENSIONS = setOf(
        "GL_OES_EGL_image_external_essl3", "GL_OES_EGL_image_external")
    private val EXTENSION_DIRECTIVE = Regex("""#extension\s+(\w+)""")
    private val UNIFORM_DECL = Regex("""uniform\s+\w+\s+""")

    fun validateSource(source: ShaderSource, requiredUniforms: List<String>):
            TransitionValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val hardCap = minOf(source.maxSourceBytes, Meta.HARD_SHADER_SIZE_CAP_BYTES)

        val fragBytes = source.fragment.toByteArray(Charsets.UTF_8)
        if (fragBytes.size > hardCap)
            errors += "Fragment shader '${source.id}' exceeds size cap (${fragBytes.size} > $hardCap bytes)."
        if (source.fragment.contains("#version"))
            errors += "Shader '${source.id}' must not contain '#version' (engine injects it)."
        BANNED.firstOrNull { source.fragment.contains(it) }?.let {
            errors += "Banned GLSL feature '$it' in '${source.id}'."
        }
        source.fragment.lineSequence()
            .filter { it.trimStart().startsWith("#extension") }
            .mapNotNull { EXTENSION_DIRECTIVE.find(it)?.groupValues?.get(1) }
            .filter { it !in ALLOWED_EXTENSIONS }
            .forEach { errors += "Extension '$it' not whitelisted in '${source.id}'." }
        requiredUniforms.forEach { u ->
            val decl = Regex("""uniform\s+\w+\s+${Regex.escape(u)}\b""")
            if (!decl.containsMatchIn(source.fragment))
                warnings += "Shader '${source.id}' does not declare standard uniform $u."
        }
        source.vertexOverride?.let { vertex ->
            val vb = vertex.toByteArray(Charsets.UTF_8)
            if (vb.size > hardCap)
                errors += "Vertex shader '${source.id}' exceeds size cap (${vb.size} > $hardCap bytes)."
            if (vertex.contains("#version"))
                errors += "Vertex override '${source.id}' must not contain '#version'."
            if (!vertex.contains("aPosition") || !vertex.contains("aUv"))
                errors += "Vertex override '${source.id}' must declare aPosition and aUv."
        }
        return TransitionValidationResult(errors, warnings)
    }

    fun validateDefinition(def: TransitionDefinition): TransitionValidationResult {
        if (def.minEngineVersion > Meta.ENGINE_VERSION)
            return TransitionValidationResult(
                "Definition '${def.id}' needs engine ${def.minEngineVersion} > ${Meta.ENGINE_VERSION}")
        var result = TransitionValidationResult.OK
        for (pass in def.graph.passes) {
            val src = def.shaders[pass.shaderId]
                ?: return TransitionValidationResult(
                    "Pass '${pass.id}' missing shader '${pass.shaderId}'")
            val required = pass.inputs.mapNotNull {
                when (it) {
                    PassInput.ClipA -> "uTextureA"
                    PassInput.ClipB -> "uTextureB"
                    else -> null
                }
            }
            result += validateSource(src, required)
        }
        return result
    }
}
