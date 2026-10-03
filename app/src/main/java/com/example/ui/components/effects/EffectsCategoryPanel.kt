package com.example.ui.components.effects

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.effects.registry.EffectCategory
import com.example.engine.effects.registry.EffectsAssetRegistry
import com.example.engine.effects.registry.RegisteredEffect
import com.example.ui.StudioViewModel

/**
 * Lightweight Effects Panel for:
 * 1. Video Effects
 * 2. Body Effects
 * 3. Photo Effects
 * 4. AI Effects
 *
 * Characteristics:
 * - Occupies ~40% screen height, opens upward from the bottom.
 * - Green + Blue visual theme.
 * - Header: ❌ Close (left) | Search bar (center) | ✓ Confirm (right).
 * - 4-column card grid: First card: "None", remaining cards: dynamically generated from EffectsAssetRegistry.
 * - Starts completely empty (zero fake/mock/sample/placeholder effects).
 */
@Composable
fun EffectsCategoryPanel(
  category: EffectCategory,
  viewModel: StudioViewModel,
  onClose: () -> Unit,
  onApply: (RegisteredEffect?) -> Unit,
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }
  var draftSelectedEffect by remember { mutableStateOf<RegisteredEffect?>(null) }
  var isNoneSelected by remember { mutableStateOf(false) }

  // Query real installed effect assets/plugins from registry matching category and search query
  val availableEffects = remember(category, searchQuery) {
    EffectsAssetRegistry.searchEffects(category, searchQuery)
  }

  // Green + Blue lightweight gradient theme
  val panelBackground = Brush.verticalGradient(
    colors = listOf(
      Color(0xFF0A2B27), // Deep emerald
      Color(0xFF0F263B)  // Deep slate blue
    )
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
      .background(panelBackground)
      .border(1.dp, Color(0xFF164746), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
      .padding(horizontal = 14.dp, vertical = 8.dp)
      .testTag("effects_panel_${category.name.lowercase()}")
  ) {
    // --- Header: Left: ❌ Close | Center: Search Bar | Right: ✓ Confirm ---
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // ❌ Close/Cancel Button
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .size(38.dp)
          .testTag("effects_close_btn")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close",
          tint = Color.White
        )
      }

      // Center: Search Bar
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF071B20),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
        modifier = Modifier
          .weight(1f)
          .height(38.dp)
          .padding(horizontal = 8.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = Color(0xFF10B981),
            modifier = Modifier.size(16.dp)
          )

          Box(
            modifier = Modifier
              .weight(1f),
            contentAlignment = Alignment.CenterStart
          ) {
            if (searchQuery.isEmpty()) {
              Text(
                text = "Search ${category.displayName}...",
                color = Color(0xFF64748B),
                fontSize = 13.sp
              )
            }
            BasicTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              textStyle = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
              ),
              cursorBrush = SolidColor(Color(0xFF10B981)),
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("effects_search_input")
            )
          }

          if (searchQuery.isNotEmpty()) {
            IconButton(
              onClick = { searchQuery = "" },
              modifier = Modifier.size(20.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear search",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
              )
            }
          }
        }
      }

      // ✓ Apply/Confirm Button
      IconButton(
        onClick = {
          if (isNoneSelected) {
            onApply(null)
          } else if (draftSelectedEffect != null) {
            onApply(draftSelectedEffect)
          }
          onClose()
        },
        modifier = Modifier
          .size(38.dp)
          .testTag("effects_confirm_btn")
      ) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = "Apply Effect",
          tint = Color(0xFF10B981)
        )
      }
    }

    // --- 4-Column Card / Grid Layout ---
    LazyVerticalGrid(
      columns = GridCells.Fixed(4),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      // 1. FIRST CARD: "None" (Functional "None" option that clears the effect)
      item {
        val isSelected = isNoneSelected || (draftSelectedEffect == null && !isNoneSelected)
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFF0F2F32),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF10B981) else Color(0xFF1E5253)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable {
              isNoneSelected = true
              draftSelectedEffect = null
            }
            .testTag("effect_none_option")
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Block,
              contentDescription = "None",
              tint = if (isSelected) Color(0xFF10B981) else Color.White,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "None",
              color = Color.White,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              fontSize = 11.sp
            )
          }
        }
      }

      // 2. REMAINING CARDS: Real installed Effects from EffectsAssetRegistry (Starts completely empty)
      items(availableEffects) { effect ->
        val isSelected = draftSelectedEffect?.id == effect.id
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFF0E2530),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF10B981) else Color(0xFF1A4557)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable {
              isNoneSelected = false
              draftSelectedEffect = effect
            }
            .testTag("effect_item_${effect.id}")
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(6.dp)
          ) {
            Text(
              text = effect.name,
              color = Color.White,
              fontSize = 11.sp,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
  }
}
