package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.model.*
import com.example.engine.SelectedTrackElement
import com.example.engine.audio.SoundEffectsCatalog
import com.example.ui.StudioViewModel
import com.example.ui.components.filter.StudioFilterPreviewCard
import com.example.ui.components.formatDuration
import com.example.ui.components.text.AddTextPanel
import com.example.ui.components.timeline.AudioVolumeEnvelopeGraph
import com.example.ui.theme.*
import java.io.File

@Composable
fun EditToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Edit Clip Operations",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
      )
      IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      EditorActionTile(icon = Icons.Default.VolumeUp, label = "Volume", color = GreenAccent) {
        viewModel.setActiveToolbarTab(com.example.ui.EditorToolbarTab.VOLUME)
      }
      EditorActionTile(icon = Icons.Default.CallSplit, label = "Split", color = CyanAccent) {
        viewModel.timelineEngine.splitSelectedClipAtPlayhead()
      }
      EditorActionTile(icon = Icons.Default.ContentCut, label = "Trim Tool", color = AmberAccent) {
        viewModel.setActiveToolbarTab(com.example.ui.EditorToolbarTab.TRIM)
      }
      EditorActionTile(icon = Icons.Default.Delete, label = "Delete", color = RedAccent) {
        viewModel.timelineEngine.deleteSelected()
      }
      EditorActionTile(icon = Icons.Default.ContentCopy, label = "Duplicate", color = PurpleAccent) {
        viewModel.timelineEngine.duplicateSelected()
      }
      EditorActionTile(icon = Icons.Default.AcUnit, label = "Freeze Frame", color = CyanAccent) {
        viewModel.timelineEngine.freezeFrameAtPlayhead()
      }
      EditorActionTile(icon = Icons.Default.RotateRight, label = "Rotate 90°", color = TextPrimary) {
        viewModel.timelineEngine.rotateSelectedClip()
      }
      EditorActionTile(icon = Icons.Default.Flip, label = "Flip H", color = TextPrimary) {
        viewModel.timelineEngine.flipSelectedClip(horizontal = true)
      }
      EditorActionTile(icon = Icons.Default.SwapVert, label = "Flip V", color = TextPrimary) {
        viewModel.timelineEngine.flipSelectedClip(horizontal = false)
      }
    }

    // Embedded Volume Slider Control
    VolumeSliderSection(viewModel = viewModel)
  }
}

@Composable
fun VolumeSliderSection(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()

  val targetClipId = when (selectedElement) {
    is SelectedTrackElement.Video -> (selectedElement as SelectedTrackElement.Video).clipId
    is SelectedTrackElement.Overlay -> (selectedElement as SelectedTrackElement.Overlay).clipId
    is SelectedTrackElement.Audio -> (selectedElement as SelectedTrackElement.Audio).clipId
    else -> timeline.videoClips.firstOrNull()?.id ?: timeline.audioClips.firstOrNull()?.id
  }

  val videoClip = timeline.videoClips.find { it.id == targetClipId }
  val overlayClip = timeline.overlayClips.find { it.id == targetClipId }
  val audioClip = timeline.audioClips.find { it.id == targetClipId }

  val clipName = videoClip?.name ?: overlayClip?.name ?: audioClip?.title ?: "Selected Track / Clip"
  val currentVol = videoClip?.volume ?: overlayClip?.volume ?: audioClip?.volume ?: 1.0f
  val isMuted = videoClip?.isMuted ?: overlayClip?.isMuted ?: audioClip?.isMuted ?: false

  var sliderVal by remember(targetClipId, currentVol, isMuted) {
    mutableFloatStateOf(if (isMuted) 0f else currentVol)
  }

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = StudioSurfaceVariant,
    border = BorderStroke(1.dp, StudioBorder),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = if (isMuted || sliderVal == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            contentDescription = null,
            tint = if (isMuted || sliderVal == 0f) RedAccent else GreenAccent,
            modifier = Modifier.size(20.dp)
          )
          Column {
            Text(
              text = "Volume Gain Adjustment",
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )
            Text(
              text = clipName,
              style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Decrease Volume Button (-)
          IconButton(
            onClick = {
              val newVol = (sliderVal - 0.10f).coerceIn(0f, 3.0f)
              sliderVal = newVol
              viewModel.timelineEngine.setClipVolume(targetClipId, newVol)
            },
            modifier = Modifier
              .size(30.dp)
              .clip(CircleShape)
              .background(StudioSurface)
              .border(1.dp, StudioBorder, CircleShape)
              .testTag("volume_decrease_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Remove,
              contentDescription = "Decrease Volume",
              tint = TextPrimary,
              modifier = Modifier.size(16.dp)
            )
          }

          // Clickable Percentage Display Chip
          Surface(
            onClick = {
              val nextVol = when {
                isMuted || sliderVal == 0f -> 1.0f
                sliderVal < 0.5f -> 0.5f
                sliderVal < 1.0f -> 1.0f
                sliderVal < 1.5f -> 1.5f
                sliderVal < 2.0f -> 2.0f
                sliderVal < 3.0f -> 3.0f
                else -> 1.0f
              }
              sliderVal = nextVol
              viewModel.timelineEngine.setClipVolume(targetClipId, nextVol)
            },
            shape = RoundedCornerShape(6.dp),
            color = if (isMuted || sliderVal == 0f) RedAccent.copy(alpha = 0.2f) else GreenAccent.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, if (isMuted || sliderVal == 0f) RedAccent else GreenAccent),
            modifier = Modifier.testTag("volume_percentage_chip")
          ) {
            Text(
              text = if (isMuted || sliderVal == 0f) "Muted" else "${(sliderVal * 100).toInt()}%",
              style = MaterialTheme.typography.labelMedium.copy(
                color = if (isMuted || sliderVal == 0f) RedAccent else GreenAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
              ),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
          }

          // Increase Volume Button (+)
          IconButton(
            onClick = {
              val newVol = if (isMuted || sliderVal == 0f) 1.0f else (sliderVal + 0.10f).coerceIn(0f, 3.0f)
              sliderVal = newVol
              viewModel.timelineEngine.setClipVolume(targetClipId, newVol)
            },
            modifier = Modifier
              .size(30.dp)
              .clip(CircleShape)
              .background(GreenAccent.copy(alpha = 0.25f))
              .border(1.dp, GreenAccent, CircleShape)
              .testTag("volume_increase_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "Increase Volume",
              tint = GreenAccent,
              modifier = Modifier.size(18.dp)
            )
          }

          // Mute / Speaker Toggle Button
          IconButton(
            onClick = {
              if (isMuted || sliderVal == 0f) {
                val newVol = if (currentVol > 0f) currentVol else 1.0f
                sliderVal = newVol
                viewModel.timelineEngine.setClipVolume(targetClipId, newVol)
              } else {
                viewModel.timelineEngine.toggleClipMute(targetClipId)
              }
            },
            modifier = Modifier
              .size(30.dp)
              .clip(CircleShape)
              .background(StudioSurface)
              .border(1.dp, StudioBorder, CircleShape)
              .testTag("volume_mute_btn")
          ) {
            Icon(
              imageVector = if (isMuted || sliderVal == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
              contentDescription = "Mute Toggle",
              tint = if (isMuted || sliderVal == 0f) RedAccent else GreenAccent,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }

      Slider(
        value = sliderVal,
        onValueChange = { newValue ->
          sliderVal = newValue
          viewModel.timelineEngine.setClipVolume(targetClipId, newValue)
        },
        valueRange = 0f..3.0f,
        colors = SliderDefaults.colors(
          thumbColor = GreenAccent,
          activeTrackColor = GreenAccent,
          inactiveTrackColor = StudioBorder
        ),
        modifier = Modifier.testTag("volume_gain_slider")
      )

      val presets = listOf(
        0.0f to "Mute",
        0.5f to "50%",
        1.0f to "100%",
        1.5f to "150%",
        2.0f to "200% Boost",
        3.0f to "300% Max"
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        presets.forEach { (volVal, label) ->
          val isSelected = (sliderVal - volVal).let { kotlin.math.abs(it) < 0.05f }
          Surface(
            onClick = {
              sliderVal = volVal
              viewModel.timelineEngine.setClipVolume(targetClipId, volVal)
            },
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) GreenAccent.copy(alpha = 0.25f) else StudioSurface,
            border = BorderStroke(1.dp, if (isSelected) GreenAccent else StudioBorder),
            modifier = Modifier.testTag("vol_preset_${label.replace("%", "").replace(" ", "_").lowercase()}")
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall.copy(
                color = if (isSelected) GreenAccent else TextPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 10.sp
              ),
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
fun VolumeToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()

  val targetClipId = when (selectedElement) {
    is SelectedTrackElement.Video -> (selectedElement as SelectedTrackElement.Video).clipId
    is SelectedTrackElement.Overlay -> (selectedElement as SelectedTrackElement.Overlay).clipId
    is SelectedTrackElement.Audio -> (selectedElement as SelectedTrackElement.Audio).clipId
    else -> timeline.videoClips.firstOrNull()?.id ?: timeline.audioClips.firstOrNull()?.id
  }

  val videoClip = timeline.videoClips.find { it.id == targetClipId }
  val audioClip = timeline.audioClips.find { it.id == targetClipId }
  val currentAudioFx = videoClip?.audioEffects ?: audioClip?.audioEffects ?: AudioEffectsSettings()

  var noiseReduceEnabled by remember(targetClipId, currentAudioFx.noiseReductionDb) {
    mutableStateOf(currentAudioFx.noiseReductionDb > 0.0f)
  }
  var noiseDbLevel by remember(targetClipId, currentAudioFx.noiseReductionDb) {
    mutableFloatStateOf(if (currentAudioFx.noiseReductionDb > 0.0f) currentAudioFx.noiseReductionDb else 12.0f)
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = GreenAccent)
        Text(
          text = "Track / Clip Volume & Noise Controls",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
      }
      IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    VolumeSliderSection(viewModel = viewModel)

    // Noise Reduction Controls Section
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = StudioSurfaceVariant,
      border = BorderStroke(1.dp, StudioBorder),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.GraphicEq,
              contentDescription = null,
              tint = CyanAccent,
              modifier = Modifier.size(20.dp)
            )
            Column {
              Text(
                text = "Reduce Noise",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
              )
              Text(
                text = "Spectral noise gate & hum reduction",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
              )
            }
          }

          Switch(
            checked = noiseReduceEnabled,
            onCheckedChange = { enabled ->
              noiseReduceEnabled = enabled
              val updatedDb = if (enabled) noiseDbLevel else 0.0f
              val newFx = currentAudioFx.copy(noiseReductionDb = updatedDb)
              viewModel.timelineEngine.setClipAudioEffects(targetClipId, newFx)
            },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = CyanAccent,
              uncheckedThumbColor = TextSecondary,
              uncheckedTrackColor = StudioBorder
            ),
            modifier = Modifier.testTag("reduce_noise_switch")
          )
        }

        if (noiseReduceEnabled) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Background Noise Reduction",
                style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
              )
              Text(
                text = "${noiseDbLevel.toInt()} dB",
                style = MaterialTheme.typography.labelMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
              )
            }

            Slider(
              value = noiseDbLevel,
              onValueChange = { db ->
                noiseDbLevel = db
                val newFx = currentAudioFx.copy(noiseReductionDb = db)
                viewModel.timelineEngine.setClipAudioEffects(targetClipId, newFx)
              },
              valueRange = 3.0f..24.0f,
              steps = 20,
              colors = SliderDefaults.colors(
                thumbColor = CyanAccent,
                activeTrackColor = CyanAccent,
                inactiveTrackColor = StudioBorder
              ),
              modifier = Modifier.testTag("noise_reduction_db_slider")
            )
          }
        }
      }
    }
  }
}

