package com.vfx.engine.gpu.glsl

/**
 * Composes effect fragment shaders:
 * header + defines + standard uniforms + effect uniforms + prelude chunks
 * + effect body (warpUV/process) + standard main.
 * Each unique body compiles exactly once (content-hashed cache upstream).
 */
object ShaderTemplate {

    private const val DEFAULT_WARP = "vec2 warpUV(vec2 uv) { return uv; }\n"

    fun vertex(es3: Boolean): String = ShaderChunks.vertex(es3)
    fun vertexExternal(es3: Boolean): String = ShaderChunks.vertexExternal(es3)

    fun fragment(
        es3: Boolean,
        effectBody: String,
        extraUniforms: List<String> = emptyList(),
        chunkExtras: String = "",
        defines: String = "",
        clampOutput: Boolean = true,
        extensionDirective: String = "",
        inputSampler: String = "uniform sampler2D u_tex;"
    ): String {
        val header = ShaderChunks.header(es3, extensionDirective)
        val prelude = """
$inputSampler
uniform vec2 u_texel;
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_intensity;
uniform float u_warpIntensity;
"""
        val main = """
void main() {
    vec2 warped = warpUV(v_uv);
    vec2 suv = clamp(mix(v_uv, warped, clamp(u_warpIntensity, 0.0, 1.0)), vec2(0.0), vec2(1.0));
    vec4 base = texture(u_tex, suv);
    vec4 result = process(base, v_uv);
    vec4 o = mix(base, result, clamp(u_intensity, 0.0, 1.0));
    o.rgb = safe3(o.rgb);
    ${if (clampOutput) "fragColor = vec4(clamp(o.rgb, 0.0, 1.0), clamp(o.a, 0.0, 1.0));"
                      else "fragColor = vec4(o.rgb, clamp(o.a, 0.0, 1.0));"}
}"""
        return buildString {
            append(header).append('\n')
            if (defines.isNotBlank()) { append(defines).append('\n') }
            append(prelude).append('\n')
            extraUniforms.forEach { append(it).append('\n') }
            append(ShaderChunks.COLOR).append('\n')
            append(ShaderChunks.NOISE).append('\n')
            append(ShaderChunks.UV).append('\n')
            if (chunkExtras.isNotBlank()) { append(chunkExtras).append('\n') }
            if (!effectBody.contains("vec2 warpUV")) append(DEFAULT_WARP)
            append(effectBody).append('\n')
            append(main)
        }
    }

    private fun ShaderChunks.header(es3: Boolean, extension: String): String =
        buildString {
            append(if (es3) HEADER_ES3 else HEADER_ES2)
            if (extension.isNotBlank()) {
                // Extension directives must come before anything but #version.
                val lines = toString().split('\n')
                clear()
                append(lines.first()).append('\n')
                append(extension).append('\n')
                lines.drop(1).forEach { append(it).append('\n') }
            }
        }
}
