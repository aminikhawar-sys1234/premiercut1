package com.example.ui.components.trim

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.domain.model.VideoClip
import com.example.engine.SelectedTrackElement
import com.example.engine.media.MediaRelinkManager
import com.example.ui.StudioViewModel
import com.example.ui.theme.*

@OptIn(UnstableApi::class)
@Composable
fun VideoTrimmingToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier,
  onDismiss: () -> Unit = { viewModel.setActiveToolbarTab(null) }
) {
  val context = LocalContext.current
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()
  val isPlaying by viewModel.playbackEngine.isPlaying.collectAsState()
  val trimPlayheadPos by viewModel.trimPlaybackPosition.collectAsState()

  // Determine currently active video clip for trimming
  val videoClips = timeline.videoClips
  val selectedClipId = when (val el = selectedElement) {
    is SelectedTrackElement.Video -> el.clipId
    is SelectedTrackElement.Overlay -> el.clipId
    else -> null
  }

  var activeClipId by remember(selectedClipId, videoClips) {
    mutableStateOf(
      selectedClipId ?: videoClips.firstOrNull()?.id.orEmpty()
    )
  }

  val activeClip = remember(activeClipId, videoClips, timeline.overlayClips) {
    videoClips.find { it.id == activeClipId }
      ?: timeline.overlayClips.find { it.id == activeClipId }
      ?: videoClips.firstOrNull()
  }

  if (activeClip == null) {
    Box(
      modifier = modifier
        .fillMaxWidth()
        .background(StudioSurface)
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.ContentCut, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text("No video clips available to trim.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onDismiss) {
          Text("Close")
        }
      }
    }
    return
  }

  val totalDurationMs = activeClip.totalMediaDurationMs.coerceAtLeast(1000L)
  var trimStartMs by remember(activeClip.id, activeClip.sourceStartMs) {
    mutableLongStateOf(activeClip.sourceStartMs)
  }
  var trimEndMs by remember(activeClip.id, activeClip.sourceEndMs) {
    mutableLongStateOf(activeClip.sourceEndMs.coerceAtMost(totalDurationMs))
  }
  var isLoopingEnabled by remember { mutableStateOf(true) }

  // Sync Media3 ExoPlayer clipping preview
  LaunchedEffect(activeClip.id, trimStartMs, trimEndMs, isLoopingEnabled) {
    viewModel.previewClipTrim(activeClip, trimStartMs, trimEndMs, loop = isLoopingEnabled)
  }

  DisposableEffect(activeClip.id) {
    onDispose {
      viewModel.exitTrimPreview()
    }
  }

  val trimmedDurationMs = (trimEndMs - trimStartMs).coerceAtLeast(0L)
  val cutDurationMs = (totalDurationMs - trimmedDurationMs).coerceAtLeast(0L)
  val cutPercentage = if (totalDurationMs > 0) ((cutDurationMs.toFloat() / totalDurationMs) * 100).toInt() else 0

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(horizontal = 14.dp, vertical = 10.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. Header with Title, Clip Sequence Badge, and Close
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AmberAccent.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.ContentCut, contentDescription = "Trim Tool", tint = AmberAccent, modifier = Modifier.size(18.dp))
        }
        Column {
          Text(
            text = "Video Trimming Tool",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
          )
          Text(
            text = "Media3 Hardware Precision In/Out Points",
            style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent)
          )
        }
      }

      IconButton(
        onClick = {
          viewModel.exitTrimPreview()
          onDismiss()
        },
        modifier = Modifier.testTag("trim_tool_close")
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    // 2. Horizontal Clip Selector (Switch between clips on the timeline)
    if (videoClips.size > 1) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        videoClips.forEachIndexed { index, clip ->
          val isSelected = clip.id == activeClip.id
          FilterChip(
            selected = isSelected,
            onClick = {
              activeClipId = clip.id
              viewModel.timelineEngine.selectElement(SelectedTrackElement.Video(clip.id))
            },
            label = {
              Text(
                text = "#${index + 1} ${clip.name}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
              )
            },
            leadingIcon = {
              Icon(
                Icons.Default.Movie,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isSelected) CyanAccent else TextSecondary
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
              selectedLabelColor = TextPrimary,
              containerColor = StudioDarkBg
            ),
            border = FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = isSelected,
              borderColor = if (isSelected) CyanAccent else StudioBorder
            ),
            modifier = Modifier.testTag("trim_clip_chip_${clip.id}")
          )
        }
      }
    }

    // 3. Compact Media3 Live Video Preview Window
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .height(130.dp),
      shape = RoundedCornerShape(10.dp),
      colors = CardDefaults.cardColors(containerColor = Color.Black),
      border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioBorder))
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        val isRealPlayable = remember(activeClip.uri) {
          MediaRelinkManager.isRealPlayableMedia(context, activeClip.uri)
        }

        if (activeClip.isVideo && isRealPlayable) {
          val trimPlayer = viewModel.playbackEngine.player
          AndroidView(
            factory = { ctx ->
              android.view.TextureView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
                )
                try {
                  trimPlayer.setVideoTextureView(this)
                } catch (e: Exception) {
                  android.util.Log.w("VideoTrimmingToolPanel", "Failed to attach TextureView to player", e)
                }
                tag = trimPlayer
              }
            },
            update = { tv ->
              if (tv.tag != trimPlayer) {
                try {
                  trimPlayer.setVideoTextureView(tv)
                  tv.tag = trimPlayer
                } catch (e: Exception) {
                  android.util.Log.w("VideoTrimmingToolPanel", "Failed to rebind TextureView to player", e)
                }
              }
            },
            onReset = { /* Keep view intact */ },
            onRelease = { tv ->
              try {
                trimPlayer.clearVideoTextureView(tv)
              } catch (_: Exception) {}
            },
            modifier = Modifier
              .fillMaxSize()
              .testTag("trim_player_view")
          )
        } else {
          // Synthetic or preview placeholder for media
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(StudioDarkBg),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(Icons.Default.MovieFilter, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(28.dp))
              Spacer(modifier = Modifier.height(4.dp))
              Text(activeClip.name, color = TextPrimary, style = MaterialTheme.typography.labelMedium)
              Text("Media3 Scoped Window: ${formatTrimTime(trimStartMs)} - ${formatTrimTime(trimEndMs)}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
          }
        }

        // Live overlay badges
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .align(Alignment.TopCenter),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.7f)
          ) {
            Text(
              text = "IN: ${formatTrimTime(trimStartMs)}",
              color = CyanAccent,
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }

          Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.7f)
          ) {
            Text(
              text = "OUT: ${formatTrimTime(trimEndMs)}",
              color = AmberAccent,
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        // Center Play / Pause toggle for Media3 playback
        IconButton(
          onClick = { viewModel.toggleTrimPlayPause() },
          modifier = Modifier
            .align(Alignment.Center)
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, CyanAccent, CircleShape)
            .testTag("trim_play_pause_button")
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause Preview" else "Play Trim Preview",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
          )
        }

        // Bottom duration chip
        Surface(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp),
          shape = RoundedCornerShape(4.dp),
          color = Color.Black.copy(alpha = 0.75f)
        ) {
          Text(
            text = "Duration: ${formatTrimDuration(trimmedDurationMs)}",
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }
    }

    // 4. Interactive Dual-Handle Trim Scrubber Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Drag handles to adjust Start (In) and End (Out) points:",
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary
      )
      Text(
        text = "Playhead: ${formatTrimTime(trimPlayheadPos)}",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = CyanAccent, fontWeight = FontWeight.Bold)
      )
    }

    DualHandleTrimScrubber(
      totalDurationMs = totalDurationMs,
      startMs = trimStartMs,
      endMs = trimEndMs,
      playheadMs = trimPlayheadPos,
      onRangeChanged = { newStart, newEnd ->
        trimStartMs = newStart
        trimEndMs = newEnd
      },
      onSeek = { targetMs ->
        viewModel.seekTrimPreviewToSourceMs(targetMs)
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(76.dp)
        .testTag("trim_dual_handle_scrubber")
    )

    // 5. In & Out Point Quick Buttons (Industry Standard NLE Shortcuts)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = {
          // Set In Point to Current Playhead or 0
          trimStartMs = trimPlayheadPos.coerceIn(0L, trimEndMs - 100L)
        },
        colors = ButtonDefaults.buttonColors(containerColor = StudioDarkBg),
        border = BorderStroke(0.5.dp, CyanAccent.copy(alpha = 0.6f)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier
          .weight(1f)
          .height(36.dp)
          .testTag("trim_in_playhead")
      ) {
        Icon(Icons.Default.FirstPage, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("[ In: Playhead", style = MaterialTheme.typography.labelSmall, color = CyanAccent, fontWeight = FontWeight.Bold)
      }

      Button(
        onClick = {
          // Loop toggle
          isLoopingEnabled = !isLoopingEnabled
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = if (isLoopingEnabled) CyanAccent.copy(alpha = 0.15f) else StudioDarkBg
        ),
        border = BorderStroke(0.5.dp, if (isLoopingEnabled) CyanAccent else StudioBorder),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier
          .weight(0.9f)
          .height(36.dp)
          .testTag("trim_loop_toggle")
      ) {
        Icon(
          Icons.Default.Repeat,
          contentDescription = null,
          tint = if (isLoopingEnabled) CyanAccent else TextSecondary,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = if (isLoopingEnabled) "Loop: ON" else "Loop: OFF",
          style = MaterialTheme.typography.labelSmall,
          color = if (isLoopingEnabled) CyanAccent else TextSecondary
        )
      }

      Button(
        onClick = {
          // Set Out Point to Current Playhead or end
          trimEndMs = trimPlayheadPos.coerceIn(trimStartMs + 100L, totalDurationMs)
        },
        colors = ButtonDefaults.buttonColors(containerColor = StudioDarkBg),
        border = BorderStroke(0.5.dp, AmberAccent.copy(alpha = 0.6f)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier
          .weight(1f)
          .height(36.dp)
          .testTag("trim_out_playhead")
      ) {
        Text("Out: Playhead ]", style = MaterialTheme.typography.labelSmall, color = AmberAccent, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.LastPage, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
      }
    }

    // 5b. Frame-Accurate Stepping (30fps / 33ms single-frame advance)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedButton(
        onClick = { viewModel.stepTrimFrame(forward = false) },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        border = BorderStroke(0.5.dp, StudioBorder),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier
          .weight(1f)
          .height(34.dp)
          .testTag("trim_step_prev_frame")
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Step -1 Frame", style = MaterialTheme.typography.labelSmall)
      }

      OutlinedButton(
        onClick = { viewModel.stepTrimFrame(forward = true) },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        border = BorderStroke(0.5.dp, StudioBorder),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier
          .weight(1f)
          .height(34.dp)
          .testTag("trim_step_next_frame")
      ) {
        Text("Step +1 Frame", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(14.dp))
      }
    }

    // 6. Precision Micro-Nudge Controls (Frame-by-frame & second adjustments)
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(StudioDarkBg)
        .padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Start Point Nudge Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CyanAccent))
          Text(
            text = "Start (In): ${formatTrimTime(trimStartMs)}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          NudgeButton("-1s") { trimStartMs = (trimStartMs - 1000L).coerceIn(0L, trimEndMs - 100L) }
          NudgeButton("-100ms") { trimStartMs = (trimStartMs - 100L).coerceIn(0L, trimEndMs - 100L) }
          NudgeButton("+100ms") { trimStartMs = (trimStartMs + 100L).coerceIn(0L, trimEndMs - 100L) }
          NudgeButton("+1s") { trimStartMs = (trimStartMs + 1000L).coerceIn(0L, trimEndMs - 100L) }
        }
      }

      HorizontalDivider(color = StudioBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

      // End Point Nudge Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AmberAccent))
          Text(
            text = "End (Out): ${formatTrimTime(trimEndMs)}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          NudgeButton("-1s") { trimEndMs = (trimEndMs - 1000L).coerceIn(trimStartMs + 100L, totalDurationMs) }
          NudgeButton("-100ms") { trimEndMs = (trimEndMs - 100L).coerceIn(trimStartMs + 100L, totalDurationMs) }
          NudgeButton("+100ms") { trimEndMs = (trimEndMs + 100L).coerceIn(trimStartMs + 100L, totalDurationMs) }
          NudgeButton("+1s") { trimEndMs = (trimEndMs + 1000L).coerceIn(trimStartMs + 100L, totalDurationMs) }
        }
      }
    }

    // 7. Trim Analytics / Summary Card
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(StudioDarkBg.copy(alpha = 0.6f))
        .padding(horizontal = 12.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text("Original Source", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(formatTrimDuration(totalDurationMs), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
      }
      Column {
        Text("Trimmed Result", style = MaterialTheme.typography.labelSmall, color = CyanAccent)
        Text(formatTrimDuration(trimmedDurationMs), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = CyanAccent)
      }
      Column {
        Text("Cut Removed", style = MaterialTheme.typography.labelSmall, color = AmberAccent)
        Text("-${formatTrimDuration(cutDurationMs)} ($cutPercentage%)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = AmberAccent)
      }
    }

    // 8. Commit / Action Buttons
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Reset Button
      OutlinedButton(
        onClick = {
          trimStartMs = 0L
          trimEndMs = totalDurationMs
          viewModel.resetClipTrim(activeClip.id)
        },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAccent),
        border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.5f)),
        modifier = Modifier.testTag("trim_reset_button")
      ) {
        Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Reset")
      }

      // Apply Button
      Button(
        onClick = {
          viewModel.applyClipTrim(activeClip.id, trimStartMs, trimEndMs)
          onDismiss()
        },
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("trim_apply_button")
      ) {
        Icon(Icons.Default.Check, contentDescription = "Apply", tint = Color.Black)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Apply Trim to Timeline", color = Color.Black, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(StudioSurface)
      .border(0.5.dp, StudioBorder, RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 6.dp, vertical = 4.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      color = TextPrimary,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium)
    )
  }
}

