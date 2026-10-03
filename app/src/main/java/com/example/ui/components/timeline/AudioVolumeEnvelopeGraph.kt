package com.example.ui.components.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AudioClip
import com.example.domain.model.ClipKeyframe
import com.example.engine.KeyframeInterpolator
import com.example.ui.components.formatDurationShort
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.max

/**
 * A volume envelope graph component for audio tracks.
 * Allows users to visualize the volume curve over the waveform,
 * draw new volume keyframes by tapping or dragging anywhere on the graph,
 * drag keyframe nodes vertically (volume 0% - 150%) and horizontally (time),
 * and adjust interactive fade-in / fade-out handles.
 */
@Composable
fun AudioVolumeEnvelopeGraph(
  audioClip: AudioClip,
  clipDurationMs: Long,
  modifier: Modifier = Modifier,
  currentPlayheadMs: Long? = null,
  isEnvelopeMode: Boolean = true,
  selectedKeyframeId: String? = null,
  showControlsHeader: Boolean = true,
  onToggleEnvelopeMode: (() -> Unit)? = null,
  onAddKeyframe: (relTimeMs: Long, volume: Float) -> Unit,
  onUpdateKeyframe: (keyframeId: String, newTimeMs: Long, newVolume: Float) -> Unit,
  onDeleteKeyframe: (keyframeId: String) -> Unit,
  onFadeInChanged: ((newFadeInMs: Long) -> Unit)? = null,
  onFadeOutChanged: ((newFadeOutMs: Long) -> Unit)? = null,
  onApplyPresetFade: ((type: String) -> Unit)? = null,
  onResetEnvelope: (() -> Unit)? = null,
  onBaseVolumeChanged: ((Float) -> Unit)? = null,
  onSeek: ((Long) -> Unit)? = null
) {
  val density = LocalDensity.current
  val safeDuration = clipDurationMs.coerceAtLeast(500L)
  val maxDisplayVolume = 1.4f

  // Dragging state for keyframe nodes
  var activeDraggingKfId by remember { mutableStateOf<String?>(null) }
  var draggingTooltipText by remember { mutableStateOf<String?>(null) }
  var draggingTooltipOffset by remember { mutableStateOf(Offset.Zero) }

  // Dragging state for fade handles
  var isDraggingFadeIn by remember { mutableStateOf(false) }
  var isDraggingFadeOut by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioDarkBg)
      .testTag("audio_volume_envelope_container")
  ) {
    // 1. Controls Header (Track Info, Envelope Mode Toggle, Quick Fade Presets)
    if (showControlsHeader) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(StudioSurface)
          .padding(horizontal = 10.dp, vertical = 6.dp)
          .testTag("audio_envelope_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // Track Title & Volume badge
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f, fill = false)
        ) {
          Icon(
            imageVector = Icons.Default.Audiotrack,
            contentDescription = null,
            tint = AudioTrackColor,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = audioClip.title.ifBlank { "Audio Track" },
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              fontSize = 12.sp
            ),
            maxLines = 1
          )
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(AudioTrackColor.copy(alpha = 0.2f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = "${audioClip.keyframes.size} KFs • ${(audioClip.volume * 100).toInt()}% Vol",
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = AudioTrackColor
              )
            )
          }
        }

        // Quick Preset Action Chips
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          // Envelope Mode Pill
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isEnvelopeMode) AudioTrackColor else StudioSurfaceVariant,
            modifier = Modifier
              .clickable { onToggleEnvelopeMode?.invoke() }
              .testTag("envelope_mode_toggle")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = if (isEnvelopeMode) Color.Black else TextSecondary,
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isEnvelopeMode) "Envelope: ON" else "Envelope",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isEnvelopeMode) Color.Black else TextSecondary
                )
              )
            }
          }

          // + Add Keyframe at Playhead
          IconButton(
            onClick = {
              val relTime = ((currentPlayheadMs ?: 0L) - audioClip.timelineStartMs)
                .coerceIn(0L, audioClip.durationMs)
              val currentVol = KeyframeInterpolator.interpolateVolume(audioClip, relTime)
              onAddKeyframe(relTime, currentVol)
            },
            modifier = Modifier
              .size(26.dp)
              .clip(CircleShape)
              .background(StudioSurfaceVariant)
              .testTag("add_keyframe_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "Add Volume Keyframe",
              tint = AmberAccent,
              modifier = Modifier.size(15.dp)
            )
          }

          // Fade In 1s
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = StudioSurfaceVariant,
            modifier = Modifier
              .clickable { onApplyPresetFade?.invoke("fadeIn") }
              .testTag("fade_in_preset_btn")
          ) {
            Text(
              text = "Fade In",
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = CyanAccent
              ),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
          }

          // Fade Out 1s
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = StudioSurfaceVariant,
            modifier = Modifier
              .clickable { onApplyPresetFade?.invoke("fadeOut") }
              .testTag("fade_out_preset_btn")
          ) {
            Text(
              text = "Fade Out",
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = PurpleAccent
              ),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
          }

          // Reset Envelope
          if (audioClip.keyframes.isNotEmpty() || audioClip.fadeInMs > 0 || audioClip.fadeOutMs > 0) {
            IconButton(
              onClick = { onResetEnvelope?.invoke() },
              modifier = Modifier.size(24.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset Envelope",
                tint = TextTertiary,
                modifier = Modifier.size(13.dp)
              )
            }
          }
        }
      }
    }

    // 2. The Main Envelope Graph Canvas & Interactive Area
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .height(84.dp)
        .background(Color(0xFF0D1117))
        .border(0.5.dp, StudioBorder)
        .testTag("envelope_graph_box")
    ) {
      val boxWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(10f)
      val boxHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(10f)

      val topMarginPx = with(density) { 10.dp.toPx() }
      val bottomMarginPx = with(density) { 10.dp.toPx() }
      val usableHeightPx = (boxHeightPx - topMarginPx - bottomMarginPx).coerceAtLeast(10f)

      fun volumeToY(vol: Float): Float {
        val normVol = (vol / maxDisplayVolume).coerceIn(0f, 1f)
        return topMarginPx + (1f - normVol) * usableHeightPx
      }

      val y100Px = volumeToY(1.0f)

      fun yToVolume(y: Float): Float {
        val normY = (1f - ((y - topMarginPx) / usableHeightPx)).coerceIn(0f, 1f)
        return normY * maxDisplayVolume
      }

      fun timeToX(tMs: Long): Float {
        return (tMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) * boxWidthPx
      }

      fun xToTime(x: Float): Long {
        return ((x / boxWidthPx).coerceIn(0f, 1f) * safeDuration).toLong()
      }

      // Drawing the Graph: Background waveform, reference lines, volume curve, and fill
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .testTag("envelope_canvas")
      ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        // A. Background Waveform Silhouettes
        val waveform = audioClip.waveformData
        if (waveform.isNotEmpty()) {
          val barWidth = 3.dp.toPx()
          val gap = 1.5.dp.toPx()
          val slot = barWidth + gap
          val count = (w / slot).toInt().coerceAtLeast(1)
          val step = max(1, waveform.size / count)

          for (i in 0 until count) {
            val sampleIdx = (i * step).coerceIn(0, waveform.size - 1)
            val amp = waveform[sampleIdx].coerceIn(0.05f, 1f)
            val barH = usableHeightPx * 0.75f * amp
            val barX = i * slot

            drawRoundRect(
              color = Color(0xFF1E293B).copy(alpha = 0.6f),
              topLeft = Offset(barX, midY - barH / 2f),
              size = Size(barWidth, barH),
              cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
          }
        }

        // B. Reference Lines (100% / 0 dB, 50% / -6 dB, 0% / Mute)
        val y100 = volumeToY(1.0f)
        val y50 = volumeToY(0.5f)
        val y0 = volumeToY(0.0f)

        // 100% Unity Reference Line
        drawLine(
          color = Color.White.copy(alpha = 0.25f),
          start = Offset(0f, y100),
          end = Offset(w, y100),
          strokeWidth = 1.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )

        // 50% Reference Line
        drawLine(
          color = Color.White.copy(alpha = 0.12f),
          start = Offset(0f, y50),
          end = Offset(w, y50),
          strokeWidth = 0.8.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)
        )

        // 0% Baseline (Mute / Silent)
        drawLine(
          color = Color(0xFFEF4444).copy(alpha = 0.35f),
          start = Offset(0f, y0),
          end = Offset(w, y0),
          strokeWidth = 1.2.dp.toPx()
        )

        // C. Shaded Fade-In and Fade-Out Zones (if present)
        if (audioClip.fadeInMs > 0L) {
          val fadeEndX = timeToX(audioClip.fadeInMs)
          drawRect(
            brush = Brush.horizontalGradient(
              listOf(CyanAccent.copy(alpha = 0.18f), Color.Transparent),
              startX = 0f,
              endX = fadeEndX
            ),
            topLeft = Offset(0f, 0f),
            size = Size(fadeEndX, h)
          )
        }
        if (audioClip.fadeOutMs > 0L) {
          val fadeStartX = timeToX(safeDuration - audioClip.fadeOutMs)
          drawRect(
            brush = Brush.horizontalGradient(
              listOf(Color.Transparent, PurpleAccent.copy(alpha = 0.18f)),
              startX = fadeStartX,
              endX = w
            ),
            topLeft = Offset(fadeStartX, 0f),
            size = Size(w - fadeStartX, h)
          )
        }

        // D. Build the Continuous Volume Envelope Curve Path
        val curvePath = Path()
        val fillPath = Path()
        val samplePoints = 120

        for (i in 0..samplePoints) {
          val norm = i.toFloat() / samplePoints.toFloat()
          val sampleTimeMs = (norm * safeDuration).toLong()
          val interpolatedVol = KeyframeInterpolator.interpolateVolume(audioClip, sampleTimeMs)
          val px = norm * w
          val py = volumeToY(interpolatedVol)

          if (i == 0) {
            curvePath.moveTo(px, py)
            fillPath.moveTo(px, y0)
            fillPath.lineTo(px, py)
          } else {
            curvePath.lineTo(px, py)
            fillPath.lineTo(px, py)
          }
        }

        fillPath.lineTo(w, y0)
        fillPath.close()

        // Draw glowing gradient fill under envelope curve
        drawPath(
          path = fillPath,
          brush = Brush.verticalGradient(
            colors = listOf(
              AudioTrackColor.copy(alpha = 0.32f),
              AudioTrackColor.copy(alpha = 0.04f)
            ),
            startY = topMarginPx,
            endY = y0
          )
        )

        // Draw outer glow around the envelope line
        drawPath(
          path = curvePath,
          color = AudioTrackColor.copy(alpha = 0.3f),
          style = Stroke(
            width = 5.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
          )
        )

        // Draw crisp core envelope line
        drawPath(
          path = curvePath,
          color = AudioTrackColor,
          style = Stroke(
            width = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
          )
        )

        // E. Reference Labels in Left Margin
        // "100%" label
        // "0%" label
      }

      // 3. Playhead Line Overlay
      currentPlayheadMs?.let { playhead ->
        val relPlayhead = (playhead - audioClip.timelineStartMs).coerceIn(0L, safeDuration)
        val playheadX = (relPlayhead.toFloat() / safeDuration.toFloat()) * boxWidthPx
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .width(2.dp)
            .offset(x = with(density) { playheadX.toDp() })
            .background(CyanAccent)
        )
      }

      // 4. Interactive Gesture Layer (Tap to add keyframe / drag to draw keyframe)
      Box(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(audioClip.id, isEnvelopeMode, safeDuration) {
            detectTapGestures(
              onTap = { offset ->
                if (!isEnvelopeMode) {
                  // Normal seek
                  val targetMs = xToTime(offset.x)
                  onSeek?.invoke(audioClip.timelineStartMs + targetMs)
                  return@detectTapGestures
                }

                // Check if tapped near an existing keyframe (within 24dp)
                val hitThresholdPx = with(density) { 24.dp.toPx() }
                val hitKf = audioClip.keyframes.find { kf ->
                  val kfX = timeToX(kf.timeMs)
                  val kfY = volumeToY(kf.volume)
                  abs(offset.x - kfX) <= hitThresholdPx && abs(offset.y - kfY) <= hitThresholdPx
                }

                if (hitKf == null) {
                  // Draw / place new keyframe at this exact time and volume
                  val clickedTimeMs = xToTime(offset.x)
                  val clickedVolume = yToVolume(offset.y).coerceIn(0f, maxDisplayVolume)
                  onAddKeyframe(clickedTimeMs, clickedVolume)
                }
              }
            )
          }
      )

      // 5. Interactive Fade-In Handle (Top-Left Slider)
      if (onFadeInChanged != null) {
        val fadeInX = timeToX(audioClip.fadeInMs.coerceAtLeast(0L))
        Box(
          modifier = Modifier
            .offset(
              x = with(density) { (fadeInX - 10.dp.toPx()).coerceAtLeast(0f).toDp() },
              y = 2.dp
            )
            .size(24.dp)
            .pointerInput(audioClip.id, safeDuration) {
              detectDragGestures(
                onDragStart = { isDraggingFadeIn = true },
                onDragEnd = { isDraggingFadeIn = false },
                onDragCancel = { isDraggingFadeIn = false },
                onDrag = { change, dragAmount ->
                  change.consume()
                  val newFadeMs = (audioClip.fadeInMs + (dragAmount.x / boxWidthPx * safeDuration).toLong())
                    .coerceIn(0L, safeDuration / 2)
                  onFadeInChanged(newFadeMs)
                }
              )
            }
            .testTag("fade_in_handle"),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(width = 8.dp, height = 16.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(CyanAccent)
              .border(1.dp, Color.White, RoundedCornerShape(3.dp))
          )
        }
      }

      // 6. Interactive Fade-Out Handle (Top-Right Slider)
      if (onFadeOutChanged != null) {
        val fadeOutX = timeToX((safeDuration - audioClip.fadeOutMs).coerceIn(0L, safeDuration))
        Box(
          modifier = Modifier
            .offset(
              x = with(density) { (fadeOutX - 10.dp.toPx()).coerceIn(0f, boxWidthPx - 20.dp.toPx()).toDp() },
              y = 2.dp
            )
            .size(24.dp)
            .pointerInput(audioClip.id, safeDuration) {
              detectDragGestures(
                onDragStart = { isDraggingFadeOut = true },
                onDragEnd = { isDraggingFadeOut = false },
                onDragCancel = { isDraggingFadeOut = false },
                onDrag = { change, dragAmount ->
                  change.consume()
                  val newFadeMs = (audioClip.fadeOutMs - (dragAmount.x / boxWidthPx * safeDuration).toLong())
                    .coerceIn(0L, safeDuration / 2)
                  onFadeOutChanged(newFadeMs)
                }
              )
            }
            .testTag("fade_out_handle"),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(width = 8.dp, height = 16.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(PurpleAccent)
              .border(1.dp, Color.White, RoundedCornerShape(3.dp))
          )
        }
      }

      // 7. Interactive Keyframe Diamond Nodes
      audioClip.keyframes.forEach { kf ->
        val kfXPx = timeToX(kf.timeMs)
        val kfYPx = volumeToY(kf.volume)
        val isSelected = kf.id == selectedKeyframeId || kf.id == activeDraggingKfId
        val nodeSizeDp = if (isSelected) 18.dp else 14.dp

        Box(
          modifier = Modifier
            .offset(
              x = with(density) { (kfXPx - with(density) { 24.dp.toPx() }).toDp() },
              y = with(density) { (kfYPx - with(density) { 24.dp.toPx() }).toDp() }
            )
            .size(48.dp) // Generous 48dp touch target for accessibility and touch precision
            .pointerInput(kf.id, safeDuration) {
              detectDragGestures(
                onDragStart = {
                  activeDraggingKfId = kf.id
                  draggingTooltipOffset = Offset(kfXPx, kfYPx)
                  draggingTooltipText = "Vol: ${(kf.volume * 100).toInt()}% • ${formatDurationShort(kf.timeMs)}"
                },
                onDragEnd = {
                  activeDraggingKfId = null
                  draggingTooltipText = null
                },
                onDragCancel = {
                  activeDraggingKfId = null
                  draggingTooltipText = null
                },
                onDrag = { change, dragAmount ->
                  change.consume()
                  val newXPx = (timeToX(kf.timeMs) + dragAmount.x).coerceIn(0f, boxWidthPx)
                  val newYPx = (volumeToY(kf.volume) + dragAmount.y).coerceIn(topMarginPx, topMarginPx + usableHeightPx)

                  val newTimeMs = xToTime(newXPx)
                  val newVolume = yToVolume(newYPx).coerceIn(0f, maxDisplayVolume)

                  onUpdateKeyframe(kf.id, newTimeMs, newVolume)

                  draggingTooltipOffset = Offset(newXPx, newYPx)
                  draggingTooltipText = "Vol: ${(newVolume * 100).toInt()}% • ${formatDurationShort(newTimeMs)}"
                }
              )
            }
            .pointerInput(kf.id) {
              detectTapGestures(
                onLongPress = {
                  // Long press deletes keyframe
                  onDeleteKeyframe(kf.id)
                },
                onDoubleTap = {
                  // Double tap deletes keyframe
                  onDeleteKeyframe(kf.id)
                }
              )
            }
            .testTag("keyframe_node_${kf.id}"),
          contentAlignment = Alignment.Center
        ) {
          // Visual Diamond Node
          Box(
            modifier = Modifier
              .size(nodeSizeDp)
              .clip(CircleShape)
              .background(if (isSelected) Color.White else AmberAccent)
              .border(
                width = if (isSelected) 2.5.dp else 1.5.dp,
                color = if (isSelected) CyanAccent else Color(0xFF0F172A),
                shape = CircleShape
              )
          )
        }
      }

      // 8. Live Dragging HUD Tooltip
      draggingTooltipText?.let { tooltip ->
        Box(
          modifier = Modifier
            .offset(
              x = with(density) { (draggingTooltipOffset.x - 45.dp.toPx()).coerceIn(4f, boxWidthPx - 100.dp.toPx()).toDp() },
              y = with(density) { (draggingTooltipOffset.y - 38.dp.toPx()).coerceAtLeast(2f).toDp() }
            )
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.88f))
            .border(1.dp, AudioTrackColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("keyframe_drag_tooltip")
        ) {
          Text(
            text = tooltip,
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = Color.White,
              fontSize = 10.sp
            )
          )
        }
      }

      // 9. Static Level Badges (Top right: +0dB guide)
      Text(
        text = "100% (+0dB)",
        style = MaterialTheme.typography.labelSmall.copy(
          fontSize = 8.sp,
          color = Color.White.copy(alpha = 0.35f)
        ),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(end = 4.dp, top = with(density) { (y100Px - 10.dp.toPx()).coerceAtLeast(0f).toDp() })
      )

      Text(
        text = "0% (Mute)",
        style = MaterialTheme.typography.labelSmall.copy(
          fontSize = 8.sp,
          color = Color(0xFFEF4444).copy(alpha = 0.45f)
        ),
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(end = 4.dp, bottom = 2.dp)
      )
    }
  }
}
