package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ClipKeyframe
import com.example.domain.model.KeyframeInterpolation
import com.example.engine.SelectedTrackElement
import com.example.ui.StudioViewModel
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.PinkAccent
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.RedAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioDarkBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.util.Locale

private enum class KeyframeCategory {
  TRANSFORM,
  PRESETS,
  CURVES,
  COLOR,
  AUDIO,
  EFFECT
}

@Composable
fun KeyframeAnimationPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()
  val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()
  val selectedKeyframeIds by viewModel.timelineEngine.selectedKeyframeIds.collectAsState()

  val activeClipData = remember(timeline, selectedElement) {
    viewModel.timelineEngine.getSelectedClipKeyframes()
  }

  var selectedCategory by remember { mutableStateOf(KeyframeCategory.TRANSFORM) }
  var linkScaleXY by remember { mutableStateOf(true) }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .border(1.dp, StudioBorder)
      .padding(12.dp)
      .testTag("keyframe_animation_panel")
  ) {
    // Top Bar: Title, Selected Clip info, and Close
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PurpleAccent.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Diamond, contentDescription = "Keyframe", tint = PurpleAccent, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = "Keyframe Property Panel",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
          )
          val currentElem = selectedElement
          val clipLabel = when (currentElem) {
            is SelectedTrackElement.Video -> "Main Video Clip"
            is SelectedTrackElement.Overlay -> "PIP Overlay Clip"
            is SelectedTrackElement.Audio -> "Audio Track"
            is SelectedTrackElement.Effect -> "Visual Effect Clip"
            is SelectedTrackElement.Text -> {
              val clip = timeline.textClips.find { it.id == currentElem.clipId }
              "Text: ${clip?.text ?: "Title"}"
            }
            is SelectedTrackElement.Sticker -> {
              val clip = timeline.stickerClips.find { it.id == currentElem.clipId }
              if (clip?.elementId != null) "Element: ${clip.emojiOrAsset.ifBlank { clip.elementCategory ?: "Shape" }}"
              else "Sticker: ${clip?.emojiOrAsset ?: "Item"}"
            }
            else -> "No clip selected"
          }
          Text(
            text = clipLabel,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
          )
        }
      }

      IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    if (activeClipData == null) {
      Spacer(modifier = Modifier.height(16.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(StudioSurfaceVariant)
          .padding(16.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Default.Layers, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(32.dp))
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Select an element, video, or audio clip on the timeline to animate",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
          )
          Spacer(modifier = Modifier.height(8.dp))
          val firstSticker = timeline.stickerClips.firstOrNull()
          if (firstSticker != null) {
            Button(
              onClick = {
                viewModel.timelineEngine.selectElement(SelectedTrackElement.Sticker(firstSticker.id))
              },
              colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White)
            ) {
              Text("Select Element (${firstSticker.emojiOrAsset.ifBlank { "Shape" }})", fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
          }
          val firstVideo = timeline.videoClips.firstOrNull()
          if (firstVideo != null) {
            Button(
              onClick = {
                viewModel.timelineEngine.selectElement(SelectedTrackElement.Video(firstVideo.id))
              },
              colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
            ) {
              Text("Select Video Clip", fontSize = 12.sp)
            }
          }
        }
      }
      return
    }

    val keyframes = activeClipData.second
    val activeKeyframe = keyframes.find { it.id in selectedKeyframeIds }
      ?: viewModel.timelineEngine.getKeyframeAtPlayhead()
      ?: keyframes.firstOrNull()

    Spacer(modifier = Modifier.height(8.dp))

    // Keyframe Transport / Navigation Controls Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(StudioDarkBg)
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = { viewModel.timelineEngine.jumpToPreviousKeyframe() },
          enabled = keyframes.isNotEmpty(),
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            Icons.Default.SkipPrevious,
            contentDescription = "Previous Keyframe",
            tint = if (keyframes.isNotEmpty()) CyanAccent else TextTertiary
          )
        }

        val keyframeAtHead = viewModel.timelineEngine.getKeyframeAtPlayhead()
        val hasKfAtHead = keyframeAtHead != null

        Button(
          onClick = {
            if (hasKfAtHead) {
              viewModel.timelineEngine.deleteSelectedKeyframes()
            } else {
              viewModel.timelineEngine.addKeyframeToSelectedClip()
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (hasKfAtHead) RedAccent.copy(alpha = 0.8f) else PurpleAccent,
            contentColor = Color.White
          ),
          modifier = Modifier.height(30.dp).testTag("keyframe_add_remove_toggle")
        ) {
          Icon(
            if (hasKfAtHead) Icons.Default.Delete else Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            if (hasKfAtHead) "Remove KF" else "Add Keyframe",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
          )
        }

        IconButton(
          onClick = { viewModel.timelineEngine.jumpToNextKeyframe() },
          enabled = keyframes.isNotEmpty(),
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            Icons.Default.SkipNext,
            contentDescription = "Next Keyframe",
            tint = if (keyframes.isNotEmpty()) CyanAccent else TextTertiary
          )
        }
      }

      // Keyframe Management Actions: Copy, Paste, Duplicate, Select All, Clear All
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = { viewModel.timelineEngine.copySelectedKeyframes() },
          enabled = keyframes.isNotEmpty(),
          modifier = Modifier.size(28.dp)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = "Copy Keyframes", tint = TextSecondary, modifier = Modifier.size(15.dp))
        }

        IconButton(
          onClick = { viewModel.timelineEngine.pasteKeyframes() },
          modifier = Modifier.size(28.dp)
        ) {
          Icon(Icons.Default.ContentPaste, contentDescription = "Paste Keyframes", tint = GreenAccent, modifier = Modifier.size(15.dp))
        }

        IconButton(
          onClick = { viewModel.timelineEngine.duplicateSelectedKeyframes() },
          enabled = keyframes.isNotEmpty(),
          modifier = Modifier.size(28.dp)
        ) {
          Icon(Icons.Default.Diamond, contentDescription = "Duplicate Keyframes", tint = AmberAccent, modifier = Modifier.size(15.dp))
        }

        IconButton(
          onClick = { viewModel.timelineEngine.selectAllKeyframesInSelectedClip() },
          enabled = keyframes.isNotEmpty(),
          modifier = Modifier.size(28.dp)
        ) {
          Icon(Icons.Default.SelectAll, contentDescription = "Select All Keyframes", tint = CyanAccent, modifier = Modifier.size(15.dp))
        }

        IconButton(
          onClick = { viewModel.timelineEngine.clearAllKeyframesInSelectedClip() },
          enabled = keyframes.isNotEmpty(),
          modifier = Modifier.size(28.dp)
        ) {
          Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All Keyframes", tint = RedAccent, modifier = Modifier.size(15.dp))
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Keyframes overview chips on clip timeline
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "${keyframes.size} KF${if (keyframes.size == 1) "" else "s"}:",
        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
      )

      if (keyframes.isEmpty()) {
        Text(
          text = "No keyframes yet. Tap '+ Add Keyframe' or choose a Motion Preset.",
          style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary, fontSize = 11.sp)
        )
      } else {
        keyframes.forEachIndexed { index, kf ->
          val isSelected = kf.id in selectedKeyframeIds || (activeKeyframe?.id == kf.id)
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(if (isSelected) PurpleAccent else StudioSurfaceVariant)
              .border(1.dp, if (isSelected) Color.White else StudioBorder, RoundedCornerShape(4.dp))
              .clickable { viewModel.timelineEngine.selectKeyframe(kf.id) }
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = "#${index + 1} ${(kf.timeMs / 1000f)}s",
              style = MaterialTheme.typography.bodySmall.copy(
                color = if (isSelected) Color.White else TextSecondary,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Sub-Categories Navigation (Transform, Presets, Curves, Color, Audio, Effect)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      KeyframeCategory.values().forEach { cat ->
        val isCatSelected = selectedCategory == cat
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isCatSelected) StudioSurfaceVariant else Color.Transparent)
            .border(1.dp, if (isCatSelected) CyanAccent else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { selectedCategory = cat }
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = when (cat) {
              KeyframeCategory.TRANSFORM -> "Transform (Scale/Pos/Rot/Op)"
              KeyframeCategory.PRESETS -> "Motion Presets"
              KeyframeCategory.CURVES -> "Curves & Easing"
              KeyframeCategory.COLOR -> "Color & Filter"
              KeyframeCategory.AUDIO -> "Volume"
              KeyframeCategory.EFFECT -> "Effect"
            },
            style = MaterialTheme.typography.bodySmall.copy(
              color = if (isCatSelected) CyanAccent else TextSecondary,
              fontSize = 11.sp,
              fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal
            )
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Parameter Controls Scroll Area
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .height(240.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      val currentElem = selectedElement
      val currentRelTime = when (currentElem) {
        is SelectedTrackElement.Video -> {
          val clip = timeline.videoClips.find { it.id == currentElem.clipId }
          (currentPosMs - (clip?.timelineStartMs ?: 0L)).coerceIn(0L, clip?.durationMs ?: 1000L)
        }
        is SelectedTrackElement.Overlay -> {
          val clip = timeline.overlayClips.find { it.id == currentElem.clipId }
          (currentPosMs - (clip?.timelineStartMs ?: 0L)).coerceIn(0L, clip?.durationMs ?: 1000L)
        }
        is SelectedTrackElement.Sticker -> {
          val clip = timeline.stickerClips.find { it.id == currentElem.clipId }
          (currentPosMs - (clip?.timelineStartMs ?: 0L)).coerceIn(0L, clip?.durationMs ?: 1000L)
        }
        is SelectedTrackElement.Text -> {
          val clip = timeline.textClips.find { it.id == currentElem.clipId }
          (currentPosMs - (clip?.timelineStartMs ?: 0L)).coerceIn(0L, clip?.durationMs ?: 1000L)
        }
        is SelectedTrackElement.Audio -> {
          val clip = timeline.audioClips.find { it.id == currentElem.clipId }
          (currentPosMs - (clip?.timelineStartMs ?: 0L)).coerceIn(0L, clip?.durationMs ?: 1000L)
        }
        is SelectedTrackElement.Effect -> {
          val clip = timeline.effectClips.find { it.id == currentElem.clipId }
          (currentPosMs - (clip?.timelineStartMs ?: 0L)).coerceIn(0L, clip?.durationMs ?: 1000L)
        }
        else -> 0L
      }

      val defaultKf = when (currentElem) {
        is SelectedTrackElement.Sticker -> {
          val clip = timeline.stickerClips.find { it.id == currentElem.clipId }
          if (clip != null) {
            val interp = com.example.engine.KeyframeInterpolator.interpolate(clip, currentRelTime)
            ClipKeyframe(
              timeMs = currentRelTime,
              posX = interp.posX,
              posY = interp.posY,
              scaleX = interp.scaleX,
              scaleY = interp.scaleY,
              rotation = interp.rotation,
              opacity = interp.opacity
            )
          } else ClipKeyframe(timeMs = currentRelTime)
        }
        is SelectedTrackElement.Text -> {
          val clip = timeline.textClips.find { it.id == currentElem.clipId }
          if (clip != null) {
            val interp = com.example.engine.KeyframeInterpolator.interpolate(clip, currentRelTime)
            ClipKeyframe(
              timeMs = currentRelTime,
              posX = interp.posX,
              posY = interp.posY,
              scaleX = interp.scaleX,
              scaleY = interp.scaleY,
              rotation = interp.rotation,
              opacity = interp.opacity
            )
          } else ClipKeyframe(timeMs = currentRelTime)
        }
        is SelectedTrackElement.Video -> {
          val clip = timeline.videoClips.find { it.id == currentElem.clipId }
          if (clip != null) {
            val interp = com.example.engine.KeyframeInterpolator.interpolate(clip, currentRelTime)
            ClipKeyframe(
              timeMs = currentRelTime,
              posX = interp.posX,
              posY = interp.posY,
              scaleX = interp.scaleX,
              scaleY = interp.scaleY,
              rotation = interp.rotation,
              opacity = interp.opacity
            )
          } else ClipKeyframe(timeMs = currentRelTime)
        }
        is SelectedTrackElement.Overlay -> {
          val clip = timeline.overlayClips.find { it.id == currentElem.clipId }
          if (clip != null) {
            val interp = com.example.engine.KeyframeInterpolator.interpolate(clip, currentRelTime)
            ClipKeyframe(
              timeMs = currentRelTime,
              posX = interp.posX,
              posY = interp.posY,
              scaleX = interp.scaleX,
              scaleY = interp.scaleY,
              rotation = interp.rotation,
              opacity = interp.opacity
            )
          } else ClipKeyframe(timeMs = currentRelTime)
        }
        else -> ClipKeyframe(timeMs = currentRelTime)
      }
      val targetKf = activeKeyframe ?: defaultKf

      when (selectedCategory) {
        KeyframeCategory.TRANSFORM -> {
          // 2D Position Interactive Touchpad & Sliders
          Text(
            text = "Position (X: ${String.format(Locale.US, "%.2f", targetKf.posX)}, Y: ${String.format(Locale.US, "%.2f", targetKf.posY)})",
            style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Interactive 2D Touchpad Canvas
            Box(
              modifier = Modifier
                .size(width = 110.dp, height = 75.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(StudioDarkBg)
                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                .pointerInput(targetKf.id) {
                  detectDragGestures { change, dragAmount ->
                    change.consume()
                    val deltaX = dragAmount.x / (size.width / 2f)
                    val deltaY = dragAmount.y / (size.height / 2f)
                    val newX = (targetKf.posX + deltaX).coerceIn(-1.0f, 1.0f)
                    val newY = (targetKf.posY + deltaY).coerceIn(-1.0f, 1.0f)
                    updateActiveKeyframe(viewModel, targetKf) { it.copy(posX = newX, posY = newY) }
                  }
                },
              contentAlignment = Alignment.Center
            ) {
              Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f

                // Crosshairs
                drawLine(StudioBorder, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1f)
                drawLine(StudioBorder, Offset(0f, cy), Offset(w, cy), strokeWidth = 1f)

                // Current position thumb
                val thumbX = cx + (targetKf.posX * (cx - 8f))
                val thumbY = cy + (targetKf.posY * (cy - 8f))

                drawCircle(color = CyanAccent.copy(alpha = 0.3f), radius = 10f, center = Offset(thumbX, thumbY))
                drawCircle(color = CyanAccent, radius = 5f, center = Offset(thumbX, thumbY))
              }
            }

            // Quick Center & Direction Alignment Buttons
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Button(
                onClick = { updateActiveKeyframe(viewModel, targetKf) { it.copy(posX = 0f, posY = 0f) } },
                colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant, contentColor = CyanAccent),
                modifier = Modifier.height(28.dp)
              ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Center (0,0)", fontSize = 10.sp)
              }

              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuickSnapButton(label = "Top", onClick = { updateActiveKeyframe(viewModel, targetKf) { it.copy(posY = -0.4f) } })
                QuickSnapButton(label = "Bottom", onClick = { updateActiveKeyframe(viewModel, targetKf) { it.copy(posY = 0.4f) } })
                QuickSnapButton(label = "Left", onClick = { updateActiveKeyframe(viewModel, targetKf) { it.copy(posX = -0.4f) } })
                QuickSnapButton(label = "Right", onClick = { updateActiveKeyframe(viewModel, targetKf) { it.copy(posX = 0.4f) } })
              }
            }
          }

          // X Position Slider
          KeyframeSliderRow(
            label = "X Position",
            value = targetKf.posX,
            range = -1.0f..1.0f,
            format = "%.2f",
            onValueChange = { newVal ->
              updateActiveKeyframe(viewModel, targetKf) { it.copy(posX = newVal) }
            },
            onReset = { updateActiveKeyframe(viewModel, targetKf) { it.copy(posX = 0f) } }
          )

          // Y Position Slider
          KeyframeSliderRow(
            label = "Y Position",
            value = targetKf.posY,
            range = -1.0f..1.0f,
            format = "%.2f",
            onValueChange = { newVal ->
              updateActiveKeyframe(viewModel, targetKf) { it.copy(posY = newVal) }
            },
            onReset = { updateActiveKeyframe(viewModel, targetKf) { it.copy(posY = 0f) } }
          )

          // Scale Controls with Link Toggle and Quick Chips
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = if (linkScaleXY) "Scale (Uniform: ${String.format(Locale.US, "%.2fx", targetKf.scaleX)})" else "Scale",
              style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = if (linkScaleXY) "Linked" else "Independent",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp)
              )
              IconButton(
                onClick = { linkScaleXY = !linkScaleXY },
                modifier = Modifier.size(24.dp)
              ) {
                Icon(
                  if (linkScaleXY) Icons.Default.Link else Icons.Default.LinkOff,
                  contentDescription = "Link Scale",
                  tint = if (linkScaleXY) CyanAccent else TextTertiary,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }

          // Scale Quick Preset Chips
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { presetScale ->
              QuickValueChip(
                label = "${presetScale}x",
                isSelected = kotlin.math.abs(targetKf.scaleX - presetScale) < 0.05f,
                onClick = {
                  updateActiveKeyframe(viewModel, targetKf) {
                    it.copy(scaleX = presetScale, scaleY = presetScale)
                  }
                }
              )
            }
          }

          // Scale X Slider
          KeyframeSliderRow(
            label = if (linkScaleXY) "Scale" else "Scale X",
            value = targetKf.scaleX,
            range = 0.1f..4.0f,
            format = "%.2fx",
            onValueChange = { newVal ->
              updateActiveKeyframe(viewModel, targetKf) {
                if (linkScaleXY) it.copy(scaleX = newVal, scaleY = newVal)
                else it.copy(scaleX = newVal)
              }
            },
            onReset = { updateActiveKeyframe(viewModel, targetKf) { it.copy(scaleX = 1f, scaleY = 1f) } }
          )

          if (!linkScaleXY) {
            KeyframeSliderRow(
              label = "Scale Y",
              value = targetKf.scaleY,
              range = 0.1f..4.0f,
              format = "%.2fx",
              onValueChange = { newVal ->
                updateActiveKeyframe(viewModel, targetKf) { it.copy(scaleY = newVal) }
              },
              onReset = { updateActiveKeyframe(viewModel, targetKf) { it.copy(scaleY = 1f) } }
            )
          }

          // Rotation Angle with Quick Angle Presets
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Rotation", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              listOf(-90f, 0f, 90f, 180f, 360f).forEach { angle ->
                QuickValueChip(
                  label = "${angle.toInt()}°",
                  isSelected = kotlin.math.abs(targetKf.rotation - angle) < 1f,
                  onClick = { updateActiveKeyframe(viewModel, targetKf) { it.copy(rotation = angle) } }
                )
              }
            }
          }

          KeyframeSliderRow(
            label = "Rotation Angle",
            value = targetKf.rotation,
            range = -360f..360f,
            format = "%.0f°",
            onValueChange = { newVal ->
              updateActiveKeyframe(viewModel, targetKf) { it.copy(rotation = newVal) }
            },
            onReset = { updateActiveKeyframe(viewModel, targetKf) { it.copy(rotation = 0f) } }
          )

          // Opacity with Quick Presets
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Opacity", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f).forEach { op ->
                QuickValueChip(
                  label = "${(op * 100).toInt()}%",
                  isSelected = kotlin.math.abs(targetKf.opacity - op) < 0.05f,
                  onClick = { updateActiveKeyframe(viewModel, targetKf) { it.copy(opacity = op) } }
                )
              }
            }
          }

          KeyframeSliderRow(
            label = "Opacity",
            value = targetKf.opacity,
            range = 0.0f..1.0f,
            format = "%.0f%%",
            displayMultiplier = 100f,
            onValueChange = { newVal ->
              updateActiveKeyframe(viewModel, targetKf) { it.copy(opacity = newVal) }
            },
            onReset = { updateActiveKeyframe(viewModel, targetKf) { it.copy(opacity = 1f) } }
          )
        }

        KeyframeCategory.PRESETS -> {
          // One-tap motion recipes
          Text(
            text = "One-Tap Motion Animation Presets",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
          )
          Text(
            text = "Automatically creates start and end keyframes across the clip duration:",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
          )

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              MotionPresetCard(
                title = "Zoom In",
                desc = "1.0x → 1.4x dynamic scale",
                accentColor = CyanAccent,
                modifier = Modifier.weight(1f),
                onClick = {
                  viewModel.timelineEngine.applyMotionPresetToSelectedClip(scaleStart = 1.0f, scaleEnd = 1.4f)
                }
              )
              MotionPresetCard(
                title = "Zoom Out",
                desc = "1.4x → 1.0x wide reveal",
                accentColor = PinkAccent,
                modifier = Modifier.weight(1f),
                onClick = {
                  viewModel.timelineEngine.applyMotionPresetToSelectedClip(scaleStart = 1.4f, scaleEnd = 1.0f)
                }
              )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              MotionPresetCard(
                title = "Pan Left → Right",
                desc = "Smooth horizontal camera track",
                accentColor = AmberAccent,
                modifier = Modifier.weight(1f),
                onClick = {
                  viewModel.timelineEngine.applyMotionPresetToSelectedClip(posXStart = -0.35f, posXEnd = 0.35f)
                }
              )
              MotionPresetCard(
                title = "Pan Right → Left",
                desc = "Cinematic counter-pan",
                accentColor = GreenAccent,
                modifier = Modifier.weight(1f),
                onClick = {
                  viewModel.timelineEngine.applyMotionPresetToSelectedClip(posXStart = 0.35f, posXEnd = -0.35f)
                }
              )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              MotionPresetCard(
                title = "Cinematic Spin",
                desc = "0° → 360° rotation spin",
                accentColor = PurpleAccent,
                modifier = Modifier.weight(1f),
                onClick = {
                  viewModel.timelineEngine.applyMotionPresetToSelectedClip(rotationStart = 0f, rotationEnd = 360f)
                }
              )
              MotionPresetCard(
                title = "Fade In / Out",
                desc = "0% → 100% opacity reveal",
                accentColor = CyanAccent,
                modifier = Modifier.weight(1f),
                onClick = {
                  viewModel.timelineEngine.applyMotionPresetToSelectedClip(opacityStart = 0f, opacityEnd = 1.0f)
                }
              )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              MotionPresetCard(
                title = "Ken Burns Drift",
                desc = "Scale 1.0x → 1.25x + Pan (0.0 → 0.15)",
                accentColor = AmberAccent,
                modifier = Modifier.weight(1f),
                onClick = {
                  viewModel.timelineEngine.applyMotionPresetToSelectedClip(
                    scaleStart = 1.0f,
                    scaleEnd = 1.25f,
                    posXStart = -0.1f,
                    posXEnd = 0.1f,
                    posYStart = -0.05f,
                    posYEnd = 0.05f
                  )
                }
              )
              MotionPresetCard(
                title = "Pulse Breathe",
                desc = "Subtle scaling pop accent",
                accentColor = PinkAccent,
                modifier = Modifier.weight(1f),
                onClick = {
                  viewModel.timelineEngine.applyMotionPresetToSelectedClip(scaleStart = 0.95f, scaleEnd = 1.15f)
                }
              )
            }
          }
        }

        KeyframeCategory.CURVES -> {
          KeyframeCurveEditor(
            keyframe = targetKf,
            onInterpolationChange = { mode ->
              updateActiveKeyframe(viewModel, targetKf) { it.copy(interpolation = mode) }
            },
            onCustomCurveChange = { newPts ->
              updateActiveKeyframe(viewModel, targetKf) {
                it.copy(
                  interpolation = KeyframeInterpolation.CUSTOM_CURVE,
                  customCurvePoints = newPts
                )
              }
            }
          )
        }

        KeyframeCategory.COLOR -> {
          KeyframeSliderRow(
            label = "Blur",
            value = targetKf.blur,
            range = 0.0f..1.0f,
            format = "%.0f%%",
            displayMultiplier = 100f,
            onValueChange = { newVal ->
              updateActiveKeyframe(viewModel, targetKf) { it.copy(blur = newVal) }
            },
            onReset = { updateActiveKeyframe(viewModel, targetKf) { it.copy(blur = 0f) } }
          )

          KeyframeSliderRow(
            label = "Brightness",
            value = targetKf.brightness,
            range = -1.0f..1.0f,
            format = "%+.2f",
            onValueChange = { newVal ->
              updateActiveKeyframe(viewModel, targetKf) { it.copy(brightness = newVal) }
            },
            onReset = { updateActiveKeyframe(viewModel, targetKf) { it.copy(brightness = 0f) } }
          )

          KeyframeSliderRow(
            label = "Contrast",
            value = targetKf.contrast,
            range = 0.0f..3.0f,
            format = "%.2fx",
            onValueChange = { newVal ->
              updateActiveKeyframe(viewModel, targetKf) { it.copy(contrast = newVal) }
            },
            onReset = { updateActiveKeyframe(viewModel, targetKf) { it.copy(contrast = 1f) } }
          )

          KeyframeSliderRow(
            label = "Saturation",
            value = targetKf.saturation,
            range = 0.0f..3.0f,
            format = "%.2fx",
            onValueChange = { newVal ->
              updateActiveKeyframe(viewModel, targetKf) { it.copy(saturation = newVal) }
            },
            onReset = { updateActiveKeyframe(viewModel, targetKf) { it.copy(saturation = 1f) } }
          )
        }

        KeyframeCategory.AUDIO -> {
          KeyframeSliderRow(
            label = "Track Volume",
            value = targetKf.volume,
            range = 0.0f..2.0f,
            format = "%.0f%%",
            displayMultiplier = 100f,
            onValueChange = { newVal ->
              updateActiveKeyframe(viewModel, targetKf) { it.copy(volume = newVal) }
            },
            onReset = { updateActiveKeyframe(viewModel, targetKf) { it.copy(volume = 1f) } }
          )
        }

        KeyframeCategory.EFFECT -> {
          KeyframeSliderRow(
            label = "Effect Intensity / Param",
            value = targetKf.effectParam,
            range = 0.0f..1.0f,
            format = "%.0f%%",
            displayMultiplier = 100f,
            onValueChange = { newVal ->
              updateActiveKeyframe(viewModel, targetKf) { it.copy(effectParam = newVal) }
            },
            onReset = { updateActiveKeyframe(viewModel, targetKf) { it.copy(effectParam = 0f) } }
          )
        }
      }
    }
  }
}

