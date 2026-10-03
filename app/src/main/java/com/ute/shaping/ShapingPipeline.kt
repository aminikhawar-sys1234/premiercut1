package com.ute.shaping

import com.ute.core.Safe
import com.ute.fonts.FontEngine
import com.ute.unicode.GraphemeClusterer
import com.ute.unicode.Script
import com.ute.unicode.ScriptDetector

/**
 * Full pipeline: script segmentation → cluster-aware font fallback → shaping per
 * (script, font) sub-run with full context. Guarantees a missing glyph never
 * crashes or renders tofu while a fallback font can supply it.
 */
class ShapingPipeline(
    private val engine: TextShapingEngine,
    private val fonts: FontEngine,
) : TextShapingEngine by engine {

    data class ShapedSegment(val result: ShapingResult, val start: Int, val count: Int, val isRtl: Boolean)

    /**
     * Shape [start, start+count) of [text] with fallback resolution.
     * Returns ordered segments; the layout engine places them via BidiEngine.
     */
    fun shapeWithFallback(
        text: CharSequence, start: Int, count: Int,
        primary: com.ute.fonts.FontHandle?, sizePx: Float,
        script: Script, language: String,
        isRtl: Boolean, features: Map<String, Int>,
    ): List<ShapedSegment> = Safe.critical("shapeFallback", listOf(ShapedSegment(
        ShapingResult(emptyList(), 0f, fonts.systemDefault(), isRtl, script), start, count, isRtl))) {

        val chain = fonts.fallbackChain(primary, script, language)
        val clusters = clusterRanges(text, start, count)

        // 1) choose a font per cluster (coverage-probed, cached in FontEngine)
        val perClusterFont = clusters.map { c ->
            val probe = representativeCodePoint(text, c.first, c.second)
            chain.firstOrNull { it.canRender(probe) } ?: chain.last()
        }

        // 2) group consecutive clusters with the same font into sub-runs
        val segments = ArrayList<ShapedSegment>()
        var i = 0
        while (i < clusters.size) {
            var j = i
            while (j < clusters.size && perClusterFont[j] == perClusterFont[i]) j++
            val s = clusters[i].first
            val e = clusters[j - 1].second
            val ctxStart = (s - 32).coerceAtLeast(0)                 // context window: ±32 chars
            val ctxEnd = (e + 32).coerceAtMost(text.length)
            val res = engine.shape(ShapingRequest(
                text, s, e - s, ctxStart, ctxEnd - ctxStart,
                isRtl, perClusterFont[i], sizePx, script, language, features))
            segments.add(ShapedSegment(res, s, e - s, isRtl))
            i = j
        }
        segments
    }

    private fun clusterRanges(text: CharSequence, start: Int, count: Int): List<Pair<Int, Int>> {
        val sub = text.subSequence(start, start + count).toString()
        val starts = GraphemeClusterer.clusterStarts(sub)
        return starts.indices.map { k ->
            val s = start + starts[k]
            val e = start + (if (k + 1 < starts.size) starts[k + 1] else sub.length)
            s to e
        }
    }

    private fun representativeCodePoint(text: CharSequence, s: Int, e: Int): Int {
        var i = s
        while (i < e) {
            val cp = Character.codePointAt(text, i)
            if (!Character.isWhitespace(cp)) return cp
            i += Character.charCount(cp)
        }
        return if (s < e) Character.codePointAt(text, s) else ' '.code
    }

    /**
     * Cluster→glyph-count mapping via prefix shaping: shape [start, boundary) with
     * full context; its glyph count tells how many glyphs belong to clusters before
     * the boundary.
     */
    fun clusterGlyphCounts(req: ShapingRequest, boundaries: List<Int>): IntArray {
        val counts = IntArray(boundaries.size)
        var prev = 0
        for ((k, b) in boundaries.withIndex()) {
            val prefix = engine.shape(req.copy(start = req.start, count = b - req.start))
            counts[k] = prefix.glyphs.size - prev
            prev = prefix.glyphs.size
        }
        return counts
    }
}