@Composable
fun AdjustToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  var currentAdjustments by remember(timeline.adjustments) { mutableStateOf(timeline.adjustments) }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp)
      .heightIn(max = 340.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Color Grading & Adjustments",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
      )
      Row {
        TextButton(onClick = {
          currentAdjustments = VideoAdjustments()
          viewModel.timelineEngine.updateAdjustments(currentAdjustments)
        }) {
          Text("Reset All", color = RedAccent, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
        }
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      item {
        AdjustmentSlider(
          label = "Brightness",
          value = currentAdjustments.brightness,
          valueRange = -0.5f..0.5f,
          onValueChange = {
            currentAdjustments = currentAdjustments.copy(brightness = it)
            viewModel.timelineEngine.updateAdjustments(currentAdjustments)
          }
        )
      }
      item {
        AdjustmentSlider(
          label = "Contrast",
          value = currentAdjustments.contrast,
          valueRange = 0.5f..1.5f,
          onValueChange = {
            currentAdjustments = currentAdjustments.copy(contrast = it)
            viewModel.timelineEngine.updateAdjustments(currentAdjustments)
          }
        )
      }
      item {
        AdjustmentSlider(
          label = "Saturation",
          value = currentAdjustments.saturation,
          valueRange = 0.0f..2.0f,
          onValueChange = {
            currentAdjustments = currentAdjustments.copy(saturation = it)
            viewModel.timelineEngine.updateAdjustments(currentAdjustments)
          }
        )
      }
      item {
        AdjustmentSlider(
          label = "Exposure",
          value = currentAdjustments.exposure,
          valueRange = -0.5f..0.5f,
          onValueChange = {
            currentAdjustments = currentAdjustments.copy(exposure = it)
            viewModel.timelineEngine.updateAdjustments(currentAdjustments)
          }
        )
      }
      item {
        AdjustmentSlider(
          label = "Temperature",
          value = currentAdjustments.temperature,
          valueRange = -0.5f..0.5f,
          onValueChange = {
            currentAdjustments = currentAdjustments.copy(temperature = it)
            viewModel.timelineEngine.updateAdjustments(currentAdjustments)
          }
        )
      }
      item {
        AdjustmentSlider(
          label = "Tint",
          value = currentAdjustments.tint,
          valueRange = -0.5f..0.5f,
          onValueChange = {
            currentAdjustments = currentAdjustments.copy(tint = it)
            viewModel.timelineEngine.updateAdjustments(currentAdjustments)
          }
        )
      }
      item {
        AdjustmentSlider(
          label = "Highlights",
          value = currentAdjustments.highlights,
          valueRange = -1.0f..1.0f,
          onValueChange = {
            currentAdjustments = currentAdjustments.copy(highlights = it)
            viewModel.timelineEngine.updateAdjustments(currentAdjustments)
          }
        )
      }
      item {
        AdjustmentSlider(
          label = "Shadows",
          value = currentAdjustments.shadows,
          valueRange = -1.0f..1.0f,
          onValueChange = {
            currentAdjustments = currentAdjustments.copy(shadows = it)
            viewModel.timelineEngine.updateAdjustments(currentAdjustments)
          }
        )
      }
      item {
        AdjustmentSlider(
          label = "Sharpness",
          value = currentAdjustments.sharpness,
          valueRange = 0f..2.0f,
          onValueChange = {
            currentAdjustments = currentAdjustments.copy(sharpness = it)
            viewModel.timelineEngine.updateAdjustments(currentAdjustments)
          }
        )
      }
      item {
        AdjustmentSlider(
          label = "Vignette",
          value = currentAdjustments.vignette,
          valueRange = 0f..1.0f,
          onValueChange = {
            currentAdjustments = currentAdjustments.copy(vignette = it)
            viewModel.timelineEngine.updateAdjustments(currentAdjustments)
          }
        )
      }
      item {
        AdjustmentSlider(
          label = "Film Grain",
          value = currentAdjustments.grain,
          valueRange = 0f..1.0f,
          onValueChange = {
            currentAdjustments = currentAdjustments.copy(grain = it)
            viewModel.timelineEngine.updateAdjustments(currentAdjustments)
          }
        )
      }
    }
  }
}

@Composable
private fun AdjustmentSlider(
  label: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(label, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
      Text(
        String.format("%.2f", value),
        style = MaterialTheme.typography.bodySmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
      )
    }
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      colors = SliderDefaults.colors(
        thumbColor = CyanAccent,
        activeTrackColor = CyanAccent,
        inactiveTrackColor = StudioBorder
      )
    )
  }
}

@Composable
fun SpeedToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()

  val speedPlayheadMs by viewModel.timelineEngine.currentPositionMs.collectAsState()
  val selectedClip = remember(timeline, selectedElement, speedPlayheadMs) {
    if (selectedElement is SelectedTrackElement.Video) {
      timeline.videoClips.find { it.id == (selectedElement as SelectedTrackElement.Video).clipId }
    } else {
      timeline.videoClips.find { speedPlayheadMs >= it.timelineStartMs && speedPlayheadMs < it.timelineStartMs + it.durationMs }
        ?: timeline.videoClips.firstOrNull()
    }
  }

  val speedPresets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Speed & Curve Ramping",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
      )
      IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    Text(
      text = "Current Speed: ${selectedClip?.speed ?: 1.0f}x",
      style = MaterialTheme.typography.bodyMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(speedPresets) { speed ->
        val isSelected = selectedClip?.speed == speed
        FilterChip(
          selected = isSelected,
          onClick = {
            selectedClip?.let {
              viewModel.timelineEngine.setClipSpeed(it.id, speed)
            }
          },
          label = { Text("${speed}x") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyanAccent,
            selectedLabelColor = Color.Black,
            containerColor = StudioSurfaceVariant,
            labelColor = TextPrimary
          )
        )
      }
    }

    // Speed curve presets
    Text(
      text = "Curve Presets",
      style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      listOf("Montage Ramp", "Hero Slow-Mo", "Bullet Time", "Jump Flash").forEach { curveName ->
        AssistChip(
          onClick = {
            selectedClip?.let {
              val preset = when (curveName) {
                "Montage Ramp" -> com.example.domain.model.SpeedCurvePreset.HERO_MONTAGE
                "Hero Slow-Mo" -> com.example.domain.model.SpeedCurvePreset.EASE_OUT
                "Bullet Time" -> com.example.domain.model.SpeedCurvePreset.BULLET_TIME
                else -> com.example.domain.model.SpeedCurvePreset.JUMPER
              }
              viewModel.timelineEngine.setClipSpeedCurve(it.id, com.example.domain.model.SpeedCurve(preset = preset))
            }
          },
          label = { Text(curveName, fontSize = 11.sp) },
          colors = AssistChipDefaults.assistChipColors(containerColor = StudioSurfaceVariant, labelColor = TextPrimary)
        )
      }
    }
  }
}

