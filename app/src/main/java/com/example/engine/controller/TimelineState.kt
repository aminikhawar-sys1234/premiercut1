package com.example.engine.controller

import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip

/**
 * Single source of truth for the unified playback controller, playhead (CTI),
 * and preview synchronization.
 *
 * Strictly manages ExoPlayer hardware state via this model to ensure zero drift
 * between the playhead (CTI) and preview surface across playback, seeking,
 * and pause cycles.
 */
data class TimelineState(
  val timeline: Timeline = Timeline(),
  val positionMs: Long = 0L,
  val positionUs: Long = 0L,
  val isPlaying: Boolean = false,
  val isScrubbing: Boolean = false,
  val isSeeking: Boolean = false,
  val activeClip: VideoClip? = null,
  val playbackState: EnginePlaybackState = EnginePlaybackState.IDLE,
  val playbackSpeed: Float = 1.0f,
  val volume: Float = 1.0f,
  val isMuted: Boolean = false,
  val loop: Boolean = false,
  val totalDurationMs: Long = 0L
) {
  val playheadMs: Long get() = positionMs
  val playheadUs: Long get() = if (positionUs > 0L) positionUs else positionMs * 1000L
  val isAtEnd: Boolean get() = totalDurationMs > 0L && positionMs >= totalDurationMs

  /** Finds the main video clip intersecting the given position in milliseconds. */
  fun findClipAt(posMs: Long): VideoClip? {
    return timeline.videoClips.firstOrNull {
      posMs >= it.timelineStartMs && posMs < (it.timelineStartMs + it.durationMs)
    }
  }

  /** Resolves the active clip at the current playhead position. */
  fun resolveActiveClip(): VideoClip? = findClipAt(positionMs)
}
