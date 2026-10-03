package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.firebase.CreatorProfile
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
import com.example.ui.components.template.CreatorProfileSetupDialog
import com.example.ui.theme.*

@Composable
fun CreatorScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val settings by viewModel.settings.collectAsState()
  val notifications by NotificationRepository.notifications.collectAsState()
  val hasUnread = remember(notifications) { notifications.any { !it.isRead } }

  val creatorProfile by FirebaseTemplateManager.creatorProfile.collectAsState()
  val allTemplates by FirebaseTemplateManager.templates.collectAsState()
  val templatesList = remember(allTemplates) {
    if (allTemplates.isNotEmpty()) allTemplates else TemplatesCatalog.templates
  }

  var selectedTab by remember { mutableStateOf("Published") } // Published, In Review, Draft
  var showProfileSetupDialog by remember { mutableStateOf(false) }

  val myPublishedTemplates = remember(templatesList, creatorProfile) {
    templatesList.filter { it.creatorId == creatorProfile.creatorId || it.creatorName.equals(creatorProfile.displayName, ignoreCase = true) }
  }

  // Calculate real XP & Level based on real template statistics
  val totalUses = creatorProfile.totalUses
  val totalViews = creatorProfile.totalViews
  val currentXP = (totalUses * 10L + totalViews + creatorProfile.templatesCount * 50L).coerceAtLeast(150L)
  val currentLevelName = when {
    currentXP >= 5000L -> "Level 5 • Elite"
    currentXP >= 2500L -> "Level 4 • Pro"
    currentXP >= 1000L -> "Level 3 • Rising"
    currentXP >= 400L -> "Level 2 • Creator"
    else -> "Level 1 • Starter"
  }
  val nextLevelXP = when {
    currentXP >= 5000L -> 10000L
    currentXP >= 2500L -> 5000L
    currentXP >= 1000L -> 2500L
    currentXP >= 400L -> 1000L
    else -> 400L
  }
  val xpProgress = (currentXP.toFloat() / nextLevelXP.toFloat()).coerceIn(0f, 1f)

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
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
      // 1. CREATOR INTRO
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = stringResource(R.string.become_creator),
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp,
              color = LightTextPrimary
            )
          )
          Spacer(modifier = Modifier.height(3.dp))
          Text(
            text = stringResource(R.string.become_creator_desc),
            style = MaterialTheme.typography.bodySmall.copy(
              fontSize = 12.sp,
              color = LightTextSecondary,
              lineHeight = 16.sp
            )
          )
        }
      }

      // 2. CREATOR LEVEL CARD
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = LightCardBg),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(LightCardBorder, LightCardBorder)))
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDE9FE)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF7C3AED),
                    modifier = Modifier.size(20.dp)
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = stringResource(R.string.creator_level),
                    style = MaterialTheme.typography.labelSmall.copy(color = LightTextSecondary, fontSize = 11.sp)
                  )
                  Text(
                    text = currentLevelName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = LightTextPrimary, fontSize = 14.sp)
                  )
                }
              }

              if (creatorProfile.displayName.isBlank()) {
                TextButton(
                  onClick = { showProfileSetupDialog = true },
                  colors = ButtonDefaults.textButtonColors(contentColor = PremierAccent)
                ) {
                  Text("Setup Profile", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
              } else {
                Text(
                  text = creatorProfile.handle.ifBlank { "@creator" },
                  style = MaterialTheme.typography.labelSmall.copy(color = PremierAccent, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                )
              }
            }

            // XP Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "XP: $currentXP / $nextLevelXP",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = LightTextSecondary)
                )
                Text(
                  text = "Next: ${nextLevelXP - currentXP} XP",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Medium)
                )
              }
              LinearProgressIndicator(
                progress = { xpProgress },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF7C3AED),
                trackColor = Color(0xFFEDE9FE)
              )
            }
          }
        }
      }

      // 3. CREATE TEMPLATE PRIMARY BUTTON
      item {
        Button(
          onClick = {
            viewModel.enterTemplateCreatorMode()
            viewModel.navigateTo(AppScreen.EDITOR)
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("create_template_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF7C3AED),
            contentColor = Color.White
          )
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.create_template),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
        }
      }

      // 4. CREATOR STATISTICS (4 Compact Cards)
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          StatCard(title = "Templates", value = creatorProfile.templatesCount.toString(), modifier = Modifier.weight(1f))
          StatCard(title = "Uses", value = totalUses.toString(), modifier = Modifier.weight(1f))
          StatCard(title = "Views", value = totalViews.toString(), modifier = Modifier.weight(1f))
          StatCard(title = "Followers", value = "${(totalUses / 4).coerceAtLeast(0L)}", modifier = Modifier.weight(1f))
        }
      }

      // 5. CREATOR REWARDS CARD
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = LightCardBg),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(LightCardBorder, LightCardBorder)))
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = stringResource(R.string.creator_rewards),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = LightTextPrimary, fontSize = 14.sp)
              )
              Text(
                text = "Balance: $${"%.2f".format((totalUses * 0.05f))}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 13.sp)
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              OutlinedButton(
                onClick = { Toast.makeText(context, "Minimum redemption threshold is $25.00", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
              ) {
                Text("Redeem", fontSize = 12.sp, color = PremierAccent)
              }
              OutlinedButton(
                onClick = { Toast.makeText(context, "Rewards history up to date", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
              ) {
                Text("History", fontSize = 12.sp, color = LightTextSecondary)
              }
            }
          }
        }
      }

      // 6. LEVEL ROADMAP
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = LightCardBg),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(LightCardBorder, LightCardBorder)))
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = "Creator Roadmap",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = LightTextPrimary, fontSize = 14.sp)
            )
            Text(
              text = "1. Starter  →  2. Creator  →  3. Rising  →  4. Pro  →  5. Elite",
              style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = LightTextSecondary, fontWeight = FontWeight.Medium)
            )
            HorizontalDivider(color = LightDivider)
            Text(
              text = "Level 4 unlocks premium effects, priority review and a verified creator badge.",
              style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = LightTextSecondary, lineHeight = 15.sp)
            )
          }
        }
      }

      // 7. YOUR TEMPLATES SECTION
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = stringResource(R.string.your_templates),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = LightTextPrimary, fontSize = 14.sp)
          )
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Published", "In Review", "Draft").forEach { tab ->
              val isSelected = selectedTab == tab
              FilterChip(
                selected = isSelected,
                onClick = { selectedTab = tab },
                label = { Text(tab, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = PremierAccent,
                  selectedLabelColor = Color.White,
                  containerColor = LightCardBg,
                  labelColor = LightTextPrimary
                ),
                shape = RoundedCornerShape(16.dp)
              )
            }
          }
        }
      }

      // Templates list for selected tab
      if (selectedTab == "Published" && myPublishedTemplates.isNotEmpty()) {
        items(myPublishedTemplates) { template ->
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = LightCardBg),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(LightCardBorder, LightCardBorder)))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(template.thumbnailGradientStart)),
                  contentAlignment = Alignment.Center
                ) {
                  Text(template.iconEmoji, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(template.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LightTextPrimary))
                  Text("${template.usesCount} uses • ${template.category}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = LightTextSecondary))
                }
              }
            }
          }
        }
      } else {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(100.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = if (selectedTab == "Published") "No published templates yet. Tap '+ Create Template' above." else "No $selectedTab templates.",
              style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary, fontSize = 12.sp)
            )
          }
        }
      }
    }
  }

  // Profile Setup Dialog
  if (showProfileSetupDialog) {
    CreatorProfileSetupDialog(
      onDismiss = { showProfileSetupDialog = false },
      onProfileCompleted = { showProfileSetupDialog = false }
    )
  }
}

@Composable
private fun StatCard(
  title: String,
  value: String,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = LightCardBg),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(LightCardBorder, LightCardBorder)))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 6.dp, vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = LightTextPrimary
        )
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
          fontSize = 10.sp,
          color = LightTextSecondary
        )
      )
    }
  }
}
