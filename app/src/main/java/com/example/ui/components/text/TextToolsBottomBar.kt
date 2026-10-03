package com.example.ui.components.text

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.components.navigation.FuturisticBottomNavBarContainer
import com.example.ui.components.navigation.FuturisticNavItemData
import com.example.ui.components.navigation.NavItemColorTheme

/**
 * Dedicated Text Tools Bottom Navigation.
 * Replaces the previous main editor bottom navigation when "Text ✏️" is selected.
 *
 * Contains exactly:
 * 1. Auto Captions
 * 2. Add Text
 * 3. Text Templates
 */
enum class TextToolCategory(val label: String, val tag: String) {
  AUTO_CAPTIONS("Auto Captions", "text_nav_auto_captions"),
  ADD_TEXT("Add Text", "text_nav_add_text"),
  TEXT_TEMPLATES("Text Templates", "text_nav_text_templates")
}

private val AutoCaptionsTheme = NavItemColorTheme(
  bgCircle = Color(0xFF0F3B37),
  iconTint = Color(0xFF00E5FF)
)

private val AddTextTheme = NavItemColorTheme(
  bgCircle = Color(0xFF0E382A),
  iconTint = Color(0xFF10B981)
)

private val TextTemplatesTheme = NavItemColorTheme(
  bgCircle = Color(0xFF132F42),
  iconTint = Color(0xFF38BDF8)
)

@Composable
fun TextToolsBottomBar(
  activeTool: TextToolCategory?,
  onSelectTool: (TextToolCategory) -> Unit,
  onBackToMainMenu: () -> Unit,
  modifier: Modifier = Modifier
) {
  val items = listOf(
    FuturisticNavItemData(
      id = TextToolCategory.AUTO_CAPTIONS.name,
      label = TextToolCategory.AUTO_CAPTIONS.label,
      icon = Icons.Default.ClosedCaption,
      theme = AutoCaptionsTheme,
      isSelected = activeTool == TextToolCategory.AUTO_CAPTIONS,
      testTag = TextToolCategory.AUTO_CAPTIONS.tag,
      onClick = { onSelectTool(TextToolCategory.AUTO_CAPTIONS) }
    ),
    FuturisticNavItemData(
      id = TextToolCategory.ADD_TEXT.name,
      label = TextToolCategory.ADD_TEXT.label,
      icon = Icons.Default.TextFields,
      theme = AddTextTheme,
      isSelected = activeTool == TextToolCategory.ADD_TEXT,
      testTag = TextToolCategory.ADD_TEXT.tag,
      onClick = { onSelectTool(TextToolCategory.ADD_TEXT) }
    ),
    FuturisticNavItemData(
      id = TextToolCategory.TEXT_TEMPLATES.name,
      label = TextToolCategory.TEXT_TEMPLATES.label,
      icon = Icons.Default.DashboardCustomize,
      theme = TextTemplatesTheme,
      isSelected = activeTool == TextToolCategory.TEXT_TEMPLATES,
      testTag = TextToolCategory.TEXT_TEMPLATES.tag,
      onClick = { onSelectTool(TextToolCategory.TEXT_TEMPLATES) }
    )
  )

  FuturisticBottomNavBarContainer(
    onBackClick = onBackToMainMenu,
    items = items,
    modifier = modifier,
    showDividers = true
  )
}
