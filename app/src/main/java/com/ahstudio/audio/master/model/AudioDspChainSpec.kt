package com.ahstudio.audio.master.model

import com.ahstudio.audio.master.dsp.eq.EqualizerBand

data class AudioDspNodeSpec(
    val type: String,
    val enabled: Boolean = true,
    val parameters: Map<String, Float> = emptyMap(),
    val bands: List<EqualizerBand> = emptyList(),
)

data class AudioDspChainSpec(val nodes: List<AudioDspNodeSpec> = emptyList()) {
    val isEmpty: Boolean get() = nodes.none { it.enabled }
}

data class AudioDspParameter(val key: String, val value: Float, val minValue: Float, val maxValue: Float, val label: String)
