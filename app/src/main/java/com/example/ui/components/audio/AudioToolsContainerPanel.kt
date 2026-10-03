package com.example.ui.components.audio

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.StudioViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.StudioDark
import kotlinx.coroutines.launch
import java.io.File

data class AudioMainToolItem(
  val id: String,
  val name: String,
  val iconEmoji: String,
  val subPanel: AudioSubPanel? = null
)

@Composable
fun AudioToolsContainerPanel(
  viewModel: StudioViewModel,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var currentSubPanel by remember { mutableStateOf(AudioSubPanel.MAIN_GRID) }

  // Shared state between Record and child panels
  var activeVoiceEffect by remember { mutableStateOf<VoiceChangerItem?>(null) }
  var voiceEnhanceEnabled by remember { mutableStateOf(false) }
  var voiceEnhanceLevel by remember { mutableStateOf(60) }

  // File picker launcher for Upload Audio
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      coroutineScope.launch {
        try {
          val mime = context.contentResolver.getType(uri) ?: ""
          val isVideo = mime.startsWith("video/")

          val audioPath: String
          val clipTitle: String
          var durationMs = 8000L

          if (isVideo) {
            val extracted = viewModel.audioEngine.extractAudioFromVideo(uri.toString())
            audioPath = extracted?.absolutePath ?: uri.toString()
            clipTitle = "Extracted Audio"
          } else {
            val fileName = "imported_audio_${System.currentTimeMillis()}.m4a"
            val dest = File(context.filesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
              dest.outputStream().use { output -> input.copyTo(output) }
            }
            audioPath = dest.absolutePath
            clipTitle = "Imported Audio"
          }

          val mmr = MediaMetadataRetriever()
          try {
            mmr.setDataSource(context, Uri.fromFile(File(audioPath)))
            val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) durationMs = durStr.toLong()
          } catch (ignored: Exception) {
          } finally {
            try { mmr.release() } catch (ignored: Exception) {}
          }

          val waveform = viewModel.audioEngine.extractWaveformFromFile(File(audioPath))

          viewModel.timelineEngine.addAudioClip(
            title = clipTitle,
            durationMs = durationMs,
            uri = audioPath,
            waveformData = waveform
          )

          Toast.makeText(context, "Audio imported & added to timeline!", Toast.LENGTH_SHORT).show()
          onClose() // Automatically close panel
        } catch (e: Exception) {
          Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  val mainTools = remember {
    listOf(
      AudioMainToolItem("upload", "Upload Audio", "🎵", null),
      AudioMainToolItem("sounds", "Sounds", "🔊", AudioSubPanel.SOUNDS_LIBRARY),
      AudioMainToolItem("sound_fx", "Sound FX", "✨", AudioSubPanel.SOUND_FX),
      AudioMainToolItem("record", "Record", "🎙️", AudioSubPanel.RECORD),
      AudioMainToolItem("text_to_audio", "Text to Audio", "🗣️", AudioSubPanel.TEXT_TO_AUDIO),
      AudioMainToolItem("music", "Music", "🎶", AudioSubPanel.MUSIC_LIBRARY),
      AudioMainToolItem("copyright", "Copyright", "©️", AudioSubPanel.COPYRIGHT_CHECK)
    )
  }

  Box(modifier = modifier.fillMaxWidth().heightIn(min = 180.dp, max = 240.dp)) {
    AnimatedContent(
      targetState = currentSubPanel,
      transitionSpec = { fadeIn() togetherWith fadeOut() },
      label = "AudioSubPanelTransition"
    ) { panel ->
      when (panel) {
        AudioSubPanel.MAIN_GRID -> {
          // Section 1: Audio Tools Main Panel (3-column × 2-row grid, 7 tools, responsive, small close icon)
          Column(
            modifier = Modifier
              .fillMaxSize()
              .background(StudioDark)
          ) {
            // Header with Title & Small ❌ Close icon
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1523))
                .padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E283E)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(15.dp)
                  )
                }
                Text(
                  text = "Audio Tools",
                  color = Color.White,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold
                )
              }

              // Small ❌ Close icon - immediately closes the panel
              IconButton(
                onClick = onClose,
                modifier = Modifier
                  .size(30.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF1E283E))
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Close Audio Tools",
                  tint = Color.White,
                  modifier = Modifier.size(16.dp)
                )
              }
            }

            Divider(color = Color(0xFF1E283E), thickness = 0.5.dp)

            // 3-column grid for the 7 tools
            LazyVerticalGrid(
              columns = GridCells.Fixed(3),
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(mainTools, key = { it.id }) { tool ->
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0xFF151C2C),
                  border = BorderStroke(1.dp, Color(0xFF1E283E)),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clickable {
                      if (tool.id == "upload") {
                        filePickerLauncher.launch("*/*")
                      } else if (tool.subPanel != null) {
                        currentSubPanel = tool.subPanel
                      }
                    }
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                  ) {
                    Text(text = tool.iconEmoji, fontSize = 20.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                      text = tool.name,
                      color = Color.White,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.SemiBold,
                      textAlign = TextAlign.Center,
                      maxLines = 1
                    )
                  }
                }
              }
            }
          }
        }

        AudioSubPanel.SOUNDS_LIBRARY -> {
          SoundsLibraryPanel(
            viewModel = viewModel,
            onClose = { currentSubPanel = AudioSubPanel.MAIN_GRID },
            onCompleteAndClose = onClose
          )
        }

        AudioSubPanel.SOUND_FX -> {
          SoundFxPanel(
            viewModel = viewModel,
            onBack = { currentSubPanel = AudioSubPanel.MAIN_GRID },
            onCompleteAndClose = onClose
          )
        }

        AudioSubPanel.RECORD -> {
          RecordAudioPanel(
            viewModel = viewModel,
            activeVoiceEffect = activeVoiceEffect,
            voiceEnhanceEnabled = voiceEnhanceEnabled,
            voiceEnhanceLevel = voiceEnhanceLevel,
            onOpenVoiceChanger = { currentSubPanel = AudioSubPanel.VOICE_CHANGER },
            onOpenVoiceEnhance = { currentSubPanel = AudioSubPanel.VOICE_ENHANCE },
            onClose = { currentSubPanel = AudioSubPanel.MAIN_GRID },
            onCompleteAndClose = onClose
          )
        }

        AudioSubPanel.VOICE_ENHANCE -> {
          VoiceEnhancePanel(
            initialEnabled = voiceEnhanceEnabled,
            initialLevel = voiceEnhanceLevel,
            onConfirm = { enabled, level ->
              voiceEnhanceEnabled = enabled
              voiceEnhanceLevel = level
              val targetClipId = viewModel.timelineEngine.selectedClipIds.value.firstOrNull()
              if (targetClipId != null) {
                val currentFx = viewModel.timelineEngine.getClipAudioEffects(targetClipId) ?: com.example.domain.model.AudioEffectsSettings()
                val updatedFx = currentFx.copy(
                  noiseReductionDb = if (enabled) (level / 100f) * 20f else 0f,
                  highGainDb = if (enabled) (level / 100f) * 4f else 0f,
                  normalizeVolume = enabled
                )
                viewModel.timelineEngine.setClipAudioEffects(targetClipId, updatedFx)
              }
              currentSubPanel = AudioSubPanel.RECORD
            },
            onClose = { currentSubPanel = AudioSubPanel.RECORD }
          )
        }

        AudioSubPanel.VOICE_CHANGER -> {
          VoiceChangerPanel(
            viewModel = viewModel,
            currentSelectedEffect = activeVoiceEffect,
            onConfirm = { selected ->
              activeVoiceEffect = selected
              val targetClipId = viewModel.timelineEngine.selectedClipIds.value.firstOrNull()
              if (targetClipId != null) {
                val currentFx = viewModel.timelineEngine.getClipAudioEffects(targetClipId) ?: com.example.domain.model.AudioEffectsSettings()
                val updatedFx = currentFx.copy(
                  voiceEffect = selected?.voiceEffect ?: com.example.domain.model.VoiceEffect.NONE,
                  pitchShiftSemitones = selected?.pitchShift ?: 0f
                )
                viewModel.timelineEngine.setClipAudioEffects(targetClipId, updatedFx)
              }
              currentSubPanel = AudioSubPanel.RECORD
            },
            onClose = { currentSubPanel = AudioSubPanel.RECORD }
          )
        }

        AudioSubPanel.TEXT_TO_AUDIO -> {
          TextToAudioFullPanel(
            viewModel = viewModel,
            onClose = { currentSubPanel = AudioSubPanel.MAIN_GRID },
            onCompleteAndClose = onClose
          )
        }

        AudioSubPanel.MUSIC_LIBRARY -> {
          MusicLibraryPanel(
            viewModel = viewModel,
            onClose = { currentSubPanel = AudioSubPanel.MAIN_GRID },
            onCompleteAndClose = onClose
          )
        }

        AudioSubPanel.COPYRIGHT_CHECK -> {
          CopyrightCheckPanel(
            viewModel = viewModel,
            onClose = { currentSubPanel = AudioSubPanel.MAIN_GRID }
          )
        }
      }
    }
  }
}
