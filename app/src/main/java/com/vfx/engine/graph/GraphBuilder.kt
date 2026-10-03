package com.vfx.engine.graph

import android.opengl.GLES30
import com.vfx.engine.core.math.Mat3
import com.vfx.engine.gpu.FramebufferObject
import com.vfx.engine.gpu.GpuTexture
import com.vfx.engine.gpu.ShaderProgram
import com.vfx.engine.gpu.TextureSpec

/** One GPU pass: draw the fullscreen quad through [program] into [targetFbo]. */
class RenderPass(
    val name: String,
    val program: ShaderProgram,
    val targetFbo: FramebufferObject,
    val uvMatrix: Mat3,
    val bindUniforms: (ShaderProgram) -> Unit
)

/**
 * Collects ordered passes for ONE frame. Effects call [addPass]; the builder owns
 * pooled output allocation so intermediates are recycled deterministically.
 *
 * Lifecycle per frame:  build (addPass...) -> execute() -> consume output -> disposeFinal()
 */
class GraphBuilder(val renderCtx: RenderContext) {

    private val passes = ArrayList<RenderPass>()

    fun addPass(
        name: String,
        program: ShaderProgram,
        input: GpuTexture,
        inputName: String = "u_tex",
        uvMatrix: Mat3 = Mat3.identity(),
        outWidth: Int = renderCtx.frameWidth,
        outHeight: Int = renderCtx.frameHeight,
        outSpec: TextureSpec = TextureSpec(),
        extraUniforms: (ShaderProgram) -> Unit = {}
    ): GpuTexture {
        val fbo = renderCtx.pool.acquire(outWidth, outHeight, outSpec)
        passes.add(RenderPass(name, program, fbo, uvMatrix) { p ->
            p.setTexture(inputName, input)
            p.setVec2("u_texel", 1f / input.width.coerceAtLeast(1), 1f / input.height.coerceAtLeast(1))
            p.setVec2("u_resolution", outWidth.toFloat(), outHeight.toFloat())
            p.setFloat("u_time", renderCtx.timeUs / 1_000_000f)
            extraUniforms(p)
        })
        return fbo.texture
    }

    fun addPass(
        name: String,
        program: ShaderProgram,
        input: GpuTexture,
        extraUniforms: (ShaderProgram) -> Unit
    ): GpuTexture = addPass(
        name = name,
        program = program,
        input = input,
        inputName = "u_tex",
        uvMatrix = Mat3.identity(),
        outWidth = renderCtx.frameWidth,
        outHeight = renderCtx.frameHeight,
        outSpec = TextureSpec(),
        extraUniforms = extraUniforms
    )

    fun addPass(
        name: String,
        program: ShaderProgram,
        input: GpuTexture,
        inputName: String,
        extraUniforms: (ShaderProgram) -> Unit
    ): GpuTexture = addPass(
        name = name,
        program = program,
        input = input,
        inputName = inputName,
        uvMatrix = Mat3.identity(),
        outWidth = renderCtx.frameWidth,
        outHeight = renderCtx.frameHeight,
        outSpec = TextureSpec(),
        extraUniforms = extraUniforms
    )

    /** Registers an additional sampler for the MOST RECENTLY added pass (multi-input passes). */
    fun addSecondaryInput(samplerName: String, tex: GpuTexture) {
        val last = passes.lastOrNull() ?: throw IllegalStateException("addSecondaryInput: no pass added")
        val prev = last.bindUniforms
        passes[passes.size - 1] = RenderPass(last.name, last.program, last.targetFbo, last.uvMatrix) { p ->
            prev(p)
            p.setTexture(samplerName, tex)
        }
    }

    /** Pass targeting a caller-owned FBO (temporal history slots) — never pooled. */
    fun addPassPersistent(
        name: String,
        program: ShaderProgram,
        input: GpuTexture,
        target: FramebufferObject,
        inputName: String = "u_tex",
        extraUniforms: (ShaderProgram) -> Unit = {}
    ): GpuTexture {
        passes.add(RenderPass(name, program, target, Mat3.identity()) { p ->
            p.setTexture(inputName, input)
            p.setVec2("u_texel", 1f / input.width.coerceAtLeast(1), 1f / input.height.coerceAtLeast(1))
            p.setVec2("u_resolution", target.texture.width.toFloat(), target.texture.height.toFloat())
            p.setFloat("u_time", renderCtx.timeUs / 1_000_000f)
            extraUniforms(p)
        })
        return target.texture
    }

    /** Renders all passes in order; recycles intermediate pooled FBOs; returns the final output. */
    fun execute(): GpuTexture {
        require(passes.isNotEmpty()) { "GraphBuilder.execute: no passes" }
        val last = passes.size - 1
        GLES30.glDisable(GLES30.GL_BLEND)
        for (i in 0..last) {
            val pass = passes[i]
            pass.targetFbo.bind()
            pass.bindUniforms(pass.program)
            pass.program.setMat3("u_uvMat", pass.uvMatrix.m)
            renderCtx.quad.draw(pass.program)
        }
        for (i in 0 until last) renderCtx.pool.releaseToPool(passes[i].targetFbo)
        return passes[last].targetFbo.texture
    }

    /** Returns the final output FBO to the pool. Call after the output has been consumed. */
    fun disposeFinal() {
        val last = passes.lastOrNull() ?: return
        renderCtx.pool.releaseToPool(last.targetFbo)
        passes.clear()
    }

    fun finalTarget(): FramebufferObject? = passes.lastOrNull()?.targetFbo
    fun passCount(): Int = passes.size
}
