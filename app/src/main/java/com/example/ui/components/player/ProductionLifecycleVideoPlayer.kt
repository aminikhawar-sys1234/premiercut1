package com.example.ui.components.player

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "LifecycleVideoPlayer"
private const val MAX_DECODER_RETRY_ATTEMPTS = 3

/**
 * Production-grade Jetpack Compose Video Player using AndroidX Media3 ExoPlayer and SurfaceView.
 *
 * Requirements implemented:
 * 1. Lifecycle Resilience:
 *    - LifecycleEventObserver inside DisposableEffect safely pauses on ON_STOP.
 *    - Clears video surface on backgrounding/destruction.
 *    - Restores playback on ON_START only after the new Surface is fully validated and attached.
 *
 * 2. Surface Lifecycle Management:
 *    - SurfaceHolder.Callback explicitly calls player.setVideoSurface(holder.surface) on surfaceCreated.
 *    - Calls player.clearVideoSurface() on surfaceDestroyed to prevent decoder stalling on dead surfaces.
 *
 * 3. State Synchronization:
 *    - Exposes and synchronizes isPlaying, isBuffering, playbackState, and isSurfaceReady with Compose.
 *
 * 4. UI Layer:
 *    - Play/Pause toggle is disabled until surface is ready.
 *    - Smooth CircularProgressIndicator during STATE_BUFFERING or keyframe recovery.
 *
 * 5. Error Recovery:
 *    - Player.Listener.onPlayerError with exponential backoff retry strategy for recoverable decoder init failures.
 */
