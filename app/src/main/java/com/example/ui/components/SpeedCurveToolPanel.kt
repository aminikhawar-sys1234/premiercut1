package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.SpeedCurve
import com.example.domain.model.SpeedCurvePreset
import com.example.ui.StudioViewModel
import com.example.ui.theme.*

@Composable
fun SpeedCurveToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier,
  onClose: () -> Unit = {}
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()

  val activeClip = remember(timeline) {
    viewModel.getSelectedVideoClip()
  }

  var currentSpeed by remember(activeClip) {
    mutableStateOf(activeClip?.speed ?: 1.0f)
  }
  var currentCurve by remember(activeClip) {
    mutableStateOf(activeClip?.speedCurve ?: SpeedCurve(preset = SpeedCurvePreset.STANDARD))
  }
  var isCurveMode by remember { mutableStateOf(currentCurve.preset != SpeedCurvePreset.STANDARD) }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Speed, contentDescription = null, tint = StudioPrimary)
        Text(
          text = "Velocity & Speed Curve Engine",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
      }
      IconButton(onClick = onClose) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    // Toggle: Linear vs Speed Curve
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(StudioSurfaceVariant)
        .padding(4.dp)
    ) {
      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(8.dp))
          .background(if (!isCurveMode) StudioPrimary else Color.Transparent)
          .clickable {
            isCurveMode = false
            val resetCurve = SpeedCurve(preset = SpeedCurvePreset.STANDARD)
            currentCurve = resetCurve
            viewModel.timelineEngine.setClipSpeedCurve(speedCurve = resetCurve)
          }
          .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
      ) {
        Text("Standard Speed", fontSize = 13.sp, color = if (!isCurveMode) Color.White else TextSecondary)
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(8.dp))
          .background(if (isCurveMode) StudioPrimary else Color.Transparent)
          .clickable {
            isCurveMode = true
            if (currentCurve.preset == SpeedCurvePreset.STANDARD) {
              val heroCurve = SpeedCurve(preset = SpeedCurvePreset.HERO_MONTAGE)
              currentCurve = heroCurve
              viewModel.timelineEngine.setClipSpeedCurve(speedCurve = heroCurve)
            }
          }
          .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
      ) {
        Text("Bézier Speed Curve", fontSize = 13.sp, color = if (isCurveMode) Color.White else TextSecondary)
      }
    }

    if (!isCurveMode) {
      // Linear speed
      Text("Speed Multiplier: ${String.format("%.2f", currentSpeed)}x", fontSize = 13.sp, color = TextPrimary)
      Slider(
        value = currentSpeed,
        onValueChange = { s ->
          currentSpeed = s
          viewModel.timelineEngine.setClipSpeed(speed = s)
        },
        valueRange = 0.1f..10.0f
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        listOf(0.2f, 0.5f, 1.0f, 2.0f, 5.0f, 10.0f).forEach { presetSpd ->
          OutlinedButton(
            onClick = {
              currentSpeed = presetSpd
              viewModel.timelineEngine.setClipSpeed(speed = presetSpd)
            },
            modifier = Modifier.padding(horizontal = 2.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text("${presetSpd}x", fontSize = 11.sp)
          }
        }
      }
    } else {
      // Speed Curve Presets & Interactive Canvas
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Speed Presets", fontSize = 12.sp, color = TextSecondary)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          SpeedCurvePreset.values().filter { it != SpeedCurvePreset.STANDARD }.forEach { preset ->
            val isSel = currentCurve.preset == preset
            FilterChip(
              selected = isSel,
              onClick = {
                val newCurve = SpeedCurve(preset = preset)
                currentCurve = newCurve
                viewModel.timelineEngine.setClipSpeedCurve(speedCurve = newCurve)
              },
              label = { Text(preset.displayName, fontSize = 12.sp) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = StudioPrimary,
                selectedLabelColor = Color.White
              )
            )
          }
        }

        // Interactive Bézier Curve Canvas
        Text("Curve Visualizer", fontSize = 12.sp, color = TextSecondary)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(StudioSurfaceVariant)
            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Baseline (1.0x speed)
            drawLine(
              color = Color.White.copy(alpha = 0.2f),
              start = Offset(0f, h * 0.75f),
              end = Offset(w, h * 0.75f),
              strokeWidth = 2f
            )

            // Draw cubic Bézier control points
            val pts = currentCurve.bezierPoints
            if (pts.size >= 4) {
              val p0 = Offset(0f, h)
              val p1 = Offset(pts[0] * w, h - (pts[1] * h))
              val p2 = Offset(pts[2] * w, h - (pts[3] * h))
              val p3 = Offset(w, 0f)

              val path = Path().apply {
                moveTo(p0.x, p0.y)
                cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
              }

              drawPath(
                path = path,
                color = StudioPrimary,
                style = Stroke(width = 4f)
              )

              drawCircle(color = Color.White, radius = 7f, center = p1)
              drawCircle(color = StudioPrimary, radius = 5f, center = p1)
              drawCircle(color = Color.White, radius = 7f, center = p2)
              drawCircle(color = StudioPrimary, radius = 5f, center = p2)
            }
          }
        }
      }
    }
  }
}

