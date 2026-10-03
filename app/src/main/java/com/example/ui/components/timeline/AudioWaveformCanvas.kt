package com.example.ui.components.timeline

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.audio.AudioPeak
import com.example.engine.audio.AudioPeakType
import com.example.engine.audio.AudioSilenceRegion
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.max

enum class WaveformStyle {
  MIRRORED_BARS,
  SOLID_ENVELOPE,
  BASELINE_UPWARD
}

/**
 * High-performance, custom Canvas audio waveform visualization component.
 * Renders real-time mirrored amplitude bars, RMS energy bodies, transient peak caps,
 * silence & dead-air shaded zones, clipping warnings, and beat alignment guides.
 */
@Composable
fun AudioWaveformCanvas(
  waveformData: List<Float>,
  peaks: List<AudioPeak>,
  clipDurationMs: Long,
  modifier: Modifier = Modifier,
  silenceRegions: List<AudioSilenceRegion> = emptyList(),
  playheadPosMs: Long? = null,
  trackColor: Color = AudioTrackColor,
  peakColor: Color = AmberAccent,
  crestColor: Color = CyanAccent,
  style: WaveformStyle = WaveformStyle.MIRRORED_BARS,
  showPeakGuides: Boolean = true,
  showSilenceHighlights: Boolean = true,
  showCenterLine: Boolean = true,
  isMuted: Boolean = false
) {
  // Infinite pulse for active playhead snap detection
  val infiniteTransition = rememberInfiniteTransition(label = "peak_snap_pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.5f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(600, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_alpha"
  )

  Box(modifier = modifier.fillMaxSize().testTag("audio_waveform_canvas")) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      if (waveformData.isEmpty() || size.width <= 2f || size.height <= 2f) {
        // Draw empty baseline
        if (showCenterLine) {
          drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx()
          )
        }
        return@Canvas
      }

      val width = size.width
      val height = size.height
      val centerY = height / 2f

      // Effective colors (muted tracks appear dimmed)
      val effectiveTrackColor = if (isMuted) trackColor.copy(alpha = 0.3f) else trackColor
      val effectivePeakColor = if (isMuted) peakColor.copy(alpha = 0.35f) else peakColor

      // 1. Draw Silence / Dead-air shaded regions
      if (showSilenceHighlights && silenceRegions.isNotEmpty() && clipDurationMs > 0L) {
        drawSilenceRegions(
          silenceRegions = silenceRegions,
          clipDurationMs = clipDurationMs,
          width = width,
          height = height,
          isMuted = isMuted
        )
      }

      // 2. Center baseline guideline
      if (showCenterLine && style != WaveformStyle.BASELINE_UPWARD) {
        drawLine(
          color = Color.White.copy(alpha = 0.18f),
          start = Offset(0f, centerY),
          end = Offset(width, centerY),
          strokeWidth = 1.dp.toPx()
        )
      }

      // 3. Determine slot density based on available canvas width
      val barWidthPx = 2.5.dp.toPx()
      val gapPx = 1.5.dp.toPx()
      val slotWidthPx = barWidthPx + gapPx
      val totalSlots = max(1, (width / slotWidthPx).toInt())

      // 4. Resample waveform into display buckets using Peak-Preserving Pooling
      val displayAmps = resamplePeakPreserving(waveformData, totalSlots)

      // Calculate playhead pixel position if playhead is within this clip
      val playheadX = if (playheadPosMs != null && clipDurationMs > 0L) {
        val relPos = playheadPosMs.coerceIn(0L, clipDurationMs)
        (relPos.toFloat() / clipDurationMs) * width
      } else null

      val snapDistancePx = 14.dp.toPx()

      // 5. Render Bars or Envelope based on selected style
      when (style) {
        WaveformStyle.MIRRORED_BARS -> {
          drawMirroredBars(
            amps = displayAmps,
            width = width,
            height = height,
            centerY = centerY,
            barWidthPx = barWidthPx,
            slotWidthPx = slotWidthPx,
            trackColor = effectiveTrackColor,
            peakColor = effectivePeakColor,
            crestColor = crestColor,
            isMuted = isMuted
          )
        }
        WaveformStyle.SOLID_ENVELOPE -> {
          drawSolidEnvelope(
            amps = displayAmps,
            width = width,
            height = height,
            centerY = centerY,
            trackColor = effectiveTrackColor,
            peakColor = effectivePeakColor
          )
        }
        WaveformStyle.BASELINE_UPWARD -> {
          drawBaselineBars(
            amps = displayAmps,
            width = width,
            height = height,
            barWidthPx = barWidthPx,
            slotWidthPx = slotWidthPx,
            trackColor = effectiveTrackColor,
            peakColor = effectivePeakColor
          )
        }
      }

      // 6. Draw Transient Peak Indicators and Cut Alignment Guides
      if (showPeakGuides && clipDurationMs > 0L && !isMuted) {
        drawPeakMarkers(
          peaks = peaks,
          clipDurationMs = clipDurationMs,
          width = width,
          height = height,
          centerY = centerY,
          peakColor = effectivePeakColor,
          crestColor = crestColor,
          playheadX = playheadX,
          snapDistancePx = snapDistancePx,
          pulseAlpha = pulseAlpha
        )
      }

      // 7. Active Playhead Cut Guide Intersection Line & Peak Snapping Aura
      if (playheadX != null) {
        val isSnappedToPeak = peaks.any { peak ->
          val peakX = (peak.timeMs.toFloat() / clipDurationMs) * width
          abs(peakX - playheadX) <= snapDistancePx
        }

        val isNearSilence = silenceRegions.any { s ->
          val startX = (s.startMs.toFloat() / clipDurationMs) * width
          val endX = (s.endMs.toFloat() / clipDurationMs) * width
          playheadX in (startX - 4f)..(endX + 4f)
        }

        val cutLineColor = when {
          isSnappedToPeak -> AmberAccent.copy(alpha = pulseAlpha)
          isNearSilence -> Color.White.copy(alpha = 0.85f)
          else -> CyanAccent.copy(alpha = 0.65f)
        }
        val cutStroke = if (isSnappedToPeak) 2.dp.toPx() else 1.dp.toPx()

        drawLine(
          color = cutLineColor,
          start = Offset(playheadX, 0f),
          end = Offset(playheadX, height),
          strokeWidth = cutStroke
        )

        if (isSnappedToPeak) {
          // Glow halo on playhead beat intersection
          drawCircle(
            color = AmberAccent.copy(alpha = 0.35f * pulseAlpha),
            radius = 8.dp.toPx(),
            center = Offset(playheadX, centerY)
          )
          drawCircle(
            color = AmberAccent,
            radius = 3.5.dp.toPx(),
            center = Offset(playheadX, centerY)
          )
        }
      }
    }
  }
}