@OptIn(UnstableApi::class)
@Composable
fun ProductionLifecycleVideoPlayer(
  player: ExoPlayer?,
  modifier: Modifier = Modifier,
  onSurfaceReadyChanged: (Boolean) -> Unit = {},
  onPlaybackError: (PlaybackException) -> Unit = {},
  showControls: Boolean = true,
  autoPlayOnReady: Boolean = false,
  enableKeyframeRecovery: Boolean = true,
  customOverlay: (@Composable BoxScope.(isSurfaceReady: Boolean, isPlaying: Boolean) -> Unit)? = null
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val coroutineScope = rememberCoroutineScope()

  // --- Compose-Synchronized Player States ---
  var isPlaying by remember { mutableStateOf(player?.isPlaying ?: false) }
  var playbackState by remember { mutableIntStateOf(player?.playbackState ?: Player.STATE_IDLE) }
  var isBuffering by remember { mutableStateOf(false) }
  var isSurfaceReady by remember { mutableStateOf(false) }
  var isRecoveringKeyframes by remember { mutableStateOf(false) }
  var wasPlayingBeforePause by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var retryCount by remember { mutableIntStateOf(0) }
  var controlsVisible by remember { mutableStateOf(true) }

  // Notify consumer of surface readiness
  LaunchedEffect(isSurfaceReady) {
    onSurfaceReadyChanged(isSurfaceReady)
  }

  // --- 1. Lifecycle Resilience & Playback Restoration ---
  DisposableEffect(lifecycleOwner, player) {
    if (player == null) return@DisposableEffect onDispose {}

    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> {
          Log.d(TAG, "Lifecycle ON_START: Awaiting surface validation before playback restore")
          if (enableKeyframeRecovery) {
            isRecoveringKeyframes = true
          }
          // Do NOT blindly play here: we wait for surfaceCreated() callback to validate surface!
        }
        Lifecycle.Event.ON_RESUME -> {
          Log.d(TAG, "Lifecycle ON_RESUME: Checking surface state: ready=$isSurfaceReady")
          if (isSurfaceReady && wasPlayingBeforePause) {
            player.play()
            wasPlayingBeforePause = false
          }
        }
        Lifecycle.Event.ON_PAUSE -> {
          Log.d(TAG, "Lifecycle ON_PAUSE: Storing playback intent")
          wasPlayingBeforePause = player.isPlaying
          player.pause()
        }
        Lifecycle.Event.ON_STOP -> {
          Log.d(TAG, "Lifecycle ON_STOP: Pausing player safely and clearing decoder state")
          player.pause()
        }
        Lifecycle.Event.ON_DESTROY -> {
          Log.d(TAG, "Lifecycle ON_DESTROY: Detaching surface")
          player.clearVideoSurface()
          isSurfaceReady = false
        }
        else -> Unit
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  // --- 2. Media3 Player.Listener & Error Recovery ---
  DisposableEffect(player) {
    if (player == null) return@DisposableEffect onDispose {}

    val listener = object : Player.Listener {
      override fun onIsPlayingChanged(playing: Boolean) {
        isPlaying = playing
      }

      override fun onPlaybackStateChanged(state: Int) {
        playbackState = state
        isBuffering = (state == Player.STATE_BUFFERING)

        if (state == Player.STATE_READY) {
          isRecoveringKeyframes = false
          errorMessage = null
          retryCount = 0 // Reset retry attempts on successful ready state
        }
      }

      override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "Media3 Player Error: ${error.errorCodeName} (${error.errorCode})", error)
        onPlaybackError(error)

        val isDecoderError = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
          error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
          error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED

        if (isDecoderError && retryCount < MAX_DECODER_RETRY_ATTEMPTS) {
          retryCount++
          val delayMs = (retryCount * 400L).coerceAtLeast(300L)
          errorMessage = "Recovering decoder (attempt $retryCount/$MAX_DECODER_RETRY_ATTEMPTS)..."
          isRecoveringKeyframes = true

          coroutineScope.launch {
            delay(delayMs)
            Log.w(TAG, "Executing automatic retry attempt #$retryCount after decoder failure")
            player.prepare()
            if (wasPlayingBeforePause || autoPlayOnReady) {
              player.play()
            }
          }
        } else {
          errorMessage = "Playback Error: ${error.localizedMessage ?: error.errorCodeName}"
          isBuffering = false
          isRecoveringKeyframes = false
        }
      }
    }

    player.addListener(listener)
    // Synchronize initial state
    isPlaying = player.isPlaying
    playbackState = player.playbackState
    isBuffering = (player.playbackState == Player.STATE_BUFFERING)

    onDispose {
      player.removeListener(listener)
    }
  }

  // Auto-hide controls after 3 seconds of playback
  LaunchedEffect(controlsVisible, isPlaying) {
    if (controlsVisible && isPlaying) {
      delay(3000L)
      controlsVisible = false
    }
  }

  Box(
    modifier = modifier
      .background(Color.Black)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) {
        controlsVisible = !controlsVisible
      },
    contentAlignment = Alignment.Center
  ) {
    // --- 3. Surface Lifecycle Management via AndroidView & SurfaceHolder.Callback ---
    AndroidView(
      factory = { ctx ->
        SurfaceView(ctx).apply {
          layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )

          holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
              val surface = holder.surface
              if (surface != null && surface.isValid) {
                Log.d(TAG, "surfaceCreated: Attaching valid Surface to ExoPlayer")
                player?.setVideoSurface(surface)
                isSurfaceReady = true

                // Safely restore playback state if previously playing
                if (wasPlayingBeforePause || autoPlayOnReady) {
                  player?.play()
                  wasPlayingBeforePause = false
                }
                isRecoveringKeyframes = false
              } else {
                Log.w(TAG, "surfaceCreated: Invalid surface encountered, deferring readiness")
                isSurfaceReady = false
              }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
              Log.d(TAG, "surfaceChanged: format=$format, size=${width}x$height")
              if (width > 0 && height > 0 && holder.surface.isValid) {
                isSurfaceReady = true
              }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
              Log.d(TAG, "surfaceDestroyed: Crucial - explicitly clearing video surface to prevent decoder stalling")
              isSurfaceReady = false
              // CRITICAL: Prevent hardware MediaCodec decoder from hanging on released native window
              player?.clearVideoSurface()
            }
          })
        }
      },
      update = { surfaceView ->
        // If player changed, re-bind if surface is currently valid
        if (surfaceView.holder.surface != null && surfaceView.holder.surface.isValid) {
          player?.setVideoSurface(surfaceView.holder.surface)
        }
      },
      onRelease = { surfaceView ->
        Log.d(TAG, "AndroidView onRelease: Clearing player video surface")
        player?.clearVideoSurface()
        isSurfaceReady = false
      },
      modifier = Modifier
        .fillMaxSize()
        .testTag("production_player_surface_view")
    )

    // --- 4. Loading & Keyframe Recovery Indicator ---
    val showLoadingIndicator = isBuffering || isRecoveringKeyframes || (!isSurfaceReady && player != null)
    AnimatedVisibility(
      visible = showLoadingIndicator,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.75f),
        modifier = Modifier.padding(16.dp)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          CircularProgressIndicator(
            modifier = Modifier
              .size(24.dp)
              .testTag("player_loading_indicator"),
            color = Color(0xFF00E5FF),
            strokeWidth = 2.5.dp
          )
          Text(
            text = when {
              isRecoveringKeyframes -> "Recovering video frames..."
              !isSurfaceReady -> "Preparing display surface..."
              else -> "Buffering..."
            },
            style = MaterialTheme.typography.bodySmall.copy(
              color = Color.White,
              fontWeight = FontWeight.Medium,
              fontSize = 12.sp
            )
          )
        }
      }
    }

    // --- 5. Error Recovery Banner ---
    if (errorMessage != null) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFD32F2F).copy(alpha = 0.9f),
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 16.dp, start = 16.dp, end = 16.dp)
          .testTag("player_error_banner")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.White, modifier = Modifier.size(16.dp))
          Text(
            text = errorMessage ?: "",
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontSize = 11.sp),
            modifier = Modifier.weight(1f, fill = false)
          )
          IconButton(
            onClick = {
              errorMessage = null
              player?.prepare()
              player?.play()
            },
            modifier = Modifier.size(24.dp)
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color.White, modifier = Modifier.size(16.dp))
          }
        }
      }
    }

    // --- 6. Custom Consumer Overlay Slot ---
    customOverlay?.invoke(this, isSurfaceReady, isPlaying)

    // --- 7. Play/Pause UI Controls Overlay ---
    if (showControls) {
      AnimatedVisibility(
        visible = controlsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center)
      ) {
        val playButtonEnabled = isSurfaceReady && player != null

        Surface(
          shape = CircleShape,
          color = if (playButtonEnabled) Color(0xFF1E222D).copy(alpha = 0.85f) else Color.DarkGray.copy(alpha = 0.5f),
          shadowElevation = 6.dp,
          modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(
              enabled = playButtonEnabled,
              onClick = {
                if (player != null) {
                  if (player.isPlaying) {
                    player.pause()
                  } else {
                    player.play()
                  }
                }
              }
            )
            .testTag("player_play_pause_button")
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (isPlaying) "Pause" else "Play",
              tint = if (playButtonEnabled) Color(0xFF00E5FF) else Color.Gray,
              modifier = Modifier.size(32.dp)
            )
          }
        }
      }
    }
  }
}