@Composable
fun FiltersToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()
  val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()
  
  // Identify selected clip if any, or active clip under playhead
  val selectedClip = remember(timeline, selectedElement, currentPosMs) {
    val fromSelection = when (selectedElement) {
      is SelectedTrackElement.Video -> timeline.videoClips.find { it.id == (selectedElement as SelectedTrackElement.Video).clipId }
      is SelectedTrackElement.Overlay -> timeline.overlayClips.find { it.id == (selectedElement as SelectedTrackElement.Overlay).clipId }
      else -> null
    }
    fromSelection
      ?: timeline.videoClips.find { currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineStartMs + it.durationMs }
      ?: timeline.videoClips.firstOrNull()
  }

  // Active filter either from selected clip or global timeline filter
  val activeFilterState = selectedClip?.filter ?: FilterSettings()
  var currentFilter by remember(activeFilterState) { mutableStateOf(activeFilterState) }
  var selectedCategory by remember { mutableStateOf("All") }
  val categories = listOf("All", "Pro Enhancements", "Cinematic & Nature", "Aesthetic Looks")

  val displayFilters = remember(selectedCategory) {
    when (selectedCategory) {
      "All" -> FilterType.values().toList()
      "Pro Enhancements" -> FilterType.values().filter { it.category == "Pro Enhancements" || it == FilterType.NONE }
      "Cinematic & Nature" -> FilterType.values().filter { it.category == "Cinematic & Nature" || it == FilterType.NONE }
      "Aesthetic Looks" -> FilterType.values().filter { it.category == "Aesthetic Looks" || it == FilterType.NONE }
      else -> emptyList()
    }
  }

  // Active preview video reference from timeline clip
  val previewUri = remember(selectedClip, timeline) {
    selectedClip?.uri?.ifBlank { null }
      ?: timeline.videoClips.firstOrNull { it.uri.isNotBlank() }?.uri
      ?: ""
  }
  val previewSourceStartMs = selectedClip?.sourceStartMs ?: 0L

  // Scroll state & visible item tracking so off-screen cards pause video decoding
  val filtersScrollState = rememberLazyListState()
  val visibleIndices by remember {
    derivedStateOf {
      filtersScrollState.layoutInfo.visibleItemsInfo.map { it.index }.toSet()
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(PurpleAccent.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(18.dp))
        }
        Column {
          Text(
            text = "Professional Video Filters",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
          )
          Text(
            text = if (selectedClip != null) "Editing: ${selectedClip.name}" else "Editing: Project Timeline (Global)",
            style = MaterialTheme.typography.bodySmall.copy(color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // Toggle Filter On/Off for current clip
        FilledTonalIconButton(
          onClick = {
            if (currentFilter.type != FilterType.NONE) {
              currentFilter = FilterSettings(type = FilterType.NONE, intensity = 1.0f)
              viewModel.timelineEngine.updateFilter(currentFilter, selectedClip?.id)
            } else {
              currentFilter = FilterSettings(type = FilterType.CINEMATIC, intensity = 1.0f)
              viewModel.timelineEngine.updateFilter(currentFilter, selectedClip?.id)
            }
          },
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (currentFilter.type != FilterType.NONE) PurpleAccent.copy(alpha = 0.25f) else StudioSurfaceVariant,
            contentColor = if (currentFilter.type != FilterType.NONE) PurpleAccent else TextSecondary
          ),
          modifier = Modifier
            .size(36.dp)
            .testTag("filter_toggle_segment_button")
        ) {
          Icon(
            if (currentFilter.type != FilterType.NONE) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            contentDescription = "Toggle Filter for Segment",
            modifier = Modifier.size(18.dp)
          )
        }

        if (currentFilter.type != FilterType.NONE) {
          TextButton(
            onClick = {
              currentFilter = FilterSettings(type = FilterType.NONE, intensity = 1.0f)
              viewModel.timelineEngine.updateFilter(currentFilter, selectedClip?.id)
              viewModel.timelineEngine.updateAdjustments(VideoAdjustments())
            },
            modifier = Modifier.testTag("filter_reset_button")
          ) {
            Text("Reset", color = TextSecondary, fontSize = 12.sp)
          }
        }
        IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
        }
      }
    }

    // Category Tabs
    ScrollableTabRow(
      selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
      edgePadding = 0.dp,
      containerColor = Color.Transparent,
      contentColor = PurpleAccent,
      divider = {},
      modifier = Modifier.fillMaxWidth()
    ) {
      categories.forEach { cat ->
        val isSelected = selectedCategory == cat
        Tab(
          selected = isSelected,
          onClick = { selectedCategory = cat },
          text = {
            Text(
              text = cat,
              color = if (isSelected) PurpleAccent else TextSecondary,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              fontSize = 12.sp
            )
          }
        )
      }
    }

    // Live Animated Filter Previews Grid / Row
    LazyRow(
      state = filtersScrollState,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
      itemsIndexed(displayFilters) { index, type ->
        val isSelected = currentFilter.type == type
        val isVisible = index in visibleIndices || visibleIndices.isEmpty()

        StudioFilterPreviewCard(
          type = type,
          isSelected = isSelected,
          isVisible = isVisible,
          videoUri = previewUri,
          sourceStartMs = previewSourceStartMs,
          onClick = {
            if (type == FilterType.NONE) {
              currentFilter = FilterSettings(type = FilterType.NONE, intensity = 1.0f)
              viewModel.timelineEngine.updateFilter(currentFilter, selectedClip?.id)
              viewModel.timelineEngine.updateAdjustments(VideoAdjustments())
            } else {
              val targetIntensity = if (currentFilter.intensity <= 0.05f) 1.0f else currentFilter.intensity
              currentFilter = FilterSettings(type = type, intensity = targetIntensity)
              viewModel.timelineEngine.updateFilter(currentFilter, selectedClip?.id)
            }
          }
        )
      }
    }

    // Filter Intensity Slider & Quick Controls
    if (currentFilter.type != FilterType.NONE) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = StudioSurfaceVariant,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "${currentFilter.type.displayName} Intensity",
                style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
              )
            }
            Text(
              text = "${(currentFilter.intensity * 100).toInt()}%",
              style = MaterialTheme.typography.bodySmall.copy(color = PurpleAccent, fontWeight = FontWeight.Bold)
            )
          }

          Slider(
            value = currentFilter.intensity,
            onValueChange = {
              currentFilter = currentFilter.copy(intensity = it)
              viewModel.timelineEngine.updateFilter(currentFilter, selectedClip?.id)
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
              thumbColor = PurpleAccent,
              activeTrackColor = PurpleAccent,
              inactiveTrackColor = StudioBorder
            ),
            modifier = Modifier.testTag("filter_intensity_slider")
          )

          // Quick Preset Chips & Compare / Apply to All actions
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Preset percentage buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              listOf(0.25f, 0.50f, 0.75f, 1.0f).forEach { preset ->
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (kotlin.math.abs(currentFilter.intensity - preset) < 0.05f) PurpleAccent.copy(alpha = 0.2f) else Color.Transparent,
                  border = BorderStroke(1.dp, if (kotlin.math.abs(currentFilter.intensity - preset) < 0.05f) PurpleAccent else StudioBorder),
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                      currentFilter = currentFilter.copy(intensity = preset)
                      viewModel.timelineEngine.updateFilter(currentFilter, selectedClip?.id)
                    }
                ) {
                  Text(
                    text = "${(preset * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontSize = 10.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = if (kotlin.math.abs(currentFilter.intensity - preset) < 0.05f) PurpleAccent else TextSecondary
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                  )
                }
              }
            }

            // Compare & Apply to All actions
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              var isComparingOriginal by remember { mutableStateOf(false) }

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isComparingOriginal) AmberAccent.copy(alpha = 0.25f) else StudioSurface,
                border = BorderStroke(1.dp, if (isComparingOriginal) AmberAccent else StudioBorder),
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .pointerInput(currentFilter, selectedClip) {
                    detectTapGestures(
                      onPress = {
                        isComparingOriginal = true
                        val savedFilter = currentFilter
                        viewModel.timelineEngine.updateFilter(FilterSettings(type = FilterType.NONE, intensity = 1.0f), selectedClip?.id)
                        tryAwaitRelease()
                        isComparingOriginal = false
                        viewModel.timelineEngine.updateFilter(savedFilter, selectedClip?.id)
                      }
                    )
                  }
                  .testTag("filter_compare_button")
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Icon(
                    Icons.Default.Compare,
                    contentDescription = "Hold to Compare",
                    tint = if (isComparingOriginal) AmberAccent else TextSecondary,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (isComparingOriginal) "Original" else "Compare",
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontSize = 11.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = if (isComparingOriginal) AmberAccent else TextSecondary
                    )
                  )
                }
              }

              // Apply to All Clips button
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = StudioSurface,
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .clickable {
                    viewModel.timelineEngine.applyFilterToAllClips(currentFilter)
                  }
                  .testTag("filter_apply_all_button")
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Icon(
                    Icons.Default.DoneAll,
                    contentDescription = "Apply to All Clips",
                    tint = PurpleAccent,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Apply All",
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontSize = 11.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = PurpleAccent
                    )
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun EffectsToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  com.example.ui.components.effects.EffectsCategoryPanel(
    category = com.example.engine.effects.registry.EffectCategory.VIDEO_EFFECTS,
    viewModel = viewModel,
    onClose = { viewModel.setActiveToolbarTab(null) },
    onApply = { effect ->
      if (effect != null && effect.effectType != null) {
        viewModel.timelineEngine.addEffectClip(effect.effectType)
      }
    },
    modifier = modifier
  )
}

