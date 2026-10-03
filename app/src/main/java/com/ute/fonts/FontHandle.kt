package com.ute.fonts

import android.graphics.Paint
import android.graphics.Typeface

/**
 * A registered font. Coverage probing is cached lazily — used by the fallback
 * policy so a missing glyph switches fonts *before* shaping, never after.
 */
open class FontHandle(
    val id: Int,
    val typeface: Typeface,
    val descriptor: FontDescriptor,
    val source: FontSource,
) {
    private val coverage = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    open fun canRender(codePoint: Int): Boolean = coverage.getOrPut(codePoint) {
        runCatching {
            val p = Paint()
            p.typeface = typeface
            p.hasGlyph(String(Character.toChars(codePoint)))
        }.getOrDefault(false)
    }

    fun newPaint(sizePx: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = this@FontHandle.typeface
        textSize = sizePx
        isLinearText = true          // disables hinting snapping for smooth scaling
    }
}
