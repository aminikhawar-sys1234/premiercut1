package com.ute.shaping

import android.graphics.Paint
import android.graphics.text.TextRunShaper
import android.os.Build
import com.ute.core.Safe

/**
 * Real complex-script shaping via Android's HarfBuzz-backed platform stack:
 * Arabic/Urdu contextual joining and lam-alef ligatures, Devanagari matra
 * reordering and conjuncts (क्ष, त्र, ज्ञ…), Latin kerning + ligatures, and
 * CJK glyph selection all come out correct because HarfBuzz does the work.
 *
 * API 31+: TextRunShaper gives real glyph IDs and positions.
 * Pre-31 : Paint.getTextRunAdvances gives HarfBuzz positioning; glyph IDs are
 *          marked -1 and the glyph atlas keys on cluster text instead (see GlyphAtlas).
 */
class PlatformHarfBuzzShaper : TextShapingEngine {

    override fun supportsGlyphIds() = Build.VERSION.SDK_INT >= 31

    override fun shape(request: ShapingRequest): ShapingResult = Safe.critical("shape") {
        val paint = request.font.newPaint(request.sizePx)
        applyFeatures(paint, request)

        if (Build.VERSION.SDK_INT >= 31) {
            val pg = TextRunShaper.shapeTextRun(
                request.text, request.start, request.count,
                request.contextStart, request.contextCount,
                0f, 0f, request.isRtl, paint
            )
            val n = pg.glyphCount()
            val glyphs = ArrayList<ShapedGlyph>(n)
            var prevX = 0f
            for (i in 0 until n) {
                val x = pg.getGlyphX(i)
                val advance = if (i + 1 < n) pg.getGlyphX(i + 1) - x else pg.advance - x
                glyphs.add(ShapedGlyph(
                    glyphId = pg.getGlyphId(i),
                    cluster = request.start,
                    xOffset = x, yOffset = pg.getGlyphY(i),
                    xAdvance = advance, yAdvance = 0f,
                ))
                prevX = x
            }
            ShapingResult(glyphs, pg.advance, request.font, request.isRtl, request.script)
        } else {
            // Compat: HarfBuzz positioning without glyph IDs.
            val advances = FloatArray(request.count.coerceAtLeast(1))
            val total = paint.getTextRunAdvances(
                request.text.toString().toCharArray(), request.start, request.count,
                request.contextStart, request.contextCount, request.isRtl,
                advances, 0
            )
            var x = 0f
            val glyphs = ArrayList<ShapedGlyph>(request.count)
            for (i in 0 until request.count) {
                val adv = advances.getOrElse(i) { 0f }
                glyphs.add(ShapedGlyph(-1, request.start + i, x, 0f, adv, 0f))
                x += adv
            }
            ShapingResult(glyphs, total, request.font, request.isRtl, request.script)
        }
    } ?: ShapingResult(emptyList(), 0f, request.font, request.isRtl, request.script)

    private fun applyFeatures(paint: Paint, req: ShapingRequest) {
        if (Build.VERSION.SDK_INT >= 34) {
            req.fontFeatures.forEach { (tag, value) ->
                runCatching { paint.fontFeatureSettings = "$tag $value" }
            }
        } else if (req.fontFeatures.isNotEmpty()) {
            val settings = buildString {
                req.fontFeatures.forEach { (tag, v) ->
                    append("\"").append(tag).append("\" ").append(if (v != 0) 1 else 0).append("; ")
                }
            }
            runCatching { paint.fontFeatureSettings = settings }
        }
        if (Build.VERSION.SDK_INT >= 26) {
            req.variableAxes.forEach { (axis, value) ->
                runCatching { paint.fontVariationSettings = "'$axis' ${value.toInt()}" }
            }
        }
    }
}