@Composable
fun TextEditorPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  AddTextPanel(
    viewModel = viewModel,
    onClose = { viewModel.setActiveToolbarTab(null) },
    onOpenPainting = {},
    modifier = modifier
  )
}

@Composable
fun AudioFadeControlsCard(
  audioClip: AudioClip,
  onFadeInChanged: (Long) -> Unit,
  onFadeOutChanged: (Long) -> Unit,
  onAddKeyframeAtPlayhead: () -> Unit,
  onResetEnvelope: () -> Unit,
  onApplyAutoFadesAll: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface, RoundedCornerShape(8.dp))
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // Fade-In Section
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
        Text("Fade-In Duration", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
      }
      Text("${(audioClip.fadeInMs / 1000f).formatSec()}s", style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
    }

    Slider(
      value = audioClip.fadeInMs.toFloat(),
      onValueChange = { onFadeInChanged(it.toLong()) },
      valueRange = 0f..(audioClip.durationMs / 2f).coerceAtLeast(100f),
      colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent, inactiveTrackColor = StudioBorder),
      modifier = Modifier.height(24.dp)
    )

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      listOf(0L to "Off", 300L to "0.3s", 500L to "0.5s", 1000L to "1.0s", 2000L to "2.0s").forEach { (ms, label) ->
        val isSelected = kotlin.math.abs(audioClip.fadeInMs - ms) < 50L
        Surface(
          onClick = { onFadeInChanged(ms) },
          shape = RoundedCornerShape(4.dp),
          color = if (isSelected) CyanAccent else StudioSurfaceVariant,
          border = BorderStroke(1.dp, if (isSelected) CyanAccent else StudioBorder)
        ) {
          Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) Color.Black else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }
    }

    HorizontalDivider(color = StudioBorder, thickness = 0.5.dp)

    // Fade-Out Section
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Default.TrendingDown, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(16.dp))
        Text("Fade-Out Duration", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
      }
      Text("${(audioClip.fadeOutMs / 1000f).formatSec()}s", style = MaterialTheme.typography.labelSmall.copy(color = PurpleAccent, fontWeight = FontWeight.Bold))
    }

    Slider(
      value = audioClip.fadeOutMs.toFloat(),
      onValueChange = { onFadeOutChanged(it.toLong()) },
      valueRange = 0f..(audioClip.durationMs / 2f).coerceAtLeast(100f),
      colors = SliderDefaults.colors(thumbColor = PurpleAccent, activeTrackColor = PurpleAccent, inactiveTrackColor = StudioBorder),
      modifier = Modifier.height(24.dp)
    )

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      listOf(0L to "Off", 300L to "0.3s", 500L to "0.5s", 1000L to "1.0s", 2000L to "2.0s").forEach { (ms, label) ->
        val isSelected = kotlin.math.abs(audioClip.fadeOutMs - ms) < 50L
        Surface(
          onClick = { onFadeOutChanged(ms) },
          shape = RoundedCornerShape(4.dp),
          color = if (isSelected) PurpleAccent else StudioSurfaceVariant,
          border = BorderStroke(1.dp, if (isSelected) PurpleAccent else StudioBorder)
        ) {
          Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) Color.White else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }
    }

    HorizontalDivider(color = StudioBorder, thickness = 0.5.dp)

    // Keyframing Quick Buttons Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Button(
        onClick = onAddKeyframeAtPlayhead,
        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
      ) {
        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("+ Keyframe at Playhead", fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }

      OutlinedButton(
        onClick = onApplyAutoFadesAll,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, GreenAccent),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
      ) {
        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Auto-Fade All Tracks", fontSize = 10.sp, color = GreenAccent, fontWeight = FontWeight.Bold)
      }

      IconButton(
        onClick = onResetEnvelope,
        modifier = Modifier.size(32.dp)
      ) {
        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Keyframes", tint = TextSecondary, modifier = Modifier.size(18.dp))
      }
    }
  }
}

private fun Float.formatSec(): String {
  return String.format(java.util.Locale.US, "%.1f", this)
}

