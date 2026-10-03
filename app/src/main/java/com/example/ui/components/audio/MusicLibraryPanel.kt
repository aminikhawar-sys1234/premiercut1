package com.example.ui.components.audio

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

@Composable
fun MusicLibraryPanel(
  viewModel: StudioViewModel,
  onClose: () -> Unit,
  onCompleteAndClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  val genres = listOf("All Genres", "Lo-Fi", "Vlog", "Cinematic", "Electronic", "Acoustic", "Tech", "Ambient", "Gaming")
  var selectedGenre by remember { mutableStateOf("All Genres") }
  var previewingId by remember { mutableStateOf<String?>(null) }

  val filteredTracks = remember(searchQuery, selectedGenre) {
    val all = MusicCatalog.TRACKS
    if (searchQuery.isNotBlank()) {
      all.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
          it.artist.contains(searchQuery, ignoreCase = true) ||
          it.genre.contains(searchQuery, ignoreCase = true)
      }
    } else {
      if (selectedGenre == "All Genres") all else all.filter { it.genre == selectedGenre }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDark)
  ) {
    // Header: Search Bar + ❌ Close button
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
        placeholder = { Text("Search royalty-free music tracks...", color = Color.Gray, fontSize = 12.sp) },
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
          contentDescription = "Close Music",
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
      }
    }

    // Genre row
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF121826))
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(genres) { genre ->
        val isSelected = selectedGenre == genre && searchQuery.isBlank()
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) CyanAccent else Color(0xFF1A2234),
          modifier = Modifier.clickable {
            searchQuery = ""
            selectedGenre = genre
          }
        ) {
          Text(
            text = genre,
            color = if (isSelected) Color.Black else Color(0xFFD0D6E2),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    }

    Divider(color = Color(0xFF1E283E), thickness = 0.5.dp)

    // Tracks List
    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(filteredTracks, key = { it.id }) { track ->
        val isPreviewing = previewingId == track.id
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
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isPreviewing) CyanAccent else Color(0xFF1E283E))
                .clickable {
                  if (isPreviewing) {
                    viewModel.audioEngine.stopAudio()
                    previewingId = null
                  } else {
                    previewingId = track.id
                    viewModel.audioEngine.playPreviewSfx(track.id, 8000L, track.genre)
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

            // Track details
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(track.icon, fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
                Text(
                  text = track.title,
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
                Text(track.artist, color = Color.Gray, fontSize = 10.sp)
                Text("•", color = Color(0xFF4A5568), fontSize = 10.sp)
                Text(track.genre, color = CyanAccent, fontSize = 10.sp)
                val sec = track.durationMs / 1000f
                Text("• ${String.format(Locale.US, "%.0fs", sec)}", color = Color(0xFF7A889B), fontSize = 10.sp)
              }
            }

            // "+ Add" button
            Button(
              onClick = {
                viewModel.audioEngine.stopAudio()
                val soundFile = viewModel.audioEngine.getOrCreateSoundWavFile(track.id, track.durationMs, track.genre)
                val waveform = viewModel.audioEngine.extractWaveformFromFile(soundFile)
                viewModel.timelineEngine.addAudioClip(
                  title = track.title,
                  durationMs = track.durationMs,
                  uri = soundFile.absolutePath,
                  waveformData = waveform
                )
                Toast.makeText(context, "Added \"${track.title}\" to timeline", Toast.LENGTH_SHORT).show()
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
