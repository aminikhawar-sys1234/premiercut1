package com.ahstudio.captions.timing

import com.ahstudio.captions.core.model.CaptionClip
import com.ahstudio.captions.core.time.TimelineUs
import com.ahstudio.captions.segmentation.ReadingSpeedConfig
import com.ahstudio.captions.core.language.TextMetrics
import kotlin.math.min

class CaptionTimingEngine(
    private val reading: ReadingSpeedConfig = ReadingSpeedConfig(),
    private val minGapUs: Long = 40_000L,
) {

    fun normalize(clips: List<CaptionClip>, mediaDurationUs: Long?): List<CaptionClip> {
        if (clips.isEmpty()) return clips
        val upper = TimelineUs(mediaDurationUs ?: Long.MAX_VALUE / 4)
        val sorted = clips.sortedBy { it.timing.start.micros }
        val out = ArrayList<CaptionClip>(sorted.size)
        var warnings = 0

        for (i in sorted.indices) {
            val clip = sorted[i]
            var s = clip.timing.start.coerceIn(TimelineUs.ZERO, upper)
            var e = clip.timing.end.coerceIn(TimelineUs.ZERO, upper)

            if (e < s) e = TimelineUs(min(s.micros + reading.minDurationUs, upper.micros))

            val nextStart = sorted.getOrNull(i + 1)?.timing?.start?.micros ?: Long.MAX_VALUE

            val chars = TextMetrics.visualLength(clip.displayText)
            val required = reading.requiredDurationUs(chars)
            if (e.micros - s.micros < required) {
                val roomBound = if (nextStart == Long.MAX_VALUE) Long.MAX_VALUE else nextStart - minGapUs
                val target = min(s.micros + required, s.micros + reading.maxDurationUs)
                e = TimelineUs(maxOf(e.micros, min(target, roomBound).coerceAtLeast(e.micros)))
            }

            if (e.micros > nextStart - minGapUs && nextStart != Long.MAX_VALUE) {
                if (nextStart - minGapUs > s.micros) e = TimelineUs(nextStart - minGapUs)
                else warnings++
            }
            if (e.micros - s.micros > reading.maxDurationUs) e = TimelineUs(s.micros + reading.maxDurationUs)

            if (e <= s) { e = TimelineUs(s.micros + 1); warnings++ }

            out += clip.copy(timing = clip.timing.copy(start = s, end = e))
        }
        return out
    }

    fun snapToWords(clip: CaptionClip): CaptionClip {
        if (clip.words.isEmpty()) return clip
        return clip.copy(timing = clip.timing.copy(start = clip.words.first().start, end = clip.words.last().end))
    }
}
