package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.TransitionType
import com.example.ui.StudioViewModel
import java.util.Locale

// Modern Blue + Green color scheme
private val PanelDarkBg = Color(0xFF090E18)
private val PanelCardBg = Color(0xFF0F1829)
private val PanelBorder = Color(0xFF1B2A42)
private val BlueGreenAccent = Color(0xFF00D1B2)
private val BlueGreenPrimary = Color(0xFF00B4D8)
private val BlueGreenHighlight = Color(0xFF00E5FF)
private val CategoryInactiveBg = Color(0xFF131D2E)
private val CategoryInactiveBorder = Color(0xFF1E2F48)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF94A3B8)

enum class TransitionTabCategory(val title: String) {
  ALL("All"),
  DISSOLVE("Dissolve"),
  FADE("Fade"),
  MOVE("Move"),
  WIPE("Wipe"),
  ZOOM("Zoom")
}

data class TransitionVisualItem(
  val type: TransitionType,
  val name: String,
  val category: TransitionTabCategory,
  val visualKind: TransitionVisualKind
)

enum class TransitionVisualKind {
  DISSOLVE,
  FADE,
  WIPE_LEFT,
  WIPE_RIGHT,
  SLIDE_LEFT,
  SLIDE_RIGHT,
  ZOOM_IN,
  ZOOM_OUT,
  PUSH_UP,
  SPIN,
  BLUR,
  FLASH,
  WHIP_PAN,
  ZOOM_BLUR,
  LIGHT_LEAK,
  GLITCH
}

// Complete catalog of real transitions matching the reference layout
val TRANSITION_CATALOG = listOf(
  // Row 1 (Matching reference screenshot)
  TransitionVisualItem(
    type = TransitionType.DISSOLVE,
    name = "Dissolve",
    category = TransitionTabCategory.DISSOLVE,
    visualKind = TransitionVisualKind.DISSOLVE
  ),
  TransitionVisualItem(
    type = TransitionType.FADE,
    name = "Fade",
    category = TransitionTabCategory.FADE,
    visualKind = TransitionVisualKind.FADE
  ),
  TransitionVisualItem(
    type = TransitionType.WIPE,
    name = "Wipe Left",
    category = TransitionTabCategory.WIPE,
    visualKind = TransitionVisualKind.WIPE_LEFT
  ),
  TransitionVisualItem(
    type = TransitionType.GLITCH_WIPE,
    name = "Wipe Right",
    category = TransitionTabCategory.WIPE,
    visualKind = TransitionVisualKind.WIPE_RIGHT
  ),

  // Row 2 (Matching reference screenshot)
  TransitionVisualItem(
    type = TransitionType.SLIDE_LEFT,
    name = "Slide Left",
    category = TransitionTabCategory.MOVE,
    visualKind = TransitionVisualKind.SLIDE_LEFT
  ),
  TransitionVisualItem(
    type = TransitionType.SLIDE_RIGHT,
    name = "Slide Right",
    category = TransitionTabCategory.MOVE,
    visualKind = TransitionVisualKind.SLIDE_RIGHT
  ),
  TransitionVisualItem(
    type = TransitionType.ZOOM_IN,
    name = "Zoom In",
    category = TransitionTabCategory.ZOOM,
    visualKind = TransitionVisualKind.ZOOM_IN
  ),
  TransitionVisualItem(
    type = TransitionType.ZOOM_OUT,
    name = "Zoom Out",
    category = TransitionTabCategory.ZOOM,
    visualKind = TransitionVisualKind.ZOOM_OUT
  ),

  // Additional Real Transitions
  TransitionVisualItem(
    type = TransitionType.PUSH_UP,
    name = "Push Up",
    category = TransitionTabCategory.MOVE,
    visualKind = TransitionVisualKind.PUSH_UP
  ),
  TransitionVisualItem(
    type = TransitionType.SPIN,
    name = "Spin",
    category = TransitionTabCategory.MOVE,
    visualKind = TransitionVisualKind.SPIN
  ),
  TransitionVisualItem(
    type = TransitionType.BLUR,
    name = "Blur",
    category = TransitionTabCategory.DISSOLVE,
    visualKind = TransitionVisualKind.BLUR
  ),
  TransitionVisualItem(
    type = TransitionType.FLASH,
    name = "Flash",
    category = TransitionTabCategory.FADE,
    visualKind = TransitionVisualKind.FLASH
  ),
  TransitionVisualItem(
    type = TransitionType.WHIP_PAN,
    name = "Whip Pan",
    category = TransitionTabCategory.MOVE,
    visualKind = TransitionVisualKind.WHIP_PAN
  ),
  TransitionVisualItem(
    type = TransitionType.ZOOM_BLUR,
    name = "Zoom Blur",
    category = TransitionTabCategory.ZOOM,
    visualKind = TransitionVisualKind.ZOOM_BLUR
  ),
  TransitionVisualItem(
    type = TransitionType.LIGHT_LEAK,
    name = "Light Leak",
    category = TransitionTabCategory.DISSOLVE,
    visualKind = TransitionVisualKind.LIGHT_LEAK
  ),
  TransitionVisualItem(
    type = TransitionType.GLITCH,
    name = "Glitch",
    category = TransitionTabCategory.ZOOM,
    visualKind = TransitionVisualKind.GLITCH
  )
)

