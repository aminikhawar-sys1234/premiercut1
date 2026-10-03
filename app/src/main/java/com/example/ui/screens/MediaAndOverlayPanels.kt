package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.presets.StockMediaCatalog
import com.example.data.presets.StockMediaItem
import com.example.domain.model.VideoClip
import com.example.engine.SelectedTrackElement
import com.example.ui.StudioViewModel
import com.example.ui.components.formatDurationShort
import com.example.ui.theme.*

enum class MediaImportTarget {
  MAIN_TRACK,
  OVERLAY_PIP
}

@Composable
fun MediaImportPanel(
  viewModel: StudioViewModel,
  onDismiss: () -> Unit,
  defaultTarget: MediaImportTarget = MediaImportTarget.MAIN_TRACK,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var selectedTarget by remember { mutableStateOf(defaultTarget) }
  var selectedFilterCategory by remember { mutableStateOf("All") }
  var imageDurationSec by remember { mutableFloatStateOf(3.0f) }
  var insertAtPlayhead by remember { mutableStateOf(true) }

  // Fallback GetMultipleContents launcher
  val fallbackMediaLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetMultipleContents()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        uris.forEach { uri ->
          try {
            val fileName = getFileNameFromUri(context, uri) ?: "Imported Media"
            val persistentPath = com.example.engine.media.MediaPersistenceManager.persistMedia(
              context = context,
              sourceUriString = uri.toString(),
              suggestedName = fileName
            )
            val metadata = com.example.engine.media.MediaMetadataHelper.extractMetadata(
              context,
              persistentPath,
              defaultImageDurationMs = (imageDurationSec * 1000).toLong()
            )

            if (selectedTarget == MediaImportTarget.MAIN_TRACK) {
              viewModel.checkAndAutoConfigureCanvasFromMedia(
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                frameRate = metadata.frameRate
              )
              viewModel.timelineEngine.addVideoClip(
                uri = persistentPath,
                name = fileName,
                isVideo = metadata.isVideo,
                durationMs = metadata.durationMs,
                atPlayhead = insertAtPlayhead,
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                frameRate = metadata.frameRate,
                mimeType = metadata.mimeType,
                hasAudio = metadata.hasAudio
              )
            } else {
              viewModel.timelineEngine.addOverlayClip(
                uri = persistentPath,
                name = fileName,
                isVideo = metadata.isVideo,
                durationMs = metadata.durationMs,
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                frameRate = metadata.frameRate,
                mimeType = metadata.mimeType,
                hasAudio = metadata.hasAudio
              )
            }
          } catch (e: Throwable) {
            android.util.Log.e("MediaImportPanel", "Error in fallback import", e)
          }
        }
        onDismiss()
      }
    }
  }

  // System Media Launchers (Zero-permission Android Photo & Video Picker)
  val pickMultipleMediaLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        uris.forEach { uri ->
          try {
            val fileName = getFileNameFromUri(context, uri) ?: "Imported Media"
            val persistentPath = com.example.engine.media.MediaPersistenceManager.persistMedia(
              context = context,
              sourceUriString = uri.toString(),
              suggestedName = fileName
            )
            val metadata = com.example.engine.media.MediaMetadataHelper.extractMetadata(
              context,
              persistentPath,
              defaultImageDurationMs = (imageDurationSec * 1000).toLong()
            )

            if (selectedTarget == MediaImportTarget.MAIN_TRACK) {
              viewModel.checkAndAutoConfigureCanvasFromMedia(
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                frameRate = metadata.frameRate
              )
              viewModel.timelineEngine.addVideoClip(
                uri = persistentPath,
                name = fileName,
                isVideo = metadata.isVideo,
                durationMs = metadata.durationMs,
                atPlayhead = insertAtPlayhead,
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                frameRate = metadata.frameRate,
                mimeType = metadata.mimeType,
                hasAudio = metadata.hasAudio
              )
            } else {
              viewModel.timelineEngine.addOverlayClip(
                uri = persistentPath,
                name = fileName,
                isVideo = metadata.isVideo,
                durationMs = metadata.durationMs,
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                frameRate = metadata.frameRate,
                mimeType = metadata.mimeType,
                hasAudio = metadata.hasAudio
              )
            }
          } catch (e: Throwable) {
            android.util.Log.e("MediaImportPanel", "Error importing media", e)
          }
        }
        onDismiss()
      }
    }
  }

  val pickVideoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        uris.forEach { uri ->
          try {
            val fileName = getFileNameFromUri(context, uri) ?: "Imported Video"
            val persistentPath = com.example.engine.media.MediaPersistenceManager.persistMedia(
              context = context,
              sourceUriString = uri.toString(),
              suggestedName = fileName
            )
            val metadata = com.example.engine.media.MediaMetadataHelper.extractMetadata(context, persistentPath)
            if (selectedTarget == MediaImportTarget.MAIN_TRACK) {
              viewModel.checkAndAutoConfigureCanvasFromMedia(
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                frameRate = metadata.frameRate
              )
              viewModel.timelineEngine.addVideoClip(
                uri = persistentPath,
                name = fileName,
                isVideo = true,
                durationMs = metadata.durationMs,
                atPlayhead = insertAtPlayhead,
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                frameRate = metadata.frameRate,
                mimeType = metadata.mimeType,
                hasAudio = metadata.hasAudio
              )
            } else {
              viewModel.timelineEngine.addOverlayClip(
                uri = persistentPath,
                name = fileName,
                isVideo = true,
                durationMs = metadata.durationMs,
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                frameRate = metadata.frameRate,
                mimeType = metadata.mimeType,
                hasAudio = metadata.hasAudio
              )
            }
          } catch (e: Throwable) {
            android.util.Log.e("MediaImportPanel", "Error importing video", e)
          }
        }
        onDismiss()
      }
    }
  }

  val pickImageLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        uris.forEach { uri ->
          try {
            val fileName = getFileNameFromUri(context, uri) ?: "Imported Photo"
            val persistentPath = com.example.engine.media.MediaPersistenceManager.persistMedia(
              context = context,
              sourceUriString = uri.toString(),
              suggestedName = fileName
            )
            val metadata = com.example.engine.media.MediaMetadataHelper.extractMetadata(
              context,
              persistentPath,
              defaultImageDurationMs = (imageDurationSec * 1000).toLong()
            )
            if (selectedTarget == MediaImportTarget.MAIN_TRACK) {
              viewModel.checkAndAutoConfigureCanvasFromMedia(
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                frameRate = metadata.frameRate
              )
              viewModel.timelineEngine.addVideoClip(
                uri = persistentPath,
                name = fileName,
                isVideo = false,
                durationMs = metadata.durationMs,
                atPlayhead = insertAtPlayhead,
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                frameRate = metadata.frameRate,
                mimeType = metadata.mimeType,
                hasAudio = false
              )
            } else {
              viewModel.timelineEngine.addOverlayClip(
                uri = persistentPath,
                name = fileName,
                isVideo = false,
                durationMs = metadata.durationMs,
                width = metadata.width,
                height = metadata.height,
                rotationDegrees = metadata.rotationDegrees,
                frameRate = metadata.frameRate,
                mimeType = metadata.mimeType,
                hasAudio = false
              )
            }
          } catch (e: Throwable) {
            android.util.Log.e("MediaImportPanel", "Error importing photo", e)
          }
        }
        onDismiss()
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Top Bar: Header & Target Switcher
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Default.VideoLibrary,
          contentDescription = null,
          tint = CyanAccent,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Add Media",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 16.sp
          )
        )
      }

      IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    // Destination Track Segmented Control (Main Track vs Overlay Track)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFF0F172A))
        .padding(3.dp)
    ) {
      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(8.dp))
          .background(if (selectedTarget == MediaImportTarget.MAIN_TRACK) CyanAccent else Color.Transparent)
          .clickable { selectedTarget = MediaImportTarget.MAIN_TRACK }
          .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.Movie,
            contentDescription = null,
            tint = if (selectedTarget == MediaImportTarget.MAIN_TRACK) Color.Black else TextSecondary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Main Video Track",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (selectedTarget == MediaImportTarget.MAIN_TRACK) Color.Black else TextSecondary
          )
        }
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(8.dp))
          .background(if (selectedTarget == MediaImportTarget.OVERLAY_PIP) AmberAccent else Color.Transparent)
          .clickable { selectedTarget = MediaImportTarget.OVERLAY_PIP }
          .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.Layers,
            contentDescription = null,
            tint = if (selectedTarget == MediaImportTarget.OVERLAY_PIP) Color.Black else TextSecondary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Overlay (PIP) Track",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (selectedTarget == MediaImportTarget.OVERLAY_PIP) Color.Black else TextSecondary
          )
        }
      }
    }

    // Device Storage Import Actions
    Text(
      text = "Import from Device Gallery",
      style = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        fontSize = 11.sp
      )
    )

    // Primary Full-Width Import Media from Gallery Button
    Button(
      onClick = {
        try {
          pickMultipleMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
          )
        } catch (e: Throwable) {
          try {
            fallbackMediaLauncher.launch("video/*,image/*")
          } catch (e2: Throwable) {
            android.util.Log.e("MediaImportPanel", "Error launching picker", e2)
          }
        }
      },
      colors = ButtonDefaults.buttonColors(
        containerColor = CyanAccent,
        contentColor = Color.Black
      ),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .testTag("import_media_from_gallery_primary_btn")
    ) {
      Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("Import Media from Gallery", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Pick Videos Button
      Button(
        onClick = {
          try {
            pickVideoLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
          } catch (e: Throwable) {
            try {
              fallbackMediaLauncher.launch("video/*")
            } catch (e2: Throwable) {
              android.util.Log.e("MediaImportPanel", "Error launching video picker", e2)
            }
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFF1E293B),
          contentColor = TextPrimary
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .weight(1f)
          .height(44.dp)
          .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
          .testTag("import_device_video_button")
      ) {
        Icon(Icons.Default.VideoCameraBack, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Videos Only", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
      }

      // Pick Images Button
      Button(
        onClick = {
          try {
            pickImageLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          } catch (e: Throwable) {
            try {
              fallbackMediaLauncher.launch("image/*")
            } catch (e2: Throwable) {
              android.util.Log.e("MediaImportPanel", "Error launching image picker", e2)
            }
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFF1E293B),
          contentColor = TextPrimary
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .weight(1f)
          .height(44.dp)
          .border(1.dp, PurpleAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
          .testTag("import_device_image_button")
      ) {
        Icon(Icons.Default.Image, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Photos Only", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
      }
    }

    // Photo Duration & Placement Options
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "Photo Duration: ${String.format("%.1f", imageDurationSec)}s",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Slider(
          value = imageDurationSec,
          onValueChange = { imageDurationSec = it },
          valueRange = 1f..10f,
          steps = 8,
          modifier = Modifier.width(130.dp),
          colors = SliderDefaults.colors(
            thumbColor = PurpleAccent,
            activeTrackColor = PurpleAccent,
            inactiveTrackColor = StudioBorder
          )
        )
      }

      if (selectedTarget == MediaImportTarget.MAIN_TRACK) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable { insertAtPlayhead = !insertAtPlayhead }
        ) {
          Checkbox(
            checked = insertAtPlayhead,
            onCheckedChange = { insertAtPlayhead = it },
            colors = CheckboxDefaults.colors(checkedColor = CyanAccent)
          )
          Text(
            text = "At Playhead",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
          )
        }
      }
    }

    // Stock & Sample Media Catalog Section
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Stock & Sample Media",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
      )
      Text(
        text = "1-Tap Import",
        style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent)
      )
    }

    // Filter Chips Row
    val categories = listOf("All", "Drone & Travel", "Urban & Cyberpunk", "Nature & Landscapes", "Portraits & Aesthetic", "Textures & Solids")
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      categories.forEach { cat ->
        val isSel = selectedFilterCategory == cat
        FilterChip(
          selected = isSel,
          onClick = { selectedFilterCategory = cat },
          label = { Text(cat, fontSize = 11.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
            selectedLabelColor = CyanAccent,
            containerColor = Color(0xFF1E293B),
            labelColor = TextSecondary
          ),
          border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSel,
            borderColor = if (isSel) CyanAccent else StudioBorder
          )
        )
      }
    }

    // Stock Items Horizontal Row / Grid
    val filteredStock = remember(selectedFilterCategory) {
      if (selectedFilterCategory == "All") {
        StockMediaCatalog.stockItems
      } else {
        StockMediaCatalog.stockItems.filter { it.category == selectedFilterCategory }
      }
    }

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(filteredStock, key = { it.id }) { item ->
        StockMediaCard(
          item = item,
          target = selectedTarget,
          onAdd = {
            val duration = if (item.isVideo) item.durationMs else (imageDurationSec * 1000).toLong()
            if (selectedTarget == MediaImportTarget.MAIN_TRACK) {
              viewModel.timelineEngine.addVideoClip(
                uri = item.uri.ifBlank { "stock://${item.id}" },
                name = item.title,
                isVideo = item.isVideo,
                durationMs = duration,
                atPlayhead = insertAtPlayhead
              )
            } else {
              viewModel.timelineEngine.addOverlayClip(
                uri = item.uri.ifBlank { "stock://${item.id}" },
                name = item.title,
                isVideo = item.isVideo,
                durationMs = duration
              )
            }
            onDismiss()
          }
        )
      }
    }
  }
}

