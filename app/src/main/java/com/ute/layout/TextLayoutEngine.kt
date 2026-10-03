package com.ute.layout

import com.ute.core.Safe
import com.ute.fonts.FontEngine
import com.ute.fonts.FontHandle
import com.ute.model.*
import com.ute.shaping.ShapedGlyph
import com.ute.shaping.ShapingPipeline
import com.ute.shaping.ShapingRequest
import com.ute.unicode.*

data class PlacedGlyph(
    val font: FontHandle,
    val clusterText: String,
    val clusterStart: Int,           // UTF-16 index in layer content (for spans/animation)
    val x: Float, val y: Float,      // baseline-relative position in text-box space (y up)
    val advance: Float,
    val glyphId: Int,                // -1 on pre-31 platform path
)

data class LayoutLine(
    val glyphs: List<PlacedGlyph>,
    val width: Float,
    val baselineY: Float,            // from text-box top, y down
    val direction: Direction,
    val paragraphIndex: Int,
    val startCluster: Int, val endCluster: Int,
)

data class TextLayout(
    val lines: List<LayoutLine>,
    val width: Float,
    val height: Float,
    val fontAscentPx: Float,
    val fontDescentPx: Float,
    val baseDirection: Direction,
) {
    val isEmpty: Boolean get() = lines.all { it.glyphs.isEmpty() }
}

/**
 * Line breaking, bidi visual ordering, alignment and justification for LTR and
 * RTL contexts. Positions every glyph; the GPU FrameBuilder only consumes it.
 */