@Composable
fun AudioToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var selectedTab by remember { mutableStateOf("Fades & Keyframes") } // "Fades & Keyframes", "Import", "Voiceover", "SFX", "Music"
  val isRecording by viewModel.audioEngine.isRecording.collectAsState()
  val recordDuration by viewModel.audioEngine.recordingDurationMs.collectAsState()
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()

  // Mic permission launcher for voice recording
  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      viewModel.audioEngine.startVoiceRecording {}
    } else {
      Toast.makeText(context, "Microphone permission required to record audio", Toast.LENGTH_SHORT).show()
    }
  }

  // File picker launcher for importing audio files directly from device
  val audioPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val fileName = getFileNameFromUri(context, uri) ?: "Imported Audio"
        val metadata = com.example.engine.media.MediaMetadataHelper.extractMetadata(context, uri.toString())
        val durationMs = if (metadata.durationMs > 0L) metadata.durationMs else 6000L

        val fileObj = try {
          val path = uri.path
          if (path != null && File(path).exists()) File(path) else null
        } catch (e: Exception) { null }
        val waveform = if (fileObj != null) viewModel.audioEngine.extractWaveformFromFile(fileObj) else null

        viewModel.timelineEngine.addAudioClip(
          title = fileName,
          durationMs = durationMs,
          uri = uri.toString(),
          waveformData = waveform
        )
        Toast.makeText(context, "Imported \"$fileName\" with automatic fade transitions!", Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {
        Toast.makeText(context, "Failed to import audio: ${e.message}", Toast.LENGTH_SHORT).show()
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Audio & Sound Design",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
      )
      IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    // Filter Chips Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilterChip(
        selected = selectedTab == "Fades & Keyframes",
        onClick = { selectedTab = "Fades & Keyframes" },
        label = { Text("Fades & Keyframes") },
        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberAccent, selectedLabelColor = Color.Black)
      )
      FilterChip(
        selected = selectedTab == "Import",
        onClick = { selectedTab = "Import" },
        label = { Text("Import Audio") },
        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GreenAccent, selectedLabelColor = Color.Black)
      )
      FilterChip(
        selected = selectedTab == "Voiceover",
        onClick = { selectedTab = "Voiceover" },
        label = { Text("Voiceover Record") },
        leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RedAccent, selectedLabelColor = Color.White)
      )
      FilterChip(
        selected = selectedTab == "SFX",
        onClick = { selectedTab = "SFX" },
        label = { Text("Sound Effects") },
        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanAccent, selectedLabelColor = Color.Black)
      )
      FilterChip(
        selected = selectedTab == "Music",
        onClick = { selectedTab = "Music" },
        label = { Text("Music Tracks") },
        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PurpleAccent, selectedLabelColor = Color.White)
      )
    }

    when (selectedTab) {
      "Fades & Keyframes" -> {
        val selectedAudioClip = timeline.audioClips.find { clip ->
          (selectedElement as? SelectedTrackElement.Audio)?.clipId == clip.id
        } ?: timeline.audioClips.firstOrNull()

        if (selectedAudioClip != null) {
          val playheadMs by viewModel.timelineEngine.currentPositionMs.collectAsState()

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(StudioSurfaceVariant)
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Audiotrack, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(18.dp))
                Text(
                  text = selectedAudioClip.title.ifBlank { "Audio Track" },
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
              }
            }

            // Interactive Volume Envelope Graph
            AudioVolumeEnvelopeGraph(
              audioClip = selectedAudioClip,
              clipDurationMs = selectedAudioClip.durationMs,
              currentPlayheadMs = playheadMs,
              showControlsHeader = true,
              onAddKeyframe = { relTime, vol ->
                viewModel.timelineEngine.addAudioVolumeKeyframe(selectedAudioClip.id, relTime, vol)
              },
              onUpdateKeyframe = { kfId, newTime, newVol ->
                viewModel.timelineEngine.updateAudioVolumeKeyframe(selectedAudioClip.id, kfId, newTime, newVol)
              },
              onDeleteKeyframe = { kfId ->
                viewModel.timelineEngine.deleteAudioVolumeKeyframe(selectedAudioClip.id, kfId)
              },
              onFadeInChanged = { newFadeIn ->
                viewModel.timelineEngine.setAudioFade(selectedAudioClip.id, newFadeIn, selectedAudioClip.fadeOutMs)
              },
              onFadeOutChanged = { newFadeOut ->
                viewModel.timelineEngine.setAudioFade(selectedAudioClip.id, selectedAudioClip.fadeInMs, newFadeOut)
              },
              onApplyPresetFade = { presetType ->
                when (presetType) {
                  "fadeIn" -> viewModel.timelineEngine.setAudioFade(selectedAudioClip.id, 1000L, selectedAudioClip.fadeOutMs)
                  "fadeOut" -> viewModel.timelineEngine.setAudioFade(selectedAudioClip.id, selectedAudioClip.fadeInMs, 1000L)
                  else -> viewModel.timelineEngine.applyAudioFadeKeyframes(selectedAudioClip.id)
                }
              },
              onResetEnvelope = {
                viewModel.timelineEngine.resetAudioVolumeEnvelope(selectedAudioClip.id)
              },
              onBaseVolumeChanged = { newVol ->
                viewModel.timelineEngine.setAudioClipBaseVolume(selectedAudioClip.id, newVol)
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
            )

            // Fade Sliders & Quick Presets
            AudioFadeControlsCard(
              audioClip = selectedAudioClip,
              onFadeInChanged = { fadeIn ->
                viewModel.timelineEngine.setAudioFade(selectedAudioClip.id, fadeIn, selectedAudioClip.fadeOutMs)
              },
              onFadeOutChanged = { fadeOut ->
                viewModel.timelineEngine.setAudioFade(selectedAudioClip.id, selectedAudioClip.fadeInMs, fadeOut)
              },
              onAddKeyframeAtPlayhead = {
                val relTime = (playheadMs - selectedAudioClip.timelineStartMs).coerceIn(0L, selectedAudioClip.durationMs)
                val currentVol = com.example.engine.KeyframeInterpolator.interpolateVolume(selectedAudioClip, relTime)
                viewModel.timelineEngine.addAudioVolumeKeyframe(selectedAudioClip.id, relTime, currentVol)
              },
              onResetEnvelope = {
                viewModel.timelineEngine.resetAudioVolumeEnvelope(selectedAudioClip.id)
              },
              onApplyAutoFadesAll = {
                viewModel.timelineEngine.applyAutoFadesToAllAudioClips()
              }
            )
          }
        } else {
          // Empty State if no audio clip
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text("No Audio Clips on Timeline", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary))
              Text("Import audio or record a voiceover to edit volume keyframes and fade transitions.", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, textAlign = TextAlign.Center))
              Button(
                onClick = { viewModel.timelineEngine.ensureAudioTrackExists() },
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black)
              ) {
                Text("+ Create Master Audio Track", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
      "Import" -> {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
          colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
          border = BorderStroke(1.dp, GreenAccent.copy(alpha = 0.5f))
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.AudioFile, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(26.dp))
              Text(
                "Add Audio Files From Device",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
              )
            }
            Text(
              "Select MP3, WAV, AAC, M4A, OGG, or FLAC files directly from your phone's storage to add background music or voice clips.",
              style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, textAlign = TextAlign.Center)
            )
            Button(
              onClick = { audioPickerLauncher.launch("audio/*") },
              colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = Color.Black),
              shape = RoundedCornerShape(8.dp)
            ) {
              Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Browse & Import Audio", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
      "Voiceover" -> {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioSurfaceVariant)
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              if (isRecording) Icons.Default.RadioButtonChecked else Icons.Default.Mic,
              contentDescription = null,
              tint = if (isRecording) RedAccent else CyanAccent,
              modifier = Modifier.size(24.dp)
            )
            Text(
              text = if (isRecording) "Recording Voiceover: ${formatDuration(recordDuration)}" else "Record Voiceover",
              style = MaterialTheme.typography.titleSmall.copy(
                color = if (isRecording) RedAccent else TextPrimary,
                fontWeight = FontWeight.Bold
              )
            )
          }

          if (isRecording) {
            val currentDb by viewModel.audioEngine.currentDecibels.collectAsState()
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              val normalizedAmp = ((currentDb + 60f) / 60f).coerceIn(0.1f, 1.0f)
              repeat(16) { index ->
                val barHeight = (12 * normalizedAmp * (0.5f + 0.5f * kotlin.math.sin(index * 0.8f + System.currentTimeMillis() * 0.005f))).dp.coerceAtLeast(4.dp)
                Box(
                  modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(4.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RedAccent)
                )
              }
            }
          } else {
            Text(
              "Tap record to capture live audio from your microphone at current playhead.",
              style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, textAlign = TextAlign.Center)
            )
          }

          Button(
            onClick = {
              if (isRecording) {
                val finalDuration = if (recordDuration > 200L) recordDuration else 2000L
                val file = viewModel.audioEngine.stopVoiceRecording()
                val waveform = viewModel.audioEngine.extractWaveformFromFile(file)
                viewModel.timelineEngine.addAudioClip(
                  title = "Voiceover",
                  durationMs = finalDuration,
                  uri = file.absolutePath,
                  waveformData = waveform
                )
                Toast.makeText(context, "Voiceover recorded and saved!", Toast.LENGTH_SHORT).show()
              } else {
                val hasPermission = ContextCompat.checkSelfPermission(
                  context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                  viewModel.audioEngine.startVoiceRecording {}
                } else {
                  micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isRecording) RedAccent else CyanAccent,
              contentColor = Color.Black
            ),
            shape = CircleShape,
            modifier = Modifier.size(60.dp)
          ) {
            Icon(
              if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
              contentDescription = "Mic Record",
              modifier = Modifier.size(28.dp)
            )
          }
        }
      }
      "SFX" -> {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          items(SoundEffectsCatalog.effects) { sfx ->
            Card(
              modifier = Modifier
                .size(width = 130.dp, height = 90.dp)
                .clip(RoundedCornerShape(12.dp)),
              colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(sfx.icon, fontSize = 20.sp)
                  IconButton(
                    onClick = { viewModel.audioEngine.playPreviewSfx(sfx.id) },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = CyanAccent)
                  }
                }
                Text(sfx.title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary), maxLines = 1)
                Button(
                  onClick = {
                    val sfxFile = viewModel.audioEngine.getOrCreateSoundWavFile(sfx.id, sfx.durationMs, sfx.category)
                    val waveform = viewModel.audioEngine.extractWaveformFromFile(sfxFile)
                    viewModel.timelineEngine.addAudioClip(
                      title = sfx.title,
                      durationMs = sfx.durationMs,
                      uri = sfxFile.absolutePath,
                      waveformData = waveform
                    )
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                  contentPadding = PaddingValues(0.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
                ) {
                  Text("+ Add", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }
      "Music" -> {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          items(SoundEffectsCatalog.musicTracks) { track ->
            Card(
              modifier = Modifier
                .size(width = 150.dp, height = 95.dp)
                .clip(RoundedCornerShape(12.dp)),
              colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
              ) {
                Text(track.icon, fontSize = 20.sp)
                Text(track.title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary), maxLines = 1)
                Button(
                  onClick = {
                    val musicFile = viewModel.audioEngine.getOrCreateSoundWavFile(track.id, track.durationMs, track.category)
                    val waveform = viewModel.audioEngine.extractWaveformFromFile(musicFile)
                    viewModel.timelineEngine.addAudioClip(
                      title = track.title,
                      durationMs = track.durationMs,
                      uri = musicFile.absolutePath,
                      waveformData = waveform
                    )
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                  contentPadding = PaddingValues(0.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White)
                ) {
                  Text("+ Add to Track", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }
    }

    // Audio Track Volume Gain Slider
    VolumeSliderSection(viewModel = viewModel)
  }
}

@Composable
fun ChromaKeyPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  var chroma by remember(timeline.chromaKey) { mutableStateOf(timeline.chromaKey) }

  val bgPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      coroutineScope.launch {
        val persistentPath = com.example.engine.media.MediaPersistenceManager.persistMedia(
          context = context,
          sourceUriString = uri.toString(),
          suggestedName = "Chroma Background"
        )
        chroma = chroma.copy(backgroundUri = persistentPath, backgroundType = "Image")
        viewModel.timelineEngine.updateChromaKey(chroma)
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. Single Compact Header: [ 🎨 Chroma Key    X ]
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(40.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E283E)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Palette,
            contentDescription = null,
            tint = Color(0xFF00C2FF),
            modifier = Modifier.size(16.dp)
          )
        }
        Text(
          text = "Chroma Key",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 14.sp
          )
        )
      }

      IconButton(
        onClick = { viewModel.setActiveToolbarTab(null) },
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(Color(0xFF1E283E))
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close",
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(Color(0xFF1E283E))
    )

    // Enable Chroma Key Toggle Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Enable Chroma Key", style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontSize = 13.sp))
      Switch(
        checked = chroma.enabled,
        onCheckedChange = {
          chroma = chroma.copy(enabled = it)
          viewModel.timelineEngine.updateChromaKey(chroma)
        },
        colors = SwitchDefaults.colors(checkedThumbColor = GreenAccent)
      )
    }

    if (chroma.enabled) {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // 1. Color Selection
        item {
          Text("Key Color", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
          Spacer(modifier = Modifier.height(4.dp))
          val presetColors = listOf(
            0xFF00FF00L to "Green",
            0xFF0000FFL to "Blue",
            0xFFFF00FFL to "Magenta",
            0xFFFF0000L to "Red",
            0xFF000000L to "Black",
            0xFFFFFFFFL to "White"
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            presetColors.forEach { (colorVal, label) ->
              val isSelected = chroma.targetColor == colorVal
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(Color(colorVal))
                  .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) CyanAccent else StudioBorder,
                    shape = CircleShape
                  )
                  .clickable {
                    chroma = chroma.copy(targetColor = colorVal)
                    viewModel.timelineEngine.updateChromaKey(chroma)
                  },
                contentAlignment = Alignment.Center
              ) {
                if (isSelected) {
                  Icon(
                    Icons.Default.Check,
                    contentDescription = label,
                    tint = if (colorVal == 0xFFFFFFFFL) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }

        // 2. Similarity Slider
        item {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Similarity", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
            Text("${(chroma.similarity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
          }
          Slider(
            value = chroma.similarity,
            valueRange = 0.05f..0.95f,
            onValueChange = {
              chroma = chroma.copy(similarity = it, intensity = it)
              viewModel.timelineEngine.updateChromaKey(chroma)
            },
            colors = SliderDefaults.colors(thumbColor = GreenAccent, activeTrackColor = GreenAccent)
          )
        }

        // 3. Smoothness Slider
        item {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Smoothness", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
            Text("${(chroma.smoothness * 100).toInt()}%", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
          }
          Slider(
            value = chroma.smoothness,
            valueRange = 0.01f..0.50f,
            onValueChange = {
              chroma = chroma.copy(smoothness = it)
              viewModel.timelineEngine.updateChromaKey(chroma)
            },
            colors = SliderDefaults.colors(thumbColor = GreenAccent, activeTrackColor = GreenAccent)
          )
        }

        // 4. Spill Suppression Slider
        item {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Spill Suppression", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
            Text("${(chroma.spillSuppression * 100).toInt()}%", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
          }
          Slider(
            value = chroma.spillSuppression,
            valueRange = 0.0f..1.0f,
            onValueChange = {
              chroma = chroma.copy(spillSuppression = it, spillReduction = it)
              viewModel.timelineEngine.updateChromaKey(chroma)
            },
            colors = SliderDefaults.colors(thumbColor = GreenAccent, activeTrackColor = GreenAccent)
          )
        }

        // 5. Background Type Selection
        item {
          Text("Background Replacement", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
          Spacer(modifier = Modifier.height(4.dp))
          val bgTypes = listOf("Transparent", "SolidColor", "Image")
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bgTypes.forEach { type ->
              FilterChip(
                selected = chroma.backgroundType == type,
                onClick = {
                  chroma = chroma.copy(backgroundType = type)
                  viewModel.timelineEngine.updateChromaKey(chroma)
                },
                label = {
                  Text(when (type) {
                    "Transparent" -> "Transparent"
                    "SolidColor" -> "Solid Color"
                    else -> "Image / Video"
                  })
                },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = CyanAccent,
                  selectedLabelColor = Color.Black,
                  containerColor = StudioSurfaceVariant,
                  labelColor = TextPrimary
                )
              )
            }
          }
        }

        // Background Image/Video Upload Button if Image background type selected
        if (chroma.backgroundType == "Image") {
          item {
            val bgUri = chroma.backgroundUri
            val hasBg = !bgUri.isNullOrBlank()
            Button(
              onClick = {
                bgPickerLauncher.launch(
                  androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
              },
              colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth().height(38.dp)
            ) {
              Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (hasBg) "Change Background Media" else "Upload Background Image / Video",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
              )
            }
            if (hasBg) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "✓ Background set: ${bgUri?.substringAfterLast("/")}",
                color = GreenAccent,
                fontSize = 11.sp
              )
            }
          }
        }

        if (chroma.backgroundType == "SolidColor") {
          item {
            Text("Solid Background Color", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
            Spacer(modifier = Modifier.height(4.dp))
            val solidPalette = listOf(
              0xFF000000L to "Black",
              0xFFFFFFFFL to "White",
              0xFF1E3A8AL to "Navy",
              0xFF7C3AEDL to "Purple",
              0xFFEF4444L to "Coral"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              solidPalette.forEach { (colorVal, name) ->
                val isSelected = chroma.backgroundColor == colorVal
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(colorVal))
                    .border(
                      width = if (isSelected) 3.dp else 1.dp,
                      color = if (isSelected) AmberAccent else StudioBorder,
                      shape = CircleShape
                    )
                    .clickable {
                      chroma = chroma.copy(backgroundColor = colorVal)
                      viewModel.timelineEngine.updateChromaKey(chroma)
                    }
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun CanvasPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val currentAspect by viewModel.activeAspectRatio.collectAsState()
  val currentRes by viewModel.activeResolution.collectAsState()
  val currentFps by viewModel.activeFps.collectAsState()

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Canvas & Aspect Ratio",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
      )
      IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(AspectRatio.values()) { ratio ->
        FilterChip(
          selected = currentAspect == ratio,
          onClick = {
            viewModel.updateProjectSettings(ratio, currentRes, currentFps)
          },
          label = { Text(ratio.label) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyanAccent,
            selectedLabelColor = Color.Black
          )
        )
      }
    }
  }
}

@Composable
fun CaptionsToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val currentPos by viewModel.timelineEngine.currentPositionMs.collectAsState()
  val isAIBusy by viewModel.isAIBusy.collectAsState()
  val aiStatusMessage by viewModel.aiStatusMessage.collectAsState()
  val textClips = timeline.textClips

  val context = LocalContext.current
  var selectedLanguage by remember { mutableStateOf("English") }
  val languages = listOf("English", "Spanish", "French", "German", "Japanese", "Portuguese", "Hindi", "Korean", "Chinese", "Italian")
  var showLanguageMenu by remember { mutableStateOf(false) }

  var activeTab by remember { mutableStateOf(0) } // 0: Auto-Captions & Editor, 1: Styling & Presets
  var showImportSubtitleDialog by remember { mutableStateOf(false) }
  var importTextValue by remember { mutableStateOf("") }
  var showExportSubtitleDialog by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(max = 540.dp)
      .background(StudioSurface)
      .padding(16.dp)
  ) {
    // Header
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
            .background(Brush.linearGradient(listOf(CyanAccent, PurpleAccent))),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.ClosedCaption, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
        }
        Column {
          Text(
            text = "Gemini AI Subtitle Studio",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
          )
          Text(
            text = if (viewModel.aiTools.isAIConfigured) "Gemini 3.5 Flash Active" else "Offline Mode",
            style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontSize = 10.sp)
          )
        }
      }
      IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Navigation Tabs
    TabRow(
      selectedTabIndex = activeTab,
      containerColor = StudioSurfaceVariant,
      contentColor = CyanAccent,
      modifier = Modifier
        .clip(RoundedCornerShape(10.dp))
        .height(38.dp)
    ) {
      Tab(
        selected = activeTab == 0,
        onClick = { activeTab = 0 },
        text = { Text("Captions & Editor (${textClips.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
      )
      Tab(
        selected = activeTab == 1,
        onClick = { activeTab = 1 },
        text = { Text("Styling & Presets", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // AI Status bar if busy
    if (isAIBusy) {
      Card(
        colors = CardDefaults.cardColors(containerColor = CyanAccent.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
      ) {
        Row(
          modifier = Modifier.padding(10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyanAccent)
          Text(
            text = aiStatusMessage.ifBlank { "Gemini API analyzing audio track & generating captions..." },
            style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold)
          )
        }
      }
    }

    if (activeTab == 0) {
      // TAB 0: AI Auto-Caption Generation & Caption Editor
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Language Selector & Generate Actions
        Card(
          colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Audio Language",
                style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold)
              )
              Box {
                OutlinedButton(
                  onClick = { showLanguageMenu = true },
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  modifier = Modifier.height(32.dp)
                ) {
                  Text(selectedLanguage, fontSize = 12.sp, color = CyanAccent)
                  Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = CyanAccent)
                }
                DropdownMenu(
                  expanded = showLanguageMenu,
                  onDismissRequest = { showLanguageMenu = false }
                ) {
                  languages.forEach { lang ->
                    DropdownMenuItem(
                      text = { Text(lang) },
                      onClick = {
                        selectedLanguage = lang
                        showLanguageMenu = false
                      }
                    )
                  }
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Button(
                onClick = { viewModel.runAIAutoCaptions(selectedLanguage) },
                enabled = !isAIBusy,
                modifier = Modifier
                  .weight(1.2f)
                  .height(42.dp)
                  .testTag("generate_captions_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
              ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generate Captions", fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }

              OutlinedButton(
                onClick = { viewModel.runAITranslateCaptions(selectedLanguage) },
                enabled = !isAIBusy && textClips.isNotEmpty(),
                modifier = Modifier
                  .weight(1f)
                  .height(42.dp)
                  .testTag("translate_captions_button"),
                shape = RoundedCornerShape(8.dp)
              ) {
                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp), tint = PurpleAccent)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Translate", fontSize = 11.sp, color = TextPrimary)
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = { showImportSubtitleDialog = true },
                modifier = Modifier
                  .weight(1f)
                  .height(36.dp)
                  .testTag("import_subtitles_button"),
                shape = RoundedCornerShape(8.dp)
              ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyanAccent)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Import Subtitles", fontSize = 11.sp, color = TextPrimary)
              }

              OutlinedButton(
                onClick = {
                  if (textClips.isNotEmpty()) {
                    showExportSubtitleDialog = true
                  }
                },
                enabled = textClips.isNotEmpty(),
                modifier = Modifier
                  .weight(1f)
                  .height(36.dp)
                  .testTag("export_subtitles_button"),
                shape = RoundedCornerShape(8.dp)
              ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp), tint = AmberAccent)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export Subtitles", fontSize = 11.sp, color = TextPrimary)
              }
            }
          }
        }

        // Action Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Synchronized Captions (${textClips.size})",
            style = MaterialTheme.typography.labelLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
          )

          TextButton(
            onClick = {
              viewModel.timelineEngine.addTextClip(
                text = "New Subtitle Caption",
                timelineStartMs = currentPos,
                durationMs = 2500L
              )
            },
            modifier = Modifier.testTag("add_manual_subtitle_button")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Subtitle", color = CyanAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }
        }

        if (textClips.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(110.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(StudioSurfaceVariant),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(Icons.Default.Subtitles, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(32.dp))
              Spacer(modifier = Modifier.height(6.dp))
              Text("No captions on timeline yet.", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
              Text("Tap 'Generate Captions' above to transcribe audio with Gemini API.", style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent))
            }
          }
        } else {
          textClips.sortedBy { it.timelineStartMs }.forEachIndexed { index, clip ->
            CaptionClipCard(
              clip = clip,
              index = index,
              onTextChange = { newText ->
                val words = newText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val newWordTimings = if (words.isNotEmpty() && clip.durationMs > 0) {
                  val wDur = clip.durationMs / words.size
                  words.mapIndexed { wIdx, w -> WordTiming(w, wIdx * wDur, wDur) }
                } else emptyList()

                viewModel.timelineEngine.updateTextClip(
                  clip.copy(text = newText, words = newWordTimings)
                )
              },
              onSeekTo = {
                viewModel.timelineEngine.selectElement(SelectedTrackElement.Text(clip.id))
                viewModel.timelineEngine.seekTo(clip.timelineStartMs)
              },
              onAdjustTiming = { deltaStartMs, deltaDurMs ->
                viewModel.timelineEngine.updateTextClip(
                  clip.copy(
                    timelineStartMs = (clip.timelineStartMs + deltaStartMs).coerceAtLeast(0L),
                    durationMs = (clip.durationMs + deltaDurMs).coerceAtLeast(500L)
                  )
                )
              },
              onDelete = {
                viewModel.timelineEngine.deleteClips(setOf(clip.id))
              }
            )
          }
        }
      }
    } else {
      // TAB 1: Styling & Presets
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Text(
          text = "Subtitle Visual Style Presets",
          style = MaterialTheme.typography.labelLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
        )

        val presets = listOf(
          CaptionStylePreset("Highlight Active Word", "HighlightWord", "🌟 Word Glow", Color(0xFF00E5FF), Color(0xAA000000)),
          CaptionStylePreset("Karaoke Gold", "Karaoke", "🎤 Yellow Bouncy", Color(0xFFFFD54F), Color(0xCC000000)),
          CaptionStylePreset("Cyberpunk Neon", "Animated", "⚡ Cyan Neon Box", Color(0xFF00E5FF), Color(0xDD2A004E)),
          CaptionStylePreset("Cinematic Box", "Bold", "🎬 Dark Banner", Color(0xFFFFFFFF), Color(0xDD111111)),
          CaptionStylePreset("Minimalist Clean", "Clean", "🎨 Soft Shadow", Color(0xFFFFFFFF), Color.Transparent),
          CaptionStylePreset("Pop Impact", "Pop", "🔥 High-Contrast", Color(0xFF000000), Color(0xFFFFC107))
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(presets) { preset ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = StudioSurfaceVariant,
              border = BorderStroke(1.dp, StudioBorder),
              modifier = Modifier
                .width(135.dp)
                .clickable {
                  val updatedClips = timeline.textClips.map { clip ->
                    clip.copy(
                      subtitleStyle = preset.styleKey,
                      textColor = preset.textColor.toArgb().toLong(),
                      backgroundColor = preset.bgColor.toArgb().toLong(),
                      hasBackground = preset.bgColor != Color.Transparent
                    )
                  }
                  viewModel.timelineEngine.replaceTimelineKeepingState(timeline.copy(textClips = updatedClips))
                  Toast.makeText(context, "Applied ${preset.name} style to all captions!", Toast.LENGTH_SHORT).show()
                }
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(preset.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(preset.bgColor),
                  contentAlignment = Alignment.Center
                ) {
                  Text("SAMPLE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = preset.textColor)
                }
              }
            }
          }
        }

        Divider(color = StudioBorder)

        Text(
          text = "Batch Formatting (Applies to all captions)",
          style = MaterialTheme.typography.labelLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
        )

        var fontSp by remember { mutableStateOf(22f) }
        Column {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Font Size", fontSize = 12.sp, color = TextSecondary)
            Text("${fontSp.toInt()} sp", fontSize = 12.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
          }
          Slider(
            value = fontSp,
            onValueChange = { fontSp = it },
            valueRange = 14f..36f,
            onValueChangeFinished = {
              val updated = timeline.textClips.map { it.copy(fontSizeSp = fontSp) }
              viewModel.timelineEngine.replaceTimelineKeepingState(timeline.copy(textClips = updated))
            },
            colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Caption Screen Position", fontSize = 12.sp, color = TextSecondary)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Top" to -0.35f, "Middle" to 0.0f, "Bottom" to 0.35f).forEach { (label, posY) ->
              OutlinedButton(
                onClick = {
                  val updated = timeline.textClips.map { it.copy(posY = posY) }
                  viewModel.timelineEngine.replaceTimelineKeepingState(timeline.copy(textClips = updated))
                },
                modifier = Modifier.weight(1f).height(34.dp),
                contentPadding = PaddingValues(0.dp)
              ) {
                Text(label, fontSize = 11.sp, color = TextPrimary)
              }
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Text Highlight Color", fontSize = 12.sp, color = TextSecondary)
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Color.White, Color(0xFF00E5FF), Color(0xFFFFD54F), Color(0xFFFF4081), Color(0xFF7C4DFF)).forEach { color ->
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(color)
                  .clickable {
                    val updated = timeline.textClips.map { it.copy(textColor = color.toArgb().toLong()) }
                    viewModel.timelineEngine.replaceTimelineKeepingState(timeline.copy(textClips = updated))
                  }
                  .border(1.dp, StudioBorder, CircleShape)
              )
            }
          }
        }
      }
    }

    if (showImportSubtitleDialog) {
      AlertDialog(
        onDismissRequest = { showImportSubtitleDialog = false },
        title = { Text("Import Subtitles (SRT / VTT / ASS)", fontWeight = FontWeight.Bold) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Paste raw SRT, WebVTT, ASS, or plain timestamps below:", fontSize = 12.sp, color = TextSecondary)
            OutlinedTextField(
              value = importTextValue,
              onValueChange = { importTextValue = it },
              modifier = Modifier.fillMaxWidth().height(150.dp).testTag("import_subtitles_input"),
              placeholder = { Text("1\n00:00:01,000 --> 00:00:03,500\nHello World caption", fontSize = 11.sp) }
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (importTextValue.isNotBlank()) {
                viewModel.importSubtitlesFromText(importTextValue)
                Toast.makeText(context, "Subtitles imported to timeline", Toast.LENGTH_SHORT).show()
                importTextValue = ""
              }
              showImportSubtitleDialog = false
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
          ) {
            Text("Import")
          }
        },
        dismissButton = {
          TextButton(onClick = { showImportSubtitleDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }

    if (showExportSubtitleDialog) {
      AlertDialog(
        onDismissRequest = { showExportSubtitleDialog = false },
        title = { Text("Export Subtitles", fontWeight = FontWeight.Bold) },
        text = {
          Text("Choose subtitle export format for ${textClips.size} captions:", fontSize = 13.sp, color = TextSecondary)
        },
        confirmButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                val f = viewModel.exportSubtitlesToFile(com.ahstudio.captions.subtitle.SubtitleFormat.SRT)
                Toast.makeText(context, "Exported SRT: ${f.name}", Toast.LENGTH_LONG).show()
                showExportSubtitleDialog = false
              },
              colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
            ) {
              Text("SRT")
            }
            Button(
              onClick = {
                val f = viewModel.exportSubtitlesToFile(com.ahstudio.captions.subtitle.SubtitleFormat.VTT)
                Toast.makeText(context, "Exported VTT: ${f.name}", Toast.LENGTH_LONG).show()
                showExportSubtitleDialog = false
              },
              colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White)
            ) {
              Text("VTT")
            }
          }
        },
        dismissButton = {
          TextButton(onClick = { showExportSubtitleDialog = false }) {
            Text("Close")
          }
        }
      )
    }
  }
}

