package com.example.ui.components.audio

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.StudioViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.StudioDark

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceChangerPanel(
  viewModel: StudioViewModel,
  currentSelectedEffect: VoiceChangerItem?,
  onConfirm: (VoiceChangerItem?) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("voice_changer_prefs", Context.MODE_PRIVATE) }

  var searchQuery by remember { mutableStateOf("") }
  val categories = listOf("Voice Filters", "Voice Characters", "Speech to Song", "Favourite")
  var selectedCategory by remember { mutableStateOf("Voice Filters") }
  var selectedEffect by remember { mutableStateOf(currentSelectedEffect) }

  var favoriteIds by remember {
    mutableStateOf(prefs.getStringSet("favorite_voice_fx", emptySet()) ?: emptySet())
  }

  fun toggleFavorite(item: VoiceChangerItem) {
    val updated = favoriteIds.toMutableSet()
    if (updated.contains(item.id)) {
      updated.remove(item.id)
      Toast.makeText(context, "Removed ${item.name} from Favourites", Toast.LENGTH_SHORT).show()
    } else {
      updated.add(item.id)
      Toast.makeText(context, "Added ${item.name} to Favourites ⭐", Toast.LENGTH_SHORT).show()
    }
    favoriteIds = updated
    prefs.edit().putStringSet("favorite_voice_fx", updated).apply()
  }

  val displayItems = remember(searchQuery, selectedCategory, favoriteIds) {
    val all = VoiceChangerCatalog.ALL_EFFECTS
    if (searchQuery.isNotBlank()) {
      all.filter { it.name.contains(searchQuery, ignoreCase = true) }
    } else {
      when (selectedCategory) {
        "Voice Filters" -> VoiceChangerCatalog.FILTERS
        "Voice Characters" -> VoiceChangerCatalog.CHARACTERS
        "Speech to Song" -> VoiceChangerCatalog.SPEECH_TO_SONG
        "Favourite" -> all.filter { favoriteIds.contains(it.id) }
        else -> VoiceChangerCatalog.FILTERS
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDark)
  ) {
    // Header: Small search bar + ✓ Confirm/select icon + ❌ Close icon
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
        placeholder = { Text("Search voice effects...", color = Color.Gray, fontSize = 12.sp) },
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
          .height(42.dp)
      )

      // ✓ Confirm/select icon
      IconButton(
        onClick = {
          selectedEffect?.let {
            Toast.makeText(context, "Voice effect \"${it.name}\" selected", Toast.LENGTH_SHORT).show()
          }
          onConfirm(selectedEffect)
        },
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(CyanAccent)
      ) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = "Confirm Voice Effect",
          tint = Color.Black,
          modifier = Modifier.size(18.dp)
        )
      }

      // ❌ Close icon
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(Color(0xFF1E283E))
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close Voice Changer",
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
      }
    }

    // Categories: Horizontally scrollable category row
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF121826))
        .padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(categories) { cat ->
        val isSelected = selectedCategory == cat && searchQuery.isBlank()
        val count = when (cat) {
          "Voice Filters" -> VoiceChangerCatalog.FILTERS.size
          "Voice Characters" -> VoiceChangerCatalog.CHARACTERS.size
          "Speech to Song" -> VoiceChangerCatalog.SPEECH_TO_SONG.size
          "Favourite" -> favoriteIds.size
          else -> 0
        }
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) CyanAccent else Color(0xFF1A2234),
          modifier = Modifier.clickable {
            searchQuery = ""
            selectedCategory = cat
          }
        ) {
          Text(
            text = "$cat ($count)",
            color = if (isSelected) Color.Black else Color(0xFFD0D6E2),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    }

    Divider(color = Color(0xFF1E283E), thickness = 0.5.dp)

    // Help banner for long press favorites
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF101624))
        .padding(horizontal = 12.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "💡 Tap to preview & select. Long press to save to Favourites.",
        color = Color(0xFF7E8EA8),
        fontSize = 10.sp
      )
    }

    // 4-column Grid: Voice Filter Cards
    if (displayItems.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = if (selectedCategory == "Favourite") "No Favourites yet.\nLong press any voice card to add!" else "No voice effects found",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
          )
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(displayItems, key = { it.id }) { item ->
          val isSelected = selectedEffect?.id == item.id
          val isFav = favoriteIds.contains(item.id)

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) Color(0xFF1B2E4B) else Color(0xFF151C2C),
            border = BorderStroke(
              width = if (isSelected) 2.dp else 1.dp,
              color = if (isSelected) CyanAccent else Color(0xFF1E283E)
            ),
            modifier = Modifier
              .aspectRatio(0.88f)
              .combinedClickable(
                onClick = {
                  selectedEffect = if (isSelected) null else item
                  // Play a small synthesized preview tone
                  viewModel.audioEngine.playPreviewSfx(
                    sfxId = item.id,
                    durationMs = 800L,
                    category = item.name
                  )
                },
                onLongClick = {
                  toggleFavorite(item)
                }
              )
          ) {
            Box(modifier = Modifier.fillMaxSize()) {
              // Top-right favorite indicator star
              if (isFav) {
                Text(
                  text = "⭐",
                  fontSize = 10.sp,
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                )
              }

              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                // Icon / Avatar
                Text(
                  text = item.icon,
                  fontSize = 26.sp
                )

                Spacer(Modifier.height(4.dp))

                // Name
                Text(
                  text = item.name,
                  color = if (isSelected) CyanAccent else Color.White,
                  fontSize = 10.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  textAlign = TextAlign.Center,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )

                if (isSelected) {
                  Spacer(Modifier.height(2.dp))
                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CyanAccent
                  ) {
                    Text(
                      text = "ACTIVE",
                      color = Color.Black,
                      fontSize = 7.sp,
                      fontWeight = FontWeight.ExtraBold,
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
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
}
