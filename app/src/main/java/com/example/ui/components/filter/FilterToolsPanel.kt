package com.example.ui.components.filter

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.FilterSettings
import com.example.domain.model.FilterType
import com.example.domain.model.VideoAdjustments
import com.example.engine.SelectedTrackElement
import com.example.ui.StudioViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

// Distinctive Blue + Green mixed studio aesthetic
private val BlueGreenDarkTop = Color(0xFF0B1F27)     // Deep Blue-Green Navy
private val BlueGreenDarkBottom = Color(0xFF07171C)  // Dark Midnight Teal
private val BlueGreenBorder = Color(0xFF133C45)      // Elegant Teal Border
private val BlueGreenAccent = Color(0xFF00D1B2)      // Bright Emerald Teal
private val BlueAccent = Color(0xFF00B4D8)           // Cyan Blue
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val CardBg = Color(0xFF0E232B)

enum class FilterToolsTab(val title: String, val icon: ImageVector) {
  FILTERS("Filters", Icons.Default.FilterVintage),
  ADJUST("Adjust", Icons.Default.Tune),
  VIDEO_QUALITY("Video Quality", Icons.Default.HighQuality)
}

enum class AdjustMode(val title: String) {
  SMART_AUTO("Smart Auto"),
  CUSTOMISE("Customise")
}

enum class SmartAutoTool(val title: String, val icon: ImageVector) {
  AUTO_ADJUST("Auto Adjust", Icons.Default.AutoFixHigh),
  COLOR_FIXINGS("Color Fixings", Icons.Default.ColorLens),
  COLOR_CORRECT("Color Correct", Icons.Default.Palette)
}

enum class CustomiseTool(val title: String, val icon: ImageVector) {
  BRIGHTNESS("Brightness", Icons.Default.WbSunny),
  SHARPEN("Sharpen", Icons.Default.Details),
  CLARITY("Clarity", Icons.Default.Deblur),
  HIGHLIGHTS("Highlights", Icons.Default.LightMode),
  WHITES("Whites", Icons.Default.Brightness7),
  BLACKS("Blacks", Icons.Default.Brightness4),
  TEMPERATURE("Temperature", Icons.Default.Thermostat),
  FADE("Fade", Icons.Default.Gradient),
  VIGNETTE("Vignette", Icons.Default.Vignette),
  CONTRAST("Contrast", Icons.Default.Contrast),
  GRAIN("Grain", Icons.Default.Grain),
  SHADOWS("Shadows", Icons.Default.DarkMode)
}

/**
 * Filter Tools Panel:
 * - 45% screen height bottom-sheet style container over editor
 * - Blue + Green mixed background
 * - Header: Reset (Left) | Filter Tools (Center) | Close X (Right)
 * - Tabs: Filters | Adjust | Video Quality
 * - Filters tab: 4 columns, vertical scroll, "None" first, real GPU filters, zero lag
 * - Adjust tab: Smart Auto (Auto Adjust, Color Fixings, Color Correct) + Customise (12 tools with 1..100 sliders)
 * - Video Quality tab: Auto Enhance, Denoise, Clarity, HDR, Anti-Flicker, Color Fix
 */
