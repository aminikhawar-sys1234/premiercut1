package com.ute.glyphs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.ute.fonts.FontHandle
import com.ute.core.Safe

data class RasterizedGlyph(
    val alpha: FloatArray,       // antialiased coverage 0..1, row-major
    val argb: IntArray?,         // non-null for color glyphs (emoji)
    val width: Int, val height: Int,
    val bearingX: Float,         // offset from pen to bitmap left
    val bearingY: Float,         // offset from pen (baseline) to bitmap top (positive up)
    val advance: Float,
    val isColorGlyph: Boolean,
)

/**
 * Rasterizes a cluster into a bitmap using the platform text stack (same engine
 * that shaped it, so emoji/CBDT color glyphs and complex scripts rasterize identically).
 */
class GlyphRasterizer {

    fun rasterize(font: FontHandle, clusterText: String, sizePx: Float): RasterizedGlyph? =
        Safe.critical("rasterize", null) {
            val paint = font.newPaint(sizePx)
            val advance = paint.measureText(clusterText)
            val metrics = paint.fontMetrics
            val pad = 2
            val w = (kotlin.math.ceil(advance) + pad * 2).toInt().coerceIn(1, 512)
            val h = (kotlin.math.ceil(metrics.descent - metrics.ascent) + pad * 2).toInt().coerceIn(1, 512)
            if (w <= 0 || h <= 0 || w * h > 512 * 512) return@critical null

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val isColor = paint.hasGlyph(clusterText) && isLikelyColorGlyph(clusterText)
            // Draw with the baseline placed so glyph top = metrics.ascent.
            canvas.drawText(clusterText, pad.toFloat(), pad - metrics.ascent, paint)

            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            val alpha = FloatArray(w * h) { i -> (pixels[i] ushr 24) / 255f }
            bmp.recycle()

            RasterizedGlyph(
                alpha = alpha,
                argb = if (isColor) pixels else null,
                width = w, height = h,
                bearingX = -pad.toFloat(),
                bearingY = metrics.descent + pad,
                advance = advance,
                isColorGlyph = isColor,
            )
        }

    private fun isLikelyColorGlyph(text: String): Boolean =
        text.codePoints().anyMatch {
            it in 0x1F000..0x1FAFF || it in 0x2600..0x27BF ||
            it == 0x200D || it in 0x1F1E6..0x1F1FF
        }
}
