package com.vfx.engine.effects

import com.vfx.engine.core.effect.WorkingSpace
import com.vfx.engine.core.curve.Curve
import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.effect.EffectSnapshot
import com.vfx.engine.core.params.BoolP
import com.vfx.engine.core.params.ColorP
import com.vfx.engine.core.params.CurveP
import com.vfx.engine.core.params.FloatP
import com.vfx.engine.core.params.Vec3P
import com.vfx.engine.gpu.glsl.EffectGlsl
import com.vfx.engine.graph.RenderContext

object ColorEffects {

    fun all(): List<EffectDefinition> = listOf(
        simpleEffect("color.brightness", "Brightness", EffectCategory.COLOR,
            listOf(FloatP("amount", "Amount", -1f, 1f, 0.01f, 0f)), EffectGlsl.BRIGHTNESS,
            description = "Adds a constant to RGB. Passes: 1. Space: gamma.")
        { p, s, _ -> p.setFloat("u_amount", s.float("amount")) },

        simpleEffect("color.contrast", "Contrast", EffectCategory.COLOR,
            listOf(FloatP("contrast", "Contrast", 0f, 3f, 0.01f, 1f)), EffectGlsl.CONTRAST)
        { p, s, _ -> p.setFloat("u_contrast", s.float("contrast")) },

        simpleEffect("color.exposure", "Exposure", EffectCategory.COLOR,
            listOf(FloatP("stops", "Stops", -4f, 4f, 0.05f, 0f)), EffectGlsl.EXPOSURE,
            workingSpace = WorkingSpace.LINEAR,
            description = "Exposure in stops; runs in LINEAR space (auto conversions inserted).")
        { p, s, _ -> p.setFloat("u_stops", s.float("stops")) },

        simpleEffect("color.gamma", "Gamma", EffectCategory.COLOR,
            listOf(FloatP("gamma", "Gamma", 0.1f, 4f, 0.01f, 1f)), EffectGlsl.GAMMA)
        { p, s, _ -> p.setFloat("u_gamma", s.float("gamma")) },

        simpleEffect("color.saturation", "Saturation", EffectCategory.COLOR,
            listOf(FloatP("sat", "Saturation", 0f, 3f, 0.01f, 1f)), EffectGlsl.SATURATION)
        { p, s, _ -> p.setFloat("u_sat", s.float("sat")) },

        simpleEffect("color.vibrance", "Vibrance", EffectCategory.COLOR,
            listOf(FloatP("vibrance", "Vibrance", -1f, 1f, 0.01f, 0f)), EffectGlsl.VIBRANCE)
        { p, s, _ -> p.setFloat("u_vibrance", s.float("vibrance")) },

        simpleEffect("color.hue", "Hue", EffectCategory.COLOR,
            listOf(FloatP("degrees", "Degrees", -180f, 180f, 0.5f, 0f)), EffectGlsl.HUE)
        { p, s, _ -> p.setFloat("u_degrees", s.float("degrees")) },

        simpleEffect("color.temperature", "Temperature & Tint", EffectCategory.COLOR,
            listOf(FloatP("temp", "Temperature", -1f, 1f, 0.01f, 0f),
                   FloatP("tint", "Tint", -1f, 1f, 0.01f, 0f)), EffectGlsl.TEMPERATURE)
        { p, s, _ -> p.setFloat("u_temp", s.float("temp")); p.setFloat("u_tint", s.float("tint")) },

        simpleEffect("color.tone.hishadow", "Highlights & Shadows", EffectCategory.COLOR,
            listOf(FloatP("highlights", "Highlights", -1f, 1f, 0.01f, 0f),
                   FloatP("shadows", "Shadows", -1f, 1f, 0.01f, 0f)), EffectGlsl.HIGHLIGHTS_SHADOWS)
        { p, s, _ -> p.setFloat("u_highlights", s.float("highlights")); p.setFloat("u_shadows", s.float("shadows")) },

        simpleEffect("color.tone.whbl", "Whites & Blacks", EffectCategory.COLOR,
            listOf(FloatP("whites", "Whites", -1f, 1f, 0.01f, 0f),
                   FloatP("blacks", "Blacks", -1f, 1f, 0.01f, 0f)), EffectGlsl.WHITES_BLACKS)
        { p, s, _ -> p.setFloat("u_whites", s.float("whites")); p.setFloat("u_blacks", s.float("blacks")) },

        simpleEffect("color.levels", "Levels", EffectCategory.COLOR,
            listOf(FloatP("inBlack", "Input Black", 0f, 1f, 0.005f, 0f),
                   FloatP("inWhite", "Input White", 0f, 1f, 0.005f, 1f),
                   FloatP("gamma", "Gamma", 0.1f, 4f, 0.01f, 1f),
                   FloatP("outBlack", "Output Black", 0f, 1f, 0.005f, 0f),
                   FloatP("outWhite", "Output White", 0f, 1f, 0.005f, 1f)), EffectGlsl.LEVELS)
        { p, s, _ ->
            p.setFloat("u_inBlack", s.float("inBlack")); p.setFloat("u_inWhite", s.float("inWhite"))
            p.setFloat("u_gamma", s.float("gamma")); p.setFloat("u_outBlack", s.float("outBlack"))
            p.setFloat("u_outWhite", s.float("outWhite")) },

        curvesEffect(),

        simpleEffect("color.balance", "Color Balance", EffectCategory.COLOR,
            listOf(Vec3P("shadows", "Shadows RGB", floatArrayOf(0f, 0f, 0f)),
                   Vec3P("midtones", "Midtones RGB", floatArrayOf(0f, 0f, 0f)),
                   Vec3P("highlights", "Highlights RGB", floatArrayOf(0f, 0f, 0f)),
                   BoolP("preserveLuma", "Preserve Luminance", true)), EffectGlsl.COLOR_BALANCE)
        { p, s, _ ->
            val sh = s.floats("shadows"); val md = s.floats("midtones"); val hi = s.floats("highlights")
            p.setVec3("u_shadows", sh[0], sh[1], sh[2])
            p.setVec3("u_midtones", md[0], md[1], md[2])
            p.setVec3("u_highlights", hi[0], hi[1], hi[2])
            p.setFloat("u_preserveLuma", if (s.bool("preserveLuma")) 1f else 0f) },

        simpleEffect("color.channelMixer", "Channel Mixer", EffectCategory.COLOR,
            listOf(Vec3P("rowR", "Red out", floatArrayOf(1f, 0f, 0f)),
                   Vec3P("rowG", "Green out", floatArrayOf(0f, 1f, 0f)),
                   Vec3P("rowB", "Blue out", floatArrayOf(0f, 0f, 1f))), EffectGlsl.CHANNEL_MIXER)
        { p, s, _ ->
            val r = s.floats("rowR"); val g = s.floats("rowG"); val b = s.floats("rowB")
            p.setVec3("u_rowR", r[0], r[1], r[2])
            p.setVec3("u_rowG", g[0], g[1], g[2])
            p.setVec3("u_rowB", b[0], b[1], b[2]) },

        hslEffect(),
        selectiveColorEffect()
    )

