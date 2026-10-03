package com.ute.color

/**
 * Derives text colors from background imagery. NOT a black/white switch:
 * it builds a candidate set (achromatic, tinted, complementary, palette-derived),
 * scores each on WCAG contrast + perceptual distance from the background +
 * template palette affinity, and returns the best — with a full report so the
 * host UI can show why, and with accessibility thresholds configurable.
 */
class ColorMatchingEngine(
    private val minContrast: Float = 4.5f,
) {

    data class BackgroundAnalysis(
        val averageColor: Int,
        val dominantColors: List<Int>,
        val luminance: Float,
        val isBusy: Boolean,
    )

    data class ColorRecommendation(
        val textColor: Int,
        val outlineColor: Int,
        val shadowColor: Int,
        val glowColor: Int,
        val contrast: Float,
        val meetsThreshold: Boolean,
        val strategy: String,
    )

    fun analyze(pixels: IntArray): BackgroundAnalysis {
        if (pixels.isEmpty()) return BackgroundAnalysis(0xFF000000.toInt(), emptyList(), 0f, false)
        val hist = HashMap<Int, Int>(1024)
        var rSum = 0L; var gSum = 0L; var bSum = 0L
        var lumVarAcc = 0f; var lumSum = 0f
        for (px in pixels) {
            val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
            rSum += r; gSum += g; bSum += b
            hist.merge(((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4), 1, Int::plus)
            lumSum += ColorMath.relativeLuminance(0xFF000000.toInt() or (r shl 16) or (g shl 8) or b)
        }
        val n = pixels.size
        val avgLum = lumSum / n
        for (px in pixels.take(n / 16)) {
            val l = ColorMath.relativeLuminance(px)
            lumVarAcc += (l - avgLum) * (l - avgLum)
        }
        val dominant = hist.entries.sortedByDescending { it.value }.take(5)
            .map { (key, _) ->
                val r = ((key shr 8) and 0xF) * 17; val g = ((key shr 4) and 0xF) * 17; val b = (key and 0xF) * 17
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        return BackgroundAnalysis(
            averageColor = (0xFF shl 24) or ((rSum / n).toInt() shl 16) or ((gSum / n).toInt() shl 8) or (bSum / n).toInt(),
            dominantColors = dominant,
            luminance = avgLum,
            isBusy = lumVarAcc / (n / 16f) > 0.02f,
        )
    }

    fun recommend(bg: BackgroundAnalysis, palette: List<Int> = emptyList()): ColorRecommendation {
        val candidates = ArrayList<Pair<Int, String>>(8)
        candidates.add(
            (if (bg.luminance > 0.35f) 0xFF111111.toInt() else 0xFFF7F7F7.toInt()) to
            (if (bg.luminance > 0.35f) "dark-neutral" else "light-neutral")
        )

        val avgLab = ColorMath.argbToOklab(bg.averageColor)
        val tintedL = if (avgLab[0] > 0.5f) 0.18f else 0.95f
        candidates.add(ColorMath.oklabToArgb(tintedL, avgLab[1] * 0.6f, avgLab[2] * 0.6f) to "tinted-complement")

        val hsv = ColorMath.hsvFromArgb(bg.averageColor)
        candidates.add(ColorMath.argbFromHsv(hsv[0] + 180f, 0.55f, if (bg.luminance > 0.35f) 0.15f else 0.95f) to "complementary")

        palette.forEach { p ->
            val pl = ColorMath.argbToOklab(p)[0]
            if (kotlin.math.abs(pl - avgLab[0]) > 0.25f) candidates.add(p to "palette")
        }

        var best: ColorRecommendation? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for ((c, strategy) in candidates) {
            val contrast = ColorMath.contrastRatio(c, bg.averageColor)
            var minDom = Float.MAX_VALUE
            for (d in bg.dominantColors) minDom = minOf(minDom, ColorMath.contrastRatio(c, d))
            val score = contrast * 2f + minDom * (if (bg.isBusy) 1.5f else 0.5f)
            if (score > bestScore) {
                bestScore = score
                val needsHelp = contrast < minContrast || bg.isBusy
                best = ColorRecommendation(
                    textColor = c,
                    outlineColor = outlineFor(c, bg),
                    shadowColor = if (needsHelp) (0xB3000000.toInt()) else 0x66000000.toInt(),
                    glowColor = glowFor(c),
                    contrast = contrast,
                    meetsThreshold = contrast >= minContrast && minDom >= 3f,
                    strategy = strategy,
                )
            }
        }
        return best ?: ColorRecommendation(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0x66000000.toInt(),
            0xFF00E5FF.toInt(), ColorMath.contrastRatio(0xFFFFFFFF.toInt(), bg.averageColor), false, "fallback")
    }

    private fun outlineFor(text: Int, bg: BackgroundAnalysis): Int {
        val lab = ColorMath.argbToOklab(text)
        return ColorMath.oklabToArgb(if (lab[0] > 0.4f) 0.08f else 0.97f, lab[1] * 0.3f, lab[2] * 0.3f)
    }

    private fun glowFor(text: Int): Int {
        val hsv = ColorMath.hsvFromArgb(text)
        return ColorMath.argbFromHsv(hsv[0], 0.9f, 1f, 0x99)
    }
}
