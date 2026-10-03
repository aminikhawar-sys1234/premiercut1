package com.ahstudio.captions.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLUtils
import java.text.Bidi

class GlyphAtlas(private val maxPages: Int = 4) {

    enum class Variant { FILL, STROKE, SHADOW }

    data class WordGlyph(
        val textureName: Int, val page: Int,
        val u0: Float, val v0: Float, val u1: Float, val v1: Float,
        val widthPx: Float, val heightPx: Float, val ascentPx: Float, val advancePx: Float,
    )

    private class Page(val tex: Int) {
        var x = 1; var y = 1; var rowH = 0
    }

    private val pages = ArrayList<Page>()
    private val cache = HashMap<String, WordGlyph>()
    private val pageKeys = HashMap<Int, MutableSet<String>>()

    private fun key(word: String, fontKey: String, v: Variant) = "$fontKey\u0001$v\u0001$word"

    fun getOrRasterize(word: String, fontKey: String, variant: Variant, paint: Paint): WordGlyph? {
        if (word.isEmpty()) return null
        cache[key(word, fontKey, variant)]?.let { return it }
        val metrics = paint.fontMetrics
        val pad = 2
        val w = (paint.measureText(word) + pad * 2).toInt().coerceAtLeast(4)
        val h = (metrics.descent - metrics.ascent + pad * 2).toInt().coerceAtLeast(4)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(paint)
        when (variant) {
            Variant.FILL -> p.color = Color.WHITE
            Variant.STROKE -> { p.style = Paint.Style.STROKE; p.strokeWidth = paint.strokeWidth; p.color = Color.WHITE }
            Variant.SHADOW -> { p.color = Color.WHITE; p.setShadowLayer(paint.textSize * 0.12f, 0f, paint.textSize * 0.06f, Color.WHITE) }
        }
        c.drawText(word, pad.toFloat(), pad - metrics.ascent, p)

        val page = obtainPage(w, h) ?: run { bmp.recycle(); return null }
        val pageIdx = pages.indexOf(page)
        val tex = page.tex
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, page.x, page.y, bmp)
        setRepeatClamp()
        val k = key(word, fontKey, variant)
        val glyph = WordGlyph(
            tex, pageIdx,
            page.x / 1024f, page.y / 1024f,
            (page.x + w) / 1024f, (page.y + h) / 1024f,
            w.toFloat(), h.toFloat(), pad - metrics.ascent, paint.measureText(word),
        )
        bmp.recycle()
        cache[k] = glyph
        pageKeys.getOrPut(pageIdx) { mutableSetOf() }.add(k)
        return glyph
    }

    private fun obtainPage(w: Int, h: Int): Page? {
        for (p in pages) {
            if (p.x + w < 1024 && p.y + h < 1024) {
                val pg = p; pg.x += w; pg.rowH = maxOf(pg.rowH, h)
                if (pg.x + w >= 1024) { pg.x = 1; pg.y += pg.rowH + 1; pg.rowH = 0 }
                return pg
            }
        }
        if (pages.size >= maxPages) evictOldestPage()
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        setRepeatClamp()
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1024, 1024, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        val page = Page(tex[0])
        pages += page
        return page
    }

    private fun setRepeatClamp() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun invalidateFont(fontKey: String) {
        val dead = cache.keys.filter { it.startsWith("$fontKey\u0001") }
        dead.forEach { cache.remove(it) }
    }

    private fun evictOldestPage() {
        val victim = pages.removeAt(0)
        pageKeys.remove(0)?.forEach { cache.remove(it) }
        val reindexed = HashMap<Int, MutableSet<String>>()
        pageKeys.forEach { (idx, keys) -> if (idx > 0) reindexed[idx - 1] = keys }
        pageKeys.clear(); pageKeys.putAll(reindexed)
        val t = IntArray(1); t[0] = victim.tex
        GLES20.glDeleteTextures(1, t, 0)
    }

    fun release() {
        for (p in pages) { val t = IntArray(1); t[0] = p.tex; GLES20.glDeleteTextures(1, t, 0) }
        pages.clear(); cache.clear(); pageKeys.clear()
    }
}

object WordVisualPlacer {
    data class PlacedWord(val logicalIndex: Int, val x: Float, val width: Float)

    fun place(lineText: String, wordTexts: List<String>, wordCharRanges: List<IntRange>,
              baseIsRtl: Boolean, paint: Paint, lineLeft: Float, lineRight: Float): List<PlacedWord> {
        val bidi = Bidi(lineText, if (baseIsRtl) Bidi.DIRECTION_RIGHT_TO_LEFT else Bidi.DIRECTION_LEFT_TO_RIGHT)
        val out = ArrayList<PlacedWord>(wordTexts.size)
        var cursorL = lineLeft
        var cursorR = lineRight
        for (run in 0 until bidi.runCount) {
            val runStart = bidi.getRunStart(run)
            val runLimit = bidi.getRunLimit(run)
            val level = bidi.getRunLevel(run)
            val runRtl = (level and 1) == 1
            val runWordIdx = wordCharRanges.indices.filter { wordCharRanges[it].first >= runStart && wordCharRanges[it].first < runLimit }
            if (runRtl) {
                for (i in runWordIdx.asReversed()) {
                    val w = paint.measureText(wordTexts[i])
                    out += PlacedWord(i, cursorR - w, w)
                    cursorR -= w
                }
            } else {
                for (i in runWordIdx) {
                    val w = paint.measureText(wordTexts[i])
                    out += PlacedWord(i, cursorL, w)
                    cursorL += w
                }
            }
        }
        return out
    }
}
