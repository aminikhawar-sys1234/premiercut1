package com.ahstudio.transition.render

import android.opengl.GLES30
import com.ahstudio.transition.core.AlphaMode
import com.ahstudio.transition.core.ParamValue
import com.ahstudio.transition.core.PassInput
import com.ahstudio.transition.core.PassOutput
import com.ahstudio.transition.core.TransitionError
import com.ahstudio.transition.core.TransitionPassSpec
import com.ahstudio.transition.core.TransitionRenderSnapshot
import com.ahstudio.transition.core.TransitionResult
import com.ahstudio.transition.diag.TransitionDiagnostics
import com.ahstudio.transition.gl.CommonShaders
import com.ahstudio.transition.gl.FramebufferPool
import com.ahstudio.transition.gl.FullscreenGeometry
import com.ahstudio.transition.gl.GlProgram
import com.ahstudio.transition.gl.GlUtil
import com.ahstudio.transition.gl.GlslCompiler
import com.ahstudio.transition.gl.TransitionShaderCache
import com.ahstudio.transition.integration.AcquiredFrame
import com.ahstudio.transition.integration.TransitionFrames

/**
 * Executes a snapshot's render graph. All methods must run on the thread with a
 * current GL context. No glReadPixels in the hot path. On any failure the renderer
 * falls back to a passthrough of Clip B so a broken transition can never crash or
 * blank the app. All pooled FBOs are released in finally — no leaks on error paths.
 */