@Composable
private fun MotionPresetCard(
  title: String,
  desc: String,
  accentColor: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(StudioSurfaceVariant)
      .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
      .clickable { onClick() }
      .padding(10.dp)
  ) {
    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp))
      }
      Spacer(modifier = Modifier.height(3.dp))
      Text(desc, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp))
    }
  }
}

@Composable
private fun QuickSnapButton(
  label: String,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(StudioSurfaceVariant)
      .clickable { onClick() }
      .padding(horizontal = 6.dp, vertical = 4.dp)
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium))
  }
}

@Composable
private fun QuickValueChip(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(if (isSelected) CyanAccent else StudioSurfaceVariant)
      .clickable { onClick() }
      .padding(horizontal = 6.dp, vertical = 2.dp)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall.copy(
        color = if (isSelected) Color.Black else TextSecondary,
        fontSize = 10.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
      )
    )
  }
}

private fun updateActiveKeyframe(
  viewModel: StudioViewModel,
  targetKf: ClipKeyframe,
  transform: (ClipKeyframe) -> ClipKeyframe
) {
  val keyframeAtHead = viewModel.timelineEngine.getKeyframeAtPlayhead()
  if (keyframeAtHead != null) {
    viewModel.timelineEngine.updateKeyframe(keyframeAtHead.id, transform)
  } else {
    viewModel.timelineEngine.addKeyframeToSelectedClip(transform(targetKf))
  }
}

