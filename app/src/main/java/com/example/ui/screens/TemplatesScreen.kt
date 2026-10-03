package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.firebase.FirebaseTemplateManager
import com.example.data.presets.TemplatesCatalog
import com.example.data.presets.VideoTemplate
import com.example.data.repository.NotificationRepository
import com.example.domain.model.AspectRatio
import com.example.domain.model.FrameRate
import com.example.domain.model.Resolution
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.components.home.PremierBottomNavigation
import com.example.ui.components.home.PremierHeader
import com.example.ui.theme.*

@Composable
fun TemplatesScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val settings by viewModel.settings.collectAsState()
  val notifications by NotificationRepository.notifications.collectAsState()
  val hasUnread = remember(notifications) { notifications.any { !it.isRead } }

  val firebaseTemplates by FirebaseTemplateManager.templates.collectAsState()
  val allTemplates = remember(firebaseTemplates) {
    if (firebaseTemplates.isNotEmpty()) firebaseTemplates else TemplatesCatalog.templates
  }

  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("All") }
  val categories = remember { listOf("All", "Trending", "Reel", "Business", "Cinematic", "Product Ads") }

  val instantVideoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      viewModel.createProjectWithMedia("Video Project", uris.map { it.toString() }, isVideo = true)
    }
  }

  val onStartNewProject: () -> Unit = {
    if (settings.openEditorDirectlyOnNewProject) {
      viewModel.createNewProject(
        name = "New Project",
        aspectRatio = AspectRatio.RATIO_16_9,
        resolution = Resolution.RES_1080P,
        fps = FrameRate.FPS_30,
        initialMediaClips = emptyList()
      )
    } else {
      instantVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }
  }

  val filteredTemplates = remember(allTemplates, searchQuery, selectedCategory) {
    allTemplates.filter { tpl ->
      val matchesSearch = searchQuery.isBlank() ||
        tpl.title.contains(searchQuery, ignoreCase = true) ||
        tpl.category.contains(searchQuery, ignoreCase = true) ||
        tpl.creatorName.contains(searchQuery, ignoreCase = true)
      val matchesCategory = when (selectedCategory) {
        "All" -> true
        "Trending" -> tpl.usesCount > 200 || tpl.category.contains("Reel", ignoreCase = true)
        "Reel" -> tpl.category.contains("Reel", ignoreCase = true) || tpl.aspectRatio == AspectRatio.RATIO_9_16
        "Business" -> tpl.category.contains("Business", ignoreCase = true) || tpl.category.contains("Ads", ignoreCase = true)
        else -> tpl.category.contains(selectedCategory, ignoreCase = true)
      }
      matchesSearch && matchesCategory
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = LightPageBg,
    topBar = {
      PremierHeader(
        onNotificationClick = { viewModel.navigateTo(AppScreen.NOTIFICATIONS) },
        onSettingsClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
        hasUnreadNotifications = hasUnread
      )
    },
    bottomBar = {
      PremierBottomNavigation(
        activeTab = null,
        onNavigateHome = { viewModel.navigateTo(AppScreen.HOME) },
        onNavigateProjects = { viewModel.navigateTo(AppScreen.HOME) },
        onNewProject = onStartNewProject,
        onNavigateAccount = { viewModel.navigateTo(AppScreen.ACCOUNT) }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(6.dp))

      // 1. Search Bar
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text(stringResource(R.string.search_templates), color = LightTextTertiary, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LightTextSecondary, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Clear, contentDescription = "Clear", tint = LightTextSecondary, modifier = Modifier.size(16.dp))
            }
          }
        },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("templates_search_bar"),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = LightCardBg,
          unfocusedContainerColor = LightCardBg,
          focusedBorderColor = PremierAccent,
          unfocusedBorderColor = LightCardBorder,
          focusedTextColor = LightTextPrimary,
          unfocusedTextColor = LightTextPrimary
        )
      )

      Spacer(modifier = Modifier.height(10.dp))

      // 2. Category Chips Row
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
      ) {
        items(categories) { category ->
          val isSelected = selectedCategory == category
          FilterChip(
            selected = isSelected,
            onClick = { selectedCategory = category },
            label = {
              Text(
                text = category,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = PremierAccent,
              selectedLabelColor = Color.White,
              containerColor = LightCardBg,
              labelColor = LightTextPrimary
            ),
            border = FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = isSelected,
              borderColor = if (isSelected) PremierAccent else LightCardBorder
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.testTag("category_chip_$category")
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // 3. 2-Column Templates Grid
      if (filteredTemplates.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = LightTextTertiary, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "No templates found",
              style = MaterialTheme.typography.bodyMedium.copy(color = LightTextSecondary, fontWeight = FontWeight.Medium)
            )
          }
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(bottom = 16.dp),
          modifier = Modifier.fillMaxSize()
        ) {
          items(filteredTemplates, key = { it.id }) { template ->
            TemplateGridCard(
              template = template,
              onClick = {
                viewModel.applyTemplate(template)
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TemplateGridCard(
  template: VideoTemplate,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(0.72f)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = true),
        onClick = onClick
      )
      .testTag("template_grid_item_${template.id}"),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = LightCardBg),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(LightCardBorder, LightCardBorder)))
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Gradient Visual Thumbnail
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              listOf(
                Color(template.thumbnailGradientStart),
                Color(template.thumbnailGradientEnd)
              )
            )
          ),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(text = template.iconEmoji, fontSize = 32.sp)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "${template.durationMs / 1000}s",
            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
          )
        }
      }

      // Pro Tag
      if (template.isPro) {
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFEAB308))
            .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
          Text(
            text = "PRO",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
          )
        }
      }

      // Bottom Info Gradient Overlay
      Column(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(
              listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
            )
          )
          .padding(horizontal = 8.dp, vertical = 8.dp)
      ) {
        Text(
          text = template.title,
          style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color.White
          ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "${template.creatorName} • ${template.usesCount} uses",
          style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 10.sp,
            color = Color(0xFFD1D5DB)
          ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
