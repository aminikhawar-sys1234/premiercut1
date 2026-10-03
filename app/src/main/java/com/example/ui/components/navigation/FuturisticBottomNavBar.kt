package com.example.ui.components.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Custom Theme Colors for Nav Items
 */
data class NavItemColorTheme(
  val bgCircle: Color,
  val iconTint: Color
)

object NavItemThemes {
  val AddText = NavItemColorTheme(Color(0xFF102A45), Color.White)
  val Captions = NavItemColorTheme(Color(0xFF321B60), Color.White)
  val Stickers = NavItemColorTheme(Color(0xFF0B4D3C), Color.White)
  val Elements = NavItemColorTheme(Color(0xFF2E1A47), Color.White)
  val DrawTheme = NavItemColorTheme(Color(0xFF421C6E), Color.White)
  val Draw = DrawTheme
  val TextTemplateTheme = NavItemColorTheme(Color(0xFF5A380A), Color.White)
  val TextTemplate = TextTemplateTheme
  val Effects = NavItemColorTheme(Color(0xFF5C103C), Color.White)
  val Filters = NavItemColorTheme(Color(0xFF0F3B66), Color.White)
  val Edit = NavItemColorTheme(Color(0xFF165DFF), Color.White)
  val Audio = NavItemColorTheme(Color(0xFF094D52), Color.White)
  val Speed = NavItemColorTheme(Color(0xFF264D12), Color.White)
  val Animations = NavItemColorTheme(Color(0xFF54420A), Color.White)
  val Overlay = NavItemColorTheme(Color(0xFF1A2350), Color.White)
  val Transitions = NavItemColorTheme(Color(0xFF59220F), Color.White)
  val AI = NavItemColorTheme(Color(0xFF381A6E), Color.White)
  val DefaultSlate = NavItemColorTheme(Color(0xFF1E293B), Color.White)
}

data class FuturisticNavItemData(
  val id: String,
  val label: String,
  val icon: ImageVector,
  val theme: NavItemColorTheme,
  val isSelected: Boolean,
  val testTag: String,
  val onClick: () -> Unit
)

/**
 * Clean, modern floating capsule bottom navigation bar matching the reference design
 */
@Composable
fun FuturisticBottomNavBarContainer(
  onBackClick: (() -> Unit)? = null,
  items: List<FuturisticNavItemData>,
  modifier: Modifier = Modifier,
  showDividers: Boolean = true
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .navigationBarsPadding(),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
        .shadow(
          elevation = 6.dp,
          shape = RoundedCornerShape(18.dp),
          ambientColor = Color.Black.copy(alpha = 0.5f),
          spotColor = Color.Black.copy(alpha = 0.6f)
        )
        .border(
          width = 1.dp,
          color = Color(0xFF222B38).copy(alpha = 0.85f),
          shape = RoundedCornerShape(18.dp)
        ),
      shape = RoundedCornerShape(18.dp),
      color = Color(0xFF070B14).copy(alpha = 0.98f)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp, vertical = 4.dp)
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)
      ) {
        if (onBackClick != null) {
          Box(
            modifier = Modifier
              .size(width = 44.dp, height = 54.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF141C2B))
              .border(
                BorderStroke(1.dp, Color(0xFF2A374A)),
                shape = RoundedCornerShape(12.dp)
              )
              .clickable(onClick = onBackClick)
              .testTag("nav_back_arrow_button"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.ArrowBack,
              contentDescription = "Back",
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
          }
          
          Box(
            modifier = Modifier
              .width(1.dp)
              .height(24.dp)
              .background(Color(0x18FFFFFF))
          )
        }

        // Navigation Items
        items.forEachIndexed { index, item ->
          FuturisticNavItemView(item = item)
          
          // Subtle divider between items when neither is selected and dividers are enabled
          if (showDividers && index < items.size - 1) {
            val nextItem = items[index + 1]
            if (!item.isSelected && !nextItem.isSelected) {
              Box(
                modifier = Modifier
                  .width(1.dp)
                  .height(20.dp)
                  .background(Color(0x14FFFFFF))
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun FuturisticNavItemView(item: FuturisticNavItemData) {
  val interactionSource = remember { MutableInteractionSource() }

  val itemModifier = if (item.isSelected) {
    Modifier
      .width(62.dp)
      .height(54.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFF165DFF),
            Color(0xFF0F47D6)
          )
        )
      )
      .border(
        BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp)
      )
  } else {
    Modifier
      .width(58.dp)
      .height(54.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(Color.Transparent)
  }

  Column(
    modifier = itemModifier
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = item.onClick
      )
      .padding(vertical = 4.dp, horizontal = 2.dp)
      .testTag(item.testTag),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = item.icon,
      contentDescription = item.label,
      tint = Color.White,
      modifier = Modifier.size(22.dp)
    )

    Spacer(modifier = Modifier.height(3.dp))

    Text(
      text = item.label,
      fontSize = 11.sp,
      fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Medium,
      color = if (item.isSelected) Color.White else Color(0xFFE2E8F0),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center
    )
  }
}
