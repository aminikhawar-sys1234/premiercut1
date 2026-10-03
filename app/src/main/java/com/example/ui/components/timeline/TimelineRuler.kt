package com.example.ui.components.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Timeline
import com.example.ui.theme.*

@Composable
fun AccurateTimecodeRuler(
  totalDurationMs: Long,
  currentPosMs: Long,
  msPerPixel: Float,
  onSeek: (Long) -> Unit,
  fps: Int = 30,
  isFrameSnapping: Boolean = false,
  onDoubleTapSnap: (() -> Unit)? = null,
  onScrubStart: () -> Unit = {},
  onScrubStop: () -> Unit = {},
  onPausePlayback: (() -> Unit)? = null,
  timeline: Timeline? = null,
  modifier: Modifier = Modifier,
  /**
   * When provided, dragging the ruler is a RELATIVE scrub: the horizontal pixel delta (content scroll
   * direction, i.e. already negated) is forwarded and the host scrolls the timeline under the fixed
   * playhead. Without it the ruler would compute an absolute time from the finger position inside the
   * scrolling content, which feeds back into itself once the content scrolls (needle drift / jumps).
   */
  onScrubDelta: ((Float) -> Unit)? = null
) {
  val msPerDp = msPerPixel
  val safeTotalDuration = totalDurationMs.coerceAtLeast(1000L)
  val rulerWidthDp = (safeTotalDuration / msPerDp).dp
  val frameDurationMs = 1000.0 / fps
  val density = LocalDensity.current

  var isScrubbing by remember { mutableStateOf(false) }
  var scrubPreviewMs by remember { mutableLongStateOf(currentPosMs) }

  // Dynamic tick calculation based on zoom level (msPerDp)
  val (majorIntervalMs, minorIntervalMs, showFrameTicks) = remember(msPerDp, fps) {
    when {
      msPerDp <= 3f -> Triple(500L, (1000L / fps).coerceAtLeast(1L), true)
      msPerDp <= 8f -> Triple(1000L, (1000L / fps).coerceAtLeast(1L), true)
      msPerDp <= 18f -> Triple(1000L, 200L, false)
      msPerDp <= 35f -> Triple(2000L, 500L, false)
      msPerDp <= 80f -> Triple(5000L, 1000L, false)
      else -> Triple(10000L, 2000L, false)
    }
  }

  Box(
    modifier = modifier
      .width(rulerWidthDp)
      .height(34.dp)
      .background(Color(0xFF080A0F))
      .testTag("timeline_timecode_ruler")
      .pointerInput(safeTotalDuration, msPerDp, isFrameSnapping, fps, density) {
        detectTapGestures(
          onDoubleTap = { offset ->
            onPausePlayback?.invoke()
            if (onDoubleTapSnap != null) {
              onDoubleTapSnap()
            } else {
              val xDp = offset.x / density.density
              val rawMs = (xDp * msPerDp).toLong().coerceIn(0L, safeTotalDuration)
              val targetMs = if (isFrameSnapping) {
                (Math.round(rawMs / frameDurationMs) * frameDurationMs).toLong()
              } else rawMs
              onSeek(targetMs)
            }
          },
          onTap = { offset ->
            onPausePlayback?.invoke()
            val xDp = offset.x / density.density
            val rawMs = (xDp * msPerDp).toLong().coerceIn(0L, safeTotalDuration)
            val targetMs = if (isFrameSnapping) {
              (Math.round(rawMs / frameDurationMs) * frameDurationMs).toLong()
            } else rawMs
            onSeek(targetMs)
          }
        )
      }
      .pointerInput(safeTotalDuration, msPerDp, isFrameSnapping, fps, density) {
        detectDragGestures(
          onDragStart = { offset ->
            onPausePlayback?.invoke()
            isScrubbing = true
            onScrubStart()
            if (onScrubDelta == null) {
              val xDp = offset.x / density.density
              val rawMs = (xDp * msPerDp).toLong().coerceIn(0L, safeTotalDuration)
              val targetMs = if (isFrameSnapping) {
                (Math.round(rawMs / frameDurationMs) * frameDurationMs).toLong()
              } else rawMs
              scrubPreviewMs = targetMs
              onSeek(targetMs)
            }
          },
          onDragEnd = {
            isScrubbing = false
            onScrubStop()
          },
          onDragCancel = {
            isScrubbing = false
            onScrubStop()
          },
          onDrag = { change, dragAmount ->
            change.consume()
            if (onScrubDelta != null) {
              onScrubDelta(-dragAmount.x)
            } else {
              val xDp = change.position.x / density.density
              val rawMs = (xDp * msPerDp).toLong().coerceIn(0L, safeTotalDuration)
              val targetMs = if (isFrameSnapping) {
                (Math.round(rawMs / frameDurationMs) * frameDurationMs).toLong()
              } else rawMs
              scrubPreviewMs = targetMs
              onSeek(targetMs)
            }
          }
        )
      }
  ) {
    // 2. Ruler Ticks & Timecode Numbers
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(30.dp)
        .align(Alignment.BottomCenter)
    ) {
      val canvasWidth = size.width
      val canvasHeight = size.height

      // Subtle ruler top gradient background
      drawRect(
        brush = Brush.verticalGradient(
          listOf(Color(0xFF040508), Color(0xFF0C0F17))
        )
      )

      // Bottom border line
      drawLine(
        color = Color(0xFF1B202D),
        start = Offset(0f, canvasHeight),
        end = Offset(canvasWidth, canvasHeight),
        strokeWidth = 1.dp.toPx()
      )

      val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 9.5.sp.toPx()
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
      }

      val frameTextPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(220, 0, 229, 255)
        textSize = 8.sp.toPx()
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
      }

      val totalTicks = (safeTotalDuration / minorIntervalMs).toInt()

      for (i in 0..totalTicks) {
        val tickTimeMs = i * minorIntervalMs
        val x = (tickTimeMs / msPerDp).dp.toPx()
        val isMajor = tickTimeMs % majorIntervalMs == 0L
        val isHalfMajor = tickTimeMs % (majorIntervalMs / 2) == 0L

        val tickHeight = when {
          isMajor -> canvasHeight * 0.55f
          isHalfMajor -> canvasHeight * 0.35f
          showFrameTicks -> canvasHeight * 0.20f
          else -> canvasHeight * 0.25f
        }

        val tickColor = when {
          isMajor -> Color.White.copy(alpha = 0.95f)
          isHalfMajor -> Color.White.copy(alpha = 0.6f)
          showFrameTicks && (i % 5 == 0) -> CyanAccent.copy(alpha = 0.7f)
          else -> Color.White.copy(alpha = 0.3f)
        }

        drawLine(
          color = tickColor,
          start = Offset(x, canvasHeight - tickHeight),
          end = Offset(x, canvasHeight),
          strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx()
        )

        if (isMajor) {
          val label = formatTimecodeRuler(tickTimeMs)
          drawContext.canvas.nativeCanvas.drawText(
            label,
            x + 4f,
            canvasHeight - tickHeight - 4f,
            textPaint
          )
        } else if (showFrameTicks && isHalfMajor && msPerDp <= 5f) {
          val frameNum = msToFrameIndex(tickTimeMs, fps)
          drawContext.canvas.nativeCanvas.drawText(
            "F$frameNum",
            x + 3f,
            canvasHeight - tickHeight - 3f,
            frameTextPaint
          )
        }
      }
    }
  }
}

private fun msToFrameIndex(ms: Long, fps: Int): Long {
  return (ms * fps / 1000L).coerceAtLeast(0L)
}

private fun formatTimecodeRuler(ms: Long): String {
  val totalSeconds = ms / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  val millis = ms % 1000
  return if (minutes > 0) {
    String.format("%02d:%02d", minutes, seconds)
  } else {
    String.format("%d.%ds", seconds, millis / 100)
  }
}
