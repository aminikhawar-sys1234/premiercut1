package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.example.engine.ai.AiBackgroundRemover
import com.example.engine.ai.AiSegmentationSettings
import com.example.ui.StudioViewModel
import com.example.ui.theme.*

@Composable
fun AiMattingToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier,
  onClose: () -> Unit = {}
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val activeClip = remember(timeline) {
    viewModel.getSelectedVideoClip()
  }

  var isAiEnabled by remember { mutableStateOf(false) }
  var feathering by remember { mutableStateOf(0.2f) }
  var sensitivity by remember { mutableStateOf(0.5f) }
  var blurBg by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = StudioPrimary)
        Text(
          text = "AI Human Background Removal",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
      }
      IconButton(onClick = onClose) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // 1-Click AI Cutout Toggle
      Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("1-Click Portrait Cutout", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("On-device AI person segmentation without green screen", fontSize = 11.sp, color = TextSecondary)
          }
          Switch(
            checked = isAiEnabled,
            onCheckedChange = { enabled ->
              isAiEnabled = enabled
            },
            colors = SwitchDefaults.colors(checkedThumbColor = StudioPrimary)
          )
        }
      }

      if (isAiEnabled) {
        // Edge Feathering
        Text("Edge Softness & Feathering: ${(feathering * 100).toInt()}%", fontSize = 12.sp, color = TextSecondary)
        Slider(
          value = feathering,
          onValueChange = { feathering = it },
          valueRange = 0.05f..0.5f
        )

        // Sensitivity
        Text("Person Detection Sensitivity: ${(sensitivity * 100).toInt()}%", fontSize = 12.sp, color = TextSecondary)
        Slider(
          value = sensitivity,
          onValueChange = { sensitivity = it },
          valueRange = 0.1f..0.9f
        )

        // Blur Background Toggle
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Blur Original Background", fontSize = 13.sp, color = TextPrimary)
          Switch(
            checked = blurBg,
            onCheckedChange = { blurBg = it },
            colors = SwitchDefaults.colors(checkedThumbColor = StudioPrimary)
          )
        }
      }
    }
  }
}