/**
 * Lightweight, high-performance Transition Panel redesigned according to
 * the exact specifications: slim header, 1-row categories, 4-column grid,
 * compact slider duration footer with real model selection & duration binding.
 */
@Composable
fun TransitionsPanel(
  viewModel: StudioViewModel,
  onClose: () -> Unit = { viewModel.setActiveToolbarTab(null) },
  onStartDragTransition: ((TransitionType) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedCutIndex by viewModel.timelineEngine.selectedTransitionCutIndex.collectAsState()

  val videoClips = timeline.videoClips
  val totalCuts = (videoClips.size - 1).coerceAtLeast(0)
  val currentCutIndex = selectedCutIndex.coerceIn(0, (totalCuts - 1).coerceAtLeast(0))
  val currentTransition = timeline.transitions.find { it.clipIndexBefore == currentCutIndex }

  var selectedCategory by remember { mutableStateOf(TransitionTabCategory.ALL) }
  var durationMs by remember(currentTransition) {
    mutableLongStateOf(currentTransition?.durationMs ?: 500L)
  }

  Surface(
    color = PanelDarkBg,
    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    border = BorderStroke(1.dp, PanelBorder),
    modifier = modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .testTag("transitions_panel")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .navigationBarsPadding()
    ) {
      // -----------------------------------------------------------------------------------------
      // 1. DRAG HANDLE & SLIM COMPACT HEADER (~38dp height)
      // -----------------------------------------------------------------------------------------
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 6.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Drag Handle Pill
        Box(
          modifier = Modifier
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF2C3E5B))
        )
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left: Transition icon + "Transitions"
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          TransitionHeaderIcon(modifier = Modifier.size(20.dp))
          Text(
            text = "Transitions",
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              color = TextWhite,
              fontSize = 15.sp
            )
          )
        }

        // Right: ✕ Close button
        IconButton(
          onClick = { onClose() },
          modifier = Modifier
            .size(28.dp)
            .testTag("close_transitions_panel_cross")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Transitions Panel",
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      // -----------------------------------------------------------------------------------------
      // 2. HORIZONTAL CATEGORIES ROW (Single Row: All | Dissolve | Fade | Move | Wipe | Zoom)
      // -----------------------------------------------------------------------------------------
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TransitionTabCategory.values().forEach { cat ->
          val isCatSelected = selectedCategory == cat
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isCatSelected) BlueGreenAccent else CategoryInactiveBg,
            border = BorderStroke(
              1.dp,
              if (isCatSelected) BlueGreenAccent else CategoryInactiveBorder
            ),
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .clickable { selectedCategory = cat }
              .testTag("transition_cat_${cat.name.lowercase()}")
          ) {
            Text(
              text = cat.title,
              color = if (isCatSelected) Color.Black else TextWhite,
              fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
              fontSize = 12.sp,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
          }
        }
      }

      // -----------------------------------------------------------------------------------------
      // 3. TRANSITION GRID (4 COLUMNS, Vertically Scrollable, Lightweight)
      // -----------------------------------------------------------------------------------------
      val filteredItems = remember(selectedCategory) {
        if (selectedCategory == TransitionTabCategory.ALL) {
          TRANSITION_CATALOG
        } else {
          TRANSITION_CATALOG.filter { it.category == selectedCategory }
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(horizontal = 10.dp, vertical = 4.dp)
      ) {
        LazyVerticalGrid(
          columns = GridCells.Fixed(4),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(bottom = 4.dp),
          modifier = Modifier.fillMaxSize()
        ) {
          items(filteredItems, key = { it.type.name }) { item ->
            val isAppliedToCurrentCut = currentTransition?.type == item.type

            TransitionGridItemCard(
              item = item,
              isSelected = isAppliedToCurrentCut,
              onSelect = {
                if (totalCuts == 0) {
                  // Split at playhead so transition junction exists
                  viewModel.timelineEngine.splitAtPlayhead()
                }
                val cutTarget = if (totalCuts == 0) 0 else currentCutIndex
                viewModel.timelineEngine.setTransition(cutTarget, item.type, durationMs)
              }
            )
          }
        }
      }

      // -----------------------------------------------------------------------------------------
      // 4. SLIM FOOTER (Duration Slider + Apply to All)
      // -----------------------------------------------------------------------------------------
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(PanelDarkBg)
          .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Duration Control (Label + Line Slider)
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = "Duration",
              fontWeight = FontWeight.Bold,
              color = TextWhite,
              fontSize = 12.5.sp
            )
            Text(
              text = String.format(Locale.US, "%.1fs", durationMs / 1000f),
              color = TextMuted,
              fontSize = 11.5.sp
            )
          }

          Slider(
            value = durationMs.toFloat(),
            onValueChange = { newMs ->
              durationMs = newMs.toLong()
              if (currentTransition != null && totalCuts > 0) {
                viewModel.timelineEngine.setTransitionDuration(currentCutIndex, durationMs)
              }
            },
            valueRange = 100f..2000f,
            colors = SliderDefaults.colors(
              thumbColor = TextWhite,
              activeTrackColor = BlueGreenAccent,
              inactiveTrackColor = PanelBorder
            ),
            modifier = Modifier
              .weight(1f)
              .height(20.dp)
              .testTag("transition_duration_slider")
          )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Apply to All Button
        Button(
          onClick = {
            if (totalCuts == 0) {
              viewModel.timelineEngine.splitAtPlayhead()
            }
            val activeType = currentTransition?.type ?: TransitionType.DISSOLVE
            viewModel.timelineEngine.applyTransitionToAllCuts(activeType, durationMs)
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = BlueGreenAccent,
            contentColor = Color.Black
          ),
          shape = RoundedCornerShape(20.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
          modifier = Modifier
            .height(34.dp)
            .testTag("apply_all_transitions_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Apply to All",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

/**
 * Clean 4-column transition grid card with 16:9 thumbnail preview
 * and title underneath.
 */
@Composable
private fun TransitionGridItemCard(
  item: TransitionVisualItem,
  isSelected: Boolean,
  onSelect: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onSelect() }
      .testTag("transition_card_${item.type.name.lowercase()}"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // 16:9 Thumbnail Box
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(8.dp))
        .background(
          Brush.verticalGradient(
            listOf(
              Color(0xFF132238),
              Color(0xFF0C1625)
            )
          )
        )
        .border(
          width = if (isSelected) 2.dp else 1.dp,
          color = if (isSelected) BlueGreenAccent else PanelBorder,
          shape = RoundedCornerShape(8.dp)
        ),
      contentAlignment = Alignment.Center
    ) {
      // Clean, dynamic transition graphic overlay
      TransitionThumbnailGraphic(
        visualKind = item.visualKind,
        isSelected = isSelected,
        modifier = Modifier.fillMaxSize()
      )
    }

    Spacer(modifier = Modifier.height(3.dp))

    // Transition Name
    Text(
      text = item.name,
      color = if (isSelected) BlueGreenHighlight else TextMuted,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      fontSize = 10.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center
    )
  }
}

