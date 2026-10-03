package com.vfx.engine.effects

import com.vfx.engine.core.effect.WorkingSpace
import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.effect.EffectSnapshot
import com.vfx.engine.core.mask.Mask
import com.vfx.engine.core.params.ParamDescriptor
import com.vfx.engine.gpu.GpuEffectRuntime
import com.vfx.engine.gpu.GpuTexture
import com.vfx.engine.gpu.ShaderProgram
import com.vfx.engine.gpu.glsl.EffectGlsl
import com.vfx.engine.gpu.glsl.ShaderChunks
import com.vfx.engine.gpu.glsl.ShaderTemplate
import com.vfx.engine.graph.GraphBuilder
import com.vfx.engine.graph.RenderContext
import kotlin.math.cos
import kotlin.math.sin

/** Compact factory for single-pass effects — the 95% case. */
fun simpleEffect(
    id: String,
    name: String,
    category: EffectCategory,
    params: List<ParamDescriptor<*>>,
    body: String,
    workingSpace: WorkingSpace = WorkingSpace.GAMMA,
    description: String = "",
    aliases: List<String> = emptyList(),
    uniforms: (ShaderProgram, EffectSnapshot, RenderContext) -> Unit = { _, _, _ -> }
): EffectDefinition = EffectDefinition(
    id = id,
    name = name,
    category = category,
    params = params,
    description = description,
    aliases = aliases,
    runtimeFactory = {
        SinglePassRuntime(id, body, workingSpace = workingSpace, uniformSetter = uniforms)
    }
)

/**
 * One-pass effect with automatic mask handling:
 *  - no mask  -> single pass (template mixes with intensity)
 *  - mask     -> effect into intermediate, then ONE shared mask-combine program
 *                (mask shape is uniform-driven — no shader recompiles when moving masks)
 */
open class SinglePassRuntime(
    private val shaderName: String,
    private val effectBody: String,
    override val workingSpace: WorkingSpace = WorkingSpace.GAMMA,
    private val uniformSetter: (ShaderProgram, EffectSnapshot, RenderContext) -> Unit = { _, _, _ -> }
) : GpuEffectRuntime {

    protected fun hasWarp(): Boolean = effectBody.contains("warpUV")

    protected fun setMixUniforms(p: ShaderProgram, s: EffectSnapshot, intensityOne: Boolean = false) {
        p.setFloat("u_intensity", if (intensityOne) 1f else s.intensity)
        p.setFloat("u_warpIntensity", if (hasWarp() && !intensityOne) s.intensity else 0f)
    }

    override fun buildPasses(builder: GraphBuilder, input: GpuTexture, s: EffectSnapshot): GpuTexture {
        val ctx = builder.renderCtx
        val es3 = ctx.caps.glesMajor >= 3
        val frag = ShaderTemplate.fragment(es3, effectBody)
        val program = ctx.programs.get(shaderName, ShaderTemplate.vertex(es3), frag)
        val mask = s.mask

        if (mask == null) {
            return builder.addPass(shaderName, program, input) { p ->
                setMixUniforms(p, s)
                uniformSetter(p, s, ctx)
            }
        }

        // Masked: effect into an intermediate, then blend-composite over the input through the mask.
        val effOut = builder.addPass(shaderName, program, input) { p ->
            setMixUniforms(p, s, intensityOne = true)
            uniformSetter(p, s, ctx)
        }
        val es3b = ctx.caps.glesMajor >= 3
        val combine = ctx.programs.get("mask.combine", ShaderTemplate.vertex(es3b),
            ShaderTemplate.fragment(es3b, EffectGlsl.MASK_COMBINE, chunkExtras = ShaderChunks.BLEND))
        builder.addSecondaryInput("u_effect", effOut)
        if (mask is Mask.Texture)
            builder.addSecondaryInput("u_maskTex",
                GpuTexture.adoptExternal(mask.textureHandle, input.width, input.height))
        return builder.addPass("$shaderName.maskCombine", combine, input) { p ->
            MaskPacking.apply(p, mask)
            p.setInt("u_blendMode", s.blendMode.glslCode)
            setMixUniforms(p, s, intensityOne = true)
        }
    }
}

/** Base for multi-pass effects (blur chains, bloom, unsharp). */
abstract class MultiPassRuntimeBase : GpuEffectRuntime {

    abstract fun build(b: GraphBuilder, input: GpuTexture, s: EffectSnapshot): GpuTexture

    final override fun buildPasses(builder: GraphBuilder, input: GpuTexture, s: EffectSnapshot): GpuTexture =
        build(builder, input, s)

    protected fun program(
        ctx: RenderContext,
        name: String,
        body: String,
        chunkExtras: String = ""
    ): ShaderProgram {
        val es3 = ctx.caps.glesMajor >= 3
        return ctx.programs.get(name, ShaderTemplate.vertex(es3),
            ShaderTemplate.fragment(es3, body, chunkExtras = chunkExtras))
    }

    protected fun setIntensityOne(p: ShaderProgram) {
        p.setFloat("u_intensity", 1f)
        p.setFloat("u_warpIntensity", 0f)
    }

    protected fun setIntensity(p: ShaderProgram, s: EffectSnapshot) {
        p.setFloat("u_intensity", s.intensity)
        p.setFloat("u_warpIntensity", 0f)
    }
}

/** Packs a Mask model into the uniform-driven shared mask-combine shader. */
object MaskPacking {
    fun apply(p: ShaderProgram, mask: Mask) {
        when (mask) {
            is Mask.Rect -> {
                p.setInt("u_maskType", 1)
                p.setVec4("u_maskA", floatArrayOf(mask.cx, mask.cy, mask.w, mask.h))
                p.setVec4("u_maskB", floatArrayOf(mask.rotationDeg, mask.cornerRadius, mask.feather, 0f))
            }
            is Mask.Circle -> {
                p.setInt("u_maskType", 2)
                p.setVec4("u_maskA", floatArrayOf(mask.cx, mask.cy, mask.r, 0f))
                p.setVec4("u_maskB", floatArrayOf(0f, 0f, mask.feather, 0f))
            }
            is Mask.Ellipse -> {
                p.setInt("u_maskType", 3)
                p.setVec4("u_maskA", floatArrayOf(mask.cx, mask.cy, mask.rx, mask.ry))
                p.setVec4("u_maskB", floatArrayOf(mask.rotationDeg, 0f, mask.feather, 0f))
            }
            is Mask.LinearGradient -> {
                val rad = Math.toRadians(mask.angleDeg.toDouble())
                p.setInt("u_maskType", 4)
                p.setVec4("u_maskA", floatArrayOf(cos(rad).toFloat(), sin(rad).toFloat(), 0f, 0f))
                p.setVec4("u_maskB", floatArrayOf(0f, 0f, mask.start, mask.end))
            }
            is Mask.RadialGradient -> {
                p.setInt("u_maskType", 5)
                p.setVec4("u_maskA", floatArrayOf(mask.cx, mask.cy, mask.innerR, mask.outerR))
                p.setVec4("u_maskB", floatArrayOf(0f, 0f, 0f, 0f))
            }
            is Mask.Texture -> p.setInt("u_maskType", 6)
        }
        p.setFloat("u_maskInvert", if (mask.invert) 1f else 0f)
        p.setFloat("u_opacity", mask.opacity.coerceIn(0f, 1f))
    }
}
