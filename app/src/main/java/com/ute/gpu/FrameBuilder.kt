package com.ute.gpu

import com.ute.animation.ResolvedLayerState
import com.ute.color.ColorEngine
import com.ute.core.Mat4
import com.ute.effects.DeformType
import com.ute.effects.MaskShape
import com.ute.fonts.FontEngine
import com.ute.layout.PlacedGlyph
import com.ute.layout.TextLayout
import com.ute.model.*
import com.ute.motion.GlyphMotion
import kotlin.math.*

/**
 * Transforms (layout + resolved animation + motion states) into GPU instance data
 * and per-draw style uniforms. All unit conversions (px → SDF, px → atlas UV)
 * happen here so the shaders stay simple.
 */
class FrameBuilder(private val fonts: FontEngine, private val colors: ColorEngine) {

    data class LayerRenderData(
        val instances: FloatArray,
        val instanceCount: Int,
        val layerMatrix: Mat4,
        val uniforms: GlTextRenderer.StyleUniforms,
        val needsBloom: Boolean,
        val bloomStrength: Float,
    )

    /** Injected per-frame by TextFrameRenderer: resolves placed glyph → atlas rect [u, v, uw, vh]. */
    var renderSessionAtlasRect: ((PlacedGlyph) -> FloatArray?)? = null

    fun build2D(
        layout: TextLayout,
        layer: TextLayer,
        state: ResolvedLayerState,
        motions: List<GlyphMotion>,
        timeSec: Double,
        viewportW: Int, viewportH: Int,
    ): LayerRenderData {
        val sdfSpread = 6f
        val data = ArrayList<Float>(layout.lines.sumOf { it.glyphs.size } * 22)
        var count = 0

        var trackingAccum = 0f
        var prevClusterLine = -1

        val appearance = layer.appearance
        val fill = appearance.fill

        layout.lines.forEachIndexed { li, line ->
            if (prevClusterLine != -1 && li != prevClusterLine) trackingAccum = 0f
            prevClusterLine = li
            for (g in line.glyphs) {
                val mi = if (motions.size > count) motions[count] else GlyphMotion()
                if (!mi.visible || mi.opacity <= 0.001f) {
                    count++
                    trackingAccum += mi.extraTrackingPx
                    continue
                }

                val fillArgb = when (fill) {
                    is PaintSpec.Solid -> state.fillOverride ?: fill.color
                    is PaintSpec.Gradient -> colors.gradientColorAt(fill.gradient,
                        colors.gradientCoord(fill.gradient, g.x, line.baselineY, layout.width, layout.height), timeSec)
                    is PaintSpec.Material -> state.fillOverride ?: fill.tint
                }
                val fa = ((fillArgb ushr 24) and 0xFF) / 255f * state.opacity * mi.opacity * appearance.opacity
                if (fa <= 0.002f) {
                    count++
                    continue
                }

                val x = g.x + trackingAccum + mi.dx
                val y = line.baselineY - g.y - mi.dy
                val rot = mi.rotationDeg
                val rad = Math.toRadians(rot.toDouble())
                val fontSizeScale = state.fontSizePx / layer.style.sizePx.coerceAtLeast(1f)
                val w = g.advance.coerceAtLeast(1f) * fontSizeScale
                val h = (layout.fontAscentPx + layout.fontDescentPx) * fontSizeScale

                val cx = x + w / 2f
                val cy = y - h / 2f

                val rect = renderSessionAtlasRect?.invoke(g)
                if (rect == null) {
                    count++
                    continue
                }

                data.addAll(listOf(
                    rect[0], rect[1], rect[2], rect[3],
                    cx, cy, w * 1.15f, h * 1.3f,
                    ((fillArgb shr 16) and 0xFF) / 255f, ((fillArgb shr 8) and 0xFF) / 255f,
                    (fillArgb and 0xFF) / 255f, fa,
                    sin(rad).toFloat(), cos(rad).toFloat(), mi.scaleX, mi.scaleY,
                    fa,
                    gradientCoordIfAny(fill, g.x, line.baselineY, layout),
                    sdfSpread * fontSizeScale,
                    0f,
                    0f, 0f
                ))
                count++
                trackingAccum += mi.extraTrackingPx
            }
        }

        val anchor = layer.transform
        val pivotX = layout.width * anchor.anchorX
        val pivotY = layout.height * anchor.anchorY
        val layerMatrix = Mat4.multiply(
            Mat4.trs2d(state.x, state.y, state.rotationDeg, state.scaleX, state.scaleY),
            Mat4.translation(-pivotX, -pivotY, 0f)
        )

        return LayerRenderData(
            instances = data.toFloatArray(),
            instanceCount = count,
            layerMatrix = layerMatrix,
            uniforms = styleUniforms(layer, state, layout, sdfSpread, viewportW, viewportH, timeSec),
            needsBloom = appearance.glow?.bloom == true,
            bloomStrength = appearance.glow?.strength ?: 0f,
        )
    }

