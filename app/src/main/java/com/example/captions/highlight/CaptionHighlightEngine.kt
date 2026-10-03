package com.ahstudio.captions.highlight

import com.ahstudio.captions.core.model.CaptionClip
import com.ahstudio.captions.core.time.TimelineUs

data class WordHighlightState(
    val clipId: String,
    val activeWordIndex: Int,
    val wordProgress: Float,
    val clipProgress: Float,
)

object CaptionHighlightEngine {
    fun at(clip: CaptionClip, t: TimelineUs): WordHighlightState? {
        if (t < clip.timing.start || t > clip.timing.end) return null
        val clipProgress = ((t - clip.timing.start).micros.toFloat() /
            (clip.timing.durationUs.toFloat().coerceAtLeast(1f))).coerceIn(0f, 1f)
        val active = clip.words.indexOfFirst { t >= it.start && t <= it.end }
        val (idx, prog) = if (active >= 0) active to run {
            val w = clip.words[active]
            ((t - w.start).micros.toFloat() / (w.end - w.start).micros.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
        } else run {
            val lastBefore = clip.words.indexOfLast { it.end <= t }
            if (lastBefore >= 0) lastBefore to 1f else 0 to 0f
        }
        return WordHighlightState(clip.id, idx, prog, clipProgress)
    }
}
