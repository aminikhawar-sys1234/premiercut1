package com.example.engine.controller

import android.util.Log
import androidx.media3.common.Player
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Single master-timeline clock for editor preview.
 *
 * Synchronized with MasterPlaybackClock which VSYNC-locks to Choreographer and AudioTrack PTS.
 * Media3/player state is a source renderer only. A player reaching STATE_ENDED is a
 * clip boundary event, never the end of the project timeline. The master clock keeps
 * advancing through video, image, gaps and media-type transitions.
 */
class TimelineSyncManager(
  private val playbackController: PlaybackController,
  private val onTimelinePositionUpdated: (Long) -> Unit,
  private val onClipTransition: (VideoClip?, Long, Boolean) -> Unit,
  private val onPlaybackEnded: () -> Unit
) {
  companion object {
    private const val TAG = "TimelineSyncManager"
  }

  private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  private var syncJob: Job? = null
  private var currentTimeline: Timeline = Timeline()
  private var activeClip: VideoClip? = null

  val masterClock = MasterPlaybackClock(
    scope = scope,
    audioClockProvider = {
      if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper() && playbackController.isPlaying) {
        val clip = activeClip
        if (clip != null && clip.isVideo && !clip.isMuted && clip.volume > 0f) {
          try {
            val exo = playbackController.player
            // Only trust the player's position while it is actually rendering (READY + playing).
            // While buffering/seeking/loading a new clip it reports a stale position which would
            // yank the master clock (and therefore the playhead) backwards/forwards.
            if (exo.playbackState == Player.STATE_READY && exo.isPlaying) {
              val playerPos = exo.currentPosition
              if (playerPos >= 0L) clip.sourceToTimelineMs(playerPos) else null
            } else null
          } catch (_: Throwable) {
            null
          }
        } else null
      } else null
    }
  )

  private val _timelinePositionMs = MutableStateFlow(0L)
  val timelinePositionMs: StateFlow<Long> = _timelinePositionMs.asStateFlow()

  val isPlaying: Boolean get() = masterClock.isPlaying.value

  init {
    scope.launch {
      masterClock.positionMs.collectLatest { pos ->
        if (masterClock.isPlaying.value) {
          val bounded = pos.coerceAtMost(currentTimeline.totalDurationMs)
          publishPosition(bounded)

          val nextClip = findClipAt(bounded)
          if (nextClip?.id != activeClip?.id) {
            activeClip = nextClip
            onClipTransition(nextClip, bounded, true)
          }

          if (currentTimeline.totalDurationMs > 0L && bounded >= currentTimeline.totalDurationMs) {
            finishPlayback()
          }
        }
      }
    }
  }

  fun updateTimeline(timeline: Timeline) {
    currentTimeline = timeline
    val maxBound = maxOf(timeline.totalDurationMs + 10000L, 10000L)
    val bounded = _timelinePositionMs.value.coerceIn(0L, maxBound)
    _timelinePositionMs.value = bounded
    masterClock.seekTo(bounded)
    playbackController.updateTimelinePosition(bounded)
    if (isPlaying) {
      onTimelinePositionUpdated(bounded)
    }
    activeClip = findClipAt(bounded)
  }

  fun setActiveClip(clip: VideoClip?) { activeClip = clip }

  fun setPosition(positionMs: Long) {
    val maxBound = maxOf(currentTimeline.totalDurationMs + 10000L, 10000L)
    val bounded = positionMs.coerceIn(0L, maxBound)
    _timelinePositionMs.value = bounded
    masterClock.seekTo(bounded)
    playbackController.updateTimelinePosition(bounded)
    onTimelinePositionUpdated(bounded)
    activeClip = findClipAt(bounded)
  }

  /** Start the master clock. This is valid even when the current clip is an image. */
  fun startSyncLoop(startPosMs: Long? = null) {
    stopSyncLoop()
    if (currentTimeline.totalDurationMs <= 0L) return
    val pos = startPosMs ?: _timelinePositionMs.value
    _timelinePositionMs.value = pos
    masterClock.play(pos)
  }

  private fun publishPosition(positionMs: Long) {
    val bounded = positionMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    if (_timelinePositionMs.value != bounded) {
      _timelinePositionMs.value = bounded
      playbackController.updateTimelinePosition(bounded)
      onTimelinePositionUpdated(bounded)
    }
  }

  /** Media3 STATE_ENDED is only a source-clip boundary. */
  fun handlePlayerEnded() {
    if (!isPlaying) return
    val position = _timelinePositionMs.value
    val nextClip = findClipAt(position + 1L)
      ?: currentTimeline.videoClips
        .asSequence()
        .filter { it.timelineStartMs > position }
        .minByOrNull { it.timelineStartMs }

    if (nextClip != null) {
      if (activeClip?.id != nextClip.id) {
        activeClip = nextClip
        publishPosition(nextClip.timelineStartMs)
        masterClock.seekTo(nextClip.timelineStartMs)
        onClipTransition(nextClip, nextClip.timelineStartMs, true)
      }
    } else if (position >= currentTimeline.totalDurationMs) {
      finishPlayback()
    }
  }

  private fun finishPlayback() {
    if (!isPlaying) return
    masterClock.pause()
    Log.d(TAG, "Timeline playback completed at ${currentTimeline.totalDurationMs}ms")
    playbackController.pause()
    publishPosition(currentTimeline.totalDurationMs)
    activeClip = findClipAt((currentTimeline.totalDurationMs - 1L).coerceAtLeast(0L))
    onPlaybackEnded()
  }

  fun findClipAt(positionMs: Long): VideoClip? = currentTimeline.videoClips.firstOrNull {
    positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.durationMs
  }

  fun stopSyncLoop() {
    masterClock.pause()
    val finalPos = masterClock.positionMs.value.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    publishPosition(finalPos)
    syncJob?.cancel()
    syncJob = null
  }

  fun release() {
    stopSyncLoop()
    scope.cancel()
  }
}