/**
 * Draws shaded silence / dead air background zones with boundary markers.
 */
private fun DrawScope.drawSilenceRegions(
  silenceRegions: List<AudioSilenceRegion>,
  clipDurationMs: Long,
  width: Float,
  height: Float,
  isMuted: Boolean
) {
  val baseAlpha = if (isMuted) 0.15f else 0.38f

  for (region in silenceRegions) {
    val startX = (region.startMs.toFloat() / clipDurationMs) * width
    val endX = (region.endMs.toFloat() / clipDurationMs) * width
    val regionWidth = max(2f, endX - startX)

    if (startX > width || endX < 0f) continue

    // 1. Shaded quiet background
    drawRect(
      color = Color.Black.copy(alpha = baseAlpha),
      topLeft = Offset(startX, 1f),
      size = Size(regionWidth, height - 2f)
    )

    // 2. Subtle dashed silence floor lines
    val centerY = height / 2f
    drawLine(
      color = TextTertiary.copy(alpha = 0.45f),
      start = Offset(startX, centerY),
      end = Offset(endX, centerY),
      strokeWidth = 1.dp.toPx(),
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
    )

    // 3. Boundary tick marks at start & end of silence to indicate cut/trim points
    if (regionWidth >= 12f) {
      drawLine(
        color = TextTertiary.copy(alpha = 0.6f),
        start = Offset(startX, 2.dp.toPx()),
        end = Offset(startX, height - 2.dp.toPx()),
        strokeWidth = 1.dp.toPx()
      )
      drawLine(
        color = TextTertiary.copy(alpha = 0.6f),
        start = Offset(endX, 2.dp.toPx()),
        end = Offset(endX, height - 2.dp.toPx()),
        strokeWidth = 1.dp.toPx()
      )
    }
  }
}