@Composable
private fun KeyframeSliderRow(
  label: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  format: String,
  displayMultiplier: Float = 1f,
  onValueChange: (Float) -> Unit,
  onReset: () -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(label, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium))
      Row(verticalAlignment = Alignment.CenterVertically) {
        val displayVal = value * displayMultiplier
        Text(
          text = String.format(Locale.US, format, displayVal),
          style = MaterialTheme.typography.bodySmall.copy(color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onReset, modifier = Modifier.size(20.dp)) {
          Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = TextTertiary, modifier = Modifier.size(12.dp))
        }
      }
    }

    Slider(
      value = value.coerceIn(range.start, range.endInclusive),
      onValueChange = onValueChange,
      valueRange = range,
      colors = SliderDefaults.colors(
        thumbColor = CyanAccent,
        activeTrackColor = CyanAccent,
        inactiveTrackColor = StudioBorder
      ),
      modifier = Modifier.height(24.dp)
    )
  }
}

@Composable
private fun KeyframeCurveEditor(
  keyframe: ClipKeyframe,
  onInterpolationChange: (KeyframeInterpolation) -> Unit,
  onCustomCurveChange: (List<Float>) -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text(
      text = "Interpolation Curve",
      style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    )

    // Interpolation modes
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      KeyframeInterpolation.values().forEach { mode ->
        val isSelected = keyframe.interpolation == mode
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) PurpleAccent else StudioSurfaceVariant)
            .border(1.dp, if (isSelected) Color.White else StudioBorder, RoundedCornerShape(6.dp))
            .clickable { onInterpolationChange(mode) }
            .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
          Text(
            text = when (mode) {
              KeyframeInterpolation.LINEAR -> "Linear"
              KeyframeInterpolation.EASE_IN -> "Ease In"
              KeyframeInterpolation.EASE_OUT -> "Ease Out"
              KeyframeInterpolation.EASE_IN_OUT -> "Ease In-Out"
              KeyframeInterpolation.HOLD -> "Hold / Step"
              KeyframeInterpolation.CUBIC_BEZIER, KeyframeInterpolation.CUSTOM_CURVE -> "Custom Bezier"
            },
            style = MaterialTheme.typography.bodySmall.copy(
              color = if (isSelected) Color.White else TextSecondary,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
          )
        }
      }
    }

    // Interactive Bezier Curve Canvas Preview
    val pts = keyframe.customCurvePoints
    var p1x by remember(pts) { mutableFloatStateOf(pts.getOrNull(0) ?: 0.42f) }
    var p1y by remember(pts) { mutableFloatStateOf(pts.getOrNull(1) ?: 0.0f) }
    var p2x by remember(pts) { mutableFloatStateOf(pts.getOrNull(2) ?: 0.58f) }
    var p2y by remember(pts) { mutableFloatStateOf(pts.getOrNull(3) ?: 1.0f) }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(80.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(StudioDarkBg)
        .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
        .padding(8.dp)
    ) {
      Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
        val w = size.width
        val h = size.height

        // Grid lines
        drawLine(StudioBorder, Offset(0f, h), Offset(w, h), strokeWidth = 1f)
        drawLine(StudioBorder, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)
        drawLine(StudioBorder, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1f)

        // Evaluate curve path
        val path = Path()
        path.moveTo(0f, h)

        val mode = keyframe.interpolation
        val steps = 40
        for (i in 1..steps) {
          val t = i.toFloat() / steps
          val factor = when (mode) {
            KeyframeInterpolation.LINEAR -> t
            KeyframeInterpolation.EASE_IN -> t * t
            KeyframeInterpolation.EASE_OUT -> t * (2f - t)
            KeyframeInterpolation.EASE_IN_OUT -> if (t < 0.5f) 2f * t * t else -1f + (4f - 2f * t) * t
            KeyframeInterpolation.HOLD -> if (t < 1.0f) 0.0f else 1.0f
            KeyframeInterpolation.CUBIC_BEZIER, KeyframeInterpolation.CUSTOM_CURVE -> {
              com.example.engine.KeyframeInterpolator.solveCubicBezier(t, p1x, p1y, p2x, p2y)
            }
          }
          val x = t * w
          val y = h - (factor.coerceIn(-0.2f, 1.4f) * h)
          path.lineTo(x, y)
        }

        drawPath(
          path = path,
          color = when (mode) {
            KeyframeInterpolation.LINEAR -> CyanAccent
            KeyframeInterpolation.EASE_IN -> GreenAccent
            KeyframeInterpolation.EASE_OUT -> AmberAccent
            KeyframeInterpolation.EASE_IN_OUT -> PinkAccent
            KeyframeInterpolation.HOLD -> AmberAccent
            KeyframeInterpolation.CUBIC_BEZIER, KeyframeInterpolation.CUSTOM_CURVE -> PurpleAccent
          },
          style = Stroke(width = 3.dp.toPx())
        )
      }
    }

    // Custom Curve Presets & Sliders
    AnimatedVisibility(visible = keyframe.interpolation == KeyframeInterpolation.CUSTOM_CURVE) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Curve Presets:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Button(
            onClick = {
              p1x = 0.42f; p1y = 0.0f; p2x = 0.58f; p2y = 1.0f
              onCustomCurveChange(listOf(p1x, p1y, p2x, p2y))
            },
            colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant, contentColor = TextPrimary),
            modifier = Modifier.weight(1f).height(28.dp)
          ) {
            Text("S-Curve", fontSize = 10.sp)
          }

          Button(
            onClick = {
              p1x = 0.6f; p1y = -0.28f; p2x = 0.735f; p2y = 0.045f
              onCustomCurveChange(listOf(p1x, p1y, p2x, p2y))
            },
            colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant, contentColor = TextPrimary),
            modifier = Modifier.weight(1f).height(28.dp)
          ) {
            Text("Anticipate", fontSize = 10.sp)
          }

          Button(
            onClick = {
              p1x = 0.175f; p1y = 0.885f; p2x = 0.32f; p2y = 1.275f
              onCustomCurveChange(listOf(p1x, p1y, p2x, p2y))
            },
            colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant, contentColor = TextPrimary),
            modifier = Modifier.weight(1f).height(28.dp)
          ) {
            Text("Overshoot", fontSize = 10.sp)
          }
        }

        // Control point 1 Y slider
        KeyframeSliderRow(
          label = "Handle 1 Curve Y",
          value = p1y,
          range = -0.5f..1.5f,
          format = "%.2f",
          onValueChange = { newVal ->
            p1y = newVal
            onCustomCurveChange(listOf(p1x, p1y, p2x, p2y))
          },
          onReset = {
            p1y = 0.0f
            onCustomCurveChange(listOf(p1x, p1y, p2x, p2y))
          }
        )

        // Control point 2 Y slider
        KeyframeSliderRow(
          label = "Handle 2 Curve Y",
          value = p2y,
          range = -0.5f..1.5f,
          format = "%.2f",
          onValueChange = { newVal ->
            p2y = newVal
            onCustomCurveChange(listOf(p1x, p1y, p2x, p2y))
          },
          onReset = {
            p2y = 1.0f
            onCustomCurveChange(listOf(p1x, p1y, p2x, p2y))
          }
        )
      }
    }
  }
}
