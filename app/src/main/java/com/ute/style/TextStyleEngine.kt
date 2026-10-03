package com.ute.style

import com.ute.model.*
import com.ute.motion.MotionPreset
import com.ute.motion.MotionSpec
import com.ute.motion.MotionUnit
import com.ute.motion.RevealDirection

/**
 * Reusable, composable styles. Styles stack with `overlay` semantics:
 * non-null fields of the overlay win; templates and presets compose styles
 * through this single code path, keeping everything data-driven.
 */
data class ReusableStyle(
    val id: String,
    val name: String,
    val style: TextStyle = TextStyle(),
    val appearance: Appearance? = null,
    val layout: LayoutConfig? = null,
    val motion: MotionSpec? = null,
)

class TextStyleEngine {

    fun compose(base: TextStyle, overlay: TextStyle?): TextStyle {
        if (overlay == null) return base
        return base.copy(
            fontFamily = overlay.fontFamily.takeIf { it != TextStyle().fontFamily } ?: base.fontFamily,
            fallbackFamilies = base.fallbackFamilies + overlay.fallbackFamilies,
            sizePx = overlay.sizePx.takeIf { it != TextStyle().sizePx } ?: base.sizePx,
            weight = overlay.weight.takeIf { it != TextStyle().weight } ?: base.weight,
            italic = overlay.italic || base.italic,
            letterSpacingEm = overlay.letterSpacingEm.takeIf { it != 0f } ?: base.letterSpacingEm,
            lineHeightMultiple = overlay.lineHeightMultiple.takeIf { it != 1.2f } ?: base.lineHeightMultiple,
            fontFeatures = base.fontFeatures + overlay.fontFeatures,
            variableAxes = base.variableAxes + overlay.variableAxes,
        )
    }

    fun applyToLayer(layer: TextLayer, style: ReusableStyle): TextLayer = layer.copy(
        style = compose(style.style, layer.style),
        appearance = style.appearance ?: layer.appearance,
        layout = style.layout ?: layer.layout,
        motion = style.motion ?: layer.motion,
    )

    fun presets(): List<ReusableStyle> = listOf(
        ReusableStyle("preset.bold.pop", "Bold Pop",
            style = TextStyle(fontFamily = "sans-serif-black", sizePx = 88f, weight = FontWeight.BLACK),
            appearance = Appearance(
                fill = PaintSpec.Solid(0xFFFFFFFF.toInt()),
                outline = OutlineSpec(color = 0xFF111111.toInt(), widthPx = 6f),
                shadow = ShadowSpec(distancePx = 8f, softnessPx = 0f),
            ),
            motion = MotionSpec(preset = MotionPreset.POP, unit = MotionUnit.CHARACTER, staggerSec = 0.04)),
        ReusableStyle("preset.neon.cyan", "Neon Cyan",
            style = TextStyle(fontFamily = "sans-serif-medium", sizePx = 72f),
            appearance = Appearance(
                fill = PaintSpec.Solid(0xFFEFFFFF.toInt()),
                glow = GlowSpec(color = 0xFF00E5FF.toInt(), radiusPx = 18f, strength = 1.4f, bloom = true),
            ),
            motion = MotionSpec(preset = MotionPreset.STAGGER_FADE, unit = MotionUnit.WORD)),
        ReusableStyle("preset.luxury.gold", "Luxury Gold",
            style = TextStyle(fontFamily = "serif", sizePx = 96f, letterSpacingEm = 0.08f),
            appearance = Appearance(
                fill = PaintSpec.Gradient(GradientSpec(stops = listOf(
                    GradientStop(0f, 0xFFF7E08A.toInt()), GradientStop(0.5f, 0xFFC9932B.toInt()),
                    GradientStop(1f, 0xFFF7E08A.toInt())))),
                shadow = ShadowSpec(distancePx = 4f, softnessPx = 12f, color = 0x66000000.toInt()),
            ),
            motion = MotionSpec(preset = MotionPreset.TRACKING_REVEAL, unit = MotionUnit.CHARACTER)),
        ReusableStyle("preset.minimal.caption", "Minimal Caption",
            style = TextStyle(fontFamily = "sans-serif", sizePx = 44f, lineHeightMultiple = 1.3f),
            appearance = Appearance(background = BackgroundSpec(color = 0xCC000000.toInt(), cornerRadiusPx = 12f)),
            layout = LayoutConfig(align = Align.CENTER)),
        ReusableStyle("preset.urdu.nastaliq", "Urdu Title",
            style = TextStyle(fontFamily = "Noto Nastaliq Urdu", sizePx = 76f,
                fallbackFamilies = listOf("Noto Naskh Arabic"), lineHeightMultiple = 1.8f),
            layout = LayoutConfig(align = Align.CENTER),
            motion = MotionSpec(preset = MotionPreset.CASCADE_UP, unit = MotionUnit.WORD,
                direction = RevealDirection.RTL)),
    )
}
