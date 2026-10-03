package com.example.ui.components.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

enum class HomeTab(
  val title: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector
) {
  TEMPLATES("Templates", Icons.Default.GridView, Icons.Outlined.GridView),
  PROJECTS("Projects", Icons.Default.Folder, Icons.Outlined.Folder),
  MY_ACCOUNT("My Account", Icons.Default.AccountCircle, Icons.Outlined.AccountCircle)
}

@Composable
fun HomeBottomNavigationBar(
  activeTab: HomeTab,
  onTabSelected: (HomeTab) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("home_bottom_navigation_bar"),
    color = StudioSurface,
    tonalElevation = 8.dp
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .border(width = 1.dp, color = StudioBorder, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        .background(StudioSurface)
        .padding(vertical = 8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        HomeTab.values().forEach { tab ->
          val isSelected = activeTab == tab
          val iconColor by animateColorAsState(
            targetValue = if (isSelected) CyanAccent else TextSecondary,
            animationSpec = tween(200),
            label = "nav_icon_color"
          )
          val textColor by animateColorAsState(
            targetValue = if (isSelected) CyanAccent else TextSecondary,
            animationSpec = tween(200),
            label = "nav_text_color"
          )

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
              .weight(1f)
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
              ) { onTabSelected(tab) }
              .padding(vertical = 4.dp)
              .testTag("nav_tab_${tab.name.lowercase().replace(" ", "_")}")
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .offset(y = (-4).dp)
                .size(28.dp)
            ) {
              Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = tab.title,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = tab.title,
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp,
                color = textColor
              ),
              maxLines = 1,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
  }
}

