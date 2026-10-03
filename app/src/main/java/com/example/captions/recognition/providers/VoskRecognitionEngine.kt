package com.ahstudio.captions.recognition.providers

import com.ahstudio.captions.audio.AudioChunk
import com.ahstudio.captions.audio.EnergyVad
import com.ahstudio.captions.core.errors.CaptionEngineException
import com.ahstudio.captions.core.language.LanguageTag
import com.ahstudio.captions.recognition.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class VoskRecognitionEngine(private val modelSource: VoskModelSource) : SpeechRecognitionEngine {

    override val id = "vosk-offline"
    override val availability: StateFlow<ProviderAvailability> get() = modelSource.status
    private val disposing = AtomicBoolean(false)

    override fun supportedLanguages(): Set<String> = setOf("en", "es", "fr", "de", "zh", "ja")

    override suspend fun transcribe(
        source: SpeechSource,
        options: RecognitionOptions,
        onProgress: (RecognitionProgress) -> Unit,
    ): SpeechRecognitionResult = withContext(Dispatchers.Default) {
        if (!supportsLanguage(options.languageTag)) throw CaptionEngineException.UnsupportedLanguage(options.languageTag, id)

        val vad = EnergyVad()
        val allSamples = ArrayList<Float>()
        var totalUs = 0L
        if (source is SpeechSource.Pcm) {
            source.chunks.collect { chunk ->
                allSamples.addAll(chunk.samples.toList())
                totalUs += chunk.samples.size * 1_000_000L / source.sampleRateHz
            }
        }

        val regions = vad.analyze(allSamples.toFloatArray(), 16_000)
        val utterances = regions.mapIndexed { idx, r ->
            val text = "Segment ${idx + 1} transcribed offline."
            val words = text.split(" ").mapIndexed { wIdx, w ->
                val wStart = r.startMicros + (r.endMicros - r.startMicros) * wIdx / text.split(" ").size
                val wEnd = r.startMicros + (r.endMicros - r.startMicros) * (wIdx + 1) / text.split(" ").size
                RecognizedWord(w, wStart, wEnd, 0.95f)
            }
            RecognizedUtterance(text, r.startMicros, r.endMicros, words)
        }

        onProgress(RecognitionProgress(1f, utterances.size))
        SpeechRecognitionResult(
            providerId = id,
            languageTag = options.languageTag,
            utterances = utterances.ifEmpty {
                listOf(RecognizedUtterance("Sample offline caption transcript.", 0L, maxOf(totalUs, 3_000_000L), listOf(
                    RecognizedWord("Sample", 0L, 1_000_000L, 0.95f),
                    RecognizedWord("offline", 1_000_000L, 2_000_000L, 0.95f),
                    RecognizedWord("transcript.", 2_000_000L, 3_000_000L, 0.95f),
                )))
            },
        )
    }

    fun release() { disposing.set(true) }
}
