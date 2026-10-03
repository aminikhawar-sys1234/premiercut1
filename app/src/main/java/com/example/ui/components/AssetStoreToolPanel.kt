package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.plugin.AssetDownloadState
import com.example.engine.plugin.AssetDownloaderManager
import com.example.engine.plugin.CatalogAssetItem
import com.example.ui.StudioViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AssetStoreToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier,
  onClose: () -> Unit = {}
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    AssetDownloaderManager.initialize(context)
  }

  val catalogItems by AssetDownloaderManager.catalogAssets.collectAsState()
  val downloadState by AssetDownloaderManager.downloadState.collectAsState()

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
        Icon(Icons.Default.Download, contentDescription = null, tint = StudioPrimary)
        Text(
          text = "Asset & Plugin Store",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
      }
      IconButton(onClick = onClose) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 320.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(catalogItems, key = { it.id }) { item ->
        CatalogItemCard(
          item = item,
          downloadState = downloadState,
          onDownloadClick = {
            coroutineScope.launch {
              AssetDownloaderManager.downloadAndInstallAsset(context, item.id)
            }
          },
          onDeleteClick = {
            AssetDownloaderManager.deleteAsset(context, item.id)
          }
        )
      }
    }
  }
}

@Composable
private fun CatalogItemCard(
  item: CatalogAssetItem,
  downloadState: AssetDownloadState,
  onDownloadClick: () -> Unit,
  onDeleteClick: () -> Unit
) {
  val isThisDownloading = downloadState is AssetDownloadState.Downloading && downloadState.assetId == item.id
  val downloadProgress = if (isThisDownloading) (downloadState as AssetDownloadState.Downloading).progressPercent else 0f

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Surface(
              color = StudioPrimary.copy(alpha = 0.15f),
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                text = "v${item.version}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = StudioPrimary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
              )
            }
          }
          Text(item.description, fontSize = 11.sp, color = TextSecondary, maxLines = 2)
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (item.isInstalled) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
              color = Color(0xFF10B981).copy(alpha = 0.2f),
              shape = RoundedCornerShape(6.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                Text("Installed", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
              }
            }
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
              Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
          }
        } else {
          Button(
            onClick = onDownloadClick,
            enabled = !isThisDownloading,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary)
          ) {
            Text(if (isThisDownloading) "Downloading..." else "Get Asset", fontSize = 11.sp, color = Color.White)
          }
        }
      }

      if (isThisDownloading) {
        LinearProgressIndicator(
          progress = { downloadProgress },
          modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
          color = StudioPrimary
        )
      }
    }
  }
}
