package com.example.ui.components.home

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ExportedVideoEntity
import com.example.data.local.ProjectEntity
import com.example.data.presets.TemplatesCatalog
import com.example.data.presets.VideoTemplate
import com.example.domain.StudioAccountManager
import com.example.auth.AuthResult
import com.example.auth.StorageBreakdown
import kotlinx.coroutines.launch
import com.example.data.local.OAuthConnectionEntity
import com.example.data.local.UserAccountEntity
import com.example.ui.components.account.RealAuthDialog
import com.example.ui.components.account.RealOAuthPlatformDialog
import com.example.ui.components.account.RealStorageDetailsDialog
import com.example.ui.components.account.RealSwitchAccountDialog
import com.example.domain.model.AspectRatio
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================================
// 1. TEMPLATES TAB VIEW
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTemplatesTabView(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val savedTemplateIds by StudioAccountManager.savedTemplateIds.collectAsState()
  val firebaseTemplates by com.example.data.firebase.FirebaseTemplateManager.templates.collectAsState()

  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf("All") } // "All", "Reels", "TikTok", "Shorts", "Saved"
  var previewTemplate by remember { mutableStateOf<VideoTemplate?>(null) }

  val filteredTemplates = remember(firebaseTemplates, searchQuery, selectedFilter, savedTemplateIds) {
    firebaseTemplates.filter { tpl ->
      val matchesSearch = searchQuery.isBlank() ||
        tpl.title.contains(searchQuery, ignoreCase = true) ||
        tpl.category.contains(searchQuery, ignoreCase = true) ||
        tpl.description.contains(searchQuery, ignoreCase = true) ||
        tpl.creatorName.contains(searchQuery, ignoreCase = true) ||
        tpl.creatorHandle.contains(searchQuery, ignoreCase = true)

      val matchesFilter = when (selectedFilter) {
        "Saved" -> savedTemplateIds.contains(tpl.id)
        "All" -> true
        else -> tpl.category.contains(selectedFilter, ignoreCase = true)
      }

      matchesSearch && matchesFilter
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    // Top Bar Header (No Create Button - Template creation is only via Template Creator)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "Templates Community",
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 22.sp
          )
        )
        Text(
          text = "Real Firebase templates • Live views & cuts synchronization",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
        )
      }
    }

    // Search Bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search templates, creators, motion styles...", color = TextTertiary, fontSize = 13.sp) },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
          }
        }
      },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp)
        .testTag("templates_search_field"),
      shape = RoundedCornerShape(14.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = StudioSurface,
        unfocusedContainerColor = StudioSurface,
        focusedBorderColor = CyanAccent,
        unfocusedBorderColor = StudioBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
      )
    )

    // Filter Chips
    val filterCategories = remember {
      listOf("All", "Reels", "TikTok", "Shorts", "YouTube", "Instagram", "Business", "Product Ads", "Birthday", "Wedding", "Travel", "Cinematic", "Saved")
    }

    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(filterCategories) { filterName ->
        val isSelected = selectedFilter == filterName
        val count = when (filterName) {
          "Saved" -> savedTemplateIds.size
          "All" -> firebaseTemplates.size
          else -> firebaseTemplates.count { it.category.contains(filterName, ignoreCase = true) }
        }

        FilterChip(
          selected = isSelected,
          onClick = { selectedFilter = filterName },
          label = {
            Text(
              text = when (filterName) {
                "Saved" -> "⭐ Saved ($count)"
                "All" -> "All ($count)"
                else -> "$filterName ($count)"
              },
              fontSize = 12.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
            selectedLabelColor = CyanAccent,
            containerColor = StudioSurface,
            labelColor = TextSecondary
          ),
          border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            selectedBorderColor = CyanAccent,
            borderColor = StudioBorder
          )
        )
      }
    }

    // Templates List / Grid
    if (filteredTemplates.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
          Icon(Icons.Outlined.Style, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(56.dp))
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = if (searchQuery.isNotBlank()) "No matching templates found" else "No Firebase templates published yet",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Templates are created exclusively through the Template Creator flow. Design in Screen Editor, export, and publish live to Firebase!",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(bottom = 12.dp)
      ) {
        items(filteredTemplates, key = { it.id }) { tpl ->
          val isSaved = savedTemplateIds.contains(tpl.id)

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .border(
                width = 1.dp,
                color = if (isSaved) AmberAccent.copy(alpha = 0.6f) else StudioBorder,
                shape = RoundedCornerShape(16.dp)
              )
              .clickable {
                com.example.data.firebase.FirebaseTemplateManager.recordTemplateView(tpl.id, tpl.creatorId)
                previewTemplate = tpl
              }
              .testTag("template_card_${tpl.id}"),
            colors = CardDefaults.cardColors(containerColor = StudioSurface)
          ) {
            Column {
              // Thumbnail Header Box (Preview)
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(110.dp)
                  .background(
                    Brush.linearGradient(
                      listOf(
                        Color(tpl.thumbnailGradientStart),
                        Color(tpl.thumbnailGradientEnd)
                      )
                    )
                  )
                  .padding(10.dp)
              ) {
                // Top Row: Emoji Icon + Bookmark Star
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(tpl.iconEmoji, fontSize = 14.sp)
                    }
                  }

                  IconButton(
                    onClick = {
                      StudioAccountManager.toggleSavedTemplate(tpl.id)
                      Toast.makeText(
                        context,
                        if (isSaved) "Removed from Saved Templates" else "Saved to My Templates ⭐",
                        Toast.LENGTH_SHORT
                      ).show()
                    },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(
                      imageVector = if (isSaved) Icons.Default.Star else Icons.Outlined.StarBorder,
                      contentDescription = "Save Template",
                      tint = if (isSaved) AmberAccent else Color.White
                    )
                  }
                }

                // Bottom Aspect Ratio & Duration Badge
                Surface(
                  modifier = Modifier.align(Alignment.BottomStart),
                  shape = RoundedCornerShape(6.dp),
                  color = Color.Black.copy(alpha = 0.65f)
                ) {
                  Text(
                    text = "${tpl.aspectRatio.label} • ${tpl.durationMs / 1000}s",
                    style = MaterialTheme.typography.labelSmall.copy(
                      color = Color.White,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }

              // Details Body: Title, Creator, Real Views & Uses/Cuts, and Use Template Button
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = tpl.title,
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.sp
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )

                // Creator Row
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(top = 2.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(16.dp)
                      .clip(CircleShape)
                      .background(Brush.linearGradient(listOf(CyanAccent, PurpleAccent))),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      tpl.creatorName.take(1).uppercase().ifBlank { "C" },
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.Black
                    )
                  }
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = tpl.creatorName.ifBlank { "Creator" },
                    style = MaterialTheme.typography.bodySmall.copy(
                      color = CyanAccent,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Real-time Views & Uses / Cuts Statistics
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(StudioSurfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "👁️ ${tpl.viewsCount}",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                  )
                  Text(
                    text = "✂️ ${tpl.usesCount} cuts",
                    style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                  )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Primary "Use Template" Button
                Button(
                  onClick = {
                    viewModel.applyTemplate(tpl)
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .testTag("use_template_btn_${tpl.id}")
                ) {
                  Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Use Template", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
              }
            }
          }
        }
      }
    }
  }

  // Template Preview Inspector Modal
  previewTemplate?.let { tpl ->
    TemplatePreviewModalDialog(
      template = tpl,
      isSaved = savedTemplateIds.contains(tpl.id),
      onToggleSave = { StudioAccountManager.toggleSavedTemplate(tpl.id) },
      onUseTemplate = {
        previewTemplate = null
        viewModel.applyTemplate(tpl)
      },
      onDismiss = { previewTemplate = null }
    )
  }
}