@Composable
fun FilterToolsPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier,
  initialTab: FilterToolsTab = FilterToolsTab.FILTERS,
  onClose: () -> Unit = { viewModel.setActiveToolbarTab(null) }
) {
  val context = LocalContext.current
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()
  val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()

  // Selected clip if any, or active clip under current playhead
  val selectedClip = remember(timeline, selectedElement, currentPosMs) {
    val fromSelection = when (selectedElement) {
      is SelectedTrackElement.Video -> timeline.videoClips.find { it.id == (selectedElement as SelectedTrackElement.Video).clipId }
      is SelectedTrackElement.Overlay -> timeline.overlayClips.find { it.id == (selectedElement as SelectedTrackElement.Overlay).clipId }
      else -> null
    }
    fromSelection
      ?: timeline.videoClips.find { currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineStartMs + it.durationMs }
      ?: timeline.videoClips.firstOrNull()
  }

  val previewUri = remember(selectedClip, timeline) {
    selectedClip?.uri?.ifBlank { null }
      ?: timeline.videoClips.firstOrNull { it.uri.isNotBlank() }?.uri
      ?: ""
  }
  val previewStartMs = selectedClip?.sourceStartMs ?: 0L

  // Active filter state
  val activeFilterState = selectedClip?.filter ?: timeline.filter
  var currentFilter by remember(activeFilterState) { mutableStateOf(activeFilterState) }

  // Active adjustments state
  val activeAdjustments = timeline.adjustments
  var currentAdjustments by remember(activeAdjustments) { mutableStateOf(activeAdjustments) }

  var activeTab by remember { mutableStateOf(initialTab) }

  // Lightweight single base thumbnail loaded ONCE for the entire grid (zero lag, zero OOM)
  var baseThumbnailBitmap by remember(previewUri) {
    val key = com.example.engine.media.VideoThumbnailManager.makeKey(previewUri, previewStartMs, 96, 96)
    mutableStateOf(com.example.engine.media.VideoThumbnailManager.getCachedThumbnail(key))
  }

  LaunchedEffect(previewUri, previewStartMs) {
    if (previewUri.isNotBlank() && baseThumbnailBitmap == null) {
      try {
        com.example.engine.media.VideoThumbnailManager.requestThumbnail(
          context = context,
          uri = previewUri,
          sourceTimeMs = previewStartMs,
          targetWidth = 96,
          targetHeight = 96,
          isVideo = true
        ) { bmp ->
          baseThumbnailBitmap = bmp
        }
      } catch (_: Throwable) {
        // Safe fallback to procedural canvas
      }
    }
  }

  val hasActiveFilter = currentFilter.type != FilterType.NONE && currentFilter.intensity > 0.01f
  val hasActiveAdjustments = remember(currentAdjustments) {
    abs(currentAdjustments.brightness) > 0.01f ||
      abs(currentAdjustments.contrast - 1f) > 0.01f ||
      abs(currentAdjustments.saturation - 1f) > 0.01f ||
      abs(currentAdjustments.exposure) > 0.01f ||
      abs(currentAdjustments.temperature) > 0.01f ||
      abs(currentAdjustments.tint) > 0.01f ||
      abs(currentAdjustments.highlights) > 0.01f ||
      abs(currentAdjustments.shadows) > 0.01f ||
      abs(currentAdjustments.whites) > 0.01f ||
      abs(currentAdjustments.blacks) > 0.01f ||
      currentAdjustments.sharpness > 0.01f ||
      currentAdjustments.vignette > 0.01f ||
      currentAdjustments.fade > 0.01f ||
      currentAdjustments.grain > 0.01f
  }
  val hasActiveQuality = remember(currentAdjustments) {
    currentAdjustments.autoEnhance > 0.01f ||
      currentAdjustments.denoise > 0.01f ||
      currentAdjustments.hdrBoost > 0.01f ||
      currentAdjustments.antiFlicker > 0.01f ||
      currentAdjustments.colorFix > 0.01f
  }

  Surface(
    color = BlueGreenDarkBottom,
    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    border = BorderStroke(1.dp, BlueGreenBorder),
    modifier = modifier.fillMaxWidth().wrapContentHeight()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .background(
          Brush.verticalGradient(
            listOf(BlueGreenDarkTop, BlueGreenDarkBottom)
          )
        )
        .navigationBarsPadding()
    ) {
      // 1. HEADER ROW: Reset (Left) | Filter Tools (Center) | Close X (Right) - Slim Design
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF081B22))
          .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left: Reset
        TextButton(
          onClick = {
            when (activeTab) {
              FilterToolsTab.FILTERS -> {
                val reset = FilterSettings(type = FilterType.NONE, intensity = 1.0f)
                currentFilter = reset
                viewModel.timelineEngine.updateFilter(reset, selectedClip?.id)
              }
              FilterToolsTab.ADJUST -> {
                val reset = currentAdjustments.copy(
                  brightness = 0f,
                  contrast = 1f,
                  saturation = 1f,
                  exposure = 0f,
                  temperature = 0f,
                  tint = 0f,
                  highlights = 0f,
                  shadows = 0f,
                  whites = 0f,
                  blacks = 0f,
                  sharpness = 0f,
                  fade = 0f,
                  vignette = 0f,
                  grain = 0f
                )
                currentAdjustments = reset
                viewModel.timelineEngine.updateAdjustments(reset)
              }
              FilterToolsTab.VIDEO_QUALITY -> {
                val reset = currentAdjustments.copy(
                  autoEnhance = 0f,
                  denoise = 0f,
                  hdrBoost = 0f,
                  antiFlicker = 0f,
                  colorFix = 0f
                )
                currentAdjustments = reset
                viewModel.timelineEngine.updateAdjustments(reset)
              }
            }
          },
          colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
          modifier = Modifier.testTag("filter_tools_reset_button")
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(13.dp), tint = TextSecondary)
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = "Reset",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
          )
        }

        // Center: Title (Slim)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "Filter Tools",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
          Text(
            text = if (selectedClip != null) "Clip: ${selectedClip.name.take(18)}" else "Timeline Master",
            color = BlueGreenAccent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
          )
        }

        // Right: Close Button (X) (Slim)
        IconButton(
          onClick = onClose,
          modifier = Modifier
            .size(28.dp)
            .testTag("filter_tools_close_button")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      // 2. MAIN PANEL TABS: Exactly ONE Slim Row (Filters | Adjust | Video Quality)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF06151B))
          .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        FilterToolsTab.values().forEach { tab ->
          val isSelected = activeTab == tab
          val hasBadge = when (tab) {
            FilterToolsTab.FILTERS -> hasActiveFilter
            FilterToolsTab.ADJUST -> hasActiveAdjustments
            FilterToolsTab.VIDEO_QUALITY -> hasActiveQuality
          }

          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) BlueGreenAccent.copy(alpha = 0.2f) else Color(0xFF0C2028),
            border = BorderStroke(
              width = if (isSelected) 1.5.dp else 1.dp,
              color = if (isSelected) BlueGreenAccent else BlueGreenBorder
            ),
            modifier = Modifier
              .weight(1f)
              .height(28.dp)
              .clip(RoundedCornerShape(6.dp))
              .clickable { activeTab = tab }
              .testTag("tab_${tab.name.lowercase()}")
          ) {
            Row(
              modifier = Modifier.fillMaxSize(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = if (isSelected) BlueGreenAccent else TextSecondary,
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = tab.title,
                color = if (isSelected) Color.White else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp
              )
              if (hasBadge) {
                Spacer(modifier = Modifier.width(3.dp))
                Box(
                  modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(BlueGreenAccent)
                )
              }
            }
          }
        }
      }

      // Subtle dividing line
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(BlueGreenBorder)
      )

      // 3. TAB CONTENT
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        when (activeTab) {
          FilterToolsTab.FILTERS -> {
            FiltersTabContent(
              viewModel = viewModel,
              currentFilter = currentFilter,
              selectedClipId = selectedClip?.id,
              baseBitmap = baseThumbnailBitmap,
              onFilterChange = { newFilter ->
                currentFilter = newFilter
                viewModel.timelineEngine.updateFilter(newFilter, selectedClip?.id)
              }
            )
          }
          FilterToolsTab.ADJUST -> {
            AdjustTabContent(
              currentAdjustments = currentAdjustments,
              onAdjustmentsChange = { newAdj ->
                currentAdjustments = newAdj
                viewModel.timelineEngine.updateAdjustments(newAdj)
              }
            )
          }
          FilterToolsTab.VIDEO_QUALITY -> {
            VideoQualityTabContent(
              currentAdjustments = currentAdjustments,
              onAdjustmentsChange = { newAdj ->
                currentAdjustments = newAdj
                viewModel.timelineEngine.updateAdjustments(newAdj)
              }
            )
          }
        }
      }
    }
  }
}