/**
 * Crisp vector graphic rendered over the thumbnail Box.
 */
@Composable
private fun TransitionThumbnailGraphic(
  visualKind: TransitionVisualKind,
  isSelected: Boolean,
  modifier: Modifier = Modifier
) {
  val iconColor = if (isSelected) TextWhite else TextWhite.copy(alpha = 0.85f)
  val accentColor = if (isSelected) BlueGreenAccent else BlueGreenPrimary

  Box(
    modifier = modifier.padding(6.dp),
    contentAlignment = Alignment.Center
  ) {
    when (visualKind) {
      TransitionVisualKind.DISSOLVE -> {
        // Two overlapping blending rounded squares
        Canvas(modifier = Modifier.fillMaxSize()) {
          val sqW = size.width * 0.32f
          val sqH = size.height * 0.55f
          val cy = (size.height - sqH) / 2f
          val cx = size.width / 2f

          // First square (translucent left)
          drawRoundRect(
            color = iconColor.copy(alpha = 0.45f),
            topLeft = Offset(cx - sqW * 0.9f, cy),
            size = Size(sqW, sqH),
            cornerRadius = CornerRadius(4f, 4f)
          )
          // Second square (solid right)
          drawRoundRect(
            color = iconColor,
            topLeft = Offset(cx - sqW * 0.1f, cy),
            size = Size(sqW, sqH),
            cornerRadius = CornerRadius(4f, 4f)
          )
        }
      }
      TransitionVisualKind.FADE -> {
        // Two overlapping blending circles
        Canvas(modifier = Modifier.fillMaxSize()) {
          val r = size.height * 0.30f
          val cy = size.height / 2f
          val cx = size.width / 2f

          drawCircle(
            color = iconColor.copy(alpha = 0.45f),
            radius = r,
            center = Offset(cx - r * 0.5f, cy)
          )
          drawCircle(
            color = iconColor,
            radius = r,
            center = Offset(cx + r * 0.5f, cy)
          )
        }
      }
      TransitionVisualKind.WIPE_LEFT -> {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = null,
          tint = iconColor,
          modifier = Modifier.size(18.dp)
        )
      }
      TransitionVisualKind.WIPE_RIGHT -> {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = null,
          tint = iconColor,
          modifier = Modifier.size(18.dp)
        )
      }
      TransitionVisualKind.SLIDE_LEFT -> {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.CompareArrows,
          contentDescription = null,
          tint = iconColor,
          modifier = Modifier.size(18.dp)
        )
      }
      TransitionVisualKind.SLIDE_RIGHT -> {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = null,
          tint = iconColor,
          modifier = Modifier.size(18.dp)
        )
      }
      TransitionVisualKind.ZOOM_IN -> {
        // 4 diagonal expand arrows ⤢
        Canvas(modifier = Modifier.fillMaxSize()) {
          val cx = size.width / 2f
          val cy = size.height / 2f
          val d = size.height * 0.28f
          val head = 5f

          // Top-Left arrow
          drawLine(iconColor, Offset(cx - 3f, cy - 3f), Offset(cx - d, cy - d), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx - d, cy - d), Offset(cx - d + head, cy - d), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx - d, cy - d), Offset(cx - d, cy - d + head), strokeWidth = 2.2f, cap = StrokeCap.Round)

          // Bottom-Right arrow
          drawLine(iconColor, Offset(cx + 3f, cy + 3f), Offset(cx + d, cy + d), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx + d, cy + d), Offset(cx + d - head, cy + d), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx + d, cy + d), Offset(cx + d, cy + d - head), strokeWidth = 2.2f, cap = StrokeCap.Round)

          // Top-Right arrow
          drawLine(iconColor, Offset(cx + 3f, cy - 3f), Offset(cx + d, cy - d), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx + d, cy - d), Offset(cx + d - head, cy - d), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx + d, cy - d), Offset(cx + d, cy - d + head), strokeWidth = 2.2f, cap = StrokeCap.Round)

          // Bottom-Left arrow
          drawLine(iconColor, Offset(cx - 3f, cy + 3f), Offset(cx - d, cy + d), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx - d, cy + d), Offset(cx - d + head, cy + d), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx - d, cy + d), Offset(cx - d, cy + d - head), strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
      }
      TransitionVisualKind.ZOOM_OUT -> {
        // 4 diagonal shrink arrows ⤡
        Canvas(modifier = Modifier.fillMaxSize()) {
          val cx = size.width / 2f
          val cy = size.height / 2f
          val d = size.height * 0.28f
          val head = 5f

          // Inward Top-Left
          drawLine(iconColor, Offset(cx - d, cy - d), Offset(cx - 3f, cy - 3f), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx - 3f, cy - 3f), Offset(cx - 3f - head, cy - 3f), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx - 3f, cy - 3f), Offset(cx - 3f, cy - 3f - head), strokeWidth = 2.2f, cap = StrokeCap.Round)

          // Inward Bottom-Right
          drawLine(iconColor, Offset(cx + d, cy + d), Offset(cx + 3f, cy + 3f), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx + 3f, cy + 3f), Offset(cx + 3f + head, cy + 3f), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx + 3f, cy + 3f), Offset(cx + 3f, cy + 3f + head), strokeWidth = 2.2f, cap = StrokeCap.Round)

          // Inward Top-Right
          drawLine(iconColor, Offset(cx + d, cy - d), Offset(cx + 3f, cy - 3f), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx + 3f, cy - 3f), Offset(cx + 3f + head, cy - 3f), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx + 3f, cy - 3f), Offset(cx + 3f, cy - 3f - head), strokeWidth = 2.2f, cap = StrokeCap.Round)

          // Inward Bottom-Left
          drawLine(iconColor, Offset(cx - d, cy + d), Offset(cx - 3f, cy + 3f), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx - 3f, cy + 3f), Offset(cx - 3f - head, cy + 3f), strokeWidth = 2.2f, cap = StrokeCap.Round)
          drawLine(iconColor, Offset(cx - 3f, cy + 3f), Offset(cx - 3f, cy + 3f + head), strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
      }
      TransitionVisualKind.PUSH_UP -> {
        Icon(
          imageVector = Icons.Default.ArrowUpward,
          contentDescription = null,
          tint = iconColor,
          modifier = Modifier.size(18.dp)
        )
      }
      TransitionVisualKind.SPIN -> {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = null,
          tint = iconColor,
          modifier = Modifier.size(18.dp)
        )
      }
      TransitionVisualKind.BLUR -> {
        Icon(
          imageVector = Icons.Default.Waves,
          contentDescription = null,
          tint = iconColor,
          modifier = Modifier.size(18.dp)
        )
      }
      TransitionVisualKind.FLASH -> {
        Icon(
          imageVector = Icons.Default.FlashOn,
          contentDescription = null,
          tint = iconColor,
          modifier = Modifier.size(18.dp)
        )
      }
      TransitionVisualKind.WHIP_PAN -> {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(18.dp)
        )
      }
      TransitionVisualKind.ZOOM_BLUR -> {
        Icon(
          imageVector = Icons.Default.AutoAwesome,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(18.dp)
        )
      }
      TransitionVisualKind.LIGHT_LEAK -> {
        Icon(
          imageVector = Icons.Default.FlashOn,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(18.dp)
        )
      }
      TransitionVisualKind.GLITCH -> {
        Icon(
          imageVector = Icons.Default.AutoAwesome,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

/**
 * Two overlapping rectangles icon matching the reference header design.
 */
@Composable
private fun TransitionHeaderIcon(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val sqW = size.width * 0.65f
    val sqH = size.height * 0.65f
    val r = 3f

    // Background rectangle
    drawRoundRect(
      color = BlueGreenAccent.copy(alpha = 0.5f),
      topLeft = Offset(0f, 0f),
      size = Size(sqW, sqH),
      cornerRadius = CornerRadius(r, r),
      style = Stroke(width = 1.8f)
    )

    // Foreground offset rectangle
    drawRoundRect(
      color = BlueGreenAccent,
      topLeft = Offset(size.width * 0.35f, size.height * 0.35f),
      size = Size(sqW, sqH),
      cornerRadius = CornerRadius(r, r),
      style = Stroke(width = 1.8f)
    )
  }
}
