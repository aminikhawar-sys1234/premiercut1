package com.example.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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

enum class HomeNavTab {
  HOME,
  PROJECTS,
  ACCOUNT
}

@Composable
fun PremierBottomNavigation(
  activeTab: HomeNavTab?,
  onNavigateHome: () -> Unit,
  onNavigateProjects: () -> Unit,
  onNewProject: () -> Unit,
  onNavigateAccount: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .testTag("premier_bottom_navigation"),
    color = LightCardBg,
    shadowElevation = 4.dp
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(LightCardBg)
        .padding(top = 4.dp, bottom = 4.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // 1. Home
        BottomNavItem(
          icon = if (activeTab == HomeNavTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
          label = stringResource(R.string.home),
          isSelected = activeTab == HomeNavTab.HOME,
          onClick = onNavigateHome,
          testTag = "nav_tab_home"
        )

        // 2. Projects
        BottomNavItem(
          icon = if (activeTab == HomeNavTab.PROJECTS) Icons.Filled.Folder else Icons.Outlined.Folder,
          label = stringResource(R.string.projects),
          isSelected = activeTab == HomeNavTab.PROJECTS,
          onClick = onNavigateProjects,
          testTag = "nav_tab_projects"
        )

        // 3. New Project (+ icon)
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier
            .weight(1f)
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(bounded = false, radius = 24.dp),
              onClick = onNewProject
            )
            .testTag("nav_tab_new_project")
        ) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(PremierAccent),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = stringResource(R.string.new_project),
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = stringResource(R.string.new_project),
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              color = PremierAccent
            )
          )
        }

        // 4. Account
        BottomNavItem(
          icon = if (activeTab == HomeNavTab.ACCOUNT) Icons.Filled.Person else Icons.Outlined.Person,
          label = stringResource(R.string.account),
          isSelected = activeTab == HomeNavTab.ACCOUNT,
          onClick = onNavigateAccount,
          testTag = "nav_tab_account"
        )
      }
    }
  }
}

@Composable
private fun RowScope.BottomNavItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = Modifier
      .weight(1f)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = false, radius = 24.dp),
        onClick = onClick
      )
      .testTag(testTag)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = if (isSelected) PremierAccent else LightTextSecondary,
      modifier = Modifier.size(22.dp)
    )
    Spacer(modifier = Modifier.height(3.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isSelected) PremierAccent else LightTextSecondary
      )
    )
  }
}
