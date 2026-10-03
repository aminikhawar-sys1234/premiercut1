package com.ahstudio.captions.timing

import com.ahstudio.captions.core.language.TextMetrics
import com.ahstudio.captions.core.model.CaptionWord
import com.ahstudio.captions.core.time.TimelineUs
import com.ahstudio.captions.transcript.TranscriptEngine
import kotlin.math.roundToLong

object WordTimestampEngine {

    const val SPACE_WEIGHT = 0.6

    fun fromMeasured(
        words: List<Triple<String, Long, Long>>,
        confidences: List<Float>? = null,
    ): List<CaptionWord> = words.mapIndexed { i, (text, s, e) ->
        CaptionWord(text, TimelineUs(s), TimelineUs(e), confidences?.get(i) ?: 1f)
    }

    fun estimate(tokens: List<String>, start: TimelineUs, end: TimelineUs): List<CaptionWord> {
        if (tokens.isEmpty()) return emptyList()
        val spanUs = (end - start).micros.coerceAtLeast(0)
        val weights = tokens.map { TextMetrics.visualLength(it) + SPACE_WEIGHT }
        val total = weights.sum().coerceAtLeast(1.0)
        var cursor = start.micros
        return tokens.mapIndexed { i, token ->
            val dur = if (i == tokens.lastIndex) (end.micros - cursor).coerceAtLeast(0)
                      else (spanUs * weights[i] / total).roundToLong().coerceAtLeast(0)
            val w = CaptionWord(token, TimelineUs(cursor), TimelineUs(cursor + dur))
            cursor += dur
            w
        }
    }

    fun estimateText(text: String, start: TimelineUs, end: TimelineUs): List<CaptionWord> =
        estimate(TranscriptEngine.splitTokens(text), start, end)
}
