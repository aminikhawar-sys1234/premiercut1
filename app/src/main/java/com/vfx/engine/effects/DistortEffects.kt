package com.vfx.engine.effects

import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.params.FloatP
import com.vfx.engine.core.params.Vec2P
import com.vfx.engine.gpu.glsl.EffectGlsl

object DistortEffects {

    fun all(): List<EffectDefinition> = listOf(
        simpleEffect("distort.lens", "Lens Distortion", EffectCategory.DISTORTION,
            listOf(FloatP("k1", "Barrel / Pincushion (k1)", -1f, 1f, 0.01f, 0f),
                   FloatP("k2", "Higher-order (k2)", -1f, 1f, 0.01f, 0f)),
            EffectGlsl.LENS_DISTORTION,
            description = "Standard polynomial warp (Brown-Conrady r^2, r^4).",
            aliases = listOf("distort.barrel", "distort.pincushion"))
        { p, s, _ -> p.setFloat("u_k1", s.float("k1")); p.setFloat("u_k2", s.float("k2")) },

        simpleEffect("distort.fisheye", "Fisheye", EffectCategory.DISTORTION,
            listOf(FloatP("strength", "Strength", -1f, 1f, 0.01f, 0.5f)), EffectGlsl.FISHEYE)
        { p, s, _ -> p.setFloat("u_fishStrength", s.float("strength")) },

        simpleEffect("distort.ripple", "Water Ripple", EffectCategory.DISTORTION,
            listOf(Vec2P("center", "Center", floatArrayOf(0.5f, 0.5f)),
                   FloatP("frequency", "Frequency", 1f, 50f, 0.5f, 20f),
                   FloatP("speed", "Speed", -10f, 10f, 0.1f, 3f),
                   FloatP("amplitude", "Amplitude", 0f, 0.1f, 0.001f, 0.02f),
                   FloatP("falloff", "Falloff", 0f, 10f, 0.1f, 2f)), EffectGlsl.RIPPLE)
        { p, s, _ ->
            val c = s.floats("center")
            p.setVec2("u_center", c[0], c[1])
            p.setFloat("u_rippleFreq", s.float("frequency"))
            p.setFloat("u_rippleSpeed", s.float("speed"))
            p.setFloat("u_rippleAmp", s.float("amplitude"))
            p.setFloat("u_rippleFalloff", s.float("falloff")) },

        simpleEffect("distort.wave", "Sine Wave", EffectCategory.DISTORTION,
            listOf(Vec2P("direction", "Direction", floatArrayOf(0f, 1f)),
                   FloatP("frequency", "Frequency", 0.5f, 30f, 0.5f, 8f),
                   FloatP("speed", "Speed", -10f, 10f, 0.1f, 2f),
                   FloatP("amplitude", "Amplitude", 0f, 0.1f, 0.001f, 0.02f)), EffectGlsl.WAVE)
        { p, s, _ ->
            val d = s.floats("direction")
            p.setVec2("u_waveDir", d[0], d[1])
            p.setFloat("u_waveFreq", s.float("frequency"))
            p.setFloat("u_waveSpeed", s.float("speed"))
            p.setFloat("u_waveAmp", s.float("amplitude")) },

        simpleEffect("distort.turbulence", "Turbulence", EffectCategory.DISTORTION,
            listOf(FloatP("frequency", "Frequency", 0.5f, 20f, 0.5f, 4f),
                   FloatP("amplitude", "Amplitude", 0f, 0.1f, 0.001f, 0.02f),
                   FloatP("speed", "Speed", 0f, 5f, 0.05f, 1f)), EffectGlsl.TURBULENCE)
        { p, s, _ ->
            p.setFloat("u_turbFreq", s.float("frequency"))
            p.setFloat("u_turbAmp", s.float("amplitude"))
            p.setFloat("u_turbSpeed", s.float("speed")) }
    )
}
