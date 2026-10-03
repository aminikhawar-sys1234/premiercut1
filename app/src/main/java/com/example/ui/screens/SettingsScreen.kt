package com.example.ui.screens

import android.widget.Toast
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
import com.example.BuildConfig
import com.example.R
import com.example.domain.StudioPreferencesManager
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.components.home.PremierHeader
import com.example.ui.theme.*
import com.example.util.LocaleManager

@Composable
fun SettingsScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val settings by viewModel.settings.collectAsState()

  var showLanguageDialog by remember { mutableStateOf(false) }
  var showThemeDialog by remember { mutableStateOf(false) }
  var showQualityDialog by remember { mutableStateOf(false) }
  var showHelpDialog by remember { mutableStateOf(false) }
  var showAboutDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = LightPageBg,
    topBar = {
      PremierHeader(
        title = stringResource(R.string.settings),
        showBack = true,
        onBackClick = { viewModel.navigateTo(AppScreen.HOME) }
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
      // General Settings Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = LightCardBg),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(LightCardBorder, LightCardBorder)))
        ) {
          Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // 1. LANGUAGE SECTION
            SettingsActionRow(
              icon = Icons.Outlined.Translate,
              title = stringResource(R.string.language),
              subtitle = stringResource(R.string.choose_app_language),
              value = settings.language,
              onClick = { showLanguageDialog = true },
              testTag = "settings_language_row"
            )

            HorizontalDivider(color = LightDivider, modifier = Modifier.padding(horizontal = 16.dp))

            // 2. THEME SECTION
            SettingsActionRow(
              icon = Icons.Outlined.Palette,
              title = stringResource(R.string.theme),
              subtitle = stringResource(R.string.choose_appearance),
              value = if (settings.isDarkTheme) "Dark" else "Light",
              onClick = { showThemeDialog = true },
              testTag = "settings_theme_row"
            )

            HorizontalDivider(color = LightDivider, modifier = Modifier.padding(horizontal = 16.dp))

            // 3. AUTO SAVE SECTION
            SettingsSwitchRow(
              icon = Icons.Outlined.Save,
              title = stringResource(R.string.auto_save),
              subtitle = stringResource(R.string.save_projects_automatically),
              checked = settings.autoSaveEnabled,
              onCheckedChange = { StudioPreferencesManager.updateAutoSave(it) },
              testTag = "settings_auto_save_switch"
            )

            HorizontalDivider(color = LightDivider, modifier = Modifier.padding(horizontal = 16.dp))

            // 4. NOTIFICATIONS SETTINGS SECTION
            SettingsSwitchRow(
              icon = Icons.Outlined.Notifications,
              title = stringResource(R.string.notifications),
              subtitle = stringResource(R.string.manage_app_notifications),
              checked = settings.notificationsEnabled,
              onCheckedChange = { StudioPreferencesManager.updateNotifications(it) },
              testTag = "settings_notifications_switch"
            )

            HorizontalDivider(color = LightDivider, modifier = Modifier.padding(horizontal = 16.dp))

            // 5. VIDEO QUALITY SECTION
            SettingsActionRow(
              icon = Icons.Outlined.HighQuality,
              title = stringResource(R.string.video_quality),
              subtitle = stringResource(R.string.choose_default_video_quality),
              value = settings.videoQuality,
              onClick = { showQualityDialog = true },
              testTag = "settings_video_quality_row"
            )
          }
        }
      }

      // Support & Info Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = LightCardBg),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(LightCardBorder, LightCardBorder)))
        ) {
          Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // 6. HELP & SUPPORT
            SettingsActionRow(
              icon = Icons.Outlined.HelpOutline,
              title = stringResource(R.string.help_and_support),
              subtitle = stringResource(R.string.get_help_with_premier_cut),
              value = null,
              onClick = { showHelpDialog = true },
              testTag = "settings_help_row"
            )

            HorizontalDivider(color = LightDivider, modifier = Modifier.padding(horizontal = 16.dp))

            // 7. ABOUT APP
            SettingsActionRow(
              icon = Icons.Outlined.Info,
              title = stringResource(R.string.about_premier_cut),
              subtitle = "v${BuildConfig.VERSION_NAME} • Professional Suite",
              value = null,
              onClick = { showAboutDialog = true },
              testTag = "settings_about_row"
            )
          }
        }
      }
    }
  }

  // 1. Language Selection Dialog
  if (showLanguageDialog) {
    AlertDialog(
      onDismissRequest = { showLanguageDialog = false },
      title = {
        Text(
          text = stringResource(R.string.choose_app_language),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LightTextPrimary)
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          LocaleManager.supportedLanguages.forEach { lang ->
            val isSelected = settings.language.equals(lang.code, ignoreCase = true) ||
              settings.language.equals(lang.displayName, ignoreCase = true) ||
              settings.language.equals(lang.englishName, ignoreCase = true)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                  LocaleManager.setLocale(context, lang.code)
                  showLanguageDialog = false
                  Toast.makeText(context, "Language set to ${lang.displayName}", Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = lang.displayName,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = LightTextPrimary,
                    fontSize = 15.sp
                  )
                )
                Text(
                  text = lang.englishName,
                  style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary, fontSize = 11.sp)
                )
              }
              if (isSelected) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Selected",
                  tint = PremierAccent,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showLanguageDialog = false }) {
          Text("Done", color = PremierAccent, fontWeight = FontWeight.Bold)
        }
      },
      containerColor = LightCardBg
    )
  }

  // 2. Theme Selection Dialog
  if (showThemeDialog) {
    AlertDialog(
      onDismissRequest = { showThemeDialog = false },
      title = {
        Text(
          text = stringResource(R.string.choose_appearance),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LightTextPrimary)
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          listOf("Light", "Dark").forEach { themeName ->
            val isSelected = (themeName == "Dark" && settings.isDarkTheme) || (themeName == "Light" && !settings.isDarkTheme)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                  StudioPreferencesManager.updateTheme(themeName == "Dark")
                  showThemeDialog = false
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = themeName,
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  color = LightTextPrimary
                )
              )
              if (isSelected) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Selected",
                  tint = PremierAccent,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showThemeDialog = false }) {
          Text("Cancel", color = LightTextSecondary)
        }
      },
      containerColor = LightCardBg
    )
  }

  // 3. Video Quality Dialog
  if (showQualityDialog) {
    AlertDialog(
      onDismissRequest = { showQualityDialog = false },
      title = {
        Text(
          text = stringResource(R.string.choose_default_video_quality),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LightTextPrimary)
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          listOf("720p", "1080p", "2K", "4K UHD").forEach { quality ->
            val isSelected = settings.videoQuality.equals(quality, ignoreCase = true)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                  StudioPreferencesManager.updateVideoQuality(quality)
                  showQualityDialog = false
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = quality,
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  color = LightTextPrimary
                )
              )
              if (isSelected) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Selected",
                  tint = PremierAccent,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showQualityDialog = false }) {
          Text("Cancel", color = LightTextSecondary)
        }
      },
      containerColor = LightCardBg
    )
  }

  // 4. Help & Support Dialog
  if (showHelpDialog) {
    AlertDialog(
      onDismissRequest = { showHelpDialog = false },
      title = {
        Text(
          text = "Premier Cut Help & Tutorials",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LightTextPrimary)
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "• Timeline: Drag clips to reorder, pinch to zoom the ruler, tap split to cut clips.",
            style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary, lineHeight = 16.sp)
          )
          Text(
            text = "• Templates: Explore popular templates and replace placeholders with your media.",
            style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary, lineHeight = 16.sp)
          )
          Text(
            text = "• Hardware Acceleration: Hardware MediaCodec pipeline enables instant 60fps renders.",
            style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary, lineHeight = 16.sp)
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showHelpDialog = false }) {
          Text("Close", color = PremierAccent, fontWeight = FontWeight.Bold)
        }
      },
      containerColor = LightCardBg
    )
  }

  // 5. About Dialog
  if (showAboutDialog) {
    AlertDialog(
      onDismissRequest = { showAboutDialog = false },
      title = {
        Text(
          text = stringResource(R.string.about_premier_cut),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LightTextPrimary)
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Premier Cut Android Video Studio",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = LightTextPrimary)
          )
          Text(
            text = "Version: ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary)
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Engine: Media3 ExoPlayer, OpenGL ES 3.0, MediaCodec Surface Pipeline.",
            style = MaterialTheme.typography.bodySmall.copy(color = LightTextTertiary, fontSize = 11.sp)
          )
          Text(
            text = "© 2026 Premier Cut. All rights reserved.",
            style = MaterialTheme.typography.labelSmall.copy(color = LightTextTertiary, fontSize = 10.sp)
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showAboutDialog = false }) {
          Text("OK", color = PremierAccent, fontWeight = FontWeight.Bold)
        }
      },
      containerColor = LightCardBg
    )
  }
}

@Composable
private fun SettingsActionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  value: String?,
  onClick: () -> Unit,
  testTag: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = true),
        onClick = onClick
      )
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .testTag(testTag),
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

    Row(verticalAlignment = Alignment.CenterVertically) {
      if (value != null) {
        Text(
          text = value,
          style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Medium,
            color = PremierAccent,
            fontSize = 12.sp
          )
        )
        Spacer(modifier = Modifier.width(4.dp))
      }
      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = LightTextTertiary,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}

@Composable
private fun SettingsSwitchRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 10.dp)
      .testTag(testTag),
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

    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = PremierAccent,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = LightCardBorder
      )
    )
  }
}