/**
 * Tab 1: FILTERS CONTENT
 * 4 Columns, Vertical Scroll, First item "None", Real filters, Lightweight preview
 */
@Composable
private fun FiltersTabContent(
  viewModel: StudioViewModel,
  currentFilter: FilterSettings,
  selectedClipId: String?,
  baseBitmap: Bitmap?,
  onFilterChange: (FilterSettings) -> Unit
) {
  var selectedCategory by remember { mutableStateOf("All") }
  val categories = listOf("All", "Pro Enhancements", "Cinematic & Nature", "Aesthetic Looks")

  val displayFilters = remember(selectedCategory) {
    val all = FilterType.values().toList()
    val filtered = when (selectedCategory) {
      "All" -> all
      "Pro Enhancements" -> all.filter { it.category == "Pro Enhancements" || it == FilterType.NONE }
      "Cinematic & Nature" -> all.filter { it.category == "Cinematic & Nature" || it == FilterType.NONE }
      "Aesthetic Looks" -> all.filter { it.category == "Aesthetic Looks" || it == FilterType.NONE }
      else -> all
    }
    // Guarantee NONE is always the very first item
    val withoutNone = filtered.filter { it != FilterType.NONE }
    listOf(FilterType.NONE) + withoutNone
  }

  val gridState = rememberLazyGridState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    // Category chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      categories.forEach { cat ->
        val isCatSelected = selectedCategory == cat
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (isCatSelected) BlueGreenAccent.copy(alpha = 0.25f) else Color(0xFF0C2028),
          border = BorderStroke(1.dp, if (isCatSelected) BlueGreenAccent else BlueGreenBorder),
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { selectedCategory = cat }
        ) {
          Text(
            text = cat,
            color = if (isCatSelected) BlueGreenAccent else TextSecondary,
            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 10.5.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
          )
        }
      }
    }

    // Filter Intensity Slider (Visible when active filter != NONE)
    if (currentFilter.type != FilterType.NONE) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0B1E26),
        border = BorderStroke(1.dp, BlueGreenBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${currentFilter.type.displayName} Intensity",
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
              fontSize = 11.sp
            )
            Text(
              text = "${(currentFilter.intensity * 100).roundToInt()}%",
              color = BlueGreenAccent,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
          }

          Slider(
            value = currentFilter.intensity,
            onValueChange = { onFilterChange(currentFilter.copy(intensity = it)) },
            valueRange = 0.01f..1f,
            colors = SliderDefaults.colors(
              thumbColor = BlueGreenAccent,
              activeTrackColor = BlueGreenAccent,
              inactiveTrackColor = BlueGreenBorder
            ),
            modifier = Modifier
              .height(24.dp)
              .testTag("filter_intensity_slider")
          )

          // Quick presets & Apply to all clips
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              listOf(0.25f, 0.50f, 0.75f, 1.0f).forEach { preset ->
                val isPSelected = abs(currentFilter.intensity - preset) < 0.05f
                Surface(
                  shape = RoundedCornerShape(4.dp),
                  color = if (isPSelected) BlueGreenAccent.copy(alpha = 0.25f) else Color(0xFF0E2730),
                  border = BorderStroke(1.dp, if (isPSelected) BlueGreenAccent else BlueGreenBorder),
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onFilterChange(currentFilter.copy(intensity = preset)) }
                ) {
                  Text(
                    text = "${(preset * 100).toInt()}%",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPSelected) BlueGreenAccent else TextSecondary,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                  )
                }
              }
            }

            Surface(
              shape = RoundedCornerShape(4.dp),
              color = Color(0xFF0E2730),
              border = BorderStroke(1.dp, BlueGreenBorder),
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                  viewModel.timelineEngine.applyFilterToAllClips(currentFilter)
                }
                .testTag("filter_apply_all_button")
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Icon(
                  Icons.Default.DoneAll,
                  contentDescription = null,
                  tint = BlueGreenAccent,
                  modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = "Apply All Clips",
                  fontSize = 9.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = BlueGreenAccent
                )
              }
            }
          }
        }
      }
    }

    // 4-Column Vertically Scrolling Filters Grid
    LazyVerticalGrid(
      columns = GridCells.Fixed(4),
      state = gridState,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
      contentPadding = PaddingValues(bottom = 12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .testTag("filters_vertical_grid_4_columns")
    ) {
      gridItems(displayFilters, key = { it.name }) { type ->
        val isSelected = currentFilter.type == type

        FilterCardItem(
          type = type,
          isSelected = isSelected,
          baseBitmap = baseBitmap,
          onClick = {
            if (type == FilterType.NONE) {
              onFilterChange(FilterSettings(type = FilterType.NONE, intensity = 1.0f))
            } else {
              val targetIntensity = if (currentFilter.intensity <= 0.05f) 1.0f else currentFilter.intensity
              onFilterChange(FilterSettings(type = type, intensity = targetIntensity))
            }
          }
        )
      }
    }
  }
}