@Composable
private fun CaptionClipCard(
  clip: TextClip,
  index: Int,
  onTextChange: (String) -> Unit,
  onSeekTo: () -> Unit,
  onAdjustTiming: (Long, Long) -> Unit,
  onDelete: () -> Unit
) {
  var editedText by remember(clip.text) { mutableStateOf(clip.text) }

  Card(
    colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
    border = BorderStroke(0.5.dp, StudioBorder),
    modifier = Modifier.fillMaxWidth().testTag("caption_card_$index")
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(
            modifier = Modifier
              .size(20.dp)
              .clip(CircleShape)
              .background(CyanAccent),
            contentAlignment = Alignment.Center
          ) {
            Text("${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
          }
          Text(
            text = "${formatDuration(clip.timelineStartMs)} → ${formatDuration(clip.timelineStartMs + clip.durationMs)} (${clip.durationMs / 1000f}s)",
            style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          IconButton(onClick = onSeekTo, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Preview", tint = TextPrimary, modifier = Modifier.size(16.dp))
          }
          IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
          }
        }
      }

      OutlinedTextField(
        value = editedText,
        onValueChange = {
          editedText = it
          onTextChange(it)
        },
        modifier = Modifier.fillMaxWidth().testTag("caption_text_input_$index"),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold),
        singleLine = false,
        maxLines = 2,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = CyanAccent,
          unfocusedBorderColor = StudioBorder,
          focusedContainerColor = StudioSurface,
          unfocusedContainerColor = StudioSurface
        )
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("Start:", fontSize = 10.sp, color = TextSecondary)
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = StudioSurface,
            modifier = Modifier.clickable { onAdjustTiming(-200L, 0L) }
          ) {
            Text("-0.2s", fontSize = 9.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
          }
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = StudioSurface,
            modifier = Modifier.clickable { onAdjustTiming(200L, 0L) }
          ) {
            Text("+0.2s", fontSize = 9.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
          }

          Spacer(modifier = Modifier.width(6.dp))

          Text("Dur:", fontSize = 10.sp, color = TextSecondary)
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = StudioSurface,
            modifier = Modifier.clickable { onAdjustTiming(0L, -200L) }
          ) {
            Text("-0.2s", fontSize = 9.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
          }
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = StudioSurface,
            modifier = Modifier.clickable { onAdjustTiming(0L, 200L) }
          ) {
            Text("+0.2s", fontSize = 9.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
          }
        }

        Text(
          text = "${clip.words.size} words synced",
          style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.sp)
        )
      }
    }
  }
}

