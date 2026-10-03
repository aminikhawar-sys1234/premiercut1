package com.ahstudio.audio.master.model

import java.util.UUID

enum class AudioSourceKind { FILE, URI, RECORDED, DERIVED }

data class AudioSourceModel(
    val id: String,
    val uri: String,
    val kind: AudioSourceKind = AudioSourceKind.FILE,
    val durationSec: Double = 0.0,
    val nativeSampleRate: Int = 48_000,
    val nativeChannels: Int = 2,
    val title: String? = null,
    val derivedFrom: String? = null,
    val derivation: String = "",
    val metadata: AudioMetadata = AudioMetadata(),
)

data class AudioMixSettings(
    val masterVolume: Float = 1f,
    val masterGainDb: Float = 0f,
    val headroomDb: Float = -0.3f,
)

data class MasterAudioProject(
    val id: String = UUID.randomUUID().toString(),
    val sampleRate: Int = 48_000,
    val channels: Int = 2,
    val tracks: List<AudioTrackModel> = emptyList(),
    val sources: Map<String, AudioSourceModel> = emptyMap(),
    val mix: AudioMixSettings = AudioMixSettings(),
    val masterDsp: AudioDspChainSpec = AudioDspChainSpec(),
) {
    fun trackById(id: String): AudioTrackModel? = tracks.firstOrNull { it.id == id }
    fun sourceById(id: String): AudioSourceModel? = sources[id]
    fun clipById(clipId: String): Pair<AudioTrackModel, AudioClipModel>? =
        tracks.asSequence().flatMap { tr -> tr.clips.asSequence().map { tr to it } }
            .firstOrNull { it.second.id == clipId }
    fun totalDurationSec(): Double =
        tracks.maxOfOrNull { t -> t.clips.maxOfOrNull { it.timelineEndSec } ?: 0.0 } ?: 0.0
    fun withTrack(updated: AudioTrackModel) = copy(tracks = tracks.map { if (it.id == updated.id) updated else it })
    fun withClip(trackId: String, updated: AudioClipModel): MasterAudioProject {
        val t = trackById(trackId) ?: return this
        return withTrack(t.copy(clips = t.clips.map { if (it.id == updated.id) updated else it }))
    }
}
