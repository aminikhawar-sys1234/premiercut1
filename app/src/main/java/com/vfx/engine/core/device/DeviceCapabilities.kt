package com.vfx.engine.core.device

/** Requirements an effect declares; the engine checks and provides fallback paths. */
data class RenderRequirements(
    val minGlesMajor: Int = 2,
    val needsFloatTextures: Boolean = false,
    val needs3dTextures: Boolean = false,
    val needsHighpFragment: Boolean = false,
    val minMaxTextureSize: Int = 2048
)

/**
 * Immutable snapshot of device GPU capabilities.
 * Probing happens in GpuContext (engine-gpu) — this model stays pure Kotlin
 * so effects and tests can depend on it without Android.
 */
data class DeviceCapabilities(
    val glesMajor: Int,
    val glesMinor: Int,
    val renderer: String,
    val maxTextureSize: Int,
    val maxTextureUnits: Int,
    val maxVertexAttributes: Int,
    val maxFragmentUniformVectors: Int,
    val extensions: Set<String>,
    val supportsHighpFragment: Boolean,
    val supportsFloatColorBuffer: Boolean,   // EXT_color_buffer_float / half_float
    val supports3dTextures: Boolean,         // GLES 3+
    val supportsNpotTextures: Boolean,        // GLES 3+ or GL_OES_texture_npot
    val eglRecordableSupported: Boolean      // EGL_RECORDABLE_ANDROID present
) {
    fun meets(req: RenderRequirements): Boolean =
        glesMajor >= req.minGlesMajor &&
            (!req.needsFloatTextures || supportsFloatColorBuffer) &&
            (!req.needs3dTextures || supports3dTextures) &&
            (!req.needsHighpFragment || supportsHighpFragment) &&
            maxTextureSize >= req.minMaxTextureSize

    /** Human-readable reason when [meets] fails — used for fallback logging. */
    fun fallbackReason(req: RenderRequirements): String? = when {
        glesMajor < req.minGlesMajor -> "GLES $glesMajor < required ${req.minGlesMajor}"
        req.needsFloatTextures && !supportsFloatColorBuffer -> "no float color buffer"
        req.needs3dTextures && !supports3dTextures -> "no 3D textures"
        req.needsHighpFragment && !supportsHighpFragment -> "no highp fragment shader"
        maxTextureSize < req.minMaxTextureSize -> "maxTextureSize $maxTextureSize < ${req.minMaxTextureSize}"
        else -> null
    }

    fun hasExtension(name: String) = extensions.contains(name)

    companion object {
        /** Conservative fallback profile for tests and headless environments. */
        val MINIMAL = DeviceCapabilities(
            glesMajor = 2, glesMinor = 0, renderer = "minimal",
            maxTextureSize = 4096, maxTextureUnits = 8, maxVertexAttributes = 8,
            maxFragmentUniformVectors = 64, extensions = emptySet(),
            supportsHighpFragment = true, supportsFloatColorBuffer = false,
            supports3dTextures = false, supportsNpotTextures = false,
            eglRecordableSupported = false
        )
    }
}
