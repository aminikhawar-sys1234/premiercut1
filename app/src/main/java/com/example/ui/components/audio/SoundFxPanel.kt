package com.example.ui.components.audio

import android.content.Context
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
import com.example.ui.theme.StudioSurface
import java.util.Locale

@Composable
fun SoundFxPanel(
  viewModel: StudioViewModel,
  onBack: () -> Unit,
  onCompleteAndClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("audio_tools_prefs", Context.MODE_PRIVATE) }
  
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("Trending") }
  var previewingClipId by remember { mutableStateOf<String?>(null) }
  var favoriteIds by remember {
    mutableStateOf(prefs.getStringSet("favorite_sfx", emptySet()) ?: emptySet())
  }

  fun toggleFavorite(clipId: String) {
    val updated = favoriteIds.toMutableSet()
    if (updated.contains(clipId)) {
      updated.remove(clipId)
      Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show()
    } else {
      updated.add(clipId)
      Toast.makeText(context, "Saved to Favorites ⭐", Toast.LENGTH_SHORT).show()
    }
    favoriteIds = updated
    prefs.edit().putStringSet("favorite_sfx", updated).apply()
  }

  val filteredClips = remember(searchQuery, selectedCategory, favoriteIds) {
    val base = SoundFxCatalog.ALL_CLIPS
    if (searchQuery.isNotBlank()) {
      base.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
          it.category.contains(searchQuery, ignoreCase = true)
      }
    } else {
      base.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDark)
  ) {
    // Header: Search Bar + ❌ Close Button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF0F1523))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Small search bar
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search Sound FX...", color = Color.Gray, fontSize = 12.sp) },
        leadingIcon = {
          Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(16.dp))
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

      // ❌ Close button - returns to previous Audio Tools state
      IconButton(
        onClick = {
          viewModel.audioEngine.stopAudio()
          onBack()
        },
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(Color(0xFF1E283E))
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close Sound FX",
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
      }
    }

    // Horizontally scrollable category row
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF121826))
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(SoundFxCatalog.CATEGORIES) { cat ->
        val isSelected = selectedCategory.equals(cat, ignoreCase = true) && searchQuery.isBlank()
        val catCount = SoundFxCatalog.ALL_CLIPS.count { it.category.equals(cat, ignoreCase = true) }
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) CyanAccent else Color(0xFF1A2234),
          modifier = Modifier.clickable {
            searchQuery = ""
            selectedCategory = cat
          }
        ) {
          Text(
            text = "$cat ($catCount)",
            color = if (isSelected) Color.Black else Color(0xFFD0D6E2),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    }

    Divider(color = Color(0xFF1E283E), thickness = 0.5.dp)

    // Audio Clip Results: Clean single-column list/card layout
    if (filteredClips.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("No Sound FX found", color = Color.Gray, fontSize = 13.sp)
          if (searchQuery.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Button(
              onClick = { searchQuery = "" },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E283E))
            ) {
              Text("Clear Search", color = CyanAccent, fontSize = 12.sp)
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        items(filteredClips, key = { it.id }) { clip ->
          val isPreviewing = previewingClipId == clip.id
          val isFav = favoriteIds.contains(clip.id)

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
              // Icon + Preview Play/Stop button
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(if (isPreviewing) CyanAccent else Color(0xFF1E283E))
                  .clickable {
                    if (isPreviewing) {
                      viewModel.audioEngine.stopAudio()
                      previewingClipId = null
                    } else {
                      previewingClipId = clip.id
                      viewModel.audioEngine.playPreviewSfx(clip.id, clip.durationMs, clip.category)
                    }
                  },
                contentAlignment = Alignment.Center
              ) {
                if (isPreviewing) {
                  Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                  )
                } else {
                  Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Preview",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }

              // Text info
              Column(
                modifier = Modifier
                  .weight(1f)
                  .clickable {
                    // Tapping title also plays preview
                    if (isPreviewing) {
                      viewModel.audioEngine.stopAudio()
                      previewingClipId = null
                    } else {
                      previewingClipId = clip.id
                      viewModel.audioEngine.playPreviewSfx(clip.id, clip.durationMs, clip.category)
                    }
                  }
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = clip.icon,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 6.dp)
                  )
                  Text(
                    text = clip.title,
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
                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E283E)
                  ) {
                    Text(
                      text = clip.category,
                      color = Color(0xFF8E9BB5),
                      fontSize = 9.sp,
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                  }
                  val sec = clip.durationMs / 1000f
                  Text(
                    text = String.format(Locale.US, "%.1fs", sec),
                    color = Color(0xFF7A889B),
                    fontSize = 10.sp
                  )
                }
              }

              // Favorite toggle button
              IconButton(
                onClick = { toggleFavorite(clip.id) },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                  contentDescription = "Favorite",
                  tint = if (isFav) Color(0xFFFF4B6E) else Color(0xFF6B7A99),
                  modifier = Modifier.size(18.dp)
                )
              }

              // "+ Use / Add to Timeline" button
              Button(
                onClick = {
                  viewModel.audioEngine.stopAudio()
                  val soundFile = viewModel.audioEngine.getOrCreateSoundWavFile(clip.id, clip.durationMs, clip.category)
                  val waveform = viewModel.audioEngine.extractWaveformFromFile(soundFile)
                  viewModel.timelineEngine.addAudioClip(
                    title = clip.title,
                    durationMs = clip.durationMs,
                    uri = soundFile.absolutePath,
                    waveformData = waveform
                  )
                  Toast.makeText(context, "Added \"${clip.title}\" to timeline", Toast.LENGTH_SHORT).show()
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
                Text("Use", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }
}
