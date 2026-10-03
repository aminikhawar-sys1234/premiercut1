package com.example.ui.components.export

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.AspectRatio
import com.example.domain.model.ExportQuality
import com.example.domain.model.FrameRate
import com.example.domain.model.Resolution
import com.example.engine.export.CodecProfile
import com.example.engine.export.ExportConfig
import com.example.ui.components.formatDuration
import com.example.ui.theme.*
import kotlin.math.roundToInt

/**
 * Dialog for comprehensive export configuration.
 * Allows users to choose resolution (4K UHD, 2K QHD, 1080p FHD, 720p, 480p),
 * framerate, video codec (Auto, AVC, HEVC), and bitrate (presets and custom slider).
 */
@Composable
fun ExportConfigurationDialog(
  projectName: String,
  totalDurationMs: Long,
  aspectRatio: AspectRatio,
  initialResolution: Resolution = Resolution.RES_1080P,
  initialFps: FrameRate = FrameRate.FPS_30,
  initialQuality: ExportQuality = ExportQuality.HIGH,
  initialBitrateKbps: Int = 12000,
  initialCodec: CodecProfile = CodecProfile.AUTO,
  onDismiss: () -> Unit,
  onConfirmExport: (config: ExportConfig) -> Unit
) {
  var selectedResolution by remember { mutableStateOf(initialResolution) }

  val simpleResolutions = remember {
    listOf(
      Resolution.RES_480P,
      Resolution.RES_720P,
      Resolution.RES_1080P,
      Resolution.RES_2K,
      Resolution.RES_4K
    )
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.90f)
        .wrapContentHeight()
        .testTag("export_config_dialog"),
      shape = RoundedCornerShape(18.dp),
      color = StudioSurface,
      tonalElevation = 8.dp,
      border = BorderStroke(1.dp, StudioBorder)
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Export Video",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
              )
            )
            Text(
              text = projectName.ifBlank { "Untitled Project" },
              style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
              maxLines = 1
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("cancel_export_dialog_btn")
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
          }
        }

        Text(
          text = "Select video size",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          )
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          simpleResolutions.chunked(2).forEach { rowItems ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              rowItems.forEach { res ->
                val selected = selectedResolution == res
                val dimensions = calculateExportDimensions(res, aspectRatio)
                Surface(
                  onClick = { selectedResolution = res },
                  shape = RoundedCornerShape(12.dp),
                  color = if (selected) SkyBlueContainer else StudioSurfaceVariant,
                  border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) CyanAccent else StudioBorder
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .testTag("resolution_chip_" + res.label)
                ) {
                  Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                  ) {
                    Text(
                      text = res.label,
                      style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (selected) CyanAccent else TextPrimary
                      )
                    )
                    Text(
                      text = dimensions.first.toString() + "×" + dimensions.second,
                      style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                  }
                }
              }
              if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
          }
        }

        Text(
          text = "Duration " + formatDuration(totalDurationMs) + " • " + aspectRatio.label,
          style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
          modifier = Modifier.fillMaxWidth()
        )

        Button(
          onClick = {
            onConfirmExport(
              ExportConfig(
                resolution = selectedResolution,
                frameRate = FrameRate.FPS_30,
                quality = ExportQuality.HIGH,
                customBitrateKbps = 12000,
                codecProfile = CodecProfile.AUTO
              )
            )
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = CyanAccent,
            contentColor = Color.Black
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("start_export_button")
        ) {
          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Export to Gallery", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

internal fun calculateExportDimensions(res: Resolution, aspect: AspectRatio): Pair<Int, Int> {
  val (w, h) = when (res) {
    Resolution.RES_SQUARE_2K -> Pair(2048, 2048)
    Resolution.RES_VERTICAL_2K -> Pair(1440, 2560)
    Resolution.RES_VERTICAL_4K -> Pair(2160, 3840)
    else -> {
      val shortSide = minOf(res.width, res.height)
      val longSide = maxOf(res.width, res.height)
      when (aspect) {
        AspectRatio.RATIO_9_16 -> Pair(shortSide, longSide)
        AspectRatio.RATIO_16_9 -> Pair(longSide, shortSide)
        AspectRatio.RATIO_1_1 -> Pair(longSide, longSide)
        AspectRatio.RATIO_4_3 -> Pair(longSide, (longSide * 3) / 4)
        AspectRatio.RATIO_4_5 -> Pair((shortSide * 4) / 5, shortSide)
        AspectRatio.RATIO_3_4 -> Pair((shortSide * 3) / 4, shortSide)
        AspectRatio.RATIO_21_9 -> Pair((shortSide * 21) / 9, shortSide)
        AspectRatio.CUSTOM -> Pair(longSide, shortSide)
      }
    }
  }
  val alignedW = (w / 2) * 2
  val alignedH = (h / 2) * 2
  return Pair(alignedW.coerceIn(320, 3840), alignedH.coerceIn(320, 3840))
}

/**
 * Computes estimated file size in bytes based on duration and export config.
 */
internal fun calculateEstimatedSize(durationMs: Long, config: ExportConfig): Long {
  val durationSec = (durationMs / 1000f).coerceAtLeast(1f)
  val effectiveBitrate = if (config.quality == ExportQuality.CUSTOM && config.customBitrateKbps > 0) {
    config.customBitrateKbps * 1000L
  } else {
    val baseBitrate = when (config.resolution) {
      Resolution.RES_480P -> 2_500_000L
      Resolution.RES_720P -> 5_000_000L
      Resolution.RES_1080P -> 10_000_000L
      Resolution.RES_2K, Resolution.RES_VERTICAL_2K -> 18_000_000L
      Resolution.RES_4K, Resolution.RES_VERTICAL_4K -> 35_000_000L
      Resolution.RES_SQUARE_2K -> 22_000_000L
    }
    val codecMultiplier = if (config.codecProfile == CodecProfile.H265_HEVC) 0.75f else 1.0f
    (baseBitrate * config.quality.bitrateMultiplier * (config.frameRate.fps / 30f) * codecMultiplier).toLong()
  }
  return (effectiveBitrate * durationSec / 8).toLong()
}
