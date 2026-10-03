package com.ute.shaping

import com.ute.fonts.FontHandle
import com.ute.unicode.Script

data class ShapingRequest(
    val text: CharSequence,
    val start: Int,          // run start in UTF-16 units
    val count: Int,
    val contextStart: Int,   // shaping context — critical for correct joining across run edges
    val contextCount: Int,
    val isRtl: Boolean,
    val font: FontHandle,
    val sizePx: Float,
    val script: Script,
    val language: String,
    val fontFeatures: Map<String, Int> = emptyMap(),
    val variableAxes: Map<String, Float> = emptyMap(),
)

data class ShapedGlyph(
    val glyphId: Int,        // -1 when the platform path cannot expose glyph IDs (pre-API 31)
    val cluster: Int,        // UTF-16 index of the grapheme cluster this glyph belongs to
    val xOffset: Float, val yOffset: Float,   // offset from run origin (y positive up)
    val xAdvance: Float, val yAdvance: Float,
)

data class ShapingResult(
    val glyphs: List<ShapedGlyph>,
    val advance: Float,
    val font: FontHandle,
    val isRtl: Boolean,
    val script: Script,
) {
    /** Normalized horizontal extent: glyph offsets mapped to [0, width]. */
    fun normalizedOffsets(): Pair<Float, Float> {
        if (glyphs.isEmpty()) return 0f to 0f
        val minX = glyphs.minOf { it.xOffset }
        val maxX = glyphs.maxOf { it.xOffset + it.xAdvance }.coerceAtLeast(minX + 0.01f)
        return minX to (maxX - minX)
    }
}
