package com.ahstudio.captions.android

import android.content.Context
import com.ahstudio.captions.android.audio.AndroidAudioExtractor
import com.ahstudio.captions.cache.RecognitionCache
import com.ahstudio.captions.engine.AutoCaptionEngine
import com.ahstudio.captions.recognition.SpeechRecognitionProviderRegistry
import com.ahstudio.captions.recognition.providers.AssetVoskModelSource
import com.ahstudio.captions.recognition.providers.VoskRecognitionEngine
import com.ahstudio.captions.recognition.providers.WhisperCloudEngine
import com.ahstudio.captions.recognition.providers.WhisperConfig
import com.ahstudio.captions.speaker.MfccDiarizationEngine
import java.io.File

object CaptionsGraph {
    @Volatile private var instance: CaptionsGraphHolder? = null

    fun get(context: Context): CaptionsGraphHolder {
        return instance ?: synchronized(this) {
            instance ?: CaptionsGraphHolder(context.applicationContext).also { instance = it }
        }
    }
}

class CaptionsGraphHolder(context: Context) {
    val extractor = AndroidAudioExtractor(context)
    val cache = RecognitionCache(File(context.filesDir, "rec_cache"))
    val voskSource = AssetVoskModelSource(context, "vosk", mapOf("en" to "vosk-model-small-en-us-0.15.zip"))
    val voskEngine = VoskRecognitionEngine(voskSource)
    val whisperEngine = WhisperCloudEngine(WhisperConfig("", null))
    val registry = SpeechRecognitionProviderRegistry(listOf(voskEngine, whisperEngine))
    val diarizer = MfccDiarizationEngine()
    val autoEngine = AutoCaptionEngine(registry, extractor, diarizer, cache)
}