private data class CaptionStylePreset(
  val name: String,
  val styleKey: String,
  val displayName: String,
  val textColor: Color,
  val bgColor: Color
)

@Composable
private fun EditorActionTile(
  icon: ImageVector,
  label: String,
  color: Color,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .size(width = 80.dp, height = 72.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium),
        maxLines = 1
      )
    }
  }
}

@Composable
fun BackgroundToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val currentAspect by viewModel.activeAspectRatio.collectAsState()
  val currentRes by viewModel.activeResolution.collectAsState()
  val currentFps by viewModel.activeFps.collectAsState()
  val currentSampleRate by viewModel.activeSampleRate.collectAsState()
  val activeCanvasColor by viewModel.activeCanvasColor.collectAsState()

  val colors = listOf(
    0xFF000000 to "Black",
    0xFF0F172A to "Slate",
    0xFF1E293B to "Charcoal",
    0xFFFFFFFF to "White",
    0xFF0A192F to "Navy",
    0xFF2E1065 to "Purple",
    0xFF064E3B to "Emerald",
    0xFF450A0A to "Crimson",
    0xFF083344 to "Cyan",
    0xFF18181B to "Zinc"
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Texture, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
        Text(
          text = "Canvas Background",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
      }
      IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    Text(
      text = "Select solid or styled background color for canvas",
      style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
    )

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      items(colors) { (colorLong, name) ->
        val isSelected = activeCanvasColor == colorLong
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.clickable {
            viewModel.updateProjectSettings(
              aspectRatio = currentAspect,
              resolution = currentRes,
              fps = currentFps,
              sampleRate = currentSampleRate,
              canvasColor = colorLong
            )
          }
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(Color(colorLong))
              .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) CyanAccent else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
              ),
            contentAlignment = Alignment.Center
          ) {
            if (isSelected) {
              Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = if (colorLong == 0xFFFFFFFF) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
              )
            }
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
              color = if (isSelected) CyanAccent else TextSecondary,
              fontSize = 10.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
          )
        }
      }
    }
  }
}

