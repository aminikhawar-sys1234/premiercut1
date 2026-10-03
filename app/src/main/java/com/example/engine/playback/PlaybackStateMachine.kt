package com.example.engine.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PreviewPlaybackState { IDLE, LOADING, READY, PLAYING, PAUSED, SEEKING, BUFFERING, ERROR, STOPPED }

/** One authoritative state machine for preview playback. */
class PlaybackStateMachine {
  private val _state = MutableStateFlow(PreviewPlaybackState.IDLE)
  val state: StateFlow<PreviewPlaybackState> = _state.asStateFlow()

  @Synchronized
  fun transition(next: PreviewPlaybackState): Boolean {
    val current = _state.value
    val allowed = when (current) {
      PreviewPlaybackState.IDLE -> next in setOf(PreviewPlaybackState.LOADING, PreviewPlaybackState.READY, PreviewPlaybackState.ERROR)
      PreviewPlaybackState.LOADING -> next in setOf(PreviewPlaybackState.READY, PreviewPlaybackState.PLAYING, PreviewPlaybackState.ERROR, PreviewPlaybackState.STOPPED)
      PreviewPlaybackState.READY -> next in setOf(PreviewPlaybackState.PLAYING, PreviewPlaybackState.SEEKING, PreviewPlaybackState.STOPPED, PreviewPlaybackState.ERROR)
      PreviewPlaybackState.PLAYING -> next in setOf(PreviewPlaybackState.PAUSED, PreviewPlaybackState.SEEKING, PreviewPlaybackState.BUFFERING, PreviewPlaybackState.STOPPED, PreviewPlaybackState.ERROR)
      PreviewPlaybackState.PAUSED -> next in setOf(PreviewPlaybackState.PLAYING, PreviewPlaybackState.SEEKING, PreviewPlaybackState.STOPPED, PreviewPlaybackState.ERROR)
      PreviewPlaybackState.SEEKING -> next in setOf(PreviewPlaybackState.READY, PreviewPlaybackState.PAUSED, PreviewPlaybackState.PLAYING, PreviewPlaybackState.ERROR, PreviewPlaybackState.STOPPED)
      PreviewPlaybackState.BUFFERING -> next in setOf(PreviewPlaybackState.PLAYING, PreviewPlaybackState.PAUSED, PreviewPlaybackState.SEEKING, PreviewPlaybackState.ERROR, PreviewPlaybackState.STOPPED)
      PreviewPlaybackState.ERROR -> next in setOf(PreviewPlaybackState.LOADING, PreviewPlaybackState.READY, PreviewPlaybackState.STOPPED, PreviewPlaybackState.ERROR)
      PreviewPlaybackState.STOPPED -> next in setOf(PreviewPlaybackState.LOADING, PreviewPlaybackState.READY, PreviewPlaybackState.PLAYING, PreviewPlaybackState.IDLE)
    }
    if (!allowed) return false
    _state.value = next
    return true
  }

  @Synchronized fun reset() { _state.value = PreviewPlaybackState.IDLE }
}