// ============================================================================
// 2. PROJECTS TAB VIEW
// ============================================================================
@Composable
fun HomeProjectsTabView(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val projects by viewModel.allProjects.collectAsState()
  var searchQuery by remember { mutableStateOf("") }
  var selectedTab by remember { mutableStateOf("All Projects") } // "All Projects", "Drafts", "Recent"
  var renameProjectTarget by remember { mutableStateOf<ProjectEntity?>(null) }
  var deleteProjectTarget by remember { mutableStateOf<ProjectEntity?>(null) }

  val filteredProjects = remember(projects, searchQuery, selectedTab) {
    projects.filter { project ->
      val matchesSearch = searchQuery.isBlank() || project.name.contains(searchQuery, ignoreCase = true)
      val matchesTab = when (selectedTab) {
        "Drafts" -> project.isDraft
        "Recent" -> System.currentTimeMillis() - project.lastEditedTime < 7 * 24 * 3600 * 1000L
        else -> true
      }
      matchesSearch && matchesTab
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    // Top Bar Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "Projects & Drafts",
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 22.sp
          )
        )
        Text(
          text = "${projects.size} total project${if (projects.size == 1) "" else "s"} saved locally",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
        )
      }
    }

    // Search Bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search projects by name...", color = TextTertiary, fontSize = 13.sp) },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
          }
        }
      },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp)
        .testTag("projects_tab_search_field"),
      shape = RoundedCornerShape(14.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = StudioSurface,
        unfocusedContainerColor = StudioSurface,
        focusedBorderColor = CyanAccent,
        unfocusedBorderColor = StudioBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
      )
    )

    // Filter Chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      listOf("All Projects", "Drafts", "Recent").forEach { tab ->
        val isSelected = selectedTab == tab
        FilterChip(
          selected = isSelected,
          onClick = { selectedTab = tab },
          label = {
            Text(
              text = when (tab) {
                "Drafts" -> "📝 Drafts (${projects.count { it.isDraft }})"
                "Recent" -> "⏱️ Recent"
                else -> "All Projects (${projects.size})"
              },
              fontSize = 12.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = StudioSurfaceVariant,
            selectedLabelColor = CyanAccent,
            containerColor = StudioSurface,
            labelColor = TextSecondary
          )
        )
      }
    }

    // Project List
    if (filteredProjects.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Outlined.Folder, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(56.dp))
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = if (searchQuery.isNotBlank()) "No matching projects" else "No saved projects yet",
            style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary)
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Tap Start Project in the navigation bar to create a new timeline",
            style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary, fontSize = 12.sp)
          )
        }
      }
    } else {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(bottom = 12.dp)
      ) {
        items(filteredProjects, key = { it.id }) { project ->
          val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }
          val formattedDate = remember(project.lastEditedTime) { dateFormat.format(Date(project.lastEditedTime)) }

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
              .clickable { viewModel.loadProject(project) }
              .testTag("project_item_${project.id}"),
            colors = CardDefaults.cardColors(containerColor = StudioSurface)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Thumbnail Box
              Box(
                modifier = Modifier
                  .size(70.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(
                    Brush.linearGradient(
                      listOf(
                        Color(0xFF1E293B),
                        Color(0xFF0F172A)
                      )
                    )
                  )
                  .border(1.dp, CyanAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Movie, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(28.dp))
                if (project.isDraft) {
                  Surface(
                    color = AmberAccent,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                      .align(Alignment.TopStart)
                      .padding(4.dp)
                  ) {
                    Text(
                      text = "DRAFT",
                      style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                      ),
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.width(14.dp))

              // Details
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = project.name,
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 15.sp
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                  text = formattedDate,
                  style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary, fontSize = 11.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = StudioSurfaceVariant
                  ) {
                    Text(
                      text = project.aspectRatio,
                      style = MaterialTheme.typography.labelSmall.copy(
                        color = CyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                      ),
                      modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                  }
                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = StudioSurfaceVariant
                  ) {
                    Text(
                      text = "${project.durationMs / 1000}s",
                      style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 10.sp
                      ),
                      modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                  }
                }
              }

              // Actions Menu
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                  onClick = { viewModel.duplicateProject(project.id) },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                  onClick = { renameProjectTarget = project },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(Icons.Default.Edit, contentDescription = "Rename", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                  onClick = { deleteProjectTarget = project },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseAccent, modifier = Modifier.size(18.dp))
                }
              }
            }
          }
        }
      }
    }
  }

  // Rename Dialog
  renameProjectTarget?.let { project ->
    var newName by remember { mutableStateOf(project.name) }
    AlertDialog(
      onDismissRequest = { renameProjectTarget = null },
      title = { Text("Rename Project", color = TextPrimary) },
      text = {
        OutlinedTextField(
          value = newName,
          onValueChange = { newName = it },
          label = { Text("Project Title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = StudioBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
          )
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (newName.isNotBlank()) {
              viewModel.renameProject(project.id, newName.trim())
            }
            renameProjectTarget = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { renameProjectTarget = null }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioSurface
    )
  }

  // Delete Dialog
  deleteProjectTarget?.let { project ->
    AlertDialog(
      onDismissRequest = { deleteProjectTarget = null },
      title = { Text("Delete Project?", color = TextPrimary) },
      text = { Text("Are you sure you want to delete \"${project.name}\"? This action cannot be undone.", color = TextSecondary) },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteProject(project.id)
            deleteProjectTarget = null
            Toast.makeText(context, "Project deleted", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = RoseAccent, contentColor = Color.White)
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteProjectTarget = null }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioSurface
    )
  }
}

// ============================================================================
// 3. VIDEO CLIPS TAB VIEW
// ============================================================================
@Composable
fun HomeVideoClipsTabView(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val exportedVideos by viewModel.exportedVideos.collectAsState()
  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf("All Clips") }
  var previewVideo by remember { mutableStateOf<ExportedVideoEntity?>(null) }
  var deleteVideoTarget by remember { mutableStateOf<ExportedVideoEntity?>(null) }

  val filteredVideos = remember(exportedVideos, searchQuery, selectedFilter) {
    exportedVideos.filter { video ->
      val matchesSearch = searchQuery.isBlank() || video.title.contains(searchQuery, ignoreCase = true)
      val matchesFilter = when (selectedFilter) {
        "4K / 1080p" -> video.resolution.contains("1080") || video.resolution.contains("4K")
        else -> true
      }
      matchesSearch && matchesFilter
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    // Top Bar Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "Video Clips & Media",
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 22.sp
          )
        )
        Text(
          text = "${exportedVideos.size} exported video clip${if (exportedVideos.size == 1) "" else "s"} available",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
        )
      }
    }

    // Search Bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search video clips by name...", color = TextTertiary, fontSize = 13.sp) },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
          }
        }
      },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp)
        .testTag("video_clips_search_field"),
      shape = RoundedCornerShape(14.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = StudioSurface,
        unfocusedContainerColor = StudioSurface,
        focusedBorderColor = CyanAccent,
        unfocusedBorderColor = StudioBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
      )
    )

    // Filter Chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      listOf("All Clips", "4K / 1080p").forEach { filter ->
        val isSelected = selectedFilter == filter
        FilterChip(
          selected = isSelected,
          onClick = { selectedFilter = filter },
          label = { Text(filter, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = StudioSurfaceVariant,
            selectedLabelColor = CyanAccent,
            containerColor = StudioSurface,
            labelColor = TextSecondary
          )
        )
      }
    }

    // Grid of Clips
    if (filteredVideos.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Outlined.OndemandVideo, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(56.dp))
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = if (searchQuery.isNotBlank()) "No matching video clips" else "No exported video clips yet",
            style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary)
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Export video projects from the timeline to see clips here",
            style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary, fontSize = 12.sp)
          )
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(bottom = 12.dp)
      ) {
        items(filteredVideos, key = { it.id }) { video ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
              .clickable { previewVideo = video }
              .testTag("video_clip_card_${video.id}"),
            colors = CardDefaults.cardColors(containerColor = StudioSurface)
          ) {
            Column {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(100.dp)
                  .background(
                    Brush.linearGradient(
                      listOf(
                        Color(0xFF0F2027),
                        Color(0xFF203A43),
                        Color(0xFF2C5364)
                      )
                    )
                  )
                  .padding(8.dp),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play", tint = CyanAccent, modifier = Modifier.size(36.dp))

                Surface(
                  modifier = Modifier.align(Alignment.BottomEnd),
                  shape = RoundedCornerShape(4.dp),
                  color = Color.Black.copy(alpha = 0.7f)
                ) {
                  Text(
                    text = "${video.durationMs / 1000}s",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 10.sp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                  )
                }
              }

              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = video.title,
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.sp
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${video.resolution} • ${video.fps} FPS",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary, fontSize = 11.sp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  IconButton(
                    onClick = { previewVideo = video },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = CyanAccent, modifier = Modifier.size(20.dp))
                  }

                  IconButton(
                    onClick = { deleteVideoTarget = video },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseAccent, modifier = Modifier.size(18.dp))
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Delete Video Dialog
  deleteVideoTarget?.let { video ->
    AlertDialog(
      onDismissRequest = { deleteVideoTarget = null },
      title = { Text("Delete Clip?", color = TextPrimary) },
      text = { Text("Remove clip \"${video.title}\" from studio storage?", color = TextSecondary) },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteExportedVideo(video.id)
            deleteVideoTarget = null
            Toast.makeText(context, "Video clip deleted", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = RoseAccent, contentColor = Color.White)
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteVideoTarget = null }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioSurface
    )
  }

  // Preview Video Dialog
  previewVideo?.let { video ->
    AlertDialog(
      onDismissRequest = { previewVideo = null },
      title = { Text(video.title, color = TextPrimary) },
      text = {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth()
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.Black),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Movie, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(48.dp))
          }
          Spacer(modifier = Modifier.height(12.dp))
          Text("Resolution: ${video.resolution}", color = TextSecondary, fontSize = 12.sp)
          Text("Duration: ${video.durationMs / 1000} seconds", color = TextSecondary, fontSize = 12.sp)
          Text("Path: ${video.filePath.takeLast(35)}", color = TextTertiary, fontSize = 10.sp)
        }
      },
      confirmButton = {
        Button(
          onClick = { previewVideo = null },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Text("Close")
        }
      },
      containerColor = StudioSurface
    )
  }
}

