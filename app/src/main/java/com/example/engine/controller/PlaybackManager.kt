package com.example.engine.controller

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters

/**
 * Owns the single Media3/ExoPlayer hardware playback pipeline.
 * It deliberately reuses the player and surface instead of recreating them per Play/Seek.
 */
@OptIn(UnstableApi::class)
class PlaybackManager(
  private val context: Context,
  private val onPlaybackStateChanged: (Int) -> Unit = {},
  private val onIsPlayingChanged: (Boolean) -> Unit = {},
  private val onPlayerError: (PlaybackException) -> Unit = {}
) {
  companion object { private const val TAG = "PlaybackManager" }

  val player: ExoPlayer = ExoPlayer.Builder(
    context.applicationContext,
    DefaultRenderersFactory(context.applicationContext)
      .setEnableDecoderFallback(true)
      .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
      .setAllowedVideoJoiningTimeMs(5000L)
  ).setLoadControl(
    DefaultLoadControl.Builder()
      .setBufferDurationsMs(
        1500,
        8000,
        500,
        1000
      )
      .setPrioritizeTimeOverSizeThresholds(true)
      .build()
  ).setSeekParameters(SeekParameters.CLOSEST_SYNC).build().apply {
    playWhenReady = false
    repeatMode = Player.REPEAT_MODE_OFF
  }

  private var currentLoadedUri: String? = null

  private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

  val isPlaying: Boolean
    get() = try {
      if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) player.isPlaying else false
    } catch (_: Throwable) {
      false
    }

  val currentPosition: Long
    get() = try {
      if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) player.currentPosition else 0L
    } catch (_: Throwable) {
      0L
    }

  val duration: Long
    get() = try {
      if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) player.duration.coerceAtLeast(0L) else 0L
    } catch (_: Throwable) {
      0L
    }

  val bufferedPosition: Long
    get() = try {
      if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) player.bufferedPosition else 0L
    } catch (_: Throwable) {
      0L
    }

  val playbackState: Int
    get() = try {
      if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) player.playbackState else Player.STATE_IDLE
    } catch (_: Throwable) {
      Player.STATE_IDLE
    }

  private val playerListener = object : Player.Listener {
    override fun onPlaybackStateChanged(state: Int) {
      if (state == Player.STATE_BUFFERING) Log.d(TAG, "BUFFERING at ${player.currentPosition}ms")
      if (state == Player.STATE_READY) Log.d(TAG, "READY at ${player.currentPosition}ms")
      if (state == Player.STATE_ENDED) Log.d(TAG, "ENDED")
      onPlaybackStateChanged(state)
    }
    override fun onIsPlayingChanged(isPlaying: Boolean) {
      Log.d(TAG, "isPlaying=$isPlaying pos=${player.currentPosition}ms")
      onIsPlayingChanged(isPlaying)
    }
    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
      Log.d(TAG, "videoSize=${videoSize.width}x${videoSize.height}")
    }
    override fun onRenderedFirstFrame() { Log.d(TAG, "first video frame rendered") }
    override fun onPlayerError(error: PlaybackException) {
      Log.e(TAG, "player error [${error.errorCodeName}] ${error.message}", error)
      onPlayerError(error)
    }
  }

  init { player.addListener(playerListener) }

  fun loadMedia(uri: Uri, startPosMs: Long = 0L, autoPlay: Boolean = false) {
    try {
      val uriString = uri.toString()
      if (uriString == currentLoadedUri && playbackState != Player.STATE_IDLE) {
        seekTo(startPosMs)
        if (autoPlay) play()
        return
      }
      currentLoadedUri = uriString
      val normalizedUri = normalizeUri(uri)
      player.setMediaItem(MediaItem.fromUri(normalizedUri), startPosMs.coerceAtLeast(0L))
      player.prepare()
      player.playWhenReady = autoPlay
      Log.d(TAG, "prepared media=$uriString start=${startPosMs}ms autoPlay=$autoPlay")
    } catch (e: Exception) {
      Log.e(TAG, "loadMedia failed", e)
    }
  }

  fun play() {
    try {
      if (playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
      player.play()
    } catch (e: Exception) {
      Log.e(TAG, "play failed", e)
    }
  }

  fun pause() {
    try {
      player.pause()
    } catch (e: Exception) {
      Log.e(TAG, "pause failed", e)
    }
  }

  /** Frame-accurate seek used while scrubbing. */
  fun seekTo(positionMs: Long) {
    try {
      player.setSeekParameters(SeekParameters.EXACT)
      player.seekTo(positionMs.coerceAtLeast(0L))
    } catch (e: Exception) {
      Log.e(TAG, "seekTo failed", e)
    }
  }

  /** Fast seek using closest keyframe sync for smooth, responsive scrubbing. */
  fun seekToFast(positionMs: Long) {
    try {
      player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
      player.seekTo(positionMs.coerceAtLeast(0L))
    } catch (e: Exception) {
      Log.e(TAG, "seekToFast failed", e)
    }
  }

  /** Exact final seek used after scrub/reposition requests. */
  fun seekToExact(positionMs: Long) {
    try {
      player.setSeekParameters(SeekParameters.EXACT)
      player.seekTo(positionMs.coerceAtLeast(0L))
    } catch (e: Exception) {
      Log.e(TAG, "seekToExact failed", e)
    }
  }

  fun setVolume(volume: Float) {
    try {
      player.volume = volume.coerceIn(0f, 2f)
    } catch (_: Exception) {}
  }

  fun setMuted(isMuted: Boolean) {
    try {
      player.volume = if (isMuted) 0f else 1f
    } catch (_: Exception) {}
  }

  fun setPlaybackSpeed(speed: Float) {
    try {
      val safe = speed.coerceIn(0.1f, 10f)
      if (player.playbackParameters.speed != safe) player.playbackParameters = PlaybackParameters(safe)
    } catch (_: Exception) {}
  }

  fun setSurface(surface: Surface?) {
    try {
      if (surface?.isValid == true) player.setVideoSurface(surface) else player.clearVideoSurface()
    } catch (e: Exception) {
      Log.w(TAG, "setSurface failed", e)
    }
  }

  fun clearSurface() {
    try {
      player.clearVideoSurface()
    } catch (_: Exception) {}
  }

  fun addListener(listener: Player.Listener) {
    try {
      player.addListener(listener)
    } catch (_: Exception) {}
  }

  fun removeListener(listener: Player.Listener) {
    try {
      player.removeListener(listener)
    } catch (_: Exception) {}
  }

  fun release() {
    try {
      player.removeListener(playerListener)
      player.stop()
      player.clearVideoSurface()
      player.release()
    } catch (_: Exception) { }
    currentLoadedUri = null
  }

  private fun normalizeUri(uri: Uri): Uri = when {
    uri.scheme == "asset" -> {
      var path = uri.path ?: ""
      if (path.startsWith("/")) path = path.substring(1)
      if (path.isEmpty()) path = uri.authority ?: ""
      Uri.parse("asset:///$path")
    }
    uri.scheme == null || uri.scheme == "file" -> {
      val path = uri.path ?: uri.toString()
      val file = java.io.File(path)
      if (file.exists()) Uri.fromFile(file) else uri
    }
    else -> uri
  }
}
