package com.ahstudio.audio.master

import com.ahstudio.audio.master.dsp.AudioDspRegistry

data class AudioMasterEngineConfig(
    val sampleRate: Int = 48_000,
    val channels: Int = 2,
    val maxBlockFrames: Int = 4096,
    val memoryCacheBytes: Long = 96L * 1024 * 1024,
    val waveformBucketsPerSecond: Int = 100,
    val undoDepth: Int = 100,
)

enum class AudioEngineState { UNINITIALIZED, READY, PLAYING, PAUSED, RECORDING, EXPORTING, RELEASED }

data class AudioEngineCapabilities(
    val dspModules: Set<String>,
    val spectralNoiseReduction: Boolean,
    val pitchShift: Boolean,
    val timeStretch: Boolean,
    val aacExport: Boolean,
    val maxTracks: Int,
) { companion object { fun of() = AudioEngineCapabilities(AudioDspRegistry.knownTypes, true, true, true, true, Int.MAX_VALUE) } }

object AudioEngineRegistry {
    @Volatile private var engine: AhStudioAudioMasterEngine? = null
    fun install(e: AhStudioAudioMasterEngine) { engine = e }
    fun get(): AhStudioAudioMasterEngine = engine ?: throw AudioEngineException(AudioEngineError.NOT_INITIALIZED, "Engine not initialized — create AhStudioAudioMasterEngine first")
    fun getOrNull(): AhStudioAudioMasterEngine? = engine
    fun clear() { engine = null }
}
