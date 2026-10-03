package com.ahstudio.audio.master.model

import kotlin.math.abs

data class AudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val durationSec: Double = 0.0,
    val bitRate: Int = 0,
    val fileSizeBytes: Long = 0L,
)

data class AudioClipTransform(
    val speed: Float = 1f,
    val pitchSemitones: Float = 0f,
    val reverse: Boolean = false,
) { val isNeutral: Boolean get() = speed == 1f && pitchSemitones == 0f && !reverse }

enum class FadeCurve { LINEAR, EXPONENTIAL, SINE }

data class AudioFadeSettings(
    val fadeInSec: Double = 0.0,
    val fadeOutSec: Double = 0.0,
    val fadeInCurve: FadeCurve = FadeCurve.LINEAR,
    val fadeOutCurve: FadeCurve = FadeCurve.LINEAR,
)

data class AudioPanSettings(val pan: Float = 0f) { init { require(pan in -1f..1f) } }

data class AudioClipModel(
    val id: String,
    val trackId: String,
    val sourceId: String,
    val timelineStartSec: Double,
    val timelineDurationSec: Double,
    val sourceStartSec: Double,
    val sourceDurationSec: Double,
    val volume: Float = 1f,
    val gainDb: Float = 0f,
    val pan: AudioPanSettings = AudioPanSettings(),
    val fade: AudioFadeSettings = AudioFadeSettings(),
    val transform: AudioClipTransform = AudioClipTransform(),
    val clipDsp: AudioDspChainSpec = AudioDspChainSpec(),
    val automation: List<AudioAutomationModel> = emptyList(),
    val metadata: AudioMetadata = AudioMetadata(),
) {
    init { require(timelineDurationSec >= 0.0 && sourceDurationSec >= 0.0) }
    val timelineEndSec: Double get() = timelineStartSec + timelineDurationSec
    val sourceEndSec: Double get() = sourceStartSec + sourceDurationSec
    fun containsTime(t: Double): Boolean = t >= timelineStartSec && t < timelineEndSec
    fun overlapsRange(startSec: Double, endSec: Double): Boolean =
        timelineStartSec < endSec && timelineEndSec > startSec
    fun isValidAgainstSource(sourceDurationSec: Double): Boolean =
        sourceStartSec >= 0.0 && sourceStartSec + sourceDurationSec <= sourceDurationSec + 1e-6 &&
            abs(timelineDurationSec * transform.speed - sourceDurationSec) < 0.002
}