/**
 * Filter Card Item:
 * - 4 columns layout
 * - Real GPU ColorMatrix applied to thumbnail
 * - Zero background loops or decoding
 */
@Composable
private fun FilterCardItem(
  type: FilterType,
  isSelected: Boolean,
  baseBitmap: Bitmap?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val matrixArray = remember(type) {
    com.example.engine.composition.ColorFilterGenerator.getFilterMatrixArray(type, 1.0f)
  }
  val composeColorFilter = remember(matrixArray, type) {
    if (type == FilterType.NONE) null
    else ColorFilter.colorMatrix(ColorMatrix(matrixArray))
  }

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = if (isSelected) Color(0xFF133640) else CardBg,
    border = BorderStroke(
      width = if (isSelected) 2.dp else 1.dp,
      color = if (isSelected) BlueGreenAccent else BlueGreenBorder
    ),
    modifier = modifier
      .fillMaxWidth()
      .height(86.dp)
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .testTag("filter_preset_${type.name.lowercase()}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(3.dp),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Preview thumbnail box
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(58.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0xFF071419)),
        contentAlignment = Alignment.Center
      ) {
        if (type == FilterType.NONE) {
          // Special distinct NONE card
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
          ) {
            Icon(
              imageVector = Icons.Default.Block,
              contentDescription = "None",
              tint = if (isSelected) BlueGreenAccent else TextSecondary,
              modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Original",
              fontSize = 9.sp,
              color = if (isSelected) BlueGreenAccent else TextSecondary,
              fontWeight = FontWeight.Medium
            )
          }
        } else if (baseBitmap != null && !baseBitmap.isRecycled) {
          Image(
            bitmap = baseBitmap.asImageBitmap(),
            contentDescription = "${type.displayName} Preview",
            colorFilter = composeColorFilter,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          // Clean procedural gradient preview
          ProceduralFilterPreview(type = type)
        }

        // Selection Checkmark Badge
        if (isSelected) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(2.dp)
              .size(15.dp)
              .clip(CircleShape)
              .background(BlueGreenAccent),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Selected",
              tint = Color.Black,
              modifier = Modifier.size(10.dp)
            )
          }
        }
      }

      // Title
      Text(
        text = type.displayName,
        color = if (isSelected) BlueGreenAccent else TextPrimary,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 9.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 1.dp)
      )
    }
  }
}

/**
 * Tab 2: ADJUST CONTENT
 * Secondary row: 1. Smart Auto | 2. Customise
 * - Smart Auto: Auto Adjust, Color Fixings, Color Correct with 1..100 sliders
 * - Customise: Horizontal row of 12 tools (Brightness, Sharpen, Clarity, Highlights, Whites, Blacks, Temperature, Fade, Vignette, Contrast, Grain, Shadows) with 1..100 slider
 */
