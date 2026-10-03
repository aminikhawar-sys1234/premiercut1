package com.ahstudio.captions.segmentation

import com.ahstudio.captions.audio.SilenceRegion
import com.ahstudio.captions.core.language.TextMetrics
import com.ahstudio.captions.core.model.CaptionClip
import com.ahstudio.captions.core.model.CaptionWord
import com.ahstudio.captions.core.model.ClipSource
import com.ahstudio.captions.core.time.TimelineUs
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ReadingSpeedConfig(
    val maxCps: Double = 20.0,
    val targetCps: Double = 15.0,
    val minDurationUs: Long = 1_000_000L,
    val maxDurationUs: Long = 6_000_000L,
) {
    fun requiredDurationUs(visualChars: Double): Long =
        (visualChars / maxCps * 1_000_000.0).toLong().coerceIn(minDurationUs, maxDurationUs)
    fun cps(visualChars: Double, durationUs: Long): Double =
        if (durationUs <= 0) Double.MAX_VALUE else visualChars * 1_000_000.0 / durationUs
}

object ReadingSpeedAnalyzer {
    fun isReadable(unit: List<CaptionWord>, cfg: ReadingSpeedConfig): Boolean {
        val chars = TextMetrics.visualLength(TextMetrics.joinWordsText(unit.map { it.text }))
        val dur = (unit.last().end - unit.first().start).micros
        return cfg.cps(chars, dur) <= cfg.maxCps && dur >= cfg.minDurationUs
    }
}

data class SegmentationOptions(
    val maxLines: Int = 2,
    val maxCharsPerLine: Int = 32,
    val reading: ReadingSpeedConfig = ReadingSpeedConfig(),
    val sentenceBreakSilenceUs: Long = 400_000L,
    val softBreakSilenceUs: Long = 180_000L,
    val respectSpeakerChanges: Boolean = true,
)

data class SegmentationResult(
    val clips: List<CaptionClip>,
    val warnings: List<String>,
)

class CaptionSegmentationEngine(private val options: SegmentationOptions = SegmentationOptions()) {

    private val terminal = ".!?…。！？؟।".toSet()
    private val soft = ",;،.,:—–".toSet()

    fun segment(
        words: List<CaptionWord>,
        trackId: String,
        styleId: String,
        silences: List<SilenceRegion> = emptyList(),
        source: ClipSource = ClipSource.RECOGNIZED,
    ): SegmentationResult {
        if (words.isEmpty()) return SegmentationResult(emptyList(), listOf("empty transcript — nothing to segment"))
        val warnings = ArrayList<String>()
        val silenceLookup = silences.map { it.start.micros to it.end.micros }

        val blocks = ArrayList<MutableList<CaptionWord>>()
        var current = mutableListOf<CaptionWord>()
        for (i in words.indices) {
            val w = words[i]
            if (current.isNotEmpty()) {
                if (options.respectSpeakerChanges && w.speakerId != null && w.speakerId != current.last().speakerId) {
                    blocks += current; current = mutableListOf()
                } else {
                    val gapStart = current.last().end.micros
                    val gapEnd = w.start.micros
                    val inSilence = silenceLookup.any { (s, e) -> s < gapEnd && e > gapStart }
                    val gap = gapEnd - gapStart
                    if (gap >= options.sentenceBreakSilenceUs || inSilence) {
                        blocks += current; current = mutableListOf()
                    }
                }
            }
            current += w
            if (w.text.lastOrNull() in terminal) { blocks += current; current = mutableListOf() }
        }
        if (current.isNotEmpty()) blocks += current

        val maxUnitChars = options.maxLines * options.maxCharsPerLine
        val units = ArrayList<List<CaptionWord>>()
        for (block in blocks) {
            var unit = mutableListOf<CaptionWord>()
            var unitChars = 0.0
            for (w in block) {
                val wChars = TextMetrics.visualLength(w.text) +
                    if (unit.isEmpty()) 0.0 else if (isCjkBoundary(unit.last(), w)) 0.0 else 1.0
                val unitDur = if (unit.isEmpty()) (w.end - w.start).micros else (w.end - unit.first().start).micros

                val tooLongChars = unitChars + wChars > maxUnitChars
                val tooLongDur = unitDur > options.reading.maxDurationUs
                val softBreak = unit.isNotEmpty() &&
                    unitDur > options.reading.maxDurationUs * 3 / 4 &&
                    unit.last().text.lastOrNull() in soft &&
                    (w.start - unit.last().end).micros >= options.softBreakSilenceUs

                if (unit.isNotEmpty() && (tooLongChars || tooLongDur || softBreak)) {
                    units += unit; unit = mutableListOf(); unitChars = 0.0
                }
                unit += w
                unitChars += wChars
            }
            if (unit.isNotEmpty()) units += unit
        }

        val repaired = ArrayList<List<CaptionWord>>(units.size)
        for ((idx, u) in units.withIndex()) {
            val chars = TextMetrics.visualLength(TextMetrics.joinWordsText(u.map { it.text }))
            val required = options.reading.requiredDurationUs(chars)
            val actual = (u.last().end - u.first().start).micros
            var endUs = u.last().end.micros
            if (actual < required) {
                val nextStart = units.getOrNull(idx + 1)?.first()?.start?.micros ?: Long.MAX_VALUE
                val gapLimit = nextStart - 40_000L
                val target = min(u.first().start.micros + required, u.first().start.micros + options.reading.maxDurationUs)
                endUs = min(max(endUs, min(target, gapLimit)), u.first().start.micros + options.reading.maxDurationUs)
                if (endUs - u.first().start.micros < required)
                    warnings += "caption at ${u.first().start.millis}ms exceeds max cps"
            }
            repaired += u
        }

        val clips = repaired.map { unit ->
            val breaks = LineBreakingEngine.breakIndices(unit, options.maxCharsPerLine, options.maxLines)
            CaptionClip(
                id = UUID.randomUUID().toString(),
                trackId = trackId,
                words = unit,
                lineBreakIndices = breaks,
                styleId = styleId,
                timing = com.ahstudio.captions.core.model.CaptionTiming(unit.first().start, unit.last().end),
                source = source,
            )
        }
        return SegmentationResult(clips, warnings)
    }

