package com.ahstudio.captions.engine

import com.ahstudio.captions.audio.AudioChunk
import com.ahstudio.captions.core.model.CaptionProject
import com.ahstudio.captions.core.model.CaptionTrack
import com.ahstudio.captions.core.model.ClipSource
import com.ahstudio.captions.core.model.TrackKind
import com.ahstudio.captions.recognition.*
import com.ahstudio.captions.segmentation.CaptionSegmentationEngine
import com.ahstudio.captions.segmentation.SegmentationOptions
import com.ahstudio.captions.speaker.SpeakerDiarizationEngine
import com.ahstudio.captions.timing.CaptionTimingEngine
import com.ahstudio.captions.transcript.TranscriptEngine
import kotlinx.coroutines.flow.Flow
import java.util.UUID

sealed class CaptionGenerationState {
    data object Idle : CaptionGenerationState()
    data class ExtractingAudio(val progress: Float) : CaptionGenerationState()
    data class Transcribing(val progress: Float, val providerId: String) : CaptionGenerationState()
    data class Diarizing(val progress: Float) : CaptionGenerationState()
    data class Segmenting(val clipCount: Int) : CaptionGenerationState()
    data class Completed(val project: CaptionProject, val warnings: List<String>) : CaptionGenerationState()
    data class Failed(val error: Throwable) : CaptionGenerationState()
}

data class AutoCaptionOptions(
    val languageTag: String = "en",
    val preferredProviderId: String? = null,
    val styleId: String = "style.default",
    val segOptions: SegmentationOptions = SegmentationOptions(),
)

class AutoCaptionEngine(
    private val registry: SpeechRecognitionProviderRegistry,
    private val extractor: com.ahstudio.captions.audio.AudioExtractor,
    private val diarizer: SpeakerDiarizationEngine,
    private val cache: com.ahstudio.captions.cache.RecognitionCache? = null,
) {
    suspend fun generate(
        mediaUri: android.net.Uri,
        options: AutoCaptionOptions,
        onState: (CaptionGenerationState) -> Unit,
    ): CaptionProject {
        onState(CaptionGenerationState.ExtractingAudio(0f))
        val info = extractor.probe(mediaUri)
        if (!info.hasAudioTrack) throw com.ahstudio.captions.core.errors.CaptionEngineException.AudioExtraction("no audio track found in media")

        val engine = registry.select(options.preferredProviderId, options.languageTag)
        val cached = cache?.get(mediaUri, options.languageTag)

        val result = cached ?: run {
            onState(CaptionGenerationState.Transcribing(0f, engine.id))
            val chunks: Flow<AudioChunk> = extractor.extract(mediaUri, 16_000)
            val source = SpeechSource.Pcm(16_000, chunks)
            val recOpts = RecognitionOptions(options.languageTag, wordTimestamps = true, sampleRateHz = 16_000, totalDurationUs = info.durationUs)
            val res = engine.transcribe(source, recOpts) { p ->
                onState(CaptionGenerationState.Transcribing(p.fraction, engine.id))
            }
            cache?.put(mediaUri, options.languageTag, res)
            res
        }

        onState(CaptionGenerationState.Diarizing(0f))
        val doc = TranscriptEngine.fromRecognition(result).let { TranscriptEngine.withEstimatedWords(it) }
        val allWords = doc.flatWords()

        val pcmChunks = extractor.extract(mediaUri, 16_000)
        val audioSource = SpeechSource.Pcm(16_000, pcmChunks)
        val (taggedWords, speakers) = diarizer.assignSpeakers(allWords, audioSource)

        onState(CaptionGenerationState.Segmenting(taggedWords.size))
        val trackId = "track_" + UUID.randomUUID().toString().take(6)
        val silenceRegions = com.ahstudio.captions.audio.SilenceAnalyzer.silences(
            com.ahstudio.captions.audio.EnergyVad().analyze(floatArrayOf(), 16_000), info.durationUs,
        )
        val segResult = CaptionSegmentationEngine(options.segOptions)
            .segment(taggedWords, trackId, options.styleId, silenceRegions, ClipSource.RECOGNIZED)

        val timedClips = CaptionTimingEngine(options.segOptions.reading).normalize(segResult.clips, info.durationUs)

        val track = CaptionTrack(
            id = trackId,
            name = "Auto Captions",
            kind = TrackKind.PRIMARY,
            languageTag = result.languageTag,
            clips = timedClips,
        )

        val project = CaptionProject(
            id = UUID.randomUUID().toString(),
            metadata = com.ahstudio.captions.core.model.CaptionMetadata(
                source = ClipSource.RECOGNIZED,
                providerId = result.providerId,
                languageTag = result.languageTag,
                mediaDurationUs = info.durationUs,
            ),
            tracks = listOf(track),
            speakers = speakers,
        )
        onState(CaptionGenerationState.Completed(project, segResult.warnings))
        return project
    }
}
