package com.ahstudio.captions.timeline

import com.ahstudio.captions.core.model.CaptionClip
import com.ahstudio.captions.core.model.CaptionTrack
import com.ahstudio.captions.core.model.CaptionWord
import com.ahstudio.captions.core.time.TimelineUs
import com.ahstudio.captions.segmentation.SegmentationOptions
import com.ahstudio.captions.timing.WordTimestampEngine
import java.util.UUID

class CaptionTimelineEngine(private val segOptions: SegmentationOptions = SegmentationOptions()) {

    fun activeClip(track: CaptionTrack, t: TimelineUs): CaptionClip? {
        val clips = track.clipsSorted()
        var lo = 0; var hi = clips.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = clips[mid]
            when {
                t < c.timing.start -> hi = mid - 1
                t > c.timing.end -> lo = mid + 1
                else -> return c
            }
        }
        return null
    }

    fun activeWord(clip: CaptionClip, t: TimelineUs): Pair<Int, Float>? {
        var last = -1
        for (i in clip.words.indices) {
            val w = clip.words[i]
            when {
                t < w.start -> return if (last >= 0) last to 1f else null
                t <= w.end -> return i to ((t - w.start).micros.toFloat() /
                        ((w.end - w.start).micros.toFloat().coerceAtLeast(1f))).coerceIn(0f, 1f)
                else -> last = i
            }
        }
        return if (last >= 0) last to 1f else null
    }

    fun split(clip: CaptionClip, at: TimelineUs): Pair<CaptionClip, CaptionClip>? {
        if (clip.words.size < 2) return null
        var k = clip.words.indexOfFirst { at < it.end }
        if (k <= 0) k = 1
        if (k >= clip.words.size) k = clip.words.size - 1
        val left = clip.words.subList(0, k)
        val right = clip.words.subList(k, clip.words.size)
        val a = clip.copy(
            id = UUID.randomUUID().toString(),
            words = left,
            lineBreakIndices = com.ahstudio.captions.segmentation.LineBreakingEngine
                .breakIndices(left, segOptions.maxCharsPerLine, segOptions.maxLines),
            timing = clip.timing.copy(start = clip.timing.start, end = left.last().end),
        )
        val b = clip.copy(
            id = UUID.randomUUID().toString(),
            words = right,
            lineBreakIndices = listOf(0),
            timing = clip.timing.copy(start = right.first().start, end = clip.timing.end),
        )
        return a to b
    }

    fun merge(a: CaptionClip, b: CaptionClip): CaptionClip {
        val words = a.words + b.words
        return a.copy(
            words = words,
            lineBreakIndices = com.ahstudio.captions.segmentation.LineBreakingEngine
                .breakIndices(words, segOptions.maxCharsPerLine, segOptions.maxLines),
            timing = a.timing.copy(start = a.timing.start, end = b.timing.end),
        )
    }

    fun withEditedText(clip: CaptionClip, newText: String): CaptionClip {
        val tokens = com.ahstudio.captions.transcript.TranscriptEngine.splitTokens(newText)
        val words: List<CaptionWord> =
            if (tokens.size == clip.words.size)
                clip.words.mapIndexed { i, w -> w.copy(text = tokens[i]) }
            else WordTimestampEngine.estimate(tokens, clip.timing.start, clip.timing.end)
        return clip.copy(
            words = words,
            lineBreakIndices = com.ahstudio.captions.segmentation.LineBreakingEngine
                .breakIndices(words, segOptions.maxCharsPerLine, segOptions.maxLines),
        )
    }

    fun withTrim(clip: CaptionClip, start: TimelineUs, end: TimelineUs): CaptionClip? {
        if (end <= start) return null
        val kept = clip.words.filter { it.end > start && it.start < end }
            .map { it.copy(start = it.start.coerceAtLeast(start), end = it.end.coerceAtMost(end)) }
        if (kept.isEmpty()) return null
        return clip.copy(
            words = kept,
            timing = clip.timing.copy(start = start, end = end),
            lineBreakIndices = com.ahstudio.captions.segmentation.LineBreakingEngine
                .breakIndices(kept, segOptions.maxCharsPerLine, segOptions.maxLines),
        )
    }

    fun withShift(clip: CaptionClip, deltaUs: Long, mediaDurationUs: Long?): CaptionClip {
        val upper = (mediaDurationUs ?: Long.MAX_VALUE / 4)
        val s = (clip.timing.start.micros + deltaUs).coerceIn(0, (upper - 1).coerceAtLeast(0))
        val e = (clip.timing.end.micros + deltaUs).coerceIn(s + 1, upper)
        val shift = TimelineUs(s) - clip.timing.start
        return clip.copy(
            timing = clip.timing.copy(start = TimelineUs(s), end = TimelineUs(e)),
            words = clip.words.map { it.copy(start = it.start + shift, end = it.end + shift) },
        )
    }
}
