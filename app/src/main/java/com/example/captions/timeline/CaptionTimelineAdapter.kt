package com.ahstudio.captions.timeline

import com.ahstudio.captions.core.model.CaptionProject

data class HostCaptionEntry(
    val clipId: String,
    val trackId: String,
    val startMicros: Long,
    val endMicros: Long,
    val text: String,
    val languageTag: String,
    val trackZOrder: Int,
)

interface HostTimelineBridge {
    fun replaceTrackEntries(trackId: String, entries: List<HostCaptionEntry>)
    fun removeTrack(trackId: String)
}

class CaptionTimelineAdapter(private val bridge: HostTimelineBridge) {
    fun publish(project: CaptionProject) {
        project.tracks.forEachIndexed { z, track ->
            bridge.replaceTrackEntries(
                track.id,
                track.clipsSorted().map { c ->
                    HostCaptionEntry(c.id, track.id, c.timing.start.micros, c.timing.end.micros,
                        c.displayText, track.languageTag, z)
                },
            )
        }
    }
    fun remove(trackId: String) = bridge.removeTrack(trackId)
}