    private fun gradientCoordIfAny(fill: PaintSpec, x: Float, y: Float, layout: TextLayout): Float =
        when (fill) {
            is PaintSpec.Gradient -> when (fill.gradient.type) {
                GradientType.LINEAR ->
                    colors.gradientCoord(fill.gradient, x, y, layout.width, layout.height)
                else -> colors.radialCoord(fill.gradient, x, y, layout.width, layout.height)
            }
            else -> 0f
        }

    private fun styleUniforms(
        layer: TextLayer, state: ResolvedLayerState,
        layout: TextLayout, sdfSpread: Float, vw: Int, vh: Int, timeSec: Double,
    ): GlTextRenderer.StyleUniforms {
        val ap = layer.appearance
        val outline = ap.outline
        val shadow = ap.shadow
        val glow = ap.glow
        val mask = layer.mask

        val shadowRad = Math.toRadians((shadow?.angleDeg ?: 45f).toDouble())
        val shDistPx = (shadow?.distancePx ?: 0f) * (state.fontSizePx / layer.style.sizePx.coerceAtLeast(1f))
        val shUvPerPx = 1f / 2048f
        val shOffX = (-cos(shadowRad).toFloat() * shDistPx) * shUvPerPx
        val shOffY = (sin(shadowRad).toFloat() * shDistPx) * shUvPerPx

        val deform = layer.deform
        return GlTextRenderer.StyleUniforms(
            outlineColor = outline?.color ?: 0,
            outlineOpacity = outline?.opacity ?: 0f,
            outlineWidthSdf = (outline?.widthPx ?: 0f) / (2f * sdfSpread),
            shadowColor = shadow?.color ?: 0,
            shadowOpacity = if (shadow != null) ((shadow.color ushr 24) and 0xFF) / 255f else 0f,
            shadowOffsetUvX = shOffX, shadowOffsetUvY = shOffY,
            glowEnabled = glow != null,
            glowStrength = glow?.strength ?: 0f,
            glowFalloffPx = (glow?.radiusPx ?: 1f).coerceAtLeast(1f),
            glowColor = glow?.color ?: 0,
            gradientTex = when (val f = ap.fill) {
                is PaintSpec.Gradient -> colors.gradientLut(f.gradient, timeSec)
                else -> 0
            },
            deformMode = when (deform?.type) {
                DeformType.ARC -> 1; DeformType.WAVE -> 2; DeformType.BULGE -> 3; DeformType.FISHEYE -> 4
                else -> 0
            },
            deformAmount = deform?.amount ?: 0f,
            deformRadius = deform?.radiusPx ?: 300f,
            deformFreq = deform?.frequency ?: 2f,
            deformPhase = (deform?.phase ?: 0f) + ((deform?.wavePxPerSec ?: 0f) * timeSec).toFloat(),
            maskMode = when (mask?.shape) {
                MaskShape.RECTANGLE, MaskShape.ROUNDED_RECT -> 1
                MaskShape.CIRCLE, MaskShape.ELLIPSE -> 2
                else -> 0
            },
            maskCenterX = (mask?.centerX ?: 0.5f) * layout.width,
            maskCenterY = (mask?.centerY ?: 0.5f) * layout.height,
            maskHalfW = (mask?.halfW ?: 0.5f) * layout.width,
            maskHalfH = (mask?.halfH ?: 0.5f) * layout.height,
            maskFeather = mask?.featherPx ?: 0f,
            maskInvert = mask?.invert ?: false,
            boxHalfW = layout.width / 2f, boxHalfH = layout.height / 2f,
        )
    }
}