@Composable
private fun AdjustTabContent(
  currentAdjustments: VideoAdjustments,
  onAdjustmentsChange: (VideoAdjustments) -> Unit
) {
  var mode by remember { mutableStateOf(AdjustMode.SMART_AUTO) }
  var selectedSmartAutoTool by remember { mutableStateOf(SmartAutoTool.AUTO_ADJUST) }
  var selectedCustomiseTool by remember { mutableStateOf(CustomiseTool.BRIGHTNESS) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // Row 2: Mode Selector (Smart Auto | Customise)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF091C23), RoundedCornerShape(8.dp))
        .padding(3.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      AdjustMode.values().forEach { m ->
        val isSel = mode == m
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = if (isSel) BlueGreenAccent else Color.Transparent,
          modifier = Modifier
            .weight(1f)
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { mode = m }
            .testTag("adjust_mode_${m.name.lowercase()}")
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = m.title,
              color = if (isSel) Color.Black else TextSecondary,
              fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
              fontSize = 11.5.sp
            )
          }
        }
      }
    }

    when (mode) {
      AdjustMode.SMART_AUTO -> {
        // Exactly three tools in one row: Auto Adjust, Color Fixings, Color Correct
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          SmartAutoTool.values().forEach { tool ->
            val isSel = selectedSmartAutoTool == tool
            val valueStrength = when (tool) {
              SmartAutoTool.AUTO_ADJUST -> (currentAdjustments.autoEnhance * 100).roundToInt().coerceIn(1, 100)
              SmartAutoTool.COLOR_FIXINGS -> (currentAdjustments.colorFix * 100).roundToInt().coerceIn(1, 100)
              SmartAutoTool.COLOR_CORRECT -> (currentAdjustments.hdrBoost * 100).roundToInt().coerceIn(1, 100)
            }
            val isActive = when (tool) {
              SmartAutoTool.AUTO_ADJUST -> currentAdjustments.autoEnhance > 0.01f
              SmartAutoTool.COLOR_FIXINGS -> currentAdjustments.colorFix > 0.01f
              SmartAutoTool.COLOR_CORRECT -> currentAdjustments.hdrBoost > 0.01f
            }

            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (isSel) Color(0xFF133942) else Color(0xFF0C2028),
              border = BorderStroke(
                width = if (isSel) 1.5.dp else 1.dp,
                color = if (isSel) BlueGreenAccent else BlueGreenBorder
              ),
              modifier = Modifier
                .weight(1f)
                .height(68.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { selectedSmartAutoTool = tool }
                .testTag("smart_auto_${tool.name.lowercase()}")
            ) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
              ) {
                Icon(
                  imageVector = tool.icon,
                  contentDescription = tool.title,
                  tint = if (isSel) BlueGreenAccent else if (isActive) BlueAccent else TextSecondary,
                  modifier = Modifier.size(20.dp)
                )
                Text(
                  text = tool.title,
                  fontSize = 10.sp,
                  fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSel) Color.White else TextPrimary,
                  textAlign = TextAlign.Center,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "$valueStrength%",
                  fontSize = 9.sp,
                  color = if (isActive) BlueGreenAccent else TextSecondary,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Selected Smart Auto Slider: 1 ───────────── 100
        val currentValue = when (selectedSmartAutoTool) {
          SmartAutoTool.AUTO_ADJUST -> if (currentAdjustments.autoEnhance <= 0f) 50f else (currentAdjustments.autoEnhance * 100f)
          SmartAutoTool.COLOR_FIXINGS -> if (currentAdjustments.colorFix <= 0f) 50f else (currentAdjustments.colorFix * 100f)
          SmartAutoTool.COLOR_CORRECT -> if (currentAdjustments.hdrBoost <= 0f) 50f else (currentAdjustments.hdrBoost * 100f)
        }

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFF0A1F26),
          border = BorderStroke(1.dp, BlueGreenBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  selectedSmartAutoTool.icon,
                  contentDescription = null,
                  tint = BlueGreenAccent,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "${selectedSmartAutoTool.title} Strength",
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp
                )
              }

              Text(
                text = "${currentValue.roundToInt()}",
                color = BlueGreenAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }

            Slider(
              value = currentValue,
              onValueChange = { newVal ->
                val fraction = (newVal / 100f).coerceIn(0.01f, 1f)
                val updated = when (selectedSmartAutoTool) {
                  SmartAutoTool.AUTO_ADJUST -> currentAdjustments.copy(autoEnhance = fraction)
                  SmartAutoTool.COLOR_FIXINGS -> currentAdjustments.copy(colorFix = fraction)
                  SmartAutoTool.COLOR_CORRECT -> currentAdjustments.copy(hdrBoost = fraction)
                }
                onAdjustmentsChange(updated)
              },
              valueRange = 1f..100f,
              colors = SliderDefaults.colors(
                thumbColor = BlueGreenAccent,
                activeTrackColor = BlueGreenAccent,
                inactiveTrackColor = BlueGreenBorder
              ),
              modifier = Modifier
                .height(30.dp)
                .testTag("smart_auto_slider")
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("1 (Subtle)", color = TextSecondary, fontSize = 9.5.sp)
              TextButton(
                onClick = {
                  val updated = when (selectedSmartAutoTool) {
                    SmartAutoTool.AUTO_ADJUST -> currentAdjustments.copy(autoEnhance = 0f)
                    SmartAutoTool.COLOR_FIXINGS -> currentAdjustments.copy(colorFix = 0f)
                    SmartAutoTool.COLOR_CORRECT -> currentAdjustments.copy(hdrBoost = 0f)
                  }
                  onAdjustmentsChange(updated)
                },
                contentPadding = PaddingValues(0.dp)
              ) {
                Text("Disable", color = TextSecondary, fontSize = 10.sp)
              }
              Text("100 (Maximum)", color = TextSecondary, fontSize = 9.5.sp)
            }
          }
        }
      }

      AdjustMode.CUSTOMISE -> {
        // Horizontally scrollable row of exactly 12 tools
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          CustomiseTool.values().forEach { tool ->
            val isSel = selectedCustomiseTool == tool
            val isModified = when (tool) {
              CustomiseTool.BRIGHTNESS -> abs(currentAdjustments.brightness) > 0.01f
              CustomiseTool.SHARPEN -> currentAdjustments.sharpness > 0.01f
              CustomiseTool.CLARITY -> currentAdjustments.clarity > 0.01f
              CustomiseTool.HIGHLIGHTS -> abs(currentAdjustments.highlights) > 0.01f
              CustomiseTool.WHITES -> abs(currentAdjustments.whites) > 0.01f
              CustomiseTool.BLACKS -> abs(currentAdjustments.blacks) > 0.01f
              CustomiseTool.TEMPERATURE -> abs(currentAdjustments.temperature) > 0.01f
              CustomiseTool.FADE -> currentAdjustments.fade > 0.01f
              CustomiseTool.VIGNETTE -> currentAdjustments.vignette > 0.01f
              CustomiseTool.CONTRAST -> abs(currentAdjustments.contrast - 1f) > 0.01f
              CustomiseTool.GRAIN -> currentAdjustments.grain > 0.01f
              CustomiseTool.SHADOWS -> abs(currentAdjustments.shadows) > 0.01f
            }

            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (isSel) Color(0xFF133942) else Color(0xFF0C2028),
              border = BorderStroke(
                width = if (isSel) 1.5.dp else 1.dp,
                color = if (isSel) BlueGreenAccent else BlueGreenBorder
              ),
              modifier = Modifier
                .width(66.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { selectedCustomiseTool = tool }
                .testTag("custom_tool_${tool.name.lowercase()}")
            ) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
              ) {
                Icon(
                  imageVector = tool.icon,
                  contentDescription = tool.title,
                  tint = if (isSel) BlueGreenAccent else if (isModified) BlueAccent else TextSecondary,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = tool.title,
                  fontSize = 9.sp,
                  fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSel) Color.White else TextPrimary,
                  textAlign = TextAlign.Center,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                // Small indicator dot if tool has non-default value
                Box(
                  modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isModified) BlueGreenAccent else Color.Transparent)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Selected Tool Slider: 1 ───────────────── 100
        val slider1to100 = when (selectedCustomiseTool) {
          CustomiseTool.BRIGHTNESS -> ((currentAdjustments.brightness * 50f) + 50f).coerceIn(1f, 100f)
          CustomiseTool.CONTRAST -> (currentAdjustments.contrast * 50f).coerceIn(1f, 100f)
          CustomiseTool.HIGHLIGHTS -> ((currentAdjustments.highlights * 50f) + 50f).coerceIn(1f, 100f)
          CustomiseTool.SHADOWS -> ((currentAdjustments.shadows * 50f) + 50f).coerceIn(1f, 100f)
          CustomiseTool.WHITES -> ((currentAdjustments.whites * 50f) + 50f).coerceIn(1f, 100f)
          CustomiseTool.BLACKS -> ((currentAdjustments.blacks * 50f) + 50f).coerceIn(1f, 100f)
          CustomiseTool.TEMPERATURE -> ((currentAdjustments.temperature * 50f) + 50f).coerceIn(1f, 100f)
          CustomiseTool.SHARPEN -> ((currentAdjustments.sharpness * 99f) + 1f).coerceIn(1f, 100f)
          CustomiseTool.CLARITY -> ((currentAdjustments.clarity * 99f) + 1f).coerceIn(1f, 100f)
          CustomiseTool.FADE -> ((currentAdjustments.fade * 99f) + 1f).coerceIn(1f, 100f)
          CustomiseTool.VIGNETTE -> ((currentAdjustments.vignette * 99f) + 1f).coerceIn(1f, 100f)
          CustomiseTool.GRAIN -> ((currentAdjustments.grain * 99f) + 1f).coerceIn(1f, 100f)
        }

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFF0A1F26),
          border = BorderStroke(1.dp, BlueGreenBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  selectedCustomiseTool.icon,
                  contentDescription = null,
                  tint = BlueGreenAccent,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = selectedCustomiseTool.title,
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp
                )
              }

              Text(
                text = "${slider1to100.roundToInt()}",
                color = BlueGreenAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }

            Slider(
              value = slider1to100,
              onValueChange = { newVal ->
                val updated = when (selectedCustomiseTool) {
                  CustomiseTool.BRIGHTNESS -> currentAdjustments.copy(brightness = (newVal - 50f) / 50f)
                  CustomiseTool.CONTRAST -> currentAdjustments.copy(contrast = newVal / 50f)
                  CustomiseTool.HIGHLIGHTS -> currentAdjustments.copy(highlights = (newVal - 50f) / 50f)
                  CustomiseTool.SHADOWS -> currentAdjustments.copy(shadows = (newVal - 50f) / 50f)
                  CustomiseTool.WHITES -> currentAdjustments.copy(whites = (newVal - 50f) / 50f)
                  CustomiseTool.BLACKS -> currentAdjustments.copy(blacks = (newVal - 50f) / 50f)
                  CustomiseTool.TEMPERATURE -> currentAdjustments.copy(temperature = (newVal - 50f) / 50f)
                  CustomiseTool.SHARPEN -> currentAdjustments.copy(sharpness = (newVal - 1f) / 99f)
                  CustomiseTool.CLARITY -> currentAdjustments.copy(clarity = (newVal - 1f) / 99f)
                  CustomiseTool.FADE -> currentAdjustments.copy(fade = (newVal - 1f) / 99f)
                  CustomiseTool.VIGNETTE -> currentAdjustments.copy(vignette = (newVal - 1f) / 99f)
                  CustomiseTool.GRAIN -> currentAdjustments.copy(grain = (newVal - 1f) / 99f)
                }
                onAdjustmentsChange(updated)
              },
              valueRange = 1f..100f,
              colors = SliderDefaults.colors(
                thumbColor = BlueGreenAccent,
                activeTrackColor = BlueGreenAccent,
                inactiveTrackColor = BlueGreenBorder
              ),
              modifier = Modifier
                .height(30.dp)
                .testTag("custom_tool_slider")
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("1", color = TextSecondary, fontSize = 9.5.sp)
              TextButton(
                onClick = {
                  val reset = when (selectedCustomiseTool) {
                    CustomiseTool.BRIGHTNESS -> currentAdjustments.copy(brightness = 0f)
                    CustomiseTool.CONTRAST -> currentAdjustments.copy(contrast = 1f)
                    CustomiseTool.HIGHLIGHTS -> currentAdjustments.copy(highlights = 0f)
                    CustomiseTool.SHADOWS -> currentAdjustments.copy(shadows = 0f)
                    CustomiseTool.WHITES -> currentAdjustments.copy(whites = 0f)
                    CustomiseTool.BLACKS -> currentAdjustments.copy(blacks = 0f)
                    CustomiseTool.TEMPERATURE -> currentAdjustments.copy(temperature = 0f)
                    CustomiseTool.SHARPEN -> currentAdjustments.copy(sharpness = 0f)
                    CustomiseTool.CLARITY -> currentAdjustments.copy(clarity = 0f)
                    CustomiseTool.FADE -> currentAdjustments.copy(fade = 0f)
                    CustomiseTool.VIGNETTE -> currentAdjustments.copy(vignette = 0f)
                    CustomiseTool.GRAIN -> currentAdjustments.copy(grain = 0f)
                  }
                  onAdjustmentsChange(reset)
                },
                contentPadding = PaddingValues(0.dp)
              ) {
                Text("Reset this tool", color = TextSecondary, fontSize = 10.sp)
              }
              Text("100", color = TextSecondary, fontSize = 9.5.sp)
            }
          }
        }
      }
    }
  }
}

