package com.vfx.engine.effects

import com.vfx.engine.core.effect.WorkingSpace
import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.effect.EffectSnapshot
import com.vfx.engine.core.params.ColorP
import com.vfx.engine.core.params.FloatP
import com.vfx.engine.core.params.IntP
import com.vfx.engine.core.params.Vec2P
import com.vfx.engine.gpu.GpuTexture
import com.vfx.engine.gpu.TextureSpec
import com.vfx.engine.gpu.glsl.EffectGlsl
import com.vfx.engine.graph.GraphBuilder

object LightEffects {

    fun all(): List<EffectDefinition> = listOf(
        simpleEffect("vignette.vignette", "Vignette", EffectCategory.VIGNETTE,
            listOf(Vec2P("center", "Center", floatArrayOf(0.5f, 0.5f)),
                   FloatP("start", "Start", 0f, 1f, 0.01f, 0.3f),
                   FloatP("end", "End", 0f, 2f, 0.01f, 0.85f),
                   ColorP("color", "Color", floatArrayOf(0f, 0f, 0f, 1f)),
                   FloatP("amount", "Amount", 0f, 1f, 0.01f, 1f)), EffectGlsl.VIGNETTE,
            aliases = listOf("vignette", "vignette.radial"))
        { p, s, _ ->
            val c = s.floats("center"); val col = s.floats("color")
            p.setVec2("u_center", c[0], c[1])
            p.setFloat("u_start", s.float("start"))
            p.setFloat("u_end", s.float("end"))
            p.setVec4("u_vigColor", col)
            p.setFloat("u_vigAmount", s.float("amount")) },

        EffectDefinition("light.bloom", "Bloom", EffectCategory.LIGHT,
            listOf(FloatP("threshold", "Threshold", 0f, 2f, 0.02f, 0.8f),
                   FloatP("knee", "Knee", 0f, 1f, 0.02f, 0.2f),
                   FloatP("intensity", "Bloom Intensity", 0f, 5f, 0.05f, 1f),
                   FloatP("radius", "Blur Radius", 1f, 16f, 0.5f, 8f)),
            description = "Threshold -> 1/4-res downsample -> H/V blur -> additive combine. Passes: 4.")
        { BloomRuntime() },

        EffectDefinition("light.glow", "Glow", EffectCategory.LIGHT,
            listOf(FloatP("strength", "Strength", 0f, 3f, 0.02f, 0.8f),
                   FloatP("radius", "Radius", 1f, 16f, 0.5f, 6f)),
            description = "Separable blur + screen combine. Passes: 3.")
        { GlowRuntime() },

        simpleEffect("light.rays", "Light Rays / Sunbeams", EffectCategory.LIGHT,
            listOf(Vec2P("pos", "Light Position", floatArrayOf(0.5f, 0.2f)),
                   FloatP("length", "Ray Length", 0f, 1f, 0.01f, 0.4f),
                   FloatP("decay", "Decay", 0.5f, 1f, 0.005f, 0.95f),
                   FloatP("strength", "Strength", 0f, 3f, 0.02f, 1f),
                   IntP("samples", "Samples", 8, 64, 32)), EffectGlsl.LIGHT_RAYS)
        { p, s, _ ->
            val pos = s.floats("pos")
            p.setVec2("u_lightPos", pos[0], pos[1])
            p.setFloat("u_rayLength", s.float("length"))
            p.setFloat("u_decay", s.float("decay"))
            p.setFloat("u_rayStrength", s.float("strength"))
            p.setInt("u_raySamples", s.int("samples")) }
    )

    private class BloomRuntime : MultiPassRuntimeBase() {
        override val workingSpace = WorkingSpace.GAMMA
        override fun build(b: GraphBuilder, input: GpuTexture, s: EffectSnapshot): GpuTexture {
            val ctx = b.renderCtx
            // 1. Threshold pass into half-res FBO to save fillrate
            val pThresh = program(ctx, "bloom.thresh", EffectGlsl.BLOOM_THRESHOLD)
            val halfW = (input.width / 2).coerceAtLeast(1)
            val halfH = (input.height / 2).coerceAtLeast(1)
            val thresh = b.addPass("bloom.thresh", pThresh, input,
                outWidth = halfW, outHeight = halfH, outSpec = TextureSpec.LINEAR_CLAMP) { prog ->
                prog.setFloat("u_threshold", s.float("threshold"))
                prog.setFloat("u_knee", s.float("knee"))
                setIntensityOne(prog)
            }
            // 2 & 3. Separable blur over the brightpass
            val blurred = SeparableBlurRuntime(box = false).build(b, thresh,
                EffectSnapshot("bloom.blur", s.timeUs,
                    mapOf("radius" to s.float("radius"), "sigma" to s.float("radius") * 0.5f), 1f))
            // 4. Combine bloom back over original full-res
            val pCombine = program(ctx, "bloom.combine", EffectGlsl.BLOOM_COMBINE)
            b.addSecondaryInput("u_bloom", blurred)
            return b.addPass("bloom.combine", pCombine, input) { prog ->
                prog.setTexture("u_bloom", blurred)
                prog.setFloat("u_bloomIntensity", s.float("intensity"))
                setIntensity(prog, s)
            }
        }
    }

    private class GlowRuntime : MultiPassRuntimeBase() {
        override val workingSpace = WorkingSpace.GAMMA
        override fun build(b: GraphBuilder, input: GpuTexture, s: EffectSnapshot): GpuTexture {
            val blurred = SeparableBlurRuntime(box = false).build(b, input,
                EffectSnapshot("glow.blur", s.timeUs,
                    mapOf("radius" to s.float("radius"), "sigma" to s.float("radius") * 0.5f), 1f))
            val p = program(b.renderCtx, "glow.combine", EffectGlsl.GLOW_COMBINE)
            b.addSecondaryInput("u_blurred", blurred)
            return b.addPass("glow.combine", p, input) { prog ->
                prog.setTexture("u_blurred", blurred)
                prog.setFloat("u_glowStrength", s.float("strength"))
                setIntensity(prog, s)
            }
        }
    }
}
