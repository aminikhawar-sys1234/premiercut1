package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.Dp

fun BorderStroke.copy(width: Dp = this.width, brush: Brush = this.brush): BorderStroke = BorderStroke(width, brush)


@Composable
fun StudioHeader(
  title: String,
  subtitle: String? = null,
  showProBadge: Boolean = true,
  onSearchClick: (() -> Unit)? = null,
  onSettingsClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(
            Brush.linearGradient(listOf(CyanAccent, PurpleAccent))
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.MovieFilter,
          contentDescription = "Studio Logo",
          tint = Color.Black,
          modifier = Modifier.size(24.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp,
              color = TextPrimary
            )
          )
          if (showProBadge) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(listOf(CyanAccent, PurpleAccent)))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = "PRO",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Black,
                  fontSize = 10.sp,
                  color = Color.Black
                )
              )
            }
          }
        }
        if (subtitle != null) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
              color = TextSecondary,
              fontSize = 12.sp
            )
          )
        }
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      if (onSearchClick != null) {
        IconButton(
          onClick = onSearchClick,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("header_search_button")
        ) {
          Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
        }
      }
      if (onSettingsClick != null) {
        IconButton(
          onClick = onSettingsClick,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("header_settings_button")
        ) {
          Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
        }
      }
    }
  }
}

@Composable
fun PrimaryPillButton(
  text: String,
  icon: ImageVector? = null,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  containerColor: Color = CyanAccent,
  contentColor: Color = Color.Black,
  enabled: Boolean = true
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
      .height(48.dp)
      .minimumInteractiveComponentSize(),
    shape = RoundedCornerShape(24.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = containerColor,
      contentColor = contentColor,
      disabledContainerColor = StudioSurfaceVariant,
      disabledContentColor = TextTertiary
    )
  ) {
    if (icon != null) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
      )
    )
  }
}

@Composable
fun StudioCard(
  modifier: Modifier = Modifier,
  backgroundColor: Color = StudioSurface,
  borderColor: Color = StudioBorder,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  val shape = RoundedCornerShape(16.dp)
  Card(
    modifier = modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    shape = shape,
    colors = CardDefaults.cardColors(containerColor = backgroundColor),
    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(borderColor, borderColor.copy(alpha = 0.5f))))
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      content = content
    )
  }
}

fun formatDuration(ms: Long): String {
  val totalSec = ms / 1000
  val min = totalSec / 60
  val sec = totalSec % 60
  val tenths = (ms % 1000) / 100
  return String.format("%02d:%02d.%d", min, sec, tenths)
}

fun formatDurationShort(ms: Long): String {
  val totalSec = ms / 1000
  val min = totalSec / 60
  val sec = totalSec % 60
  return String.format("%02d:%02d", min, sec)
}