// ============================================================================
// 4. ME / MY ACCOUNT TAB VIEW
// ============================================================================
@Composable
fun HomeAccountTabView(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val currentAccount by StudioAccountManager.currentAccount.collectAsState()
  val allAccounts by StudioAccountManager.allAccounts.collectAsState()
  val oauthConnections by StudioAccountManager.oauthConnections.collectAsState()
  val storageBreakdown by StudioAccountManager.storageBreakdown.collectAsState()
  val projects by viewModel.allProjects.collectAsState()
  val exportedVideos by viewModel.exportedVideos.collectAsState()
  val savedTemplates by StudioAccountManager.savedTemplateIds.collectAsState()

  var showAuthDialog by remember { mutableStateOf(false) }
  var showEditProfileDialog by remember { mutableStateOf(false) }
  var showFeedbackDialog by remember { mutableStateOf(false) }
  var showTermsDialog by remember { mutableStateOf(false) }
  var showPrivacyDialog by remember { mutableStateOf(false) }
  var showSwitchAccountDialog by remember { mutableStateOf(false) }
  var showStorageDetailsDialog by remember { mutableStateOf(false) }
  var selectedOAuthPlatform by remember { mutableStateOf<OAuthConnectionEntity?>(null) }
  var showLogOutDialog by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    StudioAccountManager.refreshStorageUsage()
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Top Bar Header & Settings Action
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "My Account Center",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              fontSize = 22.sp
            )
          )
          Text(
            text = "Manage your profile, connected accounts & studio settings",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
          )
        }

        IconButton(
          onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
          modifier = Modifier
            .clip(CircleShape)
            .background(StudioSurface)
            .testTag("account_settings_top_button")
        ) {
          Icon(Icons.Default.Settings, contentDescription = "Settings", tint = CyanAccent)
        }
      }
    }

    // User Profile Header Card (Authenticated vs Guest)
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .border(1.dp, StudioBorder, RoundedCornerShape(20.dp))
          .testTag("user_profile_card"),
        colors = CardDefaults.cardColors(containerColor = StudioSurface)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          val account = currentAccount
          if (account != null) {
            // Signed In View
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(60.dp)
                  .clip(CircleShape)
                  .background(Color(account.avatarColor)),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = account.displayName.take(2).uppercase(),
                  style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = 22.sp
                  )
                )
              }

              Spacer(modifier = Modifier.width(14.dp))

              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                      fontSize = 17.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (account.providerId.contains("google", ignoreCase = true)) Color(0xFF4285F4) else AmberAccent
                  ) {
                    Text(
                      text = if (account.providerId.contains("google", ignoreCase = true)) "GOOGLE AUTH" else "VERIFIED",
                      style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp
                      ),
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }

                Text(
                  text = account.customHandle.ifBlank { account.email },
                  style = MaterialTheme.typography.bodySmall.copy(color = CyanAccent, fontSize = 12.sp),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = account.bio.ifBlank { "Mobile Video Creator & Editor" },
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp),
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real Stats Bar Row
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(StudioSurfaceVariant)
                .clickable { showStorageDetailsDialog = true }
                .padding(vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceAround
            ) {
              AccountStatItem("Projects", "${projects.size}")
              AccountStatItem("Clips", "${exportedVideos.size}")
              AccountStatItem("Templates", "${savedTemplates.size}")
              AccountStatItem("Storage", storageBreakdown.formattedTotal)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = { showEditProfileDialog = true },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier
                  .weight(1f)
                  .testTag("edit_profile_btn")
              ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyanAccent)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Profile", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
              }

              Button(
                onClick = { showSwitchAccountDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant, contentColor = TextPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .weight(1f)
                  .testTag("switch_account_btn")
              ) {
                Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(16.dp), tint = AmberAccent)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Switch (${allAccounts.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }
            }
          } else {
            // Guest / Not Signed In View
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(54.dp)
                  .clip(CircleShape)
                  .background(StudioSurfaceVariant),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.PersonOutline, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(28.dp))
              }

              Spacer(modifier = Modifier.width(14.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Guest Creator Session",
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 17.sp
                  )
                )
                Text(
                  text = "Sign in to synchronize profile, multi-account switch & direct OAuth export.",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real Stats Bar Row
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(StudioSurfaceVariant)
                .clickable { showStorageDetailsDialog = true }
                .padding(vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceAround
            ) {
              AccountStatItem("Projects", "${projects.size}")
              AccountStatItem("Clips", "${exportedVideos.size}")
              AccountStatItem("Templates", "${savedTemplates.size}")
              AccountStatItem("Storage", storageBreakdown.formattedTotal)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Button(
                onClick = {
                  coroutineScope.launch {
                    val result = StudioAccountManager.signInWithGoogle(context)
                    if (result is AuthResult.Success) {
                      Toast.makeText(context, "Welcome, ${result.user.displayName}!", Toast.LENGTH_SHORT).show()
                    }
                  }
                },
                modifier = Modifier.weight(1.1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
              ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Google Sign-In", fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }

              Button(
                onClick = { showAuthDialog = true },
                modifier = Modifier.weight(0.9f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
              ) {
                Text("Sign In / Register", fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }
            }
          }
        }
      }
    }

    // Real OAuth Connected Social Accounts Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(18.dp))
          .border(1.dp, StudioBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = StudioSurface)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Connected OAuth Channels",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
              )
              Text(
                text = "Direct publishing & authenticated channel export",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          oauthConnections.forEach { conn ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { selectedOAuthPlatform = conn }
                .padding(vertical = 8.dp, horizontal = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(conn.platformIcon, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conn.platformName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    if (conn.isConnected) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Icon(Icons.Default.CheckCircle, contentDescription = "Connected", tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
                    }
                  }
                  Text(
                    text = if (conn.isConnected) conn.accountHandle else "Not authorized • Tap to connect",
                    color = if (conn.isConnected) CyanAccent else TextTertiary,
                    fontSize = 11.sp
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (conn.isConnected) Color(0xFF064E3B) else StudioSurfaceVariant,
                modifier = Modifier.clickable { selectedOAuthPlatform = conn }
              ) {
                Text(
                  text = if (conn.isConnected) "Manage" else "Connect",
                  color = if (conn.isConnected) Color(0xFF34D399) else CyanAccent,
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
              }
            }
          }
        }
      }
    }

    // Account Management & Utilities Options
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(18.dp))
          .border(1.dp, StudioBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = StudioSurface)
      ) {
        Column(modifier = Modifier.padding(8.dp)) {
          AccountOptionRow(
            icon = Icons.Default.Storage,
            title = "Device Storage & Render Cache",
            subtitle = "${storageBreakdown.formattedTotal} used (${storageBreakdown.formattedCache} cache)",
            onClick = { showStorageDetailsDialog = true }
          )

          AccountOptionRow(
            icon = Icons.Default.SwitchAccount,
            title = "Switch Account Profile",
            subtitle = if (currentAccount != null) "Active: ${currentAccount?.displayName} (${allAccounts.size} saved)" else "Sign in or link an account",
            onClick = { showSwitchAccountDialog = true }
          )

          AccountOptionRow(
            icon = Icons.Default.Feedback,
            title = "Feedback & Bug Report",
            subtitle = "Send thoughts & feature requests to developers",
            onClick = { showFeedbackDialog = true }
          )

          AccountOptionRow(
            icon = Icons.Default.Description,
            title = "Terms & Conditions",
            subtitle = "Studio usage policies & licensing",
            onClick = { showTermsDialog = true }
          )

          AccountOptionRow(
            icon = Icons.Default.PrivacyTip,
            title = "Privacy Policy",
            subtitle = "Data protection & privacy rights",
            onClick = { showPrivacyDialog = true }
          )

          if (currentAccount != null) {
            AccountOptionRow(
              icon = Icons.Default.Logout,
              title = "Log Out",
              subtitle = "Sign out of active creator session",
              textColor = RoseAccent,
              onClick = { showLogOutDialog = true }
            )
          } else {
            AccountOptionRow(
              icon = Icons.Default.Login,
              title = "Sign In / Register",
              subtitle = "Connect Google or Email account",
              textColor = CyanAccent,
              onClick = { showAuthDialog = true }
            )
          }
        }
      }
    }

    // App Version Footer
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "AH Video Studio • Mobile Edition",
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextTertiary, fontSize = 11.sp)
        )
        Text(
          text = "v3.5.0 • Authenticated Session Engine",
          style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary, fontSize = 10.sp)
        )
      }
    }
  }

  // Real Auth Dialog (Google Sign-In + Email/Password)
  if (showAuthDialog) {
    RealAuthDialog(
      onDismiss = { showAuthDialog = false },
      onSuccess = { showAuthDialog = false }
    )
  }

  // Real Switch Account Dialog
  if (showSwitchAccountDialog) {
    RealSwitchAccountDialog(
      accounts = allAccounts,
      currentAccount = currentAccount,
      onDismiss = { showSwitchAccountDialog = false },
      onAddNewAccount = { showAuthDialog = true }
    )
  }

  // Real OAuth Platform Dialog
  selectedOAuthPlatform?.let { platform ->
    RealOAuthPlatformDialog(
      connection = platform,
      onDismiss = { selectedOAuthPlatform = null }
    )
  }

  // Real Storage Details Dialog
  if (showStorageDetailsDialog) {
    RealStorageDetailsDialog(
      onDismiss = { showStorageDetailsDialog = false }
    )
  }

  // Edit Profile Dialog
  val safeCurrentAccount = currentAccount
  if (showEditProfileDialog && safeCurrentAccount != null) {
    val cur = safeCurrentAccount
    var editName by remember { mutableStateOf(cur.displayName) }
    var editHandle by remember { mutableStateOf(cur.customHandle) }
    var editBio by remember { mutableStateOf(cur.bio) }

    AlertDialog(
      onDismissRequest = { showEditProfileDialog = false },
      title = { Text("Edit Creator Profile", color = TextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = editName,
            onValueChange = { editName = it },
            label = { Text("Display Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = editHandle,
            onValueChange = { editHandle = it },
            label = { Text("Handle (@username)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = editBio,
            onValueChange = { editBio = it },
            label = { Text("Bio / Tagline") },
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            coroutineScope.launch {
              val res = StudioAccountManager.updateProfile(editName, editBio, editHandle)
              if (res.isSuccess) {
                Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
              } else {
                Toast.makeText(context, "Failed to update profile", Toast.LENGTH_SHORT).show()
              }
              showEditProfileDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Text("Save Changes")
        }
      },
      dismissButton = {
        TextButton(onClick = { showEditProfileDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioSurface
    )
  }

  // Feedback Dialog
  if (showFeedbackDialog) {
    var feedbackText by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showFeedbackDialog = false },
      title = { Text("Feedback & Bug Report", color = TextPrimary) },
      text = {
        Column {
          Text("Tell us what you'd like to see improved in AH Video Studio:", color = TextSecondary, fontSize = 12.sp)
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = feedbackText,
            onValueChange = { feedbackText = it },
            placeholder = { Text("Write your feedback or issue report here...") },
            modifier = Modifier
              .fillMaxWidth()
              .height(110.dp)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            Toast.makeText(context, "Thank you! Your feedback has been submitted.", Toast.LENGTH_SHORT).show()
            showFeedbackDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Text("Submit")
        }
      },
      dismissButton = {
        TextButton(onClick = { showFeedbackDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioSurface
    )
  }

  // Terms Dialog
  if (showTermsDialog) {
    AlertDialog(
      onDismissRequest = { showTermsDialog = false },
      title = { Text("Terms & Conditions", color = TextPrimary) },
      text = {
        Text(
          "AH Video Studio Terms of Service:\n\n1. All user projects and exported video clips remain 100% owned by the creator.\n2. Local auto-saving & timeline crash recovery protects your work on device.\n3. Pro features offer high frame rate and 4K export capabilities.",
          color = TextSecondary,
          fontSize = 12.sp
        )
      },
      confirmButton = {
        Button(
          onClick = { showTermsDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Text("Close")
        }
      },
      containerColor = StudioSurface
    )
  }

  // Privacy Dialog
  if (showPrivacyDialog) {
    AlertDialog(
      onDismissRequest = { showPrivacyDialog = false },
      title = { Text("Privacy Policy", color = TextPrimary) },
      text = {
        Text(
          "Privacy Policy Summary:\n\nYour video files, raw media assets, and timeline projects are stored locally on your device. AH Video Studio respects your privacy and does not upload your raw media without explicit permission. Authentication tokens are securely handled via Google Credential Manager and Firebase Auth.",
          color = TextSecondary,
          fontSize = 12.sp
        )
      },
      confirmButton = {
        Button(
          onClick = { showPrivacyDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Text("Close")
        }
      },
      containerColor = StudioSurface
    )
  }

  // Log Out Dialog
  if (showLogOutDialog) {
    AlertDialog(
      onDismissRequest = { showLogOutDialog = false },
      title = { Text("Log Out?", color = TextPrimary) },
      text = { Text("Sign out of active creator session? Your local project drafts will remain safe on this device.", color = TextSecondary) },
      confirmButton = {
        Button(
          onClick = {
            coroutineScope.launch {
              StudioAccountManager.signOut()
              showLogOutDialog = false
              Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = RoseAccent, contentColor = Color.White)
        ) {
          Text("Log Out")
        }
      },
      dismissButton = {
        TextButton(onClick = { showLogOutDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioSurface
    )
  }
}

// Helper Composable Components
@Composable
private fun AccountStatItem(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = value, fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 15.sp)
    Text(text = label, color = TextTertiary, fontSize = 11.sp)
  }
}

@Composable
private fun AccountOptionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  textColor: Color = TextPrimary,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .clickable { onClick() }
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(imageVector = icon, contentDescription = title, tint = if (textColor == RoseAccent) RoseAccent else CyanAccent, modifier = Modifier.size(22.dp))
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, fontWeight = FontWeight.Bold, color = textColor, fontSize = 14.sp)
      Text(text = subtitle, color = TextTertiary, fontSize = 11.sp)
    }
    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
  }
}

// Modal dialog to preview template details
@Composable
private fun TemplatePreviewModalDialog(
  template: VideoTemplate,
  isSaved: Boolean,
  onToggleSave: () -> Unit,
  onUseTemplate: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${template.iconEmoji} ${template.title}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Creator Row
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StudioSurfaceVariant)
            .padding(8.dp)
        ) {
          Box(
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(Brush.linearGradient(listOf(CyanAccent, PurpleAccent))),
            contentAlignment = Alignment.Center
          ) {
            Text(
              template.creatorName.take(1).uppercase().ifBlank { "C" },
              color = Color.Black,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              template.creatorName.ifBlank { "Verified Creator" },
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )
            Text(
              template.creatorHandle.ifBlank { "@creator" },
              style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontSize = 11.sp)
            )
          }
        }

        // Live stats: Views and Cuts
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text("👁️ ${template.viewsCount} views", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
          Text("✂️ ${template.usesCount} cuts", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Text("Category: ${template.category}", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        if (template.description.isNotBlank()) {
          Text(template.description, color = TextSecondary, fontSize = 12.sp)
        }
        Text("🎵 Soundtrack: ${template.audioTitle}", color = TextPrimary, fontSize = 12.sp)
        Text("⏱️ Duration: ${template.durationMs / 1000}s • Aspect: ${template.aspectRatio.label}", color = TextTertiary, fontSize = 11.sp)
        Text("🎞️ Media Slots: ${template.mediaPlaceholders.size} • Text Slots: ${template.textPlaceholders.size}", color = TextTertiary, fontSize = 11.sp)
      }
    },
    confirmButton = {
      Button(
        onClick = onUseTemplate,
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
      ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Use Template", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = TextSecondary)
      }
    },
    containerColor = StudioSurface
  )
}
