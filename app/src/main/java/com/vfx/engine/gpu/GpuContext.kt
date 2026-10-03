package com.vfx.engine.gpu

import android.opengl.GLES30
import com.vfx.engine.core.EffectEngineException
import com.vfx.engine.core.device.DeviceCapabilities
import com.vfx.engine.core.device.RenderRequirements
import com.vfx.engine.gpu.lut.LutEngine
import com.vfx.engine.gpu.temporal.TemporalEngine

/**
 * Aggregate of all GPU subsystems for the current EGL context.
 * Thread-confined: only the GL thread touches this. Construct + [initialize]
 * with the context current.
 */
class GpuContext {

    val egl = EglCore()
    val readback = PixelReadback()

    private var initialized = false
    lateinit var compiler: ShaderCompiler; private set
    lateinit var shaderCache: ShaderCache; private set
    lateinit var quad: QuadRenderer; private set
    lateinit var pool: FramebufferPool; private set
    lateinit var capabilities: DeviceCapabilities; private set

    val temporal = TemporalEngine()
    val lutEngine: LutEngine by lazy { LutEngine(this) }

    val isInitialized: Boolean get() = initialized

    fun initialize(maxPoolBytes: Long = FramebufferPool.DEFAULT_BUDGET) {
        if (initialized) return
        egl.initialize()
        egl.makeCurrentOnPbuffer()
        compiler = ShaderCompiler()
        shaderCache = ShaderCache(compiler)
        quad = QuadRenderer()
        pool = FramebufferPool(maxPoolBytes)
        capabilities = probeCapabilities()
        initialized = true
    }

    /** Probes device limits. Requires current context. */
    private fun probeCapabilities(): DeviceCapabilities {
        val exts = HashSet<String>()
        if (egl.glesMajor >= 3) {
            val n = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_NUM_EXTENSIONS, n, 0)
            for (i in 0 until n[0]) GLES30.glGetStringi(GLES30.GL_EXTENSIONS, i)?.let { exts.add(it) }
        } else {
            GLES30.glGetString(GLES30.GL_EXTENSIONS)?.split(" ")?.let { exts.addAll(it) }
        }
        val maxTex = IntArray(1); GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        val maxUnits = IntArray(1); GLES30.glGetIntegerv(GLES30.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, maxUnits, 0)
        val maxAttribs = IntArray(1); GLES30.glGetIntegerv(GLES30.GL_MAX_VERTEX_ATTRIBS, maxAttribs, 0)
        val maxUniforms = IntArray(1); GLES30.glGetIntegerv(GLES30.GL_MAX_FRAGMENT_UNIFORM_VECTORS, maxUniforms, 0)
        val range = IntArray(2); val precision = IntArray(2)
        GLES30.glGetShaderPrecisionFormat(GLES30.GL_FRAGMENT_SHADER, GLES30.GL_HIGH_FLOAT, range, 0, precision, 0)
        return DeviceCapabilities(
            glesMajor = egl.glesMajor, glesMinor = 0,
            renderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: "unknown",
            maxTextureSize = maxTex[0], maxTextureUnits = maxUnits[0],
            maxVertexAttributes = maxAttribs[0], maxFragmentUniformVectors = maxUniforms[0],
            extensions = exts,
            supportsHighpFragment = precision[1] > 0,
            supportsFloatColorBuffer = exts.any {
                it.contains("EXT_color_buffer_float") || it.contains("EXT_color_buffer_half_float") },
            supports3dTextures = egl.glesMajor >= 3,
            supportsNpotTextures = egl.glesMajor >= 3 || exts.contains("GL_OES_texture_npot"),
            eglRecordableSupported = true)
    }

    fun checkRequirements(req: RenderRequirements, featureName: String) {
        if (!capabilities.meets(req))
            throw EffectEngineException.UnsupportedFeature(featureName,
                capabilities.fallbackReason(req) ?: "unknown limitation")
    }

    /** Full reset after EGL context loss: drop all dead handles, GL state is gone. */
    fun handleContextLoss() {
        if (!initialized) return
        shaderCache.onContextLost()
        pool.onContextLost()
        quad.abandon()
        temporal.onContextLost()
        lutEngine.onContextLost()
    }

    fun release() {
        if (!initialized) { egl.release(); return }
        shaderCache.releaseAll()
        pool.releaseAll()
        temporal.release()
        lutEngine.release()
        quad.release()
        egl.release()
        initialized = false
    }
}