    private fun isCjkBoundary(prev: CaptionWord, next: CaptionWord) =
        TextMetrics.isCjk(prev.text.lastOrNull()?.code ?: 0) || TextMetrics.isCjk(next.text.firstOrNull()?.code ?: 0)
}

object LineBreakingEngine {

    private val closingPunct = TextMetrics.run { "。，、！？」』）】〕〉》｝»!?.,;:…؟।".map { it.code }.toSet() }
    private val openingPunct = "（「『【〔〈《｛«([{".map { it.code }.toSet()

    fun breakIndices(words: List<CaptionWord>, maxCharsPerLine: Int, maxLines: Int): List<Int> {
        if (words.size <= 1 || maxLines <= 1) return listOf(0)
        val lens = words.map { TextMetrics.visualLength(it.text) }

        fun breakAllowed(beforeIndex: Int): Boolean {
            if (beforeIndex <= 0 || beforeIndex >= words.size) return false
            val prev = words[beforeIndex - 1]; val next = words[beforeIndex]
            if (next.text.firstOrNull()?.code in closingPunct) return false
            if (prev.text.lastOrNull()?.code in openingPunct) return false
            return true
        }

        if (maxLines == 2) {
            val total = lens.sum()
            var best = -1; var bestPenalty = Double.MAX_VALUE
            var acc = 0.0
            for (i in 1 until words.size) {
                acc += lens[i - 1] + 1.0
                if (!breakAllowed(i)) continue
                val l1 = acc; val l2 = total - acc + (words.size - i - 1).coerceAtLeast(0) * 1.0
                if (l1 <= maxCharsPerLine && l2 <= maxCharsPerLine) {
                    val penalty = abs(l1 - l2)
                    if (penalty < bestPenalty) { bestPenalty = penalty; best = i }
                }
            }
            if (best > 0) return listOf(0, best)
        }

        val breaks = ArrayList<Int>(); breaks += 0
        var lineLen = 0.0
        for (i in words.indices) {
            val w = lens[i] + if (lineLen == 0.0) 0.0 else 1.0
            if (lineLen + w > maxCharsPerLine && breaks.size < maxLines && breakAllowed(i)) {
                breaks += i; lineLen = lens[i]
            } else lineLen += w
        }
        return breaks
    }
}
