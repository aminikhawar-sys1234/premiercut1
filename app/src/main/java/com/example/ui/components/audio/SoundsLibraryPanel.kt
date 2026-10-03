package com.example.ui.components.audio

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.StudioViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.StudioDark
import java.util.Locale

data class SoundAssetItem(
  val id: String,
  val title: String,
  val category: String,
  val durationMs: Long,
  val iconEmoji: String,
  val downloads: String
)

object OnlineSoundsCatalog {
  val CATEGORIES = listOf("Popular", "Electronic", "Cinematic", "Ambient", "Pop", "Beats", "Lo-Fi", "Acoustic", "Rock", "Vlog")

  val ASSETS = listOf(
    SoundAssetItem("snd_future_bass", "Future Bass Drop Chord", "Electronic", 4500L, "⚡", "48.2k"),
    SoundAssetItem("snd_lofi_rain", "Raindrops on Cozy Window", "Lo-Fi", 12000L, "🌧️", "92.5k"),
    SoundAssetItem("snd_epic_impact", "Hollywood Braam Horn Hit", "Cinematic", 3200L, "🎬", "31.4k"),
    SoundAssetItem("snd_sunset_guitar", "Sunset Acoustic Riff", "Acoustic", 8500L, "🎸", "24.1k"),
    SoundAssetItem("snd_vlog_whistle", "Cheerful Morning Whistle", "Vlog", 6200L, "☀️", "65.7k"),
    SoundAssetItem("snd_synth_wave", "Analog Retro Poly Synth", "Electronic", 9200L, "🕹️", "19.8k"),
    SoundAssetItem("snd_meditation_bowl", "Tibetan Singing Bowl", "Ambient", 11000L, "🧘", "43.0k"),
    SoundAssetItem("snd_trap_808", "Deep 808 Glide Bass Hit", "Beats", 3800L, "🔊", "88.3k"),
    SoundAssetItem("snd_piano_dream", "Nocturne Ambient Piano", "Cinematic", 14000L, "🎹", "37.6k"),
    SoundAssetItem("snd_pop_groove", "Funky Disco Bassline", "Pop", 7800L, "🕺", "52.9k")
  )
}

@Composable
fun SoundsLibraryPanel(
  viewModel: StudioViewModel,
  onClose: () -> Unit,
  onCompleteAndClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("Popular") }
  var previewingId by remember { mutableStateOf<String?>(null) }

  val filteredSounds = remember(searchQuery, selectedCategory) {
    val all = OnlineSoundsCatalog.ASSETS
    if (searchQuery.isNotBlank()) {
      all.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
          it.category.contains(searchQuery, ignoreCase = true)
      }
    } else {
      if (selectedCategory == "Popular") all else all.filter { it.category == selectedCategory }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDark)
  ) {
    // Header: Search bar + ❌ Close button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF0F1523))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search online sounds library...", color = Color.Gray, fontSize = 12.sp) },
        leadingIcon = {
          Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
              Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(14.dp))
            }
          }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = Color(0xFF161D2E),
          unfocusedContainerColor = Color(0xFF161D2E),
          focusedBorderColor = CyanAccent,
          unfocusedBorderColor = Color(0xFF26324A),
          focusedTextColor = Color.White,
          unfocusedTextColor = Color.White
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
          .weight(1f)
          .height(44.dp)
      )

      IconButton(
        onClick = {
          viewModel.audioEngine.stopAudio()
          onClose()
        },
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(Color(0xFF1E283E))
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close Sounds Library",
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
      }
    }

    // Category row
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF121826))
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(OnlineSoundsCatalog.CATEGORIES) { cat ->
        val isSelected = selectedCategory == cat && searchQuery.isBlank()
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) CyanAccent else Color(0xFF1A2234),
          modifier = Modifier.clickable {
            searchQuery = ""
            selectedCategory = cat
          }
        ) {
          Text(
            text = cat,
            color = if (isSelected) Color.Black else Color(0xFFD0D6E2),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    }

    Divider(color = Color(0xFF1E283E), thickness = 0.5.dp)

    // Sounds List
    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(filteredSounds, key = { it.id }) { sound ->
        val isPreviewing = previewingId == sound.id
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFF151C2C),
          border = BorderStroke(1.dp, if (isPreviewing) CyanAccent else Color(0xFF1E283E)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Play/Pause button
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isPreviewing) CyanAccent else Color(0xFF1E283E))
                .clickable {
                  if (isPreviewing) {
                    viewModel.audioEngine.stopAudio()
                    previewingId = null
                  } else {
                    previewingId = sound.id
                    viewModel.audioEngine.playPreviewSfx(sound.id, sound.durationMs, sound.category)
                  }
                },
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPreviewing) "Stop" else "Play",
                tint = if (isPreviewing) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
              )
            }

            // Title & Info
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sound.iconEmoji, fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
                Text(
                  text = sound.title,
                  color = Color.White,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Spacer(Modifier.height(2.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(sound.category, color = CyanAccent, fontSize = 10.sp)
                val sec = sound.durationMs / 1000f
                Text(String.format(Locale.US, "%.1fs", sec), color = Color.Gray, fontSize = 10.sp)
                Text("• ${sound.downloads} uses", color = Color(0xFF7A889B), fontSize = 10.sp)
              }
            }

            // "+ Add" button
            Button(
              onClick = {
                viewModel.audioEngine.stopAudio()
                val soundFile = viewModel.audioEngine.getOrCreateSoundWavFile(sound.id, sound.durationMs, sound.category)
                val waveform = viewModel.audioEngine.extractWaveformFromFile(soundFile)
                viewModel.timelineEngine.addAudioClip(
                  title = sound.title,
                  durationMs = sound.durationMs,
                  uri = soundFile.absolutePath,
                  waveformData = waveform
                )
                Toast.makeText(context, "Added \"${sound.title}\" to timeline", Toast.LENGTH_SHORT).show()
                onCompleteAndClose()
              },
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = CyanAccent,
                contentColor = Color.Black
              ),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
              modifier = Modifier.height(30.dp)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(2.dp))
              Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}
