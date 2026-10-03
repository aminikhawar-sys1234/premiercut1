package com.example.engine.controller

import android.graphics.Bitmap

/** Strict lifecycle states for the professional preview playback state machine. */
enum class EnginePlaybackState {
  IDLE,
  PREPARING,
  READY,
  PLAYING,
  PAUSED,
  SEEKING,
  BUFFERING,
  COMPLETED,
  ERROR,
  RELEASED
}

enum class DecoderState {
  UNINITIALIZED,
  HARDWARE_ACCELERATED,
  SOFTWARE_FALLBACK,
  ERROR
}

enum class RenderingState {
  IDLE,
  RENDERING,
  STABLE_FRAME,
  DEGRADED
}

data class VideoEngineState(
  val playbackState: EnginePlaybackState = EnginePlaybackState.IDLE,
  val currentPosition: Long = 0L,
  val duration: Long = 0L,
  val bufferedPosition: Long = 0L,
  val isPlaying: Boolean = false,
  val isReady: Boolean = false,
  val currentFrame: Bitmap? = null,
  val surfaceAvailable: Boolean = false,
  val decoderState: DecoderState = DecoderState.UNINITIALIZED,
  val renderingState: RenderingState = RenderingState.IDLE,
  val error: String? = null
)
