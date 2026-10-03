package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.ExportedVideoEntity
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.components.PrimaryPillButton
import com.example.ui.components.formatDurationShort
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportedVideosScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val exportedVideos by viewModel.exportedVideos.collectAsState()
  var playingVideo by remember { mutableStateOf<ExportedVideoEntity?>(null) }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDarkBg),
    containerColor = StudioDarkBg,
    topBar = {
      TopAppBar(
        title = { Text("Exported Videos (${exportedVideos.size})", color = TextPrimary, fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioDarkBg)
      )
    }
  ) { padding ->
    if (exportedVideos.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(64.dp))
          Spacer(modifier = Modifier.height(12.dp))
          Text("No exported videos yet", style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))
          Spacer(modifier = Modifier.height(6.dp))
          Text("Render your first project in the editor to view it here.", color = TextTertiary, fontSize = 12.sp)
          Spacer(modifier = Modifier.height(16.dp))
          PrimaryPillButton(text = "Go to Editor", onClick = { viewModel.navigateTo(AppScreen.HOME) })
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(exportedVideos, key = { it.id }) { video ->
          val formattedDate = remember(video.timestamp) {
            SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(video.timestamp))
          }
          val sizeFormatted = remember(video.fileSizeBytes) {
            String.format("%.1f MB", video.fileSizeBytes / (1024f * 1024f))
          }

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .testTag("exported_item_${video.id}"),
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(StudioBorder, StudioBorder.copy(alpha = 0.3f))))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Video Icon Thumbnail
              Box(
                modifier = Modifier
                  .size(width = 72.dp, height = 54.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(Color(0xFF0F172A))
                  .clickable { playingVideo = video },
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.PlayCircle, contentDescription = "Play", tint = CyanAccent, modifier = Modifier.size(32.dp))
              }

              Spacer(modifier = Modifier.width(14.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = video.title,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp),
                  maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "${video.resolution} • ${video.fps}fps • $sizeFormatted • ${formatDurationShort(video.durationMs)}",
                  style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent)
                )
                Text(
                  text = formattedDate,
                  style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary, fontSize = 11.sp)
                )
              }

              // Share & Delete actions
              Row {
                IconButton(
                  onClick = {
                    val file = File(video.filePath)
                    if (file.exists()) {
                      val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                      }
                      context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                    }
                  }
                ) {
                  Icon(Icons.Default.Share, contentDescription = "Share", tint = TextSecondary)
                }

                IconButton(
                  onClick = { viewModel.deleteExportedVideo(video.id) }
                ) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedAccent)
                }
              }
            }
          }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
      }
    }
  }

  // Play Video Dialog
  playingVideo?.let { vid ->
    AlertDialog(
      onDismissRequest = { playingVideo = null },
      title = { Text(vid.title, color = TextPrimary, fontWeight = FontWeight.Bold) },
      text = {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(54.dp))
            Text("Playing ${vid.resolution} @ ${vid.fps} FPS", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
            Text(vid.filePath, color = TextTertiary, fontSize = 10.sp, maxLines = 1)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { playingVideo = null },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Text("Close")
        }
      },
      containerColor = StudioSurfaceVariant
    )
  }
}
