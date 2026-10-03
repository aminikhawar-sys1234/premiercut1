package com.example.ui.components.effects

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.engine.effects.registry.EffectCategory
import com.example.ui.components.navigation.FuturisticBottomNavBarContainer
import com.example.ui.components.navigation.FuturisticNavItemData
import com.example.ui.components.navigation.NavItemColorTheme

private val VideoEffectsTheme = NavItemColorTheme(
  bgCircle = Color(0xFF0F3B37),
  iconTint = Color(0xFF00E5FF)
)

private val BodyEffectsTheme = NavItemColorTheme(
  bgCircle = Color(0xFF0E382A),
  iconTint = Color(0xFF10B981)
)

private val PhotoEffectsTheme = NavItemColorTheme(
  bgCircle = Color(0xFF132F42),
  iconTint = Color(0xFF38BDF8)
)

private val AIEffectsTheme = NavItemColorTheme(
  bgCircle = Color(0xFF2A1B4E),
  iconTint = Color(0xFFC084FC)
)

/**
 * Dedicated Effects Sub-Navigation.
 * Replaces the main editor bottom navigation when "Effects" is selected.
 *
 * Contains 4 horizontally arranged options:
 * 1. Video Effects
 * 2. Body Effects
 * 3. Photo Effects
 * 4. AI Effects
 */
@Composable
fun EffectsToolsBottomBar(
  activeCategory: EffectCategory?,
  onSelectCategory: (EffectCategory) -> Unit,
  onBackToMainMenu: () -> Unit,
  modifier: Modifier = Modifier
) {
  val items = listOf(
    FuturisticNavItemData(
      id = EffectCategory.VIDEO_EFFECTS.name,
      label = EffectCategory.VIDEO_EFFECTS.displayName,
      icon = Icons.Default.Videocam,
      theme = VideoEffectsTheme,
      isSelected = activeCategory == EffectCategory.VIDEO_EFFECTS,
      testTag = EffectCategory.VIDEO_EFFECTS.tag,
      onClick = { onSelectCategory(EffectCategory.VIDEO_EFFECTS) }
    ),
    FuturisticNavItemData(
      id = EffectCategory.BODY_EFFECTS.name,
      label = EffectCategory.BODY_EFFECTS.displayName,
      icon = Icons.Default.AccessibilityNew,
      theme = BodyEffectsTheme,
      isSelected = activeCategory == EffectCategory.BODY_EFFECTS,
      testTag = EffectCategory.BODY_EFFECTS.tag,
      onClick = { onSelectCategory(EffectCategory.BODY_EFFECTS) }
    ),
    FuturisticNavItemData(
      id = EffectCategory.PHOTO_EFFECTS.name,
      label = EffectCategory.PHOTO_EFFECTS.displayName,
      icon = Icons.Default.Image,
      theme = PhotoEffectsTheme,
      isSelected = activeCategory == EffectCategory.PHOTO_EFFECTS,
      testTag = EffectCategory.PHOTO_EFFECTS.tag,
      onClick = { onSelectCategory(EffectCategory.PHOTO_EFFECTS) }
    ),
    FuturisticNavItemData(
      id = EffectCategory.AI_EFFECTS.name,
      label = EffectCategory.AI_EFFECTS.displayName,
      icon = Icons.Default.AutoFixHigh,
      theme = AIEffectsTheme,
      isSelected = activeCategory == EffectCategory.AI_EFFECTS,
      testTag = EffectCategory.AI_EFFECTS.tag,
      onClick = { onSelectCategory(EffectCategory.AI_EFFECTS) }
    )
  )

  FuturisticBottomNavBarContainer(
    onBackClick = onBackToMainMenu,
    items = items,
    modifier = modifier,
    showDividers = true
  )
}