/**
 * Tab 3: VIDEO QUALITY CONTENT
 * Real GPU enhancement algorithms: Auto Enhance, Denoise, Super Clarity, HDR Boost, Anti-Flicker, Color Fix
 */
@Composable
private fun VideoQualityTabContent(
  currentAdjustments: VideoAdjustments,
  onAdjustmentsChange: (VideoAdjustments) -> Unit
) {
  val qualityTools = listOf(
    QualityToolConfig("Auto Enhance", Icons.Default.AutoFixHigh, currentAdjustments.autoEnhance) {
      onAdjustmentsChange(currentAdjustments.copy(autoEnhance = it))
    },
    QualityToolConfig("Denoise", Icons.Default.BlurOn, currentAdjustments.denoise) {
      onAdjustmentsChange(currentAdjustments.copy(denoise = it))
    },
    QualityToolConfig("Super Clarity", Icons.Default.HighQuality, currentAdjustments.clarity) {
      onAdjustmentsChange(currentAdjustments.copy(clarity = it))
    },
    QualityToolConfig("HDR Boost", Icons.Default.HdrOn, currentAdjustments.hdrBoost) {
      onAdjustmentsChange(currentAdjustments.copy(hdrBoost = it))
    },
    QualityToolConfig("Anti-Flicker", Icons.Default.MotionPhotosAuto, currentAdjustments.antiFlicker) {
      onAdjustmentsChange(currentAdjustments.copy(antiFlicker = it))
    },
    QualityToolConfig("Color Fix", Icons.Default.ColorLens, currentAdjustments.colorFix) {
      onAdjustmentsChange(currentAdjustments.copy(colorFix = it))
    }
  )

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    items(qualityTools) { tool ->
      val value1to100 = (tool.currentValue * 100f).coerceIn(1f, 100f)
      val isActive = tool.currentValue > 0.01f

      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0A1F26),
        border = BorderStroke(1.dp, if (isActive) BlueGreenAccent.copy(alpha = 0.6f) else BlueGreenBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                tool.icon,
                contentDescription = null,
                tint = if (isActive) BlueGreenAccent else TextSecondary,
                modifier = Modifier.size(15.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = tool.name,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp
              )
            }

            Text(
              text = if (isActive) "${(tool.currentValue * 100).roundToInt()}%" else "Off",
              color = if (isActive) BlueGreenAccent else TextSecondary,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
          }

          Slider(
            value = if (isActive) value1to100 else 1f,
            onValueChange = { newVal ->
              tool.onUpdate((newVal / 100f).coerceIn(0.01f, 1f))
            },
            valueRange = 1f..100f,
            colors = SliderDefaults.colors(
              thumbColor = if (isActive) BlueGreenAccent else TextSecondary,
              activeTrackColor = BlueGreenAccent,
              inactiveTrackColor = BlueGreenBorder
            ),
            modifier = Modifier
              .height(24.dp)
              .testTag("quality_slider_${tool.name.lowercase().replace(" ", "_")}")
          )
        }
      }
    }
  }
}

