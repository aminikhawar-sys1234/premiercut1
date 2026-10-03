package com.example.engine.audio

import com.example.ui.components.audio.MusicCatalog
import com.example.ui.components.audio.SoundFxCatalog

data class SoundEffectItem(
  val id: String,
  val title: String,
  val category: String,
  val durationMs: Long,
  val icon: String
)

/**
 * Unified Sound Effects & Music catalog bridging to the master audio library
 * and real AudioWaveformManager.
 */
object SoundEffectsCatalog {
  val effects: List<SoundEffectItem> by lazy {
    SoundFxCatalog.ALL_CLIPS.map {
      SoundEffectItem(
        id = it.id,
        title = it.title,
        category = it.category,
        durationMs = it.durationMs,
        icon = it.icon
      )
    }
  }

  val musicTracks: List<SoundEffectItem> by lazy {
    MusicCatalog.TRACKS.map {
      SoundEffectItem(
        id = it.id,
        title = it.title,
        category = it.genre,
        durationMs = it.durationMs,
        icon = it.icon
      )
    }
  }

  fun generateWaveform(seed: String, count: Int = 30): List<Float> {
    val rich = AudioWaveformManager.generateRichWaveform(seed, 5000L)
    return if (rich.size >= count) {
      rich.take(count)
    } else {
      rich + List(count - rich.size) { 0.25f }
    }
  }
}