@Composable
fun StockMediaCard(
  item: StockMediaItem,
  target: MediaImportTarget,
  onAdd: () -> Unit
) {
  Card(
    modifier = Modifier
      .width(150.dp)
      .height(180.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onAdd),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Thumbnail preview box
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(96.dp)
          .background(
            Brush.linearGradient(
              listOf(Color(item.gradientStart), Color(item.gradientEnd))
            )
          )
          .padding(6.dp)
      ) {
        Text(
          text = item.iconEmoji,
          fontSize = 28.sp,
          modifier = Modifier.align(Alignment.Center)
        )

        // Type badge (Video vs Photo)
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
          Text(
            text = if (item.isVideo) "VIDEO" else "PHOTO",
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = if (item.isVideo) CyanAccent else PurpleAccent
            )
          )
        }

        // Duration or resolution badge
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
          Text(
            text = if (item.isVideo) formatDurationShort(item.durationMs) else item.resolution,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = TextPrimary)
          )
        }
      }

      // Title & Add Button
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 12.sp
          ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
          onClick = onAdd,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (target == MediaImportTarget.MAIN_TRACK) CyanAccent else AmberAccent,
            contentColor = Color.Black
          ),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (target == MediaImportTarget.MAIN_TRACK) "+ Track" else "+ Overlay",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