/**
 * Draws mirrored vertical amplitude bars centered on the zero-crossing line.
 */
private fun DrawScope.drawMirroredBars(
  amps: FloatArray,
  width: Float,
  height: Float,
  centerY: Float,
  barWidthPx: Float,
  slotWidthPx: Float,
  trackColor: Color,
  peakColor: Color,
  crestColor: Color,
  isMuted: Boolean
) {
  val maxHalfHeight = (height * 0.44f).coerceAtLeast(4f)
  val cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)

  for (i in amps.indices) {
    val x = i * slotWidthPx
    if (x + barWidthPx > width) break

    val amp = amps[i].coerceIn(0.04f, 1.0f)
    val halfHeight = maxHalfHeight * amp
    val isHighPeak = amp >= 0.72f
    val isClipping = amp >= 0.95f

    val topY = (centerY - halfHeight).coerceAtLeast(1f)
    val barHeight = (halfHeight * 2f).coerceAtLeast(3f)

    // Gradient bar styling: soft body with bright transient peaks and clipping alert
    val barBrush = when {
      isClipping && !isMuted -> {
        Brush.verticalGradient(
          colors = listOf(
            RedAccent,
            AmberAccent,
            trackColor.copy(alpha = 0.9f),
            AmberAccent,
            RedAccent
          ),
          startY = topY,
          endY = topY + barHeight
        )
      }
      isHighPeak && !isMuted -> {
        Brush.verticalGradient(
          colors = listOf(
            peakColor,
            crestColor,
            trackColor.copy(alpha = 0.85f),
            crestColor,
            peakColor
          ),
          startY = topY,
          endY = topY + barHeight
        )
      }
      else -> {
        Brush.verticalGradient(
          colors = listOf(
            trackColor,
            trackColor.copy(alpha = 0.65f),
            trackColor
          ),
          startY = topY,
          endY = topY + barHeight
        )
      }
    }

    drawRoundRect(
      brush = barBrush,
      topLeft = Offset(x, topY),
      size = Size(barWidthPx, barHeight),
      cornerRadius = cornerRadius
    )

    // Accent crest caps on loud transient spikes
    if ((isHighPeak || isClipping) && !isMuted) {
      val capRadius = (barWidthPx / 2f).coerceAtLeast(1f)
      val capColor = if (isClipping) RedAccent else crestColor
      drawCircle(
        color = capColor,
        radius = capRadius,
        center = Offset(x + barWidthPx / 2f, topY + capRadius)
      )
      drawCircle(
        color = capColor,
        radius = capRadius,
        center = Offset(x + barWidthPx / 2f, topY + barHeight - capRadius)
      )
    }
  }
}

/**
 * Draws a solid polygon envelope of the waveform with an RMS gradient fill.
 */
private fun DrawScope.drawSolidEnvelope(
  amps: FloatArray,
  width: Float,
  height: Float,
  centerY: Float,
  trackColor: Color,
  peakColor: Color
) {
  if (amps.isEmpty()) return
  val maxHalfHeight = height * 0.44f
  val stepX = width / amps.size

  val topPath = Path()
  val bottomPath = Path()

  topPath.moveTo(0f, centerY)
  bottomPath.moveTo(0f, centerY)

  for (i in amps.indices) {
    val x = i * stepX
    val halfH = maxHalfHeight * amps[i]
    topPath.lineTo(x, centerY - halfH)
    bottomPath.lineTo(x, centerY + halfH)
  }

  topPath.lineTo(width, centerY)
  bottomPath.lineTo(width, centerY)

  val fillBrush = Brush.verticalGradient(
    colors = listOf(
      peakColor.copy(alpha = 0.7f),
      trackColor.copy(alpha = 0.4f),
      trackColor.copy(alpha = 0.4f),
      peakColor.copy(alpha = 0.7f)
    ),
    startY = 0f,
    endY = height
  )

  drawPath(topPath, brush = fillBrush)
  drawPath(bottomPath, brush = fillBrush)

  // Outline stroke
  drawPath(topPath, color = trackColor, style = Stroke(width = 1.5.dp.toPx()))
  drawPath(bottomPath, color = trackColor, style = Stroke(width = 1.5.dp.toPx()))
}