class TextLayoutEngine(
    private val shaping: ShapingPipeline,
    private val bidi: BidiEngine = BidiEngine(),
) {

    fun layout(content: String, style: TextStyle, config: LayoutConfig,
               fonts: FontEngine): TextLayout =
        Safe.critical("layout", TextLayout(emptyList(), 0f, 0f, 0f, 0f, Direction.LTR)) {
            val primary = fonts.find(style.fontFamily, style.weight.cssValue, style.italic)
            val direction = bidi.resolveDirection(Direction.AUTO, content)
            val baseLevel = bidi.baseLevel(Direction.AUTO, content)
            val paint = (primary ?: fonts.systemDefault()).newPaint(style.sizePx)
            val fm = paint.fontMetrics
            val lineH = style.lineHeightMultiple * (fm.descent - fm.ascent)
            val tracking = style.letterSpacingEm * style.sizePx

            val lines = ArrayList<LayoutLine>()
            val paragraphs = content.split('\n')
            val maxW = config.widthPx.takeIf { it > 0f }?.minus(config.safeMarginPx * 2)

            for ((pIdx, para) in paragraphs.withIndex()) {
                if (para.isEmpty()) {
                    lines.add(LayoutLine(emptyList(), 0f, 0f, direction, pIdx, 0, 0))
                    continue
                }

                // 1) shape the paragraph into font-fallback segments
                val segs = shaping.shapeWithFallback(
                    para, 0, para.length, primary, style.sizePx,
                    ScriptDetector.detect(para), "en",
                    isRtl = direction == Direction.RTL, features = style.fontFeatures
                )

                // 2) cluster table: (start, end, advance, segment) per grapheme cluster
                val clusters = buildClusters(para, segs, tracking)

                // 3) wrap into logical lines
                val logicalLines = wrap(para, clusters, maxW, config)

                // 4) visual assembly + alignment per line
                for (line in logicalLines) {
                    val subStr = para.substring(line.first, line.second)
                    val runsLogical = bidi.runs(subStr, Direction.AUTO)
                        .map { BidiRun(it.start + line.first, it.end + line.first, it.level) }
                    val runsVisual = bidi.visualOrder(runsLogical, baseLevel)

                    val glyphs = ArrayList<PlacedGlyph>()
                    var cursor = 0f
                    for (run in runsVisual) {
                        val runClusters = clusters.filter { it.start >= run.start && it.start < run.end }
                        val seg = segs.firstOrNull { it.start <= run.start && it.start + it.count >= run.end }
                            ?: segs.firstOrNull() ?: continue
                        val (minX, _) = seg.result.normalizedOffsets()
                        val runStartX = cursor
                        for (c in runClusters) {
                            for (g in c.shapedGlyphs) {
                                glyphs.add(PlacedGlyph(
                                    font = seg.result.font,
                                    clusterText = para.substring(c.start, c.end),
                                    clusterStart = c.start,
                                    x = runStartX + (g.xOffset - minX),
                                    y = g.yOffset,
                                    advance = g.xAdvance,
                                    glyphId = g.glyphId,
                                ))
                            }
                            cursor += c.advance
                        }
                    }
                    val lineWidth = cursor

                    val boxW = maxW ?: lineWidth
                    val x0 = when (config.align) {
                        Align.START -> if (direction == Direction.RTL) boxW - lineWidth else 0f
                        Align.END   -> if (direction == Direction.RTL) 0f else boxW - lineWidth
                        Align.CENTER -> (boxW - lineWidth) / 2f
                        Align.JUSTIFY -> if (line.isLast) justifyOffset(direction, boxW, lineWidth) else 0f
                    }
                    val shifted = if (x0 != 0f) glyphs.map { it.copy(x = it.x + x0) } else glyphs

                    lines.add(LayoutLine(
                        glyphs = shifted, width = lineWidth,
                        baselineY = 0f,
                        direction = direction, paragraphIndex = pIdx,
                        startCluster = line.first, endCluster = line.second,
                    ))
                }
            }

            // 5) vertical positions & alignment
            val contentH = lines.size * lineH + paragraphs.count { it.isEmpty() } * lineH * 0.5f
            val boxH = config.heightPx.takeIf { it > 0f } ?: contentH
            val yOff = when (config.verticalAlign) {
                VerticalAlign.TOP -> 0f
                VerticalAlign.MIDDLE -> (boxH - contentH) / 2f
                VerticalAlign.BOTTOM -> boxH - contentH
            } + config.safeMarginPx

            val ascent = -fm.ascent
            val positioned = ArrayList<LayoutLine>(lines.size)
            var li = 0
            for (line in lines) {
                val baseline = yOff + li * lineH + ascent
                positioned.add(line.copy(baselineY = baseline))
                li++
            }

            val maxLineW = positioned.maxOfOrNull { it.width } ?: 0f
            TextLayout(positioned, maxLineW, boxH, ascent, fm.descent, direction)
        }

    private class Cluster(
        val start: Int, val end: Int,
        val advance: Float,
        val shapedGlyphs: List<ShapedGlyph>,
        val isWhitespace: Boolean,
        val breakAfter: Boolean,
    )

    private fun buildClusters(
        para: String, segs: List<ShapingPipeline.ShapedSegment>, tracking: Float,
    ): List<Cluster> {
        val starts = GraphemeClusterer.clusterStarts(para)
        val clusters = ArrayList<Cluster>(starts.size)
        val breaks = LineBreaker.opportunities(para, 0, para.length)
        for (k in starts.indices) {
            val s = starts[k]
            val e = if (k + 1 < starts.size) starts[k + 1] else para.length
            clusters.add(Cluster(
                start = s, end = e,
                advance = 0f, shapedGlyphs = emptyList(),
                isWhitespace = para.substring(s, e).isBlank(),
                breakAfter = breaks.getOrElse(s) { false },
            ))
        }
        assignGlyphsToClusters(para, segs, clusters, starts, tracking)
        return clusters
    }

    private fun assignGlyphsToClusters(
        para: String,
        segs: List<ShapingPipeline.ShapedSegment>,
        clusters: MutableList<Cluster>,
        starts: IntArray,
        tracking: Float,
    ) {
        val rebuild = ArrayList<Cluster>(clusters.size)
        val glyphBuckets: Array<MutableList<ShapedGlyph>> = Array(clusters.size) { mutableListOf() }
        for (seg in segs) {
            val glyphs = seg.result.glyphs
            if (glyphs.isEmpty()) continue
            if (glyphs.first().glyphId >= 0) {
                val localStarts = starts.filter { it in seg.start until seg.start + seg.count }
                val counts = if (localStarts.isNotEmpty())
                    shaping.clusterGlyphCounts(
                        ShapingRequest(para, seg.start, seg.count,
                            (seg.start - 32).coerceAtLeast(0), (seg.count + 64).coerceAtMost(para.length),
                            seg.isRtl, seg.result.font, 1f, ScriptDetector.detect(para), "en"),
                        localStarts) else IntArray(0)
                var gi = 0
                for ((ck, c) in localStarts.withIndex()) {
                    val n = counts.getOrElse(ck) { 0 }
                    val bucket = glyphBuckets[starts.indexOf(c)]
                    repeat(n) { if (gi < glyphs.size) bucket.add(glyphs[gi++]) }
                }
                while (gi < glyphs.size) {
                    glyphBuckets.lastOrNull()?.add(glyphs[gi++]) ?: break
                }
            } else {
                for (i in 0 until seg.count) {
                    val g = glyphs.getOrElse(i) { break }
                    val abs = seg.start + i
                    val idx = starts.indexOfLast { it <= abs }.coerceAtLeast(0)
                    glyphBuckets[idx].add(g)
                }
            }
        }
        for (k in clusters.indices) {
            val c = clusters[k]
            val gs = glyphBuckets[k]
            val adv = gs.sumOf { it.xAdvance.toDouble() }.toFloat() +
                    (if (gs.isNotEmpty()) tracking else 0f)
            rebuild.add(Cluster(c.start, c.end, adv, gs, c.isWhitespace, c.breakAfter))
        }
        clusters.clear(); clusters.addAll(rebuild)
    }

    private class LineRange(val first: Int, val second: Int, val isLast: Boolean)

    private fun wrap(
        para: String, clusters: List<Cluster>, maxW: Float?, config: LayoutConfig,
    ): List<LineRange> {
        if (maxW == null || config.wrapMode == WrapMode.NONE) {
            return listOf(LineRange(0, para.length, true))
        }
        val lines = ArrayList<LineRange>()
        var lineStart = 0
        var width = 0f
        var lastBreak = -1
        var widthAtBreak = 0f
        var i = 0
        while (i < clusters.size) {
            val c = clusters[i]
            val cw = c.advance
            if (maxW > 0f && width + cw > maxW && width > 0f) {
                val breakAt = if (config.wrapMode == WrapMode.WORD && lastBreak > lineStart) lastBreak else i
                val lineEnd = clusters[breakAt.coerceAtMost(clusters.size - 1)].end
                lines.add(LineRange(clusters[lineStart].start, lineEnd, false))
                lineStart = breakAt
                width = width - widthAtBreak + cw
                lastBreak = -1; widthAtBreak = 0f
            } else {
                width += cw
                if (c.breakAfter) { lastBreak = i + 1; widthAtBreak = width }
            }
            i++
        }
        lines.add(LineRange(clusters[lineStart].start, para.length, true))
        return lines
    }

    private fun justifyOffset(direction: Direction, boxW: Float, lineWidth: Float): Float =
        if (direction == Direction.RTL) boxW - lineWidth else 0f
}
