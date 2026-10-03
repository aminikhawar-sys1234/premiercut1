package com.ute.model

/**
 * Typography style. 'size' and spacings are in pixels relative to a reference
 * canvas so documents scale losslessly across resolutions.
 */
data class TextStyle(
    val fontFamily: String = "sans-serif",
    val fallbackFamilies: List<String> = emptyList(),   // user extension of the chain
    val sizePx: Float = 64f,
    val weight: FontWeight = FontWeight.REGULAR,
    val italic: Boolean = false,
    val letterSpacingEm: Float = 0f,     // tracking, em units
    val lineHeightMultiple: Float = 1.2f,
    val paragraphSpacingPx: Float = 0f,
    val fontFeatures: Map<String, Int> = emptyMap(),    // OpenType tags, e.g. "liga" to 1
    val variableAxes: Map<String, Float> = emptyMap(),  // "wght", "wdth", "slnt", "opsz"
    val smallCaps: Boolean = false,                     // mapped to OpenType "smcp" if font supports
    val numericStyle: NumericStyle = NumericStyle.DEFAULT,
)

enum class FontWeight(val cssValue: Int) {
    THIN(100), EXTRA_LIGHT(200), LIGHT(300), REGULAR(400), MEDIUM(500),
    SEMI_BOLD(600), BOLD(700), EXTRA_BOLD(800), BLACK(900);

    companion object { fun fromCss(v: Int) = entries.minByOrNull { kotlin.math.abs(it.cssValue - v) } ?: REGULAR }
}

enum class NumericStyle { DEFAULT, LATIN, ARABIC_INDIC, DEVANAGARI, HAN }
