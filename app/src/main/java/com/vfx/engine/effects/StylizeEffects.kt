package com.vfx.engine.effects

import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.params.BoolP
import com.vfx.engine.core.params.ColorP
import com.vfx.engine.core.params.FloatP
import com.vfx.engine.core.params.Vec2P
import com.vfx.engine.gpu.glsl.EffectGlsl

object StylizeEffects {

    fun all(): List<EffectDefinition> = listOf(
        simpleEffect("stylize.posterize", "Posterize", EffectCategory.STYLIZE,
            listOf(FloatP("levels", "Levels", 2f, 32f, 1f, 6f)), EffectGlsl.POSTERIZE)
        { p, s, _ -> p.setFloat("u_levels", s.float("levels")) },

        simpleEffect("stylize.threshold", "Threshold", EffectCategory.STYLIZE,
            listOf(FloatP("threshold", "Threshold", 0f, 1f, 0.01f, 0.5f),
                   BoolP("keepColor", "Keep Chroma", false)), EffectGlsl.THRESHOLD)
        { p, s, _ ->
            p.setFloat("u_threshold", s.float("threshold"))
            p.setFloat("u_keepColor", if (s.bool("keepColor")) 1f else 0f) },

        simpleEffect("stylize.pixelate", "Pixelate", EffectCategory.STYLIZE,
            listOf(FloatP("blocksX", "Blocks X", 4f, 256f, 1f, 64f),
                   FloatP("blocksY", "Blocks Y", 4f, 256f, 1f, 64f)), EffectGlsl.PIXELATE)
        { p, s, _ ->
            p.setFloat("u_blocksX", s.float("blocksX"))
            p.setFloat("u_blocksY", s.float("blocksY")) },

        simpleEffect("stylize.edge", "Find Edges", EffectCategory.STYLIZE,
            listOf(FloatP("strength", "Strength", 0f, 5f, 0.05f, 1.5f),
                   ColorP("fg", "Foreground", floatArrayOf(1f, 1f, 1f, 1f)),
                   ColorP("bg", "Background", floatArrayOf(0f, 0f, 0f, 1f))), EffectGlsl.EDGE_DETECT)
        { p, s, _ ->
            p.setFloat("u_edgeStrength", s.float("strength"))
            p.setVec4("u_edgeFg", s.floats("fg"))
            p.setVec4("u_edgeBg", s.floats("bg")) },

        simpleEffect("stylize.sketch", "Sketch", EffectCategory.STYLIZE,
            listOf(FloatP("strength", "Strength", 0f, 5f, 0.05f, 2f)), EffectGlsl.SKETCH)
        { p, s, _ -> p.setFloat("u_sketchStrength", s.float("strength")) },

        simpleEffect("stylize.emboss", "Emboss", EffectCategory.STYLIZE,
            listOf(FloatP("amount", "Amount", 0f, 5f, 0.05f, 1.5f)), EffectGlsl.EMBOSS)
        { p, s, _ -> p.setFloat("u_embossAmount", s.float("amount")) },

        simpleEffect("stylize.halftone", "Halftone", EffectCategory.STYLIZE,
            listOf(FloatP("dotSize", "Dot Size (px)", 2f, 40f, 0.5f, 10f),
                   FloatP("angle", "Grid Angle", 0f, 90f, 1f, 45f),
                   ColorP("dark", "Dark", floatArrayOf(0f, 0f, 0f, 1f)),
                   ColorP("light", "Light", floatArrayOf(1f, 1f, 1f, 1f))), EffectGlsl.HALFTONE)
        { p, s, _ ->
            p.setFloat("u_dotSize", s.float("dotSize"))
            p.setFloat("u_gridAngle", s.float("angle"))
            p.setVec4("u_dark", s.floats("dark"))
            p.setVec4("u_light", s.floats("light")) },

        simpleEffect("stylize.duotone", "Duotone", EffectCategory.STYLIZE,
            listOf(ColorP("dark", "Shadow Tint", floatArrayOf(0.1f, 0f, 0.2f, 1f)),
                   ColorP("light", "Highlight Tint", floatArrayOf(1f, 0.85f, 0.5f, 1f)),
                   FloatP("detail", "Detail Blend", 0f, 1f, 0.01f, 0.1f)), EffectGlsl.DUOTONE)
        { p, s, _ ->
            p.setVec4("u_duoDark", s.floats("dark"))
            p.setVec4("u_duoLight", s.floats("light"))
            p.setFloat("u_duoDetail", s.float("detail")) },

        // NOISE category
        simpleEffect("noise.filmGrain", "Film Grain", EffectCategory.NOISE,
            listOf(FloatP("amount", "Amount", 0f, 0.5f, 0.005f, 0.08f),
                   FloatP("size", "Grain Size", 0.5f, 4f, 0.1f, 1.2f),
                   FloatP("seed", "Seed", 0f, 1000f, 1f, 0f),
                   FloatP("lumBias", "Luminance Bias", 0f, 1f, 0.01f, 0.5f)), EffectGlsl.FILM_GRAIN)
        { p, s, _ ->
            p.setFloat("u_grainAmount", s.float("amount"))
            p.setFloat("u_grainSize", s.float("size"))
            p.setFloat("u_seed", s.float("seed"))
            p.setFloat("u_lumBias", s.float("lumBias")) },

        simpleEffect("noise.digital", "Digital Noise", EffectCategory.NOISE,
            listOf(FloatP("amount", "Amount", 0f, 0.5f, 0.005f, 0.1f),
                   FloatP("seed", "Seed", 0f, 1000f, 1f, 0f)), EffectGlsl.DIGITAL_NOISE)
        { p, s, _ ->
            p.setFloat("u_noiseAmount", s.float("amount"))
            p.setFloat("u_noiseSeed", s.float("seed")) },

        // CHROMATIC category
        simpleEffect("chromatic.aberration", "Chromatic Aberration", EffectCategory.CHROMATIC,
            listOf(FloatP("amount", "Amount", -0.05f, 0.05f, 0.001f, 0.008f),
                   Vec2P("center", "Center", floatArrayOf(0.5f, 0.5f))), EffectGlsl.CHROMATIC_ABERRATION)
        { p, s, _ ->
            val c = s.floats("center")
            p.setFloat("u_caAmount", s.float("amount"))
            p.setVec2("u_caCenter", c[0], c[1]) },

        simpleEffect("chromatic.rgbSplit", "RGB Split", EffectCategory.CHROMATIC,
            listOf(Vec2P("dir", "Direction", floatArrayOf(1f, 0f)),
                   FloatP("amount", "Amount", 0f, 0.05f, 0.001f, 0.008f)), EffectGlsl.RGB_SPLIT,
            aliases = listOf("chromatic.separation"))
        { p, s, _ ->
            val d = s.floats("dir")
            p.setVec2("u_splitDir", d[0], d[1])
            p.setFloat("u_splitAmount", s.float("amount")) }
    )
}
