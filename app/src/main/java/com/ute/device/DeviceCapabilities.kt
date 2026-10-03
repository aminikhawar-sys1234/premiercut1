package com.ute.device

import android.opengl.GLES10
import android.opengl.GLES30

/** Query once per context; drives all fallbacks. */
class DeviceCapabilities private constructor(
    val glEsVersionMajor: Int,
    val maxTextureSize: Int,
    val maxVertexAttribs: Int,
    val supportsFloatTextures: Boolean,
    val supportsInstancing: Boolean,
    val supportsMSAAFramebuffer: Boolean,
    val extensions: Set<String>,
) {
    val supportsSdfPath: Boolean get() = glEsVersionMajor >= 3 && maxTextureSize >= 2048
    val supports3DPath: Boolean get() = glEsVersionMajor >= 3
    val atlasSize: Int get() = minOf(2048, maxTextureSize)

    companion object {
        fun detect(): DeviceCapabilities {
            val version = runCatching { GLES30.glGetString(GLES30.GL_VERSION) }.getOrDefault("") ?: ""
            val major = Regex("OpenGL ES ([0-9])").find(version)?.groupValues?.get(1)?.toIntOrNull() ?: 2
            val maxTex = IntArray(1)
            runCatching { GLES10.glGetIntegerv(GLES10.GL_MAX_TEXTURE_SIZE, maxTex, 0) }
            val maxAttr = IntArray(1)
            runCatching { GLES30.glGetIntegerv(GLES30.GL_MAX_VERTEX_ATTRIBS, maxAttr, 0) }
            val ext = runCatching { GLES30.glGetString(GLES30.GL_EXTENSIONS) }.getOrDefault("") ?: ""
            return DeviceCapabilities(
                glEsVersionMajor = major.coerceIn(2, 3),
                maxTextureSize = maxTex.getOrElse(0) { 2048 }.coerceAtLeast(1024),
                maxVertexAttribs = maxAttr.getOrElse(0) { 16 },
                supportsFloatTextures = ext.contains("EXT_color_buffer_float") || major >= 3,
                supportsInstancing = major >= 3,
                supportsMSAAFramebuffer = ext.contains("EXT_multisampled_render_to_texture"),
                extensions = ext.split(" ").toSet(),
            )
        }
    }
}