/**
 * Interactive Dual-Handle Scrubber Canvas representing 0..totalDurationMs
 * with draggable left and right in/out markers, active striping, playhead indicator, and excluded dimming.
 */
@Composable
fun DualHandleTrimScrubber(
  totalDurationMs: Long,
  startMs: Long,
  endMs: Long,
  playheadMs: Long? = null,
  onRangeChanged: (Long, Long) -> Unit,
  onSeek: ((Long) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val density = LocalDensity.current
  val currentStartMs by rememberUpdatedState(startMs)
  val currentEndMs by rememberUpdatedState(endMs)
  val handleWidthDp = 28.dp
  val handleWidthPx = with(density) { handleWidthDp.toPx() }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(Color(0xFF14171E))
      .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
      .pointerInput(totalDurationMs, startMs, endMs) {
        detectTapGestures { offset ->
          val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
          val tappedMs = (ratio * totalDurationMs).toLong()
          if (tappedMs in currentStartMs..currentEndMs) {
            onSeek?.invoke(tappedMs)
          } else {
            val distToStart = kotlin.math.abs(tappedMs - currentStartMs)
            val distToEnd = kotlin.math.abs(tappedMs - currentEndMs)
            if (distToStart < distToEnd) {
              val newStart = tappedMs.coerceIn(0L, currentEndMs - 100L)
              onRangeChanged(newStart, currentEndMs)
            } else {
              val newEnd = tappedMs.coerceIn(currentStartMs + 100L, totalDurationMs)
              onRangeChanged(currentStartMs, newEnd)
            }
          }
        }
      }
  ) {
    val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)

    val startFraction = if (totalDurationMs > 0) (startMs.toFloat() / totalDurationMs).coerceIn(0f, 1f) else 0f
    val endFraction = if (totalDurationMs > 0) (endMs.toFloat() / totalDurationMs).coerceIn(0f, 1f) else 1f

    val startPx = startFraction * widthPx
    val endPx = endFraction * widthPx

    Canvas(modifier = Modifier.fillMaxSize()) {
      val canvasWidth = size.width
      val canvasHeight = size.height

      // 1. Filmstrip sprocket perforations
      val sprocketCount = (canvasWidth / 24f).toInt().coerceAtLeast(4)
      for (i in 0 until sprocketCount) {
        val sx = i * 24f + 4f
        // top sprocket
        drawRoundRect(
          color = Color.White.copy(alpha = 0.08f),
          topLeft = Offset(sx, 4f),
          size = Size(10f, 6f),
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
        )
        // bottom sprocket
        drawRoundRect(
          color = Color.White.copy(alpha = 0.08f),
          topLeft = Offset(sx, canvasHeight - 10f),
          size = Size(10f, 6f),
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
        )
      }

      // 2. Dimmed background for cut sections
      // Left excluded section
      if (startPx > 0f) {
        drawRect(
          color = Color.Black.copy(alpha = 0.7f),
          topLeft = Offset(0f, 0f),
          size = Size(startPx, canvasHeight)
        )
      }
      // Right excluded section
      if (endPx < canvasWidth) {
        drawRect(
          color = Color.Black.copy(alpha = 0.7f),
          topLeft = Offset(endPx, 0f),
          size = Size(canvasWidth - endPx, canvasHeight)
        )
      }

      // 3. Highlighted active trimmed region
      val activeWidth = (endPx - startPx).coerceAtLeast(4f)
      drawRect(
        color = CyanAccent.copy(alpha = 0.14f),
        topLeft = Offset(startPx, 0f),
        size = Size(activeWidth, canvasHeight)
      )

      // Top and bottom glowing border
      drawLine(
        color = CyanAccent,
        start = Offset(startPx, 0f),
        end = Offset(endPx, 0f),
        strokeWidth = 4f
      )
      drawLine(
        color = AmberAccent,
        start = Offset(startPx, canvasHeight),
        end = Offset(endPx, canvasHeight),
        strokeWidth = 4f
      )

      // 4. Center film ticks in active zone
      val tickStep = 16f
      var currentTickX = startPx + tickStep
      while (currentTickX < endPx) {
        drawLine(
          color = Color.White.copy(alpha = 0.22f),
          start = Offset(currentTickX, 16f),
          end = Offset(currentTickX, canvasHeight - 16f),
          strokeWidth = 1.5f
        )
        currentTickX += tickStep
      }

      // 5. Active Media3 live Playhead cursor within trimmed range
      if (playheadMs != null && totalDurationMs > 0) {
        val playheadFraction = (playheadMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
        val playheadPx = playheadFraction * canvasWidth
        drawLine(
          color = Color.White,
          start = Offset(playheadPx, 0f),
          end = Offset(playheadPx, canvasHeight),
          strokeWidth = 3f
        )
        // Playhead top triangular arrow
        val path = androidx.compose.ui.graphics.Path().apply {
          moveTo(playheadPx - 6f, 0f)
          lineTo(playheadPx + 6f, 0f)
          lineTo(playheadPx, 9f)
          close()
        }
        drawPath(path, color = Color.White)
      }
    }

    val leftHandleOffsetDp = with(density) {
      (startPx - handleWidthPx / 2f).coerceIn(0f, (widthPx - handleWidthPx).coerceAtLeast(0f)).toDp()
    }

    val rightHandleOffsetDp = with(density) {
      (endPx - handleWidthPx / 2f).coerceIn(0f, (widthPx - handleWidthPx).coerceAtLeast(0f)).toDp()
    }

    // Left Handle (Start Point [)
    var leftDragAccumulatorPx by remember { mutableFloatStateOf(0f) }
    Box(
      modifier = Modifier
        .offset(x = leftHandleOffsetDp)
        .width(handleWidthDp)
        .fillMaxHeight()
        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        .background(CyanAccent)
        .pointerInput(totalDurationMs, widthPx) {
          detectDragGestures(
            onDragStart = { leftDragAccumulatorPx = 0f },
            onDrag = { change, dragAmount ->
              change.consume()
              leftDragAccumulatorPx += dragAmount.x
              val deltaMs = ((leftDragAccumulatorPx / widthPx) * totalDurationMs).toLong()
              if (deltaMs != 0L) {
                val newStart = (currentStartMs + deltaMs).coerceIn(0L, currentEndMs - 100L)
                if (newStart != currentStartMs) {
                  onRangeChanged(newStart, currentEndMs)
                  leftDragAccumulatorPx = 0f
                }
              }
            }
          )
        }
        .testTag("trim_handle_start"),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("[", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Box(modifier = Modifier.width(3.dp).height(16.dp).background(Color.Black.copy(alpha = 0.4f)))
      }
    }

    // Right Handle (End Point ])
    var rightDragAccumulatorPx by remember { mutableFloatStateOf(0f) }
    Box(
      modifier = Modifier
        .offset(x = rightHandleOffsetDp)
        .width(handleWidthDp)
        .fillMaxHeight()
        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
        .background(AmberAccent)
        .pointerInput(totalDurationMs, widthPx) {
          detectDragGestures(
            onDragStart = { rightDragAccumulatorPx = 0f },
            onDrag = { change, dragAmount ->
              change.consume()
              rightDragAccumulatorPx += dragAmount.x
              val deltaMs = ((rightDragAccumulatorPx / widthPx) * totalDurationMs).toLong()
              if (deltaMs != 0L) {
                val newEnd = (currentEndMs + deltaMs).coerceIn(currentStartMs + 100L, totalDurationMs)
                if (newEnd != currentEndMs) {
                  onRangeChanged(currentStartMs, newEnd)
                  rightDragAccumulatorPx = 0f
                }
              }
            }
          )
        }
        .testTag("trim_handle_end"),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("]", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Box(modifier = Modifier.width(3.dp).height(16.dp).background(Color.Black.copy(alpha = 0.4f)))
      }
    }
  }
}

private fun formatTrimTime(ms: Long): String {
  val totalSec = ms / 1000
  val minutes = totalSec / 60
  val seconds = totalSec % 60
  val tenths = (ms % 1000) / 100
  return String.format("%02d:%02d.%d", minutes, seconds, tenths)
}

private fun formatTrimDuration(ms: Long): String {
  val seconds = ms / 1000.0
  return String.format("%.2fs", seconds)
}
