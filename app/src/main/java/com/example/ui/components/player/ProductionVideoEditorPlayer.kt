package com.example.ui.components.player

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

/**
 * Production-ready Video Player Composable for Android Video Editing applications.
 *
 * Key Capabilities:
 * 1. Safe ExoPlayer lifecycle management (initialized in remember/DisposableEffect, properly released on dispose).
 * 2. Robust URI/FilePath handling (supports content://, file://, raw local paths, and http/https).
 * 3. Media3 PlayerView integration via AndroidView with useController = false and custom responsive rendering.
 * 4. Bidirectional Play/Pause state synchronization between UI controls and ExoPlayer.playWhenReady.
 * 5. High-precision real-time position emission (~16ms loop) for smooth 60 FPS timeline playhead sync.
 * 6. Buffering, Ready, and Error state management with non-blocking feedback to prevent blank/black screens.
 */
@OptIn(UnstableApi::class)
@Composable
fun ProductionVideoEditorPlayer(
  mediaUri: String?,
  isPlaying: Boolean,
  onPlayPauseToggle: (Boolean) -> Unit,
  onPositionUpdate: (currentPositionMs: Long, durationMs: Long) -> Unit,
  modifier: Modifier = Modifier,
  seekToPositionMs: Long? = null,
  isMuted: Boolean = false,
  showControls: Boolean = true,
  onPlayerReady: (durationMs: Long) -> Unit = {},
  onPlayerError: (String) -> Unit = {},
  customOverlay: (@Composable BoxScope.(isBuffering: Boolean, isPlaying: Boolean) -> Unit)? = null
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  // State trackers
  var playerState by remember { mutableIntStateOf(Player.STATE_IDLE) }
  var isBuffering by remember { mutableStateOf(false) }
  var isFirstFrameRendered by remember { mutableStateOf(false) }
  var durationMs by remember { mutableLongStateOf(0L) }
  var playbackError by remember { mutableStateOf<String?>(null) }

  // Current values references for callbacks
  val currentOnPositionUpdate by rememberUpdatedState(onPositionUpdate)
  val currentOnPlayerReady by rememberUpdatedState(onPlayerReady)
  val currentOnPlayerError by rememberUpdatedState(onPlayerError)
  val currentOnPlayPauseToggle by rememberUpdatedState(onPlayPauseToggle)

  // 1. Initialize ExoPlayer safely with low-latency load control
  val exoPlayer = remember(context) {
    val renderersFactory = DefaultRenderersFactory(context.applicationContext)
      .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
      .setEnableDecoderFallback(true)

    val loadControl = DefaultLoadControl.Builder()
      .setBufferDurationsMs(
        500,  // minBufferMs (fast start)
        5000, // maxBufferMs
        250,  // bufferForPlaybackMs
        500   // bufferForPlaybackAfterRebufferMs
      )
      .setPrioritizeTimeOverSizeThresholds(true)
      .build()

    ExoPlayer.Builder(context.applicationContext, renderersFactory)
      .setLoadControl(loadControl)
      .setSeekParameters(SeekParameters.CLOSEST_SYNC)
      .build().apply {
        playWhenReady = false
        repeatMode = Player.REPEAT_MODE_OFF
        videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
      }
  }

  // 2. Lifecycle observation and Player.Listener setup
  DisposableEffect(lifecycleOwner, exoPlayer) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(state: Int) {
        playerState = state
        isBuffering = (state == Player.STATE_BUFFERING)

        if (state == Player.STATE_READY) {
          val dur = exoPlayer.duration.coerceAtLeast(0L)
          durationMs = dur
          currentOnPlayerReady(dur)
          playbackError = null
        } else if (state == Player.STATE_ENDED) {
          currentOnPlayPauseToggle(false)
        }
      }

      override fun onIsPlayingChanged(playing: Boolean) {
        // Sync external state if player paused itself (e.g. audio focus loss or stream end)
        if (playing != isPlaying && !isBuffering) {
          currentOnPlayPauseToggle(playing)
        }
      }

      override fun onRenderedFirstFrame() {
        isFirstFrameRendered = true
        isBuffering = false
      }

      override fun onPlayerError(error: PlaybackException) {
        val msg = error.localizedMessage ?: "Playback Error (${error.errorCodeName})"
        playbackError = msg
        isBuffering = false
        currentOnPlayerError(msg)
      }
    }

    exoPlayer.addListener(listener)

    val lifecycleObserver = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_PAUSE -> {
          if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            currentOnPlayPauseToggle(false)
          }
        }
        Lifecycle.Event.ON_STOP -> {
          exoPlayer.playWhenReady = false
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
      exoPlayer.removeListener(listener)
      exoPlayer.stop()
      exoPlayer.release()
    }
  }

  // 3. Prepare MediaItem when mediaUri changes
  LaunchedEffect(mediaUri, exoPlayer) {
    if (mediaUri.isNullOrBlank()) {
      exoPlayer.clearMediaItems()
      isFirstFrameRendered = false
      return@LaunchedEffect
    }

    try {
      val uri = when {
        mediaUri.startsWith("content://") || mediaUri.startsWith("file://") || mediaUri.startsWith("http://") || mediaUri.startsWith("https://") -> {
          Uri.parse(mediaUri)
        }
        else -> {
          Uri.fromFile(File(mediaUri))
        }
      }

      val mediaItem = MediaItem.fromUri(uri)
      isFirstFrameRendered = false
      playbackError = null
      isBuffering = true

      exoPlayer.setMediaItem(mediaItem)
      exoPlayer.prepare()
    } catch (e: Exception) {
      val err = "Failed to load media URI: ${e.message}"
      playbackError = err
      currentOnPlayerError(err)
    }
  }

  // 4. Bidirectional Play/Pause synchronization
  LaunchedEffect(isPlaying, exoPlayer) {
    if (exoPlayer.playWhenReady != isPlaying) {
      if (isPlaying) {
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
          exoPlayer.seekTo(0L)
        }
        exoPlayer.play()
      } else {
        exoPlayer.pause()
      }
    }
  }

  // 5. Volume/Mute synchronization
  LaunchedEffect(isMuted, exoPlayer) {
    exoPlayer.volume = if (isMuted) 0f else 1f
  }

  // 6. External seek synchronization
  LaunchedEffect(seekToPositionMs, exoPlayer) {
    if (seekToPositionMs != null && seekToPositionMs >= 0L) {
      exoPlayer.seekTo(seekToPositionMs)
    }
  }

  // 7. Real-Time Position Emitting Coroutine Loop (~16ms for 60 FPS timeline sync)
  LaunchedEffect(exoPlayer) {
    while (isActive) {
      if (exoPlayer.playbackState != Player.STATE_IDLE) {
        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val currentDur = exoPlayer.duration.coerceAtLeast(0L)
        currentOnPositionUpdate(currentPos, currentDur)
      }
      delay(16L) // ~60fps poll rate
    }
  }

  // 8. Player UI Layout with AndroidView
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .testTag("production_video_editor_player"),
    contentAlignment = Alignment.Center
  ) {
    AndroidView(
      factory = { ctx ->
        PlayerView(ctx).apply {
          useController = false
          layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
          setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) // Custom compose indicator used
          setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
          this.player = exoPlayer
        }
      },
      update = { playerView ->
        if (playerView.player != exoPlayer) {
          playerView.player = exoPlayer
        }
      },
      modifier = Modifier.fillMaxSize()
    )

    // Buffering & Loading Indicator (prevents dark frozen screen)
    AnimatedVisibility(
      visible = isBuffering && !isFirstFrameRendered && playbackError == null,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(
          color = MaterialTheme.colorScheme.primary,
          strokeWidth = 3.dp,
          modifier = Modifier.size(44.dp)
        )
      }
    }

    // Playback Error Overlay
    playbackError?.let { err ->
      Surface(
        color = Color(0xCC1A0505),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .padding(16.dp)
          .align(Alignment.Center)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = err,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }

    // Custom overlay slot if provided
    customOverlay?.invoke(this, isBuffering, isPlaying)

    // Built-in Quick Play/Pause Center Touch Trigger
    if (showControls && playbackError == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
          ) {
            currentOnPlayPauseToggle(!isPlaying)
          },
        contentAlignment = Alignment.Center
      ) {
        AnimatedVisibility(
          visible = !isPlaying && isFirstFrameRendered,
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          IconButton(
            onClick = { currentOnPlayPauseToggle(true) },
            colors = IconButtonDefaults.iconButtonColors(
              containerColor = Color.Black.copy(alpha = 0.6f),
              contentColor = Color.White
            ),
            modifier = Modifier
              .size(64.dp)
              .clip(CircleShape)
              .testTag("preview_center_play_btn")
          ) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = "Play Preview",
              modifier = Modifier.size(36.dp)
            )
          }
        }
      }
    }
  }
}