// -----------------------------------------------------------------------------------------
// Overlay Tool Panel (Inspector & Layer Controls)
// -----------------------------------------------------------------------------------------

@Composable
fun OverlayToolPanel(
  viewModel: StudioViewModel,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()
  var showImportSheet by remember { mutableStateOf(false) }

  // Check if an overlay is currently selected
  val selectedOverlayId = (selectedElement as? SelectedTrackElement.Overlay)?.clipId
  val activeOverlay = remember(timeline.overlayClips, selectedOverlayId) {
    timeline.overlayClips.find { it.id == selectedOverlayId }
  }

  if (showImportSheet) {
    MediaImportPanel(
      viewModel = viewModel,
      onDismiss = { showImportSheet = false },
      defaultTarget = MediaImportTarget.OVERLAY_PIP
    )
    return
  }

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
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Layers, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Overlay (PIP) Studio",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 16.sp
          )
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        // Quick Add Overlay Button
        Button(
          onClick = { showImportSheet = true },
          colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
          shape = RoundedCornerShape(16.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
          modifier = Modifier.height(32.dp).testTag("add_overlay_button")
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Add Overlay", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
        }
      }
    }

    if (activeOverlay != null) {
      // Selected Overlay Inspector
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF0F172A))
          .border(1.dp, AmberAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = activeOverlay.name,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "Time: ${formatDurationShort(activeOverlay.timelineStartMs)} • ${formatDurationShort(activeOverlay.durationMs)}",
              style = MaterialTheme.typography.bodySmall.copy(color = AmberAccent, fontSize = 11.sp)
            )
          }

          Row {
            IconButton(
              onClick = { viewModel.timelineEngine.duplicateSelected() },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(
              onClick = { viewModel.timelineEngine.deleteSelected() },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedAccent, modifier = Modifier.size(18.dp))
            }
          }
        }

        // Scale Slider
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Size Scale: ${String.format("%.2f", activeOverlay.cropScale)}x",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
          )
          Slider(
            value = activeOverlay.cropScale,
            onValueChange = { scale ->
              viewModel.timelineEngine.setOverlayScale(activeOverlay.id, scale)
            },
            valueRange = 0.15f..2.5f,
            modifier = Modifier.width(200.dp),
            colors = SliderDefaults.colors(thumbColor = AmberAccent, activeTrackColor = AmberAccent)
          )
        }

        // Opacity Slider
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Opacity: ${(activeOverlay.opacity * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
          )
          Slider(
            value = activeOverlay.opacity,
            onValueChange = { opacity ->
              viewModel.timelineEngine.setOverlayOpacity(activeOverlay.id, opacity)
            },
            valueRange = 0.05f..1f,
            modifier = Modifier.width(200.dp),
            colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
          )
        }

        // Position Quick Presets
        Text(
          text = "Quick Placement",
          style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary, fontWeight = FontWeight.Bold)
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          PositionPresetButton("Center", activeOverlay.id, 0f, 0f, viewModel)
          PositionPresetButton("Top-Right", activeOverlay.id, 0.45f, -0.45f, viewModel)
          PositionPresetButton("Top-Left", activeOverlay.id, -0.45f, -0.45f, viewModel)
          PositionPresetButton("Bottom-Right", activeOverlay.id, 0.45f, 0.45f, viewModel)
          PositionPresetButton("Bottom-Left", activeOverlay.id, -0.45f, 0.45f, viewModel)
        }

        // Fine Adjustment X & Y
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Offset X: ${String.format("%.2f", activeOverlay.cropOffsetX)}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = TextSecondary))
            Slider(
              value = activeOverlay.cropOffsetX,
              onValueChange = { viewModel.timelineEngine.setOverlayPosition(activeOverlay.id, it, activeOverlay.cropOffsetY) },
              valueRange = -1.0f..1.0f,
              colors = SliderDefaults.colors(thumbColor = PurpleAccent, activeTrackColor = PurpleAccent)
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text("Offset Y: ${String.format("%.2f", activeOverlay.cropOffsetY)}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = TextSecondary))
            Slider(
              value = activeOverlay.cropOffsetY,
              onValueChange = { viewModel.timelineEngine.setOverlayPosition(activeOverlay.id, activeOverlay.cropOffsetX, it) },
              valueRange = -1.0f..1.0f,
              colors = SliderDefaults.colors(thumbColor = PurpleAccent, activeTrackColor = PurpleAccent)
            )
          }
        }

        // Blend Modes
        Text(
          text = "Blend Mode",
          style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary, fontWeight = FontWeight.Bold)
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          val blendModes = listOf("Normal", "Screen", "Multiply", "Overlay", "Lighten")
          blendModes.forEach { mode ->
            val isSel = activeOverlay.blendMode == mode
            FilterChip(
              selected = isSel,
              onClick = { viewModel.timelineEngine.setOverlayBlendMode(activeOverlay.id, mode) },
              label = { Text(mode, fontSize = 11.sp) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AmberAccent.copy(alpha = 0.25f),
                selectedLabelColor = AmberAccent,
                containerColor = Color(0xFF1E293B),
                labelColor = TextSecondary
              )
            )
          }
        }
      }
    } else {
      // Empty or no overlay selected
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF0F172A))
          .padding(20.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Default.Layers, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(36.dp))
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = if (timeline.overlayClips.isEmpty()) "No Overlays on Timeline" else "Select an Overlay to edit",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold)
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Add videos or images as picture-in-picture floating layers",
            style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary, fontSize = 11.sp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = { showImportSheet = true },
            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Overlay Video / Image", fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
        }
      }
    }

    // List of active overlays on timeline
    if (timeline.overlayClips.isNotEmpty()) {
      Text(
        text = "Overlay Layers (${timeline.overlayClips.size})",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
      )

      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(timeline.overlayClips, key = { it.id }) { clip ->
          val isSelected = clip.id == selectedOverlayId
          Card(
            modifier = Modifier
              .width(130.dp)
              .clip(RoundedCornerShape(8.dp))
              .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) AmberAccent else StudioBorder,
                shape = RoundedCornerShape(8.dp)
              )
              .clickable {
                viewModel.timelineEngine.selectElement(SelectedTrackElement.Overlay(clip.id))
                viewModel.timelineEngine.setPosition(clip.timelineStartMs)
              },
            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF1E1B4B) else Color(0xFF1E293B))
          ) {
            Column(modifier = Modifier.padding(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = if (clip.isVideo) "VIDEO" else "IMAGE",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
                )
                Text(
                  text = formatDurationShort(clip.durationMs),
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = TextSecondary)
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = clip.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RowScope.PositionPresetButton(
  label: String,
  clipId: String,
  x: Float,
  y: Float,
  viewModel: StudioViewModel
) {
  Button(
    onClick = { viewModel.timelineEngine.setOverlayPosition(clipId, x, y) },
    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = TextSecondary),
    shape = RoundedCornerShape(6.dp),
    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    modifier = Modifier
      .weight(1f)
      .height(28.dp)
  ) {
    Text(label, fontSize = 9.sp, maxLines = 1)
  }
}

// -----------------------------------------------------------------------------------------
// Helper Utilities for resolving Media Uris
// -----------------------------------------------------------------------------------------

fun getFileNameFromUri(context: Context, uri: Uri): String? {
  return try {
    var result: String? = null
    if (uri.scheme == "content") {
      val cursor = context.contentResolver.query(uri, null, null, null, null)
      cursor?.use {
        if (it.moveToFirst()) {
          val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (index != -1) {
            result = it.getString(index)
          }
        }
      }
    }
    result ?: uri.lastPathSegment
  } catch (e: Exception) {
    uri.lastPathSegment
  }
}

private fun isVideoUri(context: Context, uri: Uri, fileName: String): Boolean {
  return try {
    val mime = context.contentResolver.getType(uri)
    if (mime != null) {
      mime.startsWith("video/")
    } else {
      val lower = fileName.lowercase()
      lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".3gp")
    }
  } catch (e: Exception) {
    true
  }
}
