package com.example.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

@Composable
fun PremierHeader(
  title: String? = null,
  showBack: Boolean = false,
  onBackClick: (() -> Unit)? = null,
  onNotificationClick: (() -> Unit)? = null,
  onSettingsClick: (() -> Unit)? = null,
  hasUnreadNotifications: Boolean = false,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(LightPageBg)
      .statusBarsPadding()
      .padding(horizontal = 16.dp, vertical = 10.dp)
      .testTag("premier_header"),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Left: Back button OR Logo + Brand Title
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f, fill = false)
    ) {
      if (showBack && onBackClick != null) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier
            .size(36.dp)
            .testTag("header_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = LightTextPrimary,
            modifier = Modifier.size(20.dp)
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
      } else {
        // App Logo Icon
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PremierAccent),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.ContentCut,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
      }

      Text(
        text = title ?: stringResource(R.string.premier_cut),
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = LightTextPrimary
        ),
        maxLines = 1
      )
    }

    // Right: Actions (Notification, Settings)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      if (onNotificationClick != null) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(bounded = true, radius = 20.dp),
              onClick = onNotificationClick
            )
            .testTag("header_notification_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Outlined.Notifications,
            contentDescription = stringResource(R.string.notifications),
            tint = LightTextPrimary,
            modifier = Modifier.size(22.dp)
          )
          if (hasUnreadNotifications) {
            Box(
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(Color(0xFFEF4444))
            )
          }
        }
      }

      if (onSettingsClick != null) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(bounded = true, radius = 20.dp),
              onClick = onSettingsClick
            )
            .testTag("header_settings_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.settings),
            tint = LightTextPrimary,
            modifier = Modifier.size(22.dp)
          )
        }
      }
    }
  }
}