class TransitionRenderer(
    private val shaderCache: TransitionShaderCache,
    private val fboPool: FramebufferPool,
    private val diagnostics: TransitionDiagnostics,
) {
    private val geometry = FullscreenGeometry()

    private class PassTarget(val width: Int, val height: Int, val fbo: Int,
                             val pooledFb: FramebufferPool.PooledFramebuffer?)

    fun render(snapshot: TransitionRenderSnapshot, frames: TransitionFrames, outputFbo: Int):
            TransitionResult<Unit> {
        val prevFbo = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, prevFbo, 0)
        val pooled = ArrayList<FramebufferPool.PooledFramebuffer>()
        try {
            geometry.ensure()
            val texAResult = resolveSource(frames.frameA, snapshot, pooled)
            val texA = when (texAResult) {
                is TransitionResult.Ok -> texAResult.value
                is TransitionResult.Err -> return fallback(frames, snapshot, outputFbo, texAResult.error)
            }
            val texBResult = resolveSource(frames.frameB, snapshot, pooled)
            val texB = when (texBResult) {
                is TransitionResult.Ok -> texBResult.value
                is TransitionResult.Err -> return fallback(frames, snapshot, outputFbo, texBResult.error)
            }
            val sources = HashMap<String, Int>()          // passId -> output texture

            for (pass in snapshot.graph.passes) {
                val targetResult = passOutputTarget(pass, snapshot, outputFbo, pooled)
                val target = when (targetResult) {
                    is TransitionResult.Ok -> targetResult.value
                    is TransitionResult.Err -> return fallback(frames, snapshot, outputFbo, targetResult.error)
                }

                val progResult = obtainProgram(snapshot, pass)
                val program = when (progResult) {
                    is TransitionResult.Ok -> progResult.value
                    is TransitionResult.Err -> return fallback(frames, snapshot, outputFbo, progResult.error)
                }

                program.use()
                var unit = 0
                for (input in pass.inputs) {
                    when (input) {
                        PassInput.ClipA -> program.bindTexture("uTextureA", unit++, texA, GlUtil.TEXTURE_2D)
                        PassInput.ClipB -> program.bindTexture("uTextureB", unit++, texB, GlUtil.TEXTURE_2D)
                        is PassInput.Pass -> program.bindTexture("u_" + input.passId, unit++,
                            sources.getValue(input.passId), GlUtil.TEXTURE_2D)
                    }
                }
                bindStandardUniforms(program, snapshot, target.width, target.height)
                bindParameters(program, snapshot)

                if (pass.blendEnabled) {
                    GLES30.glEnable(GLES30.GL_BLEND)
                    GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                } else {
                    GLES30.glDisable(GLES30.GL_BLEND)
                }

                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.fbo)
                GLES30.glViewport(0, 0, target.width, target.height)
                geometry.draw()

                val out = pass.output
                if (out is PassOutput.Intermediate) sources[out.id] = target.pooledFb!!.texture
            }
            diagnostics.framesRendered.incrementAndGet()
            diagnostics.renderPassCount = snapshot.graph.passes.size
            return TransitionResult.Ok(Unit)
        } catch (t: Throwable) {
            return fallback(frames, snapshot, outputFbo,
                TransitionError.Render("Unexpected render failure", t))
        } finally {
            pooled.forEach { fboPool.release(it) }
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, prevFbo[0])
        }
    }

    /**
     * Source normalization: applies the provider-supplied transform matrix once,
     * so all transition shaders see canonical upright textures in [0,1] uv. Skipped when
     * the frame is already a 2D texture with identity transform. Handles EXTERNAL_OES
     * via a cached shader variant. Acquired FBOs are registered into [pooled] for release.
     */
    private fun resolveSource(frame: AcquiredFrame, snapshot: TransitionRenderSnapshot,
                              pooled: MutableList<FramebufferPool.PooledFramebuffer>):
            TransitionResult<Int> {
        if (frame.target == GlUtil.TEXTURE_2D && GlUtil.isIdentity(frame.transformMatrix))
            return TransitionResult.Ok(frame.textureId)

        val useOes = frame.target != GlUtil.TEXTURE_2D
        val defines = if (useOes) mapOf("EXTERNAL_OES" to "1") else emptyMap()
        val key = "normalize|" + (if (useOes) "oes" else "2d")
        val progResult = shaderCache.getOrCreate(key) {
            val c = GlslCompiler.compile(CommonShaders.FULLSCREEN_VERTEX, CommonShaders.NORMALIZE_FRAG, defines)
            if (c.error != null) TransitionResult.Err(c.error) else TransitionResult.Ok(c.program!!)
        }
        val program = when (progResult) {
            is TransitionResult.Ok -> progResult.value
            is TransitionResult.Err -> return progResult
        }

        val fbResult = fboPool.acquire(snapshot.outputWidth, snapshot.outputHeight)
        val fb = when (fbResult) {
            is TransitionResult.Ok -> fbResult.value
            is TransitionResult.Err -> return fbResult
        }
        pooled.add(fb)
        program.use()
        program.bindTexture("uSrc", 0, frame.textureId,
            if (useOes) GlUtil.TEXTURE_EXTERNAL_OES else GlUtil.TEXTURE_2D)
        program.bindMat4("uTransform", frame.transformMatrix)
        GLES30.glDisable(GLES30.GL_BLEND)
        fb.bindAndViewport()
        geometry.draw()
        return TransitionResult.Ok(fb.texture)
    }

    private fun obtainProgram(snapshot: TransitionRenderSnapshot, pass: TransitionPassSpec):
            TransitionResult<GlProgram> {
        val src = snapshot.shaders[pass.shaderId]
            ?: return TransitionResult.Err(TransitionError.Validation("Missing shader '${pass.shaderId}'"))
        val key = buildString {
            append(snapshot.definitionId); append('|')
            append(pass.shaderId); append('|')
            append(src.fragment.hashCode()); append('|')
            append(src.vertexOverride?.hashCode() ?: 0); append('|')
            pass.defines.entries.sortedBy { it.key }
                .forEach { append(it.key); append('='); append(it.value); append(';') }
        }
        return shaderCache.getOrCreate(key) {
            val c = GlslCompiler.compile(src.vertexOverride ?: CommonShaders.FULLSCREEN_VERTEX,
                src.fragment, pass.defines)
            if (c.error != null) TransitionResult.Err(c.error) else TransitionResult.Ok(c.program!!)
        }
    }

    private fun passOutputTarget(pass: TransitionPassSpec, snapshot: TransitionRenderSnapshot,
                                 outputFbo: Int,
                                 pooled: MutableList<FramebufferPool.PooledFramebuffer>):
            TransitionResult<PassTarget> = when (val o = pass.output) {
        is PassOutput.Final ->
            TransitionResult.Ok(PassTarget(snapshot.outputWidth, snapshot.outputHeight, outputFbo, null))
        is PassOutput.Intermediate -> {
            val w = (snapshot.outputWidth * o.resolutionScale).toInt().coerceAtLeast(1)
            val h = (snapshot.outputHeight * o.resolutionScale).toInt().coerceAtLeast(1)
            when (val fb = fboPool.acquire(w, h)) {
                is TransitionResult.Ok -> {
                    pooled.add(fb.value)
                    TransitionResult.Ok(PassTarget(w, h, fb.value.framebuffer, fb.value))
                }
                is TransitionResult.Err -> TransitionResult.Err(fb.error)
            }
        }
    }

    private fun bindStandardUniforms(p: GlProgram, s: TransitionRenderSnapshot, w: Int, h: Int) {
        p.bind("uProgress", s.progress)
        p.bind("uRawProgress", s.rawProgress)
        p.bind("uTime", (s.timelineTimeMs - s.startMs) / 1000f)
        p.bind("uDuration", (s.endMs - s.startMs) / 1000f)
        p.bindVec2("uResolution", w.toFloat(), h.toFloat())
        val dir = (s.parameters["direction"]?.value as? ParamValue.Vec2Value)?.values
        p.bindVec2("uDirection", dir?.get(0) ?: 1f, dir?.get(1) ?: 0f)
        p.bind("uOutputPremultiplied", if (s.outputPremultiplied) 1f else 0f)
        p.bindInt("uPlaybackDirection", s.playbackDirection)
    }

    private fun bindParameters(p: GlProgram, s: TransitionRenderSnapshot) {
        s.parameters.forEach { (id, rp) ->
            val name = "u_$id"
            when (rp.value) {
                is ParamValue.FloatValue -> p.bind(name, rp.value.value)
                is ParamValue.NormalizedValue -> p.bind(name, rp.value.value)
                is ParamValue.AngleValue -> p.bind(name, rp.value.degrees)
                is ParamValue.IntValue -> p.bindInt(name, rp.value.value)
                is ParamValue.EnumValue -> p.bindInt(name, rp.value.index)
                is ParamValue.BoolValue -> p.bindInt(name, if (rp.value.value) 1 else 0)
                is ParamValue.ColorValue -> {
                    val c = rp.value.rgba; p.bindVec4(name, c[0], c[1], c[2], c[3])
                }
                is ParamValue.Vec2Value -> {
                    val v = rp.value.values; p.bindVec2(name, v[0], v[1])
                }
                is ParamValue.Vec3Value -> {
                    val v = rp.value.values; p.bindVec3(name, v[0], v[1], v[2])
                }
            }
        }
    }

    /** Safe fallback: plain passthrough of Clip B — never crash, never black. */
    private fun fallback(frames: TransitionFrames, snapshot: TransitionRenderSnapshot,
                         outputFbo: Int, error: TransitionError): TransitionResult<Unit> {
        diagnostics.onError(error)
        val pooled = ArrayList<FramebufferPool.PooledFramebuffer>()
        try {
            geometry.ensure()
            val progResult = shaderCache.getOrCreate(PASSTHROUGH_KEY) {
                val c = GlslCompiler.compile(CommonShaders.FULLSCREEN_VERTEX,
                    CommonShaders.PASSTHROUGH_FRAG, emptyMap())
                if (c.error != null) TransitionResult.Err(c.error) else TransitionResult.Ok(c.program!!)
            }
            val program = when (progResult) {
                is TransitionResult.Ok -> progResult.value
                is TransitionResult.Err -> return TransitionResult.Err(error)
            }
            val srcBResult = resolveSource(frames.frameB, snapshot, pooled)
            val srcB = when (srcBResult) {
                is TransitionResult.Ok -> srcBResult.value
                is TransitionResult.Err -> return TransitionResult.Err(error)
            }
            program.use()
            program.bindTexture("uTextureB", 0, srcB, GlUtil.TEXTURE_2D)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outputFbo)
            GLES30.glViewport(0, 0, snapshot.outputWidth, snapshot.outputHeight)
            geometry.draw()
        } catch (_: Throwable) {
            // absolute last resort: leave previous output contents untouched
        } finally {
            pooled.forEach { fboPool.release(it) }
        }
        return TransitionResult.Err(error)
    }

    fun releaseGpuResources() { geometry.release() }

    companion object { private const val PASSTHROUGH_KEY = "passthrough|v1" }
}
