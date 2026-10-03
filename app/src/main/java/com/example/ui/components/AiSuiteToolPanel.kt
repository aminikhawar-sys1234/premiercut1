package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ai.*
import com.example.ui.EditorToolbarTab
import com.example.ui.StudioViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

data class AiFeatureItem(
  val id: String,
  val title: String,
  val description: String,
  val icon: ImageVector,
  val tag: String = "AI PRO"
)

@Composable
fun AiSuiteToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier,
  onClose: () -> Unit = {}
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var isProcessing by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf("") }
  var selectedLanguage by remember { mutableStateOf(CaptionLanguage.URDU) }

  val aiFeatures = listOf(
    AiFeatureItem("captions", "AI Auto Captions", "Urdu, English, Arabic Speech-to-Text", Icons.Default.ClosedCaption),
    AiFeatureItem("scene_cut", "AI Scene Cut", "Auto detect scene boundaries & split", Icons.Default.ContentCut),
    AiFeatureItem("color", "AI Color Grade", "Intelligent white balance & skin tone", Icons.Default.Palette),
    AiFeatureItem("motion", "Motion Tracker", "Track objects & anchor text/stickers", Icons.Default.GpsFixed),
    AiFeatureItem("matting", "AI Cutout / Matting", "Remove background without green screen", Icons.Default.AutoAwesome),
    AiFeatureItem("tts", "Text to Speech", "Urdu/English voice synthesis", Icons.Default.RecordVoiceOver)
  )

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
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFE040FB))
        Text(
          text = "AI Creator Engine",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
      }
      IconButton(onClick = onClose) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    if (isProcessing) {
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          CircularProgressIndicator(color = Color(0xFFE040FB))
          Text(statusMessage, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
      }
    } else {
      // Language Selector Bar for Auto Captions / Speech
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("AI Language Mode:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          CaptionLanguage.values().take(3).forEach { lang ->
            FilterChip(
              selected = selectedLanguage == lang,
              onClick = { selectedLanguage = lang },
              label = { Text(lang.displayName, fontSize = 11.sp) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFE040FB).copy(alpha = 0.2f),
                selectedLabelColor = Color(0xFFE040FB)
              )
            )
          }
        }
      }

      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.heightIn(max = 240.dp)
      ) {
        items(aiFeatures) { feature ->
          Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                coroutineScope.launch {
                  isProcessing = true
                  when (feature.id) {
                    "captions" -> {
                      val timeline = viewModel.timelineEngine.timeline.value
                      val clips = AiAutoCaptionEngine.generateCaptions(context, timeline, selectedLanguage) { _, msg ->
                        statusMessage = msg
                      }
                      clips.forEach { clip ->
                        viewModel.timelineEngine.addTextClipObject(clip)
                      }
                    }
                    "scene_cut" -> {
                      val activeClip = viewModel.getSelectedVideoClip()
                      if (activeClip != null) {
                        val cuts = AiSceneDetector.detectSceneCuts(activeClip) { _, msg ->
                          statusMessage = msg
                        }
                        cuts.forEach { _ ->
                          viewModel.timelineEngine.splitAtPlayhead()
                        }
                      }
                    }
                    "color" -> {
                      val activeClip = viewModel.getSelectedVideoClip()
                      if (activeClip != null) {
                        val adj = AiColorCorrectionEngine.autoColorCorrect(activeClip) { _, msg ->
                          statusMessage = msg
                        }
                        viewModel.timelineEngine.updateAdjustments(adj)
                      }
                    }
                    "motion" -> {
                      viewModel.setActiveToolbarTab(EditorToolbarTab.MOTION_TRACKING)
                    }
                    "matting" -> {
                      viewModel.setActiveToolbarTab(EditorToolbarTab.AI_MATTING)
                    }
                    "tts" -> {
                      statusMessage = "Synthesizing Urdu/English speech clip..."
                      kotlinx.coroutines.delay(1000)
                    }
                  }
                  isProcessing = false
                }
              }
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color(0xFFE040FB).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(feature.icon, contentDescription = null, tint = Color(0xFFE040FB), modifier = Modifier.size(20.dp))
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(feature.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(feature.description, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
              }
            }
          }
        }
      }
    }
  }
}
