package com.ahstudio.captions.recognition

import com.ahstudio.captions.audio.AudioChunk
import com.ahstudio.captions.core.errors.CaptionEngineException
import com.ahstudio.captions.core.language.LanguageTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

sealed class ProviderAvailability {
    data object Available : ProviderAvailability()
    data class Unavailable(val reason: String) : ProviderAvailability()
    data class Downloading(val progress: Float) : ProviderAvailability()
    data object Processing : ProviderAvailability()
    data class Failed(val message: String) : ProviderAvailability()
}

data class RecognitionOptions(
    val languageTag: String,
    val wordTimestamps: Boolean = true,
    val sampleRateHz: Int = 16_000,
    val totalDurationUs: Long? = null,
)

@Serializable
data class RecognizedWord(val text: String, val startUs: Long, val endUs: Long, val confidence: Float)

@Serializable
data class RecognizedUtterance(
    val text: String,
    val startUs: Long,
    val endUs: Long,
    val words: List<RecognizedWord>?,
)

@Serializable
data class SpeechRecognitionResult(
    val providerId: String,
    val languageTag: String,
    val utterances: List<RecognizedUtterance>,
) {
    val hasMeasuredWordTimings: Boolean get() = utterances.all { it.words != null }
}

sealed class SpeechSource {
    data class Pcm(val sampleRateHz: Int, val chunks: Flow<AudioChunk>) : SpeechSource()
}

typealias AudioSource = SpeechSource

data class RecognitionProgress(val fraction: Float, val utterancesSoFar: Int)

interface SpeechRecognitionEngine {
    val id: String
    val availability: StateFlow<ProviderAvailability>
    fun supportedLanguages(): Set<String>
    fun supportsLanguage(languageTag: String): Boolean =
        supportedLanguages().any { languageMatches(it, languageTag) }

    suspend fun transcribe(
        source: SpeechSource,
        options: RecognitionOptions,
        onProgress: (RecognitionProgress) -> Unit = {},
    ): SpeechRecognitionResult

    companion object {
        fun languageMatches(supported: String, requested: String): Boolean {
            val s = LanguageTag.baseOf(supported)
            val r = LanguageTag.baseOf(requested)
            return s == r
        }
    }
}

data class ProviderInfo(
    val id: String,
    val availability: ProviderAvailability,
    val languages: Set<String>,
)

class SpeechRecognitionProviderRegistry(private val engines: List<SpeechRecognitionEngine>) {
    fun providers(): List<SpeechRecognitionEngine> = engines
    fun providerInfo(): List<ProviderInfo> =
        engines.map { ProviderInfo(it.id, it.availability.value, it.supportedLanguages()) }

    fun select(preferredId: String?, languageTag: String): SpeechRecognitionEngine {
        preferredId?.let { id ->
            val e = engines.firstOrNull { it.id == id }
                ?: throw CaptionEngineException.RecognitionUnavailable("provider '$id' not registered")
            if (!e.supportsLanguage(languageTag)) throw CaptionEngineException.UnsupportedLanguage(languageTag, id)
            return e
        }
        return engines.firstOrNull { it.supportsLanguage(languageTag) }
            ?: throw CaptionEngineException.RecognitionUnavailable(
                "no registered provider supports language '$languageTag'; install an offline model"
            )
    }
}
