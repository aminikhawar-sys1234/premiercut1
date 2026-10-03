package com.example.engine.playback

import android.content.Context
import android.graphics.ColorMatrix
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.SeekParameters
import com.example.domain.model.Timeline
import com.example.domain.model.TrackType
import com.example.domain.model.VideoClip
import com.example.domain.model.timelineToSourceMs
import com.example.engine.composition.ColorFilterGenerator
import com.example.engine.controller.CustomVideoEngineController
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Public playback facade retained for the existing editor UI.
 * All primary playback/seek/surface commands are delegated to one CustomVideoEngineController -> PlaybackController.
 * No independent elapsed-realtime playback clock is used here.
 */
@OptIn(UnstableApi::class)
class VideoPlaybackEngine(
  private val context: Context,
  onTimelinePositionChanged: (Long) -> Unit,
  onPlaybackEnded: () -> Unit,
  private val proxyEngine: ProxyMediaEngine? = null
) {
  companion object { private const val TAG = "VideoPlaybackEngine"; private const val UI_TICK_MS = 16L }

  private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  private var trimPollJob: Job? = null
  private val seekGeneration = AtomicLong(0L)

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
  private val _currentPositionMs = MutableStateFlow(0L)
  val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()
  private val _activeClip = MutableStateFlow<VideoClip?>(null)
  val activeClip: StateFlow<VideoClip?> = _activeClip.asStateFlow()
  private val _playerError = MutableStateFlow<String?>(null)
  val playerError: StateFlow<String?> = _playerError.asStateFlow()
  private val _trimPlaybackPositionMs = MutableStateFlow(0L)
  val trimPlaybackPositionMs: StateFlow<Long> = _trimPlaybackPositionMs.asStateFlow()

  private var currentTimeline = Timeline()
  private var currentPosMs = 0L
  private var isScrubbingMode = false
  private var wasPlayingBeforeScrub = false
  private var isTrimPreviewMode = false
  private var trimPreviewClip: VideoClip? = null
  private var trimRangeStartMs = 0L
  private var trimRangeEndMs = 0L

  val engineController: CustomVideoEngineController = CustomVideoEngineController(
    context = context,
    onTimelinePositionChanged = { pos ->
      currentPosMs = pos
      _currentPositionMs.value = pos
      _activeClip.value = findClipAt(pos)
      syncOverlayPlayers(pos)
      syncAudioTrackPlayers(pos)
      onTimelinePositionChanged(pos)
    },
    onPlaybackEnded = {
      _isPlaying.value = false
      onPlaybackEnded()
    },
    proxyEngine = proxyEngine
  )

  val player: ExoPlayer get() = engineController.player
  val isScrubbing: Boolean get() = isScrubbingMode
  val isTrimPreview: Boolean get() = isTrimPreviewMode

  private val overlayPlayers = ConcurrentHashMap<String, ExoPlayer>()
  private val overlayLoadedUris = ConcurrentHashMap<String, String>()
  private val audioTrackPlayers = ConcurrentHashMap<String, ExoPlayer>()
  private val audioLoadedUris = ConcurrentHashMap<String, String>()

  init {
    player.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
        if (isTrimPreviewMode && isPlaying) startTrimPolling() else trimPollJob?.cancel()
      }
      override fun onPlayerError(error: PlaybackException) {
        _playerError.value = "${error.errorCodeName}: ${error.message}"
        Log.e(TAG, "Preview player error", error)
      }
    })
  }

  fun isPlayableInPlayer(uriString: String?): Boolean = MediaRelinkManager.isRealPlayableMedia(context, uriString)

  fun applyVideoFilter(colorMatrix: ColorMatrix?) {
    // Existing GPU composition layer remains responsible for realtime filter/shader application.
  }

  fun getOverlayPlayer(clipId: String): ExoPlayer? = try { overlayPlayers[clipId] } catch (_: Exception) { null }

  fun syncOverlayPlayers(posMs: Long) {
    if (isTrimPreviewMode) return
    if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return
    try {
      val activeOverlays = currentTimeline.overlayClips.filter { it.isVideo && !it.isHidden && isPlayableInPlayer(it.uri) }
      val activeIds = activeOverlays.map { it.id }.toSet()
      overlayPlayers.keys.toList().filter { it !in activeIds }.forEach { id ->
        overlayPlayers.remove(id)?.let { p -> try { p.stop(); p.clearVideoSurface(); p.release() } catch (_: Exception) {} }
        overlayLoadedUris.remove(id)
      }
      for (overlay in activeOverlays) {
        val effectiveUri = proxyEngine?.getProxyUri(overlay) ?: overlay.uri
        var p = overlayPlayers[overlay.id]
        if (p == null) {
          p = try {
            ExoPlayer.Builder(context.applicationContext, DefaultRenderersFactory(context.applicationContext).setEnableDecoderFallback(true))
              .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(500, 5000, 250, 500).build())
              .setSeekParameters(SeekParameters.CLOSEST_SYNC).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
          } catch (e: Exception) {
            Log.w(TAG, "Overlay player creation failed", e); continue
          }
          overlayPlayers[overlay.id] = p
        }
        if (overlayLoadedUris[overlay.id] != effectiveUri || p.mediaItemCount == 0 || p.playbackState == Player.STATE_IDLE) {
          try {
            p.setMediaItem(MediaItem.fromUri(Uri.parse(effectiveUri)))
            p.prepare()
            overlayLoadedUris[overlay.id] = effectiveUri
          } catch (e: Exception) {
            Log.w(TAG, "Overlay prepare failed", e)
          }
        }
        try {
          p.playbackParameters = androidx.media3.common.PlaybackParameters(overlay.speed.coerceAtLeast(0.01f))
          p.volume = if (overlay.isMuted) 0f else overlay.volume
          val active = posMs >= overlay.timelineStartMs && posMs < overlay.timelineStartMs + overlay.durationMs
          val source = overlay.timelineToSourceMs(posMs)
          if (active && engineController.timelineSyncManager.isPlaying) {
            val playerPos = try { p.currentPosition } catch (_: Exception) { 0L }
            val isPlayWhenReady = try { p.playWhenReady } catch (_: Exception) { false }
            if (!isPlayWhenReady) {
              p.seekTo(source)
              p.playWhenReady = true
            } else if (kotlin.math.abs(playerPos - source) > 350L) {
              p.seekTo(source)
            }
          } else {
            val isPlayWhenReady = try { p.playWhenReady } catch (_: Exception) { false }
            if (isPlayWhenReady) {
              p.playWhenReady = false
            }
            if (!engineController.timelineSyncManager.isPlaying) {
              p.seekTo(source.coerceAtLeast(0L))
            }
          }
        } catch (e: Exception) {
          Log.w(TAG, "Overlay sync error", e)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "syncOverlayPlayers error", e)
    }
  }

  fun syncAudioTrackPlayers(posMs: Long) {
    if (isTrimPreviewMode) return
    if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return
    try {
      val hasSolo = currentTimeline.audioClips.any { it.isSolo }
      val isAudioTrackMuted = currentTimeline.trackSettings[TrackType.AUDIO]?.isMuted == true
      val isAudioTrackHidden = currentTimeline.trackSettings[TrackType.AUDIO]?.isHidden == true
      val activeAudios = if (!isAudioTrackHidden) {
        currentTimeline.audioClips.filter {
          !it.isHidden && !it.isMuted && !isAudioTrackMuted &&
          (!hasSolo || it.isSolo) &&
          isPlayableInPlayer(it.uri)
        }
      } else emptyList()

      val activeIds = activeAudios.map { it.id }.toSet()
      audioTrackPlayers.keys.toList().filter { it !in activeIds }.forEach { id ->
        audioTrackPlayers.remove(id)?.let { p -> try { p.stop(); p.release() } catch (_: Exception) {} }
        audioLoadedUris.remove(id)
      }

      for (audio in activeAudios) {
        val effectiveUri = audio.uri
        var p = audioTrackPlayers[audio.id]
        if (p == null) {
          p = try {
            ExoPlayer.Builder(context.applicationContext, DefaultRenderersFactory(context.applicationContext).setEnableDecoderFallback(true))
              .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(1000, 6000, 400, 800).build())
              .setSeekParameters(SeekParameters.CLOSEST_SYNC).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
          } catch (e: Exception) {
            Log.w(TAG, "Audio player creation failed", e); continue
          }
          audioTrackPlayers[audio.id] = p
        }
        if (audioLoadedUris[audio.id] != effectiveUri || p.mediaItemCount == 0 || p.playbackState == Player.STATE_IDLE) {
          try {
            p.setMediaItem(MediaItem.fromUri(Uri.parse(effectiveUri)))
            p.prepare()
            audioLoadedUris[audio.id] = effectiveUri
          } catch (e: Exception) {
            Log.w(TAG, "Audio player prepare failed", e)
          }
        }
        try {
          p.playbackParameters = androidx.media3.common.PlaybackParameters(audio.speed.coerceAtLeast(0.01f))
          val rel = (posMs - audio.timelineStartMs).coerceAtLeast(0L)
          var calculatedVol = audio.volume.coerceIn(0f, 2f)
          if (audio.fadeInMs > 0L && rel < audio.fadeInMs) {
            calculatedVol *= (rel.toFloat() / audio.fadeInMs.toFloat()).coerceIn(0f, 1f)
          }
          val timeFromEnd = (audio.durationMs - rel).coerceAtLeast(0L)
          if (audio.fadeOutMs > 0L && timeFromEnd < audio.fadeOutMs) {
            calculatedVol *= (timeFromEnd.toFloat() / audio.fadeOutMs.toFloat()).coerceIn(0f, 1f)
          }
          p.volume = if (audio.isMuted) 0f else calculatedVol
          val active = posMs >= audio.timelineStartMs && posMs < audio.timelineStartMs + audio.durationMs
          val source = audio.timelineToSourceMs(posMs)
          if (active && engineController.timelineSyncManager.isPlaying) {
            val playerPos = try { p.currentPosition } catch (_: Exception) { 0L }
            val isPlayWhenReady = try { p.playWhenReady } catch (_: Exception) { false }
            if (!isPlayWhenReady) {
              p.seekTo(source)
              p.playWhenReady = true
            } else if (kotlin.math.abs(playerPos - source) > 350L) {
              p.seekTo(source)
            }
          } else {
            val isPlayWhenReady = try { p.playWhenReady } catch (_: Exception) { false }
            if (isPlayWhenReady) {
              p.playWhenReady = false
            }
            if (!engineController.timelineSyncManager.isPlaying) {
              p.seekTo(source.coerceAtLeast(0L))
            }
          }
        } catch (e: Exception) {
          Log.w(TAG, "Audio track sync error", e)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "syncAudioTrackPlayers error", e)
    }
  }

  fun updateTimeline(timeline: Timeline) {
    currentTimeline = timeline
    engineController.updateTimeline(timeline)
    currentPosMs = currentPosMs.coerceIn(0L, timeline.totalDurationMs.coerceAtLeast(0L))
    _currentPositionMs.value = currentPosMs
    _activeClip.value = findClipAt(currentPosMs)
    applyVideoFilter(ColorFilterGenerator.createCombinedMatrix(timeline.adjustments, timeline.filter, _activeClip.value?.filter))
    syncOverlayPlayers(currentPosMs)
    syncAudioTrackPlayers(currentPosMs)
  }

  fun startScrubbing() {
    isScrubbingMode = true
    wasPlayingBeforeScrub = _isPlaying.value
    engineController.startScrubbing()
  }

  fun scrubTo(timelinePosMs: Long) {
    if (!isScrubbingMode) startScrubbing()
    val bounded = timelinePosMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    currentPosMs = bounded
    _currentPositionMs.value = bounded
    _activeClip.value = findClipAt(bounded)
    engineController.scrubTo(bounded)
    syncOverlayPlayers(bounded)
    syncAudioTrackPlayers(bounded)
  }

  fun stopScrubbing(finalPosMs: Long? = null) {
    val target = finalPosMs ?: currentPosMs
    isScrubbingMode = false
    engineController.stopScrubbing(target)
    _currentPositionMs.value = target
    syncOverlayPlayers(target)
    syncAudioTrackPlayers(target)
    if (wasPlayingBeforeScrub) wasPlayingBeforeScrub = false
  }

  fun seekTo(timelinePosMs: Long) {
    isScrubbingMode = false
    val bounded = timelinePosMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    currentPosMs = bounded
    _currentPositionMs.value = bounded
    _activeClip.value = findClipAt(bounded)
    engineController.seekTo(bounded)
    syncOverlayPlayers(bounded)
    syncAudioTrackPlayers(bounded)
  }

  fun play(startPosMs: Long? = null) {
    if (isTrimPreviewMode) { playTrimPreview(); return }
    _playerError.value = null
    engineController.play(startPosMs)
  }

  fun pause() {
    if (isTrimPreviewMode) { pauseTrimPreview(); return }
    engineController.pause()
    overlayPlayers.values.forEach { try { it.playWhenReady = false; it.pause() } catch (_: Exception) {} }
    audioTrackPlayers.values.forEach { try { it.playWhenReady = false; it.pause() } catch (_: Exception) {} }
    _isPlaying.value = false
  }

  fun togglePlayPause() {
    if (isTrimPreviewMode) { toggleTrimPlayPause(); return }
    engineController.togglePlayPause()
  }

  fun stepFrame(forward: Boolean, fps: Int? = null) {
    pause()
    val effectiveFps = fps ?: (_activeClip.value?.frameRate?.toInt() ?: 30).coerceIn(12, 120)
    val frameMs = (1000L / effectiveFps).coerceAtLeast(1L)
    seekTo(if (forward) currentPosMs + frameMs else currentPosMs - frameMs)
  }

  fun previewTrimRange(clip: VideoClip, startMs: Long, endMs: Long, loop: Boolean = true) {
    isTrimPreviewMode = true
    trimPreviewClip = clip
    trimRangeStartMs = startMs.coerceAtLeast(0L)
    trimRangeEndMs = endMs.coerceAtLeast(trimRangeStartMs + 50L)
    _trimPlaybackPositionMs.value = trimRangeStartMs
    if (!isPlayableInPlayer(clip.uri)) return
    engineController.playbackController.loadTrimPreview(
      Uri.parse(clip.uri), trimRangeStartMs, trimRangeEndMs,
      clip.speed, if (clip.isMuted) 0f else clip.volume, loop
    )
  }

  fun seekTrimPreview(offsetFromStartMs: Long) {
    if (!isTrimPreviewMode) return
    val offset = offsetFromStartMs.coerceIn(0L, trimRangeEndMs - trimRangeStartMs)
    engineController.playbackController.seekTo(offset, resumeAfter = false, exact = true)
    _trimPlaybackPositionMs.value = trimRangeStartMs + offset
  }

  fun seekTrimPreviewToSourceMs(sourceTimeMs: Long) {
    if (!isTrimPreviewMode) return
    val target = sourceTimeMs.coerceIn(trimRangeStartMs, trimRangeEndMs)
    engineController.playbackController.seekTo(target - trimRangeStartMs, resumeAfter = false, exact = true)
    _trimPlaybackPositionMs.value = target
  }

  fun stepTrimFrame(forward: Boolean, fps: Int? = null) {
    if (!isTrimPreviewMode) return
    pauseTrimPreview()
    val effectiveFps = fps ?: (trimPreviewClip?.frameRate?.toInt() ?: 30).coerceIn(12, 120)
    val delta = (1000L / effectiveFps).coerceAtLeast(1L)
    seekTrimPreviewToSourceMs(if (forward) _trimPlaybackPositionMs.value + delta else _trimPlaybackPositionMs.value - delta)
  }

  fun pauseTrimPreview() { if (isTrimPreviewMode) engineController.playbackController.pause() }
  fun playTrimPreview() { if (isTrimPreviewMode) engineController.playbackController.play() }
  fun toggleTrimPlayPause() { if (isTrimPreviewMode) engineController.playbackController.let { if (it.isPlaying) it.pause() else it.play() } }

  fun exitTrimPreview() {
    if (!isTrimPreviewMode) return
    isTrimPreviewMode = false
    trimPreviewClip = null
    trimPollJob?.cancel()
    engineController.playbackController.setRepeatMode(Player.REPEAT_MODE_OFF)
    engineController.seekTo(currentPosMs)
  }

  private fun startTrimPolling() {
    trimPollJob?.cancel()
    trimPollJob = scope.launch {
      while (isActive && isTrimPreviewMode) {
        val p = player.currentPosition
        val source = (trimRangeStartMs + p).coerceIn(trimRangeStartMs, trimRangeEndMs)
        _trimPlaybackPositionMs.value = source
        delay(UI_TICK_MS)
      }
    }
  }

  private fun findClipAt(posMs: Long): VideoClip? = currentTimeline.videoClips.firstOrNull {
    posMs >= it.timelineStartMs && posMs < it.timelineStartMs + it.durationMs
  }

  fun release() {
    trimPollJob?.cancel()
    scope.cancel()
    overlayPlayers.values.forEach { try { it.stop(); it.release() } catch (_: Exception) {} }
    overlayPlayers.clear()
    overlayLoadedUris.clear()
    audioTrackPlayers.values.forEach { try { it.stop(); it.release() } catch (_: Exception) {} }
    audioTrackPlayers.clear()
    audioLoadedUris.clear()
    engineController.release()
  }
}
