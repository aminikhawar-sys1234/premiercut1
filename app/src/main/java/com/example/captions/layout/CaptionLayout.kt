package com.ahstudio.captions.layout

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextDirectionHeuristics
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import com.ahstudio.captions.core.language.BidiTextEngine
import com.ahstudio.captions.core.language.TextDirection
import com.ahstudio.captions.core.model.CaptionStyle
import com.ahstudio.captions.core.model.TextAlignment

data class SafeArea(
    val topFraction: Float = 0.08f,
    val bottomFraction: Float = 0.16f,
    val leftFraction: Float = 0.06f,
    val rightFraction: Float = 0.06f,
) {
    companion object {
        val SOCIAL = SafeArea(0.10f, 0.20f, 0.08f, 0.08f)
        val CINEMATIC = SafeArea(0.05f, 0.10f, 0.05f, 0.05f)
    }
}

object SafeAreaEngine {
    fun safeRect(canvasW: Float, canvasH: Float, area: SafeArea): RectF = RectF(
        canvasW * area.leftFraction, canvasH * area.topFraction,
        canvasW * (1 - area.rightFraction), canvasH * (1 - area.bottomFraction),
    )
}

object CaptionBoundsCalculator {
    fun expand(bounds: RectF, style: CaptionStyle, density: Float): RectF {
        val pad = style.paddingDp * density
        val stroke = style.strokeWidthDp * density
        val shadow = (style.shadowRadiusDp + kotlin.math.abs(style.shadowDxDp) + kotlin.math.abs(style.shadowDyDp)) * density
        val grow = pad + stroke + shadow
        return RectF(bounds.left - grow, bounds.top - grow, bounds.right + grow, bounds.bottom + grow)
    }
}

data class CaptionLayout(
    val staticLayout: StaticLayout,
    val anchorXPx: Float,
    val anchorYPx: Float,
    val textBounds: RectF,
    val backgroundRect: RectF?,
    val clipBoundsSafe: RectF,
    val wordCharRanges: List<IntRange>,
    val lineCount: Int,
)

class LayoutCacheKey(
    val textHash: Int, val styleKey: String, val widthPx: Int,
    val alignment: Layout.Alignment, val highlight: IntRange?,
)

class LayoutCache(private val maxEntries: Int = 48) {
    private val map = object : LinkedHashMap<LayoutCacheKey, CaptionLayout>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutCacheKey, CaptionLayout>) =
            size > maxEntries
    }
    @Synchronized fun get(k: LayoutCacheKey): CaptionLayout? = map[k]
    @Synchronized fun put(k: LayoutCacheKey, v: CaptionLayout) { map[k] = v }
    @Synchronized fun clear() = map.clear()
}

class CaptionLayoutEngine(private val cache: LayoutCache = LayoutCache()) {

    fun layout(
        displayLines: List<String>,
        wordCharRangesPerLine: List<List<IntRange>>,
        style: CaptionStyle,
        density: Float,
        canvasW: Float,
        canvasH: Float,
        safeArea: SafeArea,
        anchorXFraction: Float,
        anchorYFraction: Float,
        highlightWordGlobalIndex: Int? = null,
        highlightColorArgb: Int = 0xFFE8FF00.toInt(),
        highlightBackgroundArgb: Int? = null,
    ): CaptionLayout {
        val safe = SafeAreaEngine.safeRect(canvasW, canvasH, safeArea)
        val maxWidth = (safe.width()).toInt().coerceAtLeast(64)
        val joined = displayLines.joinToString("\n")

        val ranges = ArrayList<IntRange>()
        var offset = 0
        displayLines.forEachIndexed { li, line ->
            wordCharRangesPerLine.getOrNull(li)?.forEach { r -> ranges += (r.first + offset)..(r.last + offset) }
            offset += line.length + 1
        }

        val builder = SpannableStringBuilder(joined)
        if (highlightWordGlobalIndex != null && highlightWordGlobalIndex in ranges.indices) {
            val r = ranges[highlightWordGlobalIndex]
            builder.setSpan(ForegroundColorSpan(highlightColorArgb), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            highlightBackgroundArgb?.let {
                builder.setSpan(BackgroundColorSpan(it), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        val alignment = when (style.alignment) {
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val direction = BidiTextEngine.baseDirection(joined)

        val key = LayoutCacheKey(joined.hashCode(), styleKey(style), maxWidth, alignment,
            ranges.getOrNull(highlightWordGlobalIndex ?: -1))
        cache.get(key)?.let { return it }

        val paint = textPaint(style, density)
        val layout = StaticLayout.Builder
            .obtain(builder, 0, builder.length, paint, maxWidth)
            .setAlignment(alignment)
            .setLineSpacing(0f, style.lineSpacingMultiplier)
            .setTextDirection(if (direction == TextDirection.RTL)
                TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .build()

        var w = 0f; for (i in 0 until layout.lineCount) w = maxOf(w, layout.getLineWidth(i))
        val h = layout.height.toFloat()
        val textBounds = RectF(0f, 0f, w, h)

        val anchorX = when (style.alignment) {
            TextAlignment.CENTER -> safe.left + (safe.width() - w) / 2f
            TextAlignment.LEFT -> safe.left
            TextAlignment.RIGHT -> safe.right - w
        } + (safe.width() * (anchorXFraction - 0.5f)).coerceIn(-(safe.width() - w) / 2f, (safe.width() - w) / 2f)
        val anchorY = (safe.top + (safe.height() - h) * anchorYFraction.coerceIn(0f, 1f))

        val bg = style.background?.let {
            val p = style.paddingDp * density
            RectF(anchorX - p, anchorY - p, anchorX + w + p, anchorY + h + p)
        }

        val result = CaptionLayout(layout, anchorX, anchorY, textBounds, bg, safe, ranges, layout.lineCount)
        cache.put(key, result)
        return result
    }

    private fun styleKey(s: CaptionStyle) =
        "${s.id}|${s.fontFamily}|${s.fontSizeSp}|${s.bold}|${s.italic}|${s.lineSpacingMultiplier}|${s.letterSpacingEm}"

    fun textPaint(style: CaptionStyle, density: Float): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = style.fontSizeSp * density
        typeface = when {
            style.bold && style.italic -> Typeface.create(style.fontFamily, Typeface.BOLD_ITALIC)
            style.bold -> Typeface.create(style.fontFamily, Typeface.BOLD)
            style.italic -> Typeface.create(style.fontFamily, Typeface.ITALIC)
            else -> Typeface.create(style.fontFamily, Typeface.NORMAL)
        }
        letterSpacing = style.letterSpacingEm
        if (style.shadowRadiusDp > 0f) {
            setShadowLayer(style.shadowRadiusDp * density, style.shadowDxDp * density,
                style.shadowDyDp * density, style.shadowColorArgb.toInt())
        }
        isDither = false; isFilterBitmap = true
    }
}
