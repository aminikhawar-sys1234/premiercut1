package com.ahstudio.audio.master.model

data class AudioRoutingModel(val targetBusId: String = MASTER) {
    companion object { const val MASTER = "master" }
}

data class AudioTrackSettings(
    val name: String = "Audio Track",
    val volume: Float = 1f,
    val gainDb: Float = 0f,
    val pan: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val routing: AudioRoutingModel = AudioRoutingModel(),
)

data class AudioTrackModel(
    val id: String,
    val index: Int,
    val settings: AudioTrackSettings = AudioTrackSettings(),
    val clips: List<AudioClipModel> = emptyList(),
    val trackDsp: AudioDspChainSpec = AudioDspChainSpec(),
    val automation: List<AudioAutomationModel> = emptyList(),
)
