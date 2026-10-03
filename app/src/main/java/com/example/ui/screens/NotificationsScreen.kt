package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.repository.AppNotification
import com.example.data.repository.NotificationRepository
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.components.home.PremierHeader
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val notifications by NotificationRepository.notifications.collectAsState()
  val dateFormat = remember { SimpleDateFormat("MMM d • HH:mm", Locale.getDefault()) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = LightPageBg,
    topBar = {
      PremierHeader(
        title = stringResource(R.string.notifications),
        showBack = true,
        onBackClick = { viewModel.navigateTo(AppScreen.HOME) }
      )
    }
  ) { padding ->
    if (notifications.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier.padding(24.dp)
        ) {
          Box(
            modifier = Modifier
              .size(56.dp)
              .clip(CircleShape)
              .background(Color(0xFFE5E7EB)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Outlined.NotificationsNone,
              contentDescription = null,
              tint = LightTextSecondary,
              modifier = Modifier.size(28.dp)
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = stringResource(R.string.no_notifications),
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              color = LightTextPrimary,
              fontSize = 16.sp
            )
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "You're all caught up with your project exports and template updates.",
            style = MaterialTheme.typography.bodySmall.copy(
              color = LightTextSecondary,
              fontSize = 12.sp
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
      ) {
        // Mark all as read action header
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Recent Notifications",
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = LightTextSecondary,
                fontSize = 12.sp
              )
            )
            Text(
              text = "Mark all as read",
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = PremierAccent,
                fontSize = 12.sp
              ),
              modifier = Modifier
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = ripple(bounded = false),
                  onClick = { NotificationRepository.markAllAsRead() }
                )
                .padding(4.dp)
                .testTag("mark_all_notifications_read")
            )
          }
        }

        items(notifications, key = { it.id }) { notification ->
          NotificationCard(
            notification = notification,
            formattedDate = dateFormat.format(Date(notification.timestampMs)),
            onClick = {
              NotificationRepository.markAsRead(notification.id)
              notification.targetScreen?.let { target ->
                viewModel.navigateTo(target)
              }
            }
          )
        }
      }
    }
  }
}

@Composable
private fun NotificationCard(
  notification: AppNotification,
  formattedDate: String,
  onClick: () -> Unit
) {
  val icon = when (notification.iconType) {
    "export" -> Icons.Default.DownloadDone
    "project" -> Icons.Default.Movie
    "template" -> Icons.Default.AutoAwesome
    "pro" -> Icons.Default.WorkspacePremium
    else -> Icons.Default.Info
  }

  val iconColor = when (notification.iconType) {
    "export" -> Color(0xFF10B981)
    "project" -> PremierAccent
    "template" -> Color(0xFF8B5CF6)
    "pro" -> Color(0xFFF59E0B)
    else -> PremierAccent
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = true),
        onClick = onClick
      )
      .testTag("notification_card_${notification.id}"),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (notification.isRead) LightCardBg else Color(0xFFF0F9FF)
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    border = CardDefaults.outlinedCardBorder().copy(
      brush = Brush.linearGradient(
        listOf(
          if (notification.isRead) LightCardBorder else PremierAccentLight,
          if (notification.isRead) LightCardBorder else PremierAccentLight
        )
      )
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.Top
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(iconColor.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconColor,
          modifier = Modifier.size(18.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = notification.title,
            style = MaterialTheme.typography.titleSmall.copy(
              fontWeight = if (notification.isRead) FontWeight.SemiBold else FontWeight.Bold,
              fontSize = 13.sp,
              color = LightTextPrimary
            )
          )
          Text(
            text = formattedDate,
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 10.sp,
              color = LightTextTertiary
            )
          )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = notification.message,
          style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.sp,
            color = LightTextSecondary,
            lineHeight = 16.sp
          )
        )
      }

      if (!notification.isRead) {
        Spacer(modifier = Modifier.width(8.dp))
        Box(
          modifier = Modifier
            .padding(top = 4.dp)
            .size(6.dp)
            .clip(CircleShape)
            .background(PremierAccent)
        )
      }
    }
  }
}
