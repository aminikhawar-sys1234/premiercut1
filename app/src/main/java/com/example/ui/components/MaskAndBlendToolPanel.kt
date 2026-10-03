package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.MaskSettings
import com.example.domain.model.MaskShape
import com.example.ui.StudioViewModel
import com.example.ui.theme.*

@Composable
fun MaskAndBlendToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier,
  onClose: () -> Unit = {}
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()

  val activeClip = remember(timeline, selectedElement) {
    viewModel.getSelectedVideoClip()
  }

  var currentMask by remember(activeClip) {
    mutableStateOf(activeClip?.mask ?: MaskSettings())
  }
  var currentBlendMode by remember(activeClip) {
    mutableStateOf(activeClip?.blendMode ?: "Normal")
  }
  var selectedTab by remember { mutableStateOf(0) } // 0=Mask, 1=Blending

  val blendModes = listOf(
    "Normal", "Multiply", "Screen", "Overlay", "Darken",
    "Lighten", "Add", "Color Dodge", "Color Burn", "Soft Light", "Hard Light", "Difference", "Exclusion"
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. SINGLE COMPACT HEADER: [ 🔧 Mask & Blend    X ]
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
            imageVector = Icons.Default.Layers,
            contentDescription = null,
            tint = Color(0xFF00C2FF),
            modifier = Modifier.size(16.dp)
          )
        }
        Text(
          text = "Mask & Blend",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 14.sp
          )
        )
      }

      IconButton(
        onClick = onClose,
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

    // 2. TAB SWITCHER (GPU Masking vs Blend Modes)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(StudioSurfaceVariant)
        .padding(3.dp)
    ) {
      listOf("GPU Masking", "Blend Modes").forEachIndexed { index, label ->
        val isSel = selectedTab == index
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSel) StudioPrimary else Color.Transparent)
            .clickable { selectedTab = index }
            .padding(vertical = 7.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
            color = if (isSel) Color.White else TextSecondary
          )
        }
      }
    }

    // 3. CONTENT BODY
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      if (selectedTab == 0) {
        // GPU Masking Controls
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text("Mask Shape", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(MaskShape.NONE, MaskShape.RECTANGLE, MaskShape.CIRCLE, MaskShape.LINEAR, MaskShape.MIRROR).forEach { shape ->
              val isSelected = currentMask.shape == shape && currentMask.enabled
              val label = when (shape) {
                MaskShape.NONE -> "None"
                MaskShape.RECTANGLE -> "Rectangle"
                MaskShape.CIRCLE -> "Circle"
                MaskShape.LINEAR -> "Linear"
                MaskShape.MIRROR -> "Mirror"
                else -> shape.name
              }
              FilterChip(
                selected = isSelected,
                onClick = {
                  val newMask = currentMask.copy(shape = shape, enabled = shape != MaskShape.NONE)
                  currentMask = newMask
                  viewModel.timelineEngine.setClipMask(mask = newMask)
                },
                label = { Text(label, fontSize = 11.5.sp) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = StudioPrimary,
                  selectedLabelColor = Color.White
                )
              )
            }
          }

          if (currentMask.shape != MaskShape.NONE && currentMask.enabled) {
            // Invert Toggle
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Invert Mask", fontSize = 12.5.sp, color = TextPrimary)
              Switch(
                checked = currentMask.isInverted,
                onCheckedChange = { inv ->
                  val newMask = currentMask.copy(isInverted = inv)
                  currentMask = newMask
                  viewModel.timelineEngine.setClipMask(mask = newMask)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = StudioPrimary)
              )
            }

            // Size / Scale
            Text("Mask Size: ${(currentMask.width * 100).toInt()}%", fontSize = 11.5.sp, color = TextSecondary)
            Slider(
              value = currentMask.width,
              onValueChange = { sz ->
                val newMask = currentMask.copy(width = sz, height = sz)
                currentMask = newMask
                viewModel.timelineEngine.setClipMask(mask = newMask)
              },
              valueRange = 0.1f..2.0f
            )

            // Feather Softness
            Text("Feather Softness: ${(currentMask.feather * 100).toInt()}%", fontSize = 11.5.sp, color = TextSecondary)
            Slider(
              value = currentMask.feather,
              onValueChange = { f ->
                val newMask = currentMask.copy(feather = f)
                currentMask = newMask
                viewModel.timelineEngine.setClipMask(mask = newMask)
              },
              valueRange = 0.0f..1.0f
            )

            // Rotation
            Text("Rotation: ${currentMask.rotation.toInt()}°", fontSize = 11.5.sp, color = TextSecondary)
            Slider(
              value = currentMask.rotation,
              onValueChange = { r ->
                val newMask = currentMask.copy(rotation = r)
                currentMask = newMask
                viewModel.timelineEngine.setClipMask(mask = newMask)
              },
              valueRange = -180f..180f
            )

            // Opacity
            Text("Opacity: ${(currentMask.opacity * 100).toInt()}%", fontSize = 11.5.sp, color = TextSecondary)
            Slider(
              value = currentMask.opacity,
              onValueChange = { op ->
                val newMask = currentMask.copy(opacity = op)
                currentMask = newMask
                viewModel.timelineEngine.setClipMask(mask = newMask)
              },
              valueRange = 0.0f..1.0f
            )
          }
        }
      } else {
        // Blend Modes
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("GPU Composite Blend Mode", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
          blendModes.chunked(3).forEach { rowModes ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              rowModes.forEach { mode ->
                val isSel = currentBlendMode.equals(mode, ignoreCase = true)
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) StudioPrimary else StudioSurfaceVariant)
                    .border(
                      width = if (isSel) 2.dp else 1.dp,
                      color = if (isSel) StudioPrimary else StudioBorder,
                      shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                      currentBlendMode = mode
                      viewModel.timelineEngine.setClipBlendMode(blendMode = mode)
                    }
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = mode,
                    fontSize = 11.5.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSel) Color.White else TextPrimary
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
