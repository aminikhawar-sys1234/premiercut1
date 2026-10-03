package com.ahstudio.audio.master.dsp

import com.ahstudio.audio.master.dsp.dynamics.*
import com.ahstudio.audio.master.dsp.eq.*
import com.ahstudio.audio.master.dsp.spatial.*
import com.ahstudio.audio.master.dsp.utility.*
import com.ahstudio.audio.master.model.AudioDspChainSpec
import com.ahstudio.audio.master.model.AudioDspNodeSpec

object AudioDspRegistry {
    private val creators = mutableMapOf<String, (AudioDspNodeSpec) -> AudioDspProcessor>()
    val knownTypes: Set<String> get() = creators.keys

    fun register(type: String, creator: (AudioDspNodeSpec) -> AudioDspProcessor) { creators[type] = creator }

    init {
        register("eq") { spec ->
            ParametricEqualizer(if (spec.bands.isEmpty()) ParametricEqualizer.defaultBands() else spec.bands)
        }
        register("compressor") { CompressorProcessor() }
        register("limiter") { LimiterProcessor() }
        register("gate") { NoiseGateProcessor() }
        register("expander") { ExpanderProcessor() }
        register("deesser") { DeEsserProcessor() }
        register("reverb") { ReverbProcessor() }
        register("delay") { DelayProcessor() }
        register("echo") { EchoProcessor() }
        register("width") { StereoWidthProcessor() }
        register("dc") { DcOffsetFilter() }
        register("softclip") { SoftClipProcessor() }
    }

    fun create(spec: AudioDspNodeSpec): AudioDspProcessor {
        val p = creators[spec.type]?.invoke(spec)
            ?: throw IllegalArgumentException("Unknown DSP type '${spec.type}'. Known: $knownTypes")
        for ((k, v) in spec.parameters) p.setParam(k, v)
        return p
    }
}

object AudioDspFactory {
    fun create(spec: AudioDspNodeSpec): AudioDspProcessor = AudioDspRegistry.create(spec)
    fun chain(spec: AudioDspChainSpec): AudioDspChain = if (spec.isEmpty) AudioDspChain.EMPTY else AudioDspChain(spec)
}

object AudioDspPreset {
    fun voiceCleanup(): AudioDspChainSpec = AudioDspChainSpec(listOf(
        AudioDspNodeSpec("eq", bands = listOf(
            EqualizerBand(BiquadType.HIGH_PASS, 85f),
            EqualizerBand(BiquadType.PEAKING, 250f, -2.5f, 1.0f),
            EqualizerBand(BiquadType.PEAKING, 3200f, 2.5f, 1.0f))),
        AudioDspNodeSpec("compressor", parameters = mapOf("threshold" to -18f, "ratio" to 3f, "attack" to 8f, "release" to 120f)),
        AudioDspNodeSpec("deesser"),
        AudioDspNodeSpec("limiter", parameters = mapOf("ceiling" to -0.5f)),
    ))
    fun musicBus(): AudioDspChainSpec = AudioDspChainSpec(listOf(
        AudioDspNodeSpec("compressor", parameters = mapOf("threshold" to -14f, "ratio" to 2f, "attack" to 15f, "release" to 200f)),
        AudioDspNodeSpec("limiter", parameters = mapOf("ceiling" to -0.3f)),
    ))
}