private data class QualityToolConfig(
  val name: String,
  val icon: ImageVector,
  val currentValue: Float,
  val onUpdate: (Float) -> Unit
)

/**
 * Procedural fallback preview for filters
 */
@Composable
private fun ProceduralFilterPreview(
  type: FilterType,
  modifier: Modifier = Modifier
) {
  val baseColors = remember(type) {
    when (type) {
      FilterType.NONE -> listOf(Color(0xFF334155), Color(0xFF0F172A))
      FilterType.FOUR_K -> listOf(Color(0xFF0284C7), Color(0xFF1E3A8A), Color(0xFF0F172A))
      FilterType.BLACKLIGHT_FIX -> listOf(Color(0xFFD97706), Color(0xFFB45309), Color(0xFF1E1B4B))
      FilterType.ENHANCE -> listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFF312E81))
      FilterType.HDR -> listOf(Color(0xFFFF007F), Color(0xFF7928CA), Color(0xFF0284C7))
      FilterType.GLOW -> listOf(Color(0xFFFDE047), Color(0xFFF472B6), Color(0xFF4F46E5))
      FilterType.FOCUS -> listOf(Color(0xFF10B981), Color(0xFF0369A1), Color(0xFF0F172A))
      FilterType.QUALITY_RESTORATION -> listOf(Color(0xFF2DD4BF), Color(0xFF2563EB), Color(0xFF0F172A))
      FilterType.GOLDEN_AUTUMN -> listOf(Color(0xFFF97316), Color(0xFF9A3412), Color(0xFF451A03))
      FilterType.OCEANIC_VIEW -> listOf(Color(0xFF06B6D4), Color(0xFF0369A1), Color(0xFF082F49))
      FilterType.ALMOND -> listOf(Color(0xFFFDE68A), Color(0xFFD4A373), Color(0xFF78350F))
      FilterType.SUNLIGHT_ORANGE_BLUE -> listOf(Color(0xFFFB923C), Color(0xFF0284C7), Color(0xFF0F172A))
      FilterType.CINEMATIC -> listOf(Color(0xFF0D9488), Color(0xFFF97316), Color(0xFF042F2E))
      FilterType.WARM -> listOf(Color(0xFFF59E0B), Color(0xFFD97706), Color(0xFF451A03))
      FilterType.COOL -> listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFF082F49))
      FilterType.PORTRAIT -> listOf(Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFF881337))
      FilterType.BLACK_AND_WHITE -> listOf(Color(0xFFE2E8F0), Color(0xFF64748B), Color(0xFF0F172A))
      FilterType.VINTAGE -> listOf(Color(0xFFB45309), Color(0xFFFDE68A), Color(0xFF451A03))
      FilterType.SATURATION -> listOf(Color(0xFFEC4899), Color(0xFF3B82F6), Color(0xFF1E1B4B))
      FilterType.FILM -> listOf(Color(0xFFA16207), Color(0xFF78350F), Color(0xFF1C1917))
      FilterType.RETRO -> listOf(Color(0xFFD946EF), Color(0xFF8B5CF6), Color(0xFF3B0764))
      FilterType.NATURE -> listOf(Color(0xFF10B981), Color(0xFF047857), Color(0xFF064E3B))
      FilterType.FOOD -> listOf(Color(0xFFEA580C), Color(0xFFFACC15), Color(0xFF7C2D12))
      FilterType.TRAVEL -> listOf(Color(0xFF0284C7), Color(0xFF10B981), Color(0xFF064E3B))
      FilterType.SOCIAL_MEDIA -> listOf(Color(0xFFFF007F), Color(0xFF8B5CF6), Color(0xFF312E81))
    }
  }

  Canvas(modifier = modifier.fillMaxSize()) {
    drawRect(
      brush = Brush.verticalGradient(baseColors),
      size = size
    )
  }
}