@Composable
fun GenerateMediaToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  var prompt by remember { mutableStateOf("") }
  val isAIBusy by viewModel.isAIBusy.collectAsState()
  val context = androidx.compose.ui.platform.LocalContext.current

  val samplePrompts = listOf(
    "Neon Cyberpunk City",
    "Golden Sunset Over Ocean",
    "Anime Lo-Fi Room",
    "Deep Space Galaxy"
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = CyanAccent, shape = RoundedCornerShape(4.dp)) {
          Text(
            text = "AI",
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
          )
        }
        Text(
          text = "Generate Media (AI)",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
      }
      IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    OutlinedTextField(
      value = prompt,
      onValueChange = { prompt = it },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("ai_media_prompt_input"),
      placeholder = { Text("Describe image, clip, or sticker to generate...", color = TextTertiary) },
      singleLine = true,
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CyanAccent,
        unfocusedBorderColor = StudioBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
      )
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      items(samplePrompts) { sample ->
        Surface(
          onClick = { prompt = sample },
          shape = RoundedCornerShape(12.dp),
          color = StudioSurfaceVariant,
          border = BorderStroke(1.dp, StudioBorder)
        ) {
          Text(
            text = sample,
            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = {
          val activePrompt = prompt.ifBlank { "Cinematic AI Visual Asset" }
          viewModel.timelineEngine.addStickerClip("🌟 $activePrompt")
          android.widget.Toast.makeText(context, "Generated media added to timeline!", android.widget.Toast.LENGTH_SHORT).show()
          viewModel.setActiveToolbarTab(null)
        },
        enabled = !isAIBusy,
        modifier = Modifier.weight(1f).testTag("generate_media_submit_btn"),
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
      ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Generate Media", fontWeight = FontWeight.Bold, fontSize = 12.sp)
      }

      Button(
        onClick = {
          viewModel.runAIAutoEdit()
          viewModel.setActiveToolbarTab(null)
        },
        enabled = !isAIBusy,
        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White)
      ) {
        Text("Auto Reel", fontSize = 12.sp)
      }
    }

    OutlinedButton(
      onClick = {
        viewModel.setActiveToolbarTab(null)
        viewModel.navigateTo(com.example.ui.AppScreen.AI_SUITE)
      },
      modifier = Modifier.fillMaxWidth(),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
    ) {
      Text("Open Full AI Suite (Captions, Speech, Highlights)", fontSize = 11.sp)
    }
  }
}

@Composable
fun AIAvatarToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  var avatarScript by remember { mutableStateOf("Welcome to our video presentation! Powered by AI.") }
  var selectedAvatar by remember { mutableStateOf("Sophia (Presenter)") }
  val context = androidx.compose.ui.platform.LocalContext.current

  val avatars = listOf(
    "Sophia (Presenter)" to "👩‍💼",
    "Alex (Tech Host)" to "👨‍💻",
    "Marcus (Storyteller)" to "🎙️",
    "Emma (Lifestyle)" to "✨",
    "Cyber Host" to "🤖"
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Diamond, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(18.dp))
        Text(
          text = "AI Avatar Presenter",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
      }
      IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    Text("Choose Avatar Persona:", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(avatars) { (name, emoji) ->
        val isSelected = selectedAvatar == name
        Surface(
          onClick = { selectedAvatar = name },
          shape = RoundedCornerShape(10.dp),
          color = if (isSelected) PurpleAccent.copy(alpha = 0.25f) else StudioSurfaceVariant,
          border = BorderStroke(1.dp, if (isSelected) PurpleAccent else StudioBorder)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(emoji, fontSize = 14.sp)
            Text(
              text = name,
              style = MaterialTheme.typography.labelSmall.copy(
                color = if (isSelected) Color.White else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
              )
            )
          }
        }
      }
    }

    OutlinedTextField(
      value = avatarScript,
      onValueChange = { avatarScript = it },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("ai_avatar_script_input"),
      placeholder = { Text("What should the avatar say?", color = TextTertiary) },
      maxLines = 3,
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PurpleAccent,
        unfocusedBorderColor = StudioBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
      )
    )

    Button(
      onClick = {
        val script = avatarScript.ifBlank { "Hello from AI Avatar!" }
        viewModel.runAITextToSpeech(script)
        viewModel.timelineEngine.addTextClip(
          text = "[${selectedAvatar.substringBefore(" ")}]: $script",
          durationMs = 4000L
        )
        android.widget.Toast.makeText(context, "AI Avatar & speech added to timeline!", android.widget.Toast.LENGTH_SHORT).show()
        viewModel.setActiveToolbarTab(null)
      },
      modifier = Modifier.fillMaxWidth().testTag("add_ai_avatar_btn"),
      colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White)
    ) {
      Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text("Insert AI Avatar to Timeline", fontWeight = FontWeight.Bold)
    }
  }
}