    private fun hslEffect() = simpleEffect("color.hsl", "HSL Bands", EffectCategory.COLOR,
        listOf(
            Vec3P("reds", "Reds (hue°,sat,lum)", floatArrayOf(0f, 1f, 1f)),
            Vec3P("yellows", "Yellows", floatArrayOf(0f, 1f, 1f)),
            Vec3P("greens", "Greens", floatArrayOf(0f, 1f, 1f)),
            Vec3P("cyans", "Cyans", floatArrayOf(0f, 1f, 1f)),
            Vec3P("blues", "Blues", floatArrayOf(0f, 1f, 1f)),
            Vec3P("magentas", "Magentas", floatArrayOf(0f, 1f, 1f))),
        EffectGlsl.HSL_BANDS,
        description = "6 hue bands, 60° smooth falloff. Vec3 = (hue shift deg, sat scale, lum scale).")
    { p, s, _ ->
        val bands = arrayOf("reds", "yellows", "greens", "cyans", "blues", "magentas")
        val hs = FloatArray(6); val ss = FloatArray(6); val ls = FloatArray(6)
        bands.forEachIndexed { i, b -> val v = s.floats(b); hs[i] = v[0]; ss[i] = v[1]; ls[i] = v[2] }
        p.setFloatArray("u_hueShift", hs); p.setFloatArray("u_satScale", ss); p.setFloatArray("u_lumScale", ls)
    }

    private fun selectiveColorEffect() = simpleEffect("color.selective", "Selective Color", EffectCategory.COLOR,
        listOf(
            Vec3P("reds", "Reds CMY", floatArrayOf(0f, 0f, 0f)),
            Vec3P("yellows", "Yellows CMY", floatArrayOf(0f, 0f, 0f)),
            Vec3P("greens", "Greens CMY", floatArrayOf(0f, 0f, 0f)),
            Vec3P("cyans", "Cyans CMY", floatArrayOf(0f, 0f, 0f)),
            Vec3P("blues", "Blues CMY", floatArrayOf(0f, 0f, 0f)),
            Vec3P("magentas", "Magentas CMY", floatArrayOf(0f, 0f, 0f)),
            Vec3P("whites", "Whites CMY", floatArrayOf(0f, 0f, 0f)),
            Vec3P("neutrals", "Neutrals CMY", floatArrayOf(0f, 0f, 0f)),
            Vec3P("blacks", "Blacks CMY", floatArrayOf(0f, 0f, 0f))),
        EffectGlsl.SELECTIVE_COLOR,
        description = "9 ranges (6 hue + whites/neutrals/blacks), CMY deltas -1..1.")
    { p, s, _ ->
        val ranges = arrayOf("reds", "yellows", "greens", "cyans", "blues", "magentas", "whites", "neutrals", "blacks")
        val deltas = FloatArray(27)
        ranges.forEachIndexed { i, r ->
            val v = s.floats(r)
            deltas[i * 3] = v[0]; deltas[i * 3 + 1] = v[1]; deltas[i * 3 + 2] = v[2]
        }
        p.setFloatArray("u_cmyDelta", deltas)
    }

    /** Curves: master + R/G/B monotone-cubic curves, baked into a cached 256x1 RGBA LUT. */
    private fun curvesEffect() = simpleEffect("color.curves", "Curves", EffectCategory.COLOR,
        listOf(CurveP("master", "Master"), CurveP("red", "Red"),
               CurveP("green", "Green"), CurveP("blue", "Blue")),
        EffectGlsl.CURVES,
        description = "Monotone cubic splines; 1 pass + content-cached curve LUT.")
    { p, s, ctx ->
        val lut = ctx.lutEngine.getCurveLut(
            s.curve("master"), s.curve("red"), s.curve("green"), s.curve("blue"))
        p.setTexture("u_curveLut", lut)
    }
}
