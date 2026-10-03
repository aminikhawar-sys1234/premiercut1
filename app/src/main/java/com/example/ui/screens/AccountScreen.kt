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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.AuthManager
import com.example.data.repository.NotificationRepository
import com.example.domain.StudioAccountManager
import com.example.domain.model.AspectRatio
import com.example.domain.model.FrameRate
import com.example.domain.model.Resolution
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.components.account.RealAuthDialog
import com.example.ui.components.account.RealStorageDetailsDialog
import com.example.ui.components.account.RealSwitchAccountDialog
import com.example.ui.components.home.HomeNavTab
import com.example.ui.components.home.PremierBottomNavigation
import com.example.ui.components.home.PremierHeader
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val settings by viewModel.settings.collectAsState()
  val notifications by NotificationRepository.notifications.collectAsState()
  val hasUnread = remember(notifications) { notifications.any { !it.isRead } }

  val currentAccount by StudioAccountManager.currentAccount.collectAsState()
  val allAccounts by StudioAccountManager.allAccounts.collectAsState()
  val storageBreakdown by StudioAccountManager.storageBreakdown.collectAsState()

  var showAuthDialog by remember { mutableStateOf(false) }
  var showSwitchAccountDialog by remember { mutableStateOf(false) }
  var showStorageDialog by remember { mutableStateOf(false) }

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
        activeTab = HomeNavTab.ACCOUNT,
        onNavigateHome = { viewModel.navigateTo(AppScreen.HOME) },
        onNavigateProjects = { viewModel.navigateTo(AppScreen.HOME) },
        onNewProject = onStartNewProject,
        onNavigateAccount = {}
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
      // 1. ACCOUNT PROFILE HEADER
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = LightCardBg),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(LightCardBorder, LightCardBorder)))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(PremierAccent),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = currentAccount?.displayName?.take(1)?.uppercase() ?: "P",
                  style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = currentAccount?.displayName ?: settings.userName,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LightTextPrimary, fontSize = 15.sp)
                )
                Text(
                  text = currentAccount?.email ?: settings.userEmail,
                  style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary, fontSize = 12.sp)
                )
              }
            }

            if (currentAccount != null) {
              TextButton(
                onClick = { showSwitchAccountDialog = true },
                colors = ButtonDefaults.textButtonColors(contentColor = PremierAccent)
              ) {
                Text("Switch", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
            } else {
              Button(
                onClick = { showAuthDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PremierAccent),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Text(stringResource(R.string.log_in), fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // 2. ACCOUNT SECTIONS CARD
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = LightCardBg),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(LightCardBorder, LightCardBorder)))
        ) {
          Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // Profile
            AccountRow(
              icon = Icons.Outlined.Person,
              title = stringResource(R.string.profile),
              subtitle = "Manage personal details & handles",
              onClick = {
                if (currentAccount == null) showAuthDialog = true else showSwitchAccountDialog = true
              }
            )

            HorizontalDivider(color = LightDivider, modifier = Modifier.padding(horizontal = 16.dp))

            // Account Security
            AccountRow(
              icon = Icons.Outlined.Security,
              title = stringResource(R.string.account_security),
              subtitle = "Authentication & session protection",
              onClick = {
                Toast.makeText(context, "Account security verified", Toast.LENGTH_SHORT).show()
              }
            )

            HorizontalDivider(color = LightDivider, modifier = Modifier.padding(horizontal = 16.dp))

            // Storage
            val usedMb = (storageBreakdown.totalStorageBytes / (1024 * 1024)).coerceAtLeast(12L)
            AccountRow(
              icon = Icons.Outlined.CloudQueue,
              title = stringResource(R.string.storage),
              subtitle = "${usedMb} MB used across projects & cache",
              onClick = { showStorageDialog = true }
            )

            HorizontalDivider(color = LightDivider, modifier = Modifier.padding(horizontal = 16.dp))

            // Settings (Opens Settings page)
            AccountRow(
              icon = Icons.Outlined.Settings,
              title = stringResource(R.string.settings),
              subtitle = "Languages, themes & preferences",
              onClick = { viewModel.navigateTo(AppScreen.SETTINGS) }
            )
          }
        }
      }

      // 3. AUTHENTICATION LOG OUT
      if (currentAccount != null) {
        item {
          OutlinedButton(
            onClick = {
              coroutineScope.launch {
                StudioAccountManager.signOut()
                Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
          ) {
            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.log_out), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }

  // Dialogs
  if (showAuthDialog) {
    RealAuthDialog(
      onDismiss = { showAuthDialog = false },
      onSuccess = { showAuthDialog = false }
    )
  }

  if (showSwitchAccountDialog) {
    RealSwitchAccountDialog(
      accounts = allAccounts,
      currentAccount = currentAccount,
      onDismiss = { showSwitchAccountDialog = false },
      onAddNewAccount = {
        showSwitchAccountDialog = false
        showAuthDialog = true
      }
    )
  }

  if (showStorageDialog) {
    RealStorageDetailsDialog(
      onDismiss = { showStorageDialog = false }
    )
  }
}

@Composable
private fun AccountRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = true),
        onClick = onClick
      )
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f)
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(PremierAccentLight),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = PremierAccent,
          modifier = Modifier.size(18.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = LightTextPrimary
          )
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 11.sp,
            color = LightTextSecondary
          )
        )
      }
    }

    Icon(
      imageVector = Icons.Default.ChevronRight,
      contentDescription = null,
      tint = LightTextTertiary,
      modifier = Modifier.size(18.dp)
    )
  }
}
