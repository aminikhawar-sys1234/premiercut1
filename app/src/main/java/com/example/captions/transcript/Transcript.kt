package com.ahstudio.captions.transcript

import com.ahstudio.captions.core.language.TextMetrics
import com.ahstudio.captions.core.model.CaptionWord
import com.ahstudio.captions.core.model.WordTimestampSource
import com.ahstudio.captions.core.time.TimelineUs
import com.ahstudio.captions.core.time.TimelineUsSerializer
import com.ahstudio.captions.recognition.RecognizedUtterance
import com.ahstudio.captions.recognition.SpeechRecognitionResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class TranscriptSegment(
    val words: List<CaptionWord>,
    @Serializable(with = TimelineUsSerializer::class) val start: TimelineUs,
    @Serializable(with = TimelineUsSerializer::class) val end: TimelineUs,
    val speakerId: String? = null,
) {
    val text: String get() = TextMetrics.joinWordsText(words.map { it.text })
}

@Serializable
data class TranscriptDocument(
    val languageTag: String,
    val segments: List<TranscriptSegment>,
    val wordTimestampSource: WordTimestampSource = WordTimestampSource.NONE,
    val providerId: String? = null,
) {
    fun flatWords(): List<CaptionWord> = segments.flatMap { it.words }
    fun displayText(): String = segments.joinToString("\n") { it.text }
}

object TranscriptEngine {

    fun fromRecognition(result: SpeechRecognitionResult): TranscriptDocument {
        val segments = result.utterances.map { u: RecognizedUtterance ->
            val words = u.words?.map {
                CaptionWord(it.text, TimelineUs(it.startUs), TimelineUs(it.endUs), it.confidence)
            }
            TranscriptSegment(
                words = words ?: emptyList(),
                start = TimelineUs(u.startUs),
                end = TimelineUs(maxOf(u.endUs, u.startUs)),
            )
        }
        val source = when {
            result.utterances.isEmpty() -> WordTimestampSource.NONE
            result.utterances.all { it.words != null } -> WordTimestampSource.MEASURED
            else -> WordTimestampSource.ESTIMATED
        }
        return TranscriptDocument(result.languageTag, segments, source, result.providerId)
    }

    fun withEstimatedWords(doc: TranscriptDocument): TranscriptDocument {
        if (doc.segments.all { it.words.isNotEmpty() }) return doc
        val fixed = doc.segments.map { seg ->
            if (seg.words.isNotEmpty()) seg
            else seg.copy(words = com.ahstudio.captions.timing.WordTimestampEngine.estimate(splitTokens(seg.text), seg.start, seg.end))
        }
        val newSource = if (doc.wordTimestampSource == WordTimestampSource.MEASURED) WordTimestampSource.MEASURED
                        else WordTimestampSource.ESTIMATED
        return doc.copy(segments = fixed, wordTimestampSource = newSource)
    }

    fun splitTokens(text: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() { if (sb.isNotEmpty()) { out += sb.toString(); sb.clear() } }
        TextMetrics.forEachCodePoint(text) { cp ->
            when {
                Character.isWhitespace(cp) -> flush()
                TextMetrics.isCjk(cp) -> { flush(); out += String(Character.toChars(cp)) }
                else -> sb.appendCodePoint(cp)
            }
        }
        flush()
        return out
    }

    fun normalizeText(text: String): String = text.replace(Regex("\\s+"), " ").trim()
}
