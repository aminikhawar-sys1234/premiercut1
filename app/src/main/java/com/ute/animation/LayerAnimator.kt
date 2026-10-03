package com.ute.animation

import com.ute.color.ColorMath
import com.ute.core.MathUtil
import com.ute.effects.EffectType
import com.ute.model.*
import java.util.EnumMap

/** The resolved state of a layer at a local timestamp — pure data, no GL. */
data class ResolvedLayerState(
    val x: Float, val y: Float,
    val scaleX: Float, val scaleY: Float,
    val rotationDeg: Float,
    val opacity: Float,
    val fontSizePx: Float,
    val trackingEm: Float,
    val fillOverride: Int?,          // ARGB when FILL_COLOR track present
    val outlineWidthPx: Float?,
    val extrusionDepthPx: Float?,
    val effectIntensity: Map<EffectType, Float>,
)

class LayerAnimator(tracks: Map<AnimatableProperty, List<KeyframeSpec>>) {

    private val compiled = tracks.mapValues { Track(it.value) }

    fun hasTracks() = compiled.isNotEmpty()

    /** [tLocal] = composition time − layer start. Deterministic by contract. */
    fun evaluate(base: TextLayer, tLocal: Double): ResolvedLayerState {
        val tr = base.transform
        fun p(prop: AnimatableProperty, default: Float) =
            compiled[prop]?.let { if (!it.isEmpty()) it.evaluate(tLocal) else default } ?: default

        val fx = EnumMap<EffectType, Float>(EffectType::class.java)
        base.effects.forEach { fx[it.type] = MathUtil.sanitize(it.intensity) }
        compiled[AnimatableProperty.EFFECT_INTENSITY]?.let { t ->
            base.effects.forEach { fx[it.type] = t.evaluate(tLocal) * it.intensity }
        }

        return ResolvedLayerState(
            x = p(AnimatableProperty.POSITION_X, tr.x),
            y = p(AnimatableProperty.POSITION_Y, tr.y),
            scaleX = p(AnimatableProperty.SCALE_X, p(AnimatableProperty.SCALE, tr.scaleX)),
            scaleY = p(AnimatableProperty.SCALE_Y, p(AnimatableProperty.SCALE, tr.scaleY)),
            rotationDeg = p(AnimatableProperty.ROTATION, tr.rotationDeg),
            opacity = p(AnimatableProperty.OPACITY, tr.opacity).coerceIn(0f, 1f),
            fontSizePx = p(AnimatableProperty.FONT_SIZE, base.style.sizePx),
            trackingEm = p(AnimatableProperty.TRACKING, base.style.letterSpacingEm),
            fillOverride = compiled[AnimatableProperty.FILL_COLOR]?.let {
                if (it.isEmpty()) null else ColorMath.oklabToArgb(it.evaluate(tLocal).coerceIn(0f, 1f),
                    base.labA(), base.labB(), base.fillAlpha())
            },
            outlineWidthPx = compiled[AnimatableProperty.OUTLINE_WIDTH]?.let { it.evaluate(tLocal) },
            extrusionDepthPx = compiled[AnimatableProperty.EXTRUSION_DEPTH]?.let { it.evaluate(tLocal) },
            effectIntensity = fx,
        )
    }

    private fun TextLayer.labA(): Float = ColorMath.argbToOklab((appearance.fill as? PaintSpec.Solid)?.color ?: 0xFFFFFFFF.toInt())[1]
    private fun TextLayer.labB(): Float = ColorMath.argbToOklab((appearance.fill as? PaintSpec.Solid)?.color ?: 0xFFFFFFFF.toInt())[2]
    private fun TextLayer.fillAlpha(): Int = (MathUtil.sanitize(appearance.opacity, 1f) * 255).toInt()
}