/**
 * Draws bottom-aligned vertical bars (useful for compact audio tracks).
 */
private fun DrawScope.drawBaselineBars(
  amps: FloatArray,
  width: Float,
  height: Float,
  barWidthPx: Float,
  slotWidthPx: Float,
  trackColor: Color,
  peakColor: Color
) {
  val maxHeight = height * 0.85f
  for (i in amps.indices) {
    val x = i * slotWidthPx
    if (x + barWidthPx > width) break
    val barH = (maxHeight * amps[i]).coerceAtLeast(3f)
    val isPeak = amps[i] >= 0.75f
    val isClipping = amps[i] >= 0.95f
    val color = when {
      isClipping -> RedAccent
      isPeak -> peakColor
      else -> trackColor
    }

    drawRoundRect(
      color = color,
      topLeft = Offset(x, height - barH),
      size = Size(barWidthPx, barH),
      cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
    )
  }
}

/**
 * Draws peak markers, clipping warnings, and vertical cut alignment guides along transient peaks.
 */
private fun DrawScope.drawPeakMarkers(
  peaks: List<AudioPeak>,
  clipDurationMs: Long,
  width: Float,
  height: Float,
  centerY: Float,
  peakColor: Color,
  crestColor: Color,
  playheadX: Float?,
  snapDistancePx: Float,
  pulseAlpha: Float
) {
  for (peak in peaks) {
    val peakX = (peak.timeMs.toFloat() / clipDurationMs) * width
    if (peakX < 0f || peakX > width) continue

    val isNearPlayhead = playheadX != null && abs(peakX - playheadX) <= snapDistancePx
    val markerColor = when {
      isNearPlayhead -> AmberAccent
      peak.isClipping -> RedAccent
      peak.isProminent -> crestColor
      else -> peakColor.copy(alpha = 0.75f)
    }

    // 1. Subtle dashed vertical guide line along prominent peaks to align cuts
    if (peak.isProminent || isNearPlayhead || peak.isClipping) {
      val guideAlpha = if (isNearPlayhead) 0.85f * pulseAlpha else 0.35f
      drawLine(
        color = markerColor.copy(alpha = guideAlpha),
        start = Offset(peakX, 2.dp.toPx()),
        end = Offset(peakX, height - 2.dp.toPx()),
        strokeWidth = if (isNearPlayhead) 1.5.dp.toPx() else 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
      )
    }

    // 2. Peak indicator diamond at top crest
    val markerSize = if (isNearPlayhead) 5.dp.toPx() else if (peak.isProminent) 3.5.dp.toPx() else 2.5.dp.toPx()
    val markerY = (centerY - (height * 0.44f * peak.amplitude) - markerSize).coerceAtLeast(markerSize)

    val diamondPath = Path().apply {
      moveTo(peakX, markerY - markerSize)
      lineTo(peakX + markerSize, markerY)
      lineTo(peakX, markerY + markerSize)
      lineTo(peakX - markerSize, markerY)
      close()
    }

    drawPath(path = diamondPath, color = markerColor)

    if (isNearPlayhead) {
      drawCircle(
        color = AmberAccent.copy(alpha = 0.4f * pulseAlpha),
        radius = markerSize * 2.2f,
        center = Offset(peakX, markerY)
      )
    }
  }
}

/**
 * Resamples the given waveform into [targetCount] slots using peak-preserving max pooling.
 * This guarantees that sharp transient spikes are retained even when zoomed out.
 */
private fun resamplePeakPreserving(input: List<Float>, targetCount: Int): FloatArray {
  if (input.isEmpty()) return FloatArray(targetCount)
  if (input.size == targetCount) return input.toFloatArray()

  val result = FloatArray(targetCount)
  val step = input.size.toFloat() / targetCount

  for (i in 0 until targetCount) {
    val startIndex = (i * step).toInt().coerceIn(0, input.size - 1)
    val endIndex = ((i + 1) * step).toInt().coerceIn(startIndex + 1, input.size)

    var maxVal = 0f
    for (j in startIndex until endIndex) {
      val v = input[j]
      if (v > maxVal) maxVal = v
    }
    result[i] = if (maxVal > 0f) maxVal else input[startIndex]
  }

  return result
}
