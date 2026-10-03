package com.example.engine.audio.phase3

/** Sorted interval index for fast timeline-position audio queries. Rebuild only after timeline edits. */
class AudioTimelineIndex(tracks: List<TimelineAudioTrack> = emptyList()) {
  private var entries: List<Entry> = emptyList()
  init { rebuild(tracks) }

  fun rebuild(tracks: List<TimelineAudioTrack>) {
    entries = tracks.asSequence()
      .filter { it.enabled }
      .flatMap { track -> track.clips.asSequence().map { Entry(it.timelineStartMs, it.timelineEndMs, it) } }
      .sortedWith(compareBy<Entry> { it.start }.thenBy { it.end }.thenBy { it.clip.id })
      .toList()
  }

  fun activeAt(positionMs: Long): List<TimelineAudioClip> {
    if (entries.isEmpty()) return emptyList()
    var lo = 0
    var hi = entries.size
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (entries[mid].start <= positionMs) lo = mid + 1 else hi = mid
    }
    val result = ArrayList<TimelineAudioClip>()
    var i = lo - 1
    while (i >= 0 && entries[i].start <= positionMs) {
      val e = entries[i]
      if (positionMs < e.end && e.clip.enabled) result += e.clip
      i--
    }
    return result.sortedBy { it.trackId }
  }

  private data class Entry(val start: Long, val end: Long, val clip: TimelineAudioClip)
}
