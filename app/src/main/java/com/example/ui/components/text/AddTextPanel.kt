package com.example.ui.components.text

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.TextClip
import com.example.engine.SelectedTrackElement
import com.example.engine.text.registry.*
import com.example.ui.StudioViewModel
import com.example.util.FontManager
import java.util.UUID

enum class AddTextSubTool(val label: String, val tag: String) {
  TEMPLATES("Templates", "subtool_templates"),
  FONTS("Fonts", "subtool_fonts"),
  STYLES("Styles", "subtool_styles"),
  ANIMATIONS("Animations", "subtool_animations"),
  EFFECTS("Effects", "subtool_effects"),
  THREE_D("3D Text", "subtool_3d_text"),
  PAINTING("Painting", "subtool_painting")
}

@Composable
fun AddTextPanel(
  viewModel: StudioViewModel,
  onClose: () -> Unit,
  onOpenPainting: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()
  val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()

  // Identify currently selected TextClip or initialize a new draft clip on timeline
  val existingClip = remember(selectedElement, timeline.textClips) {
    (selectedElement as? SelectedTrackElement.Text)?.let { sel ->
      timeline.textClips.find { it.id == sel.clipId }
    }
  }

  // Active clip being edited in real-time
  var activeClipId by remember {
    mutableStateOf(existingClip?.id ?: "")
  }

  var textValue by remember {
    mutableStateOf(existingClip?.text ?: "")
  }

  // If opening for a brand new text element, initialize it immediately on timeline for live preview
  LaunchedEffect(Unit) {
    if (existingClip == null) {
      val newId = UUID.randomUUID().toString()
      val insertionTime = currentPosMs.coerceAtLeast(0L)
      val newClip = TextClip(
        id = newId,
        text = "",
        timelineStartMs = insertionTime,
        durationMs = 3000L,
        fontSizeSp = 28f,
        textColor = 0xFFFFFFFF,
        strokeWidth = 0f,
        strokeColor = 0xFF000000,
        alignment = "Center",
        animationType = "None",
        animationIn = "None",
        animationOut = "None"
      )
      activeClipId = newId
      viewModel.timelineEngine.addTextClipObject(newClip)
    } else {
      activeClipId = existingClip.id
      textValue = existingClip.text
    }
  }

  // Lookup current clip from timeline state
  val currentClip = timeline.textClips.find { it.id == activeClipId }

  var activeSubTool by remember { mutableStateOf(AddTextSubTool.STYLES) }

  // Simple lightweight green + blue background
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
      .padding(horizontal = 14.dp, vertical = 6.dp)
      .testTag("add_text_panel")
  ) {
    // --- Header: Left: ❌ Close | Center: Title | Right: ✓ Confirm (Slim) ---
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // ❌ Close: Closes the panel (and discards if empty)
      IconButton(
        onClick = {
          if (currentClip != null && currentClip.text.isBlank()) {
            viewModel.timelineEngine.deleteClips(setOf(currentClip.id))
          }
          onClose()
        },
        modifier = Modifier
          .size(32.dp)
          .testTag("add_text_close_btn")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close",
          tint = Color.White,
          modifier = Modifier.size(20.dp)
        )
      }

      Text(
        text = "Add Text",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
      )

      // ✓ Confirm: Commits the currently edited text to the project and closes panel automatically
      IconButton(
        onClick = {
          if (currentClip != null && textValue.isNotBlank()) {
            val updated = currentClip.copy(text = textValue)
            viewModel.timelineEngine.updateTextClip(updated)
          } else if (currentClip != null && textValue.isBlank()) {
            viewModel.timelineEngine.deleteClips(setOf(currentClip.id))
          }
          onClose()
        },
        modifier = Modifier
          .size(32.dp)
          .testTag("add_text_confirm_btn")
      ) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = "Confirm",
          tint = Color(0xFF10B981),
          modifier = Modifier.size(20.dp)
        )
      }
    }

    // --- Center Text Editing Field (Live Preview Updated on Every Keystroke) ---
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = Color(0xFF071B20),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .padding(vertical = 2.dp)
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        if (textValue.isEmpty()) {
          Text(
            text = "Type text here...",
            color = Color(0xFF64748B),
            fontSize = 14.sp
          )
        }
        BasicTextField(
          value = textValue,
          onValueChange = { newText ->
            textValue = newText
            // Live update project state on timeline so preview updates immediately!
            if (currentClip != null) {
              val updated = currentClip.copy(text = newText)
              viewModel.timelineEngine.updateTextClip(updated)
            }
          },
          textStyle = TextStyle(
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
          ),
          cursorBrush = SolidColor(Color(0xFF10B981)),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("add_text_input_field")
        )
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // --- Horizontally Scrollable Sub-Tool Row ---
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      AddTextSubTool.values().forEach { tool ->
        val isSelected = activeSubTool == tool
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) Color(0xFF10B981) else Color(0xFF0F3235),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF34D399) else Color(0xFF1A4A4B)
          ),
          modifier = Modifier
            .clickable {
              if (tool == AddTextSubTool.PAINTING) {
                onOpenPainting()
              } else {
                activeSubTool = tool
              }
            }
            .testTag(tool.tag)
        ) {
          Text(
            text = tool.label,
            color = if (isSelected) Color(0xFF06221D) else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // --- Active Sub-Tool Content Area ---
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      when (activeSubTool) {
        AddTextSubTool.TEMPLATES -> {
          TemplatesSubToolView(
            currentClip = currentClip,
            onApplyTemplate = { tmpl ->
              if (currentClip != null) {
                val updated = if (tmpl == null) {
                  // "None" option: reset template formatting
                  currentClip.copy(
                    fontFamily = "Default",
                    strokeWidth = 0f,
                    strokeColor = 0xFF000000,
                    textColor = 0xFFFFFFFF,
                    is3D = false,
                    animationIn = "None",
                    animationOut = "None",
                    effectStyle = "None"
                  )
                } else {
                  currentClip.copy(
                    fontFamily = tmpl.templateClip.fontFamily,
                    fontSizeSp = tmpl.templateClip.fontSizeSp,
                    textColor = tmpl.templateClip.textColor,
                    strokeWidth = tmpl.templateClip.strokeWidth,
                    strokeColor = tmpl.templateClip.strokeColor,
                    is3D = tmpl.templateClip.is3D,
                    depth3D = tmpl.templateClip.depth3D,
                    animationIn = tmpl.templateClip.animationIn,
                    animationOut = tmpl.templateClip.animationOut,
                    effectStyle = tmpl.templateClip.effectStyle
                  )
                }
                viewModel.timelineEngine.updateTextClip(updated)
              }
            }
          )
        }

        AddTextSubTool.FONTS -> {
          FontsSubToolView(
            context = context,
            currentClip = currentClip,
            onSelectFont = { font ->
              if (currentClip != null) {
                val updated = currentClip.copy(
                  fontFamily = font.fontFamilyName,
                  customFontPath = font.fontFilePath
                )
                viewModel.timelineEngine.updateTextClip(updated)
              }
            }
          )
        }

        AddTextSubTool.STYLES -> {
          StylesSubToolView(
            currentClip = currentClip,
            onUpdateClip = { updated ->
              viewModel.timelineEngine.updateTextClip(updated)
            }
          )
        }

        AddTextSubTool.ANIMATIONS -> {
          AnimationsSubToolView(
            currentClip = currentClip,
            onSelectAnimation = { cat, animKey ->
              if (currentClip != null) {
                val updated = when (cat) {
                  "In" -> currentClip.copy(animationIn = animKey)
                  "Out" -> currentClip.copy(animationOut = animKey)
                  else -> currentClip.copy(animationType = animKey)
                }
                viewModel.timelineEngine.updateTextClip(updated)
              }
            }
          )
        }

        AddTextSubTool.EFFECTS -> {
          EffectsSubToolView(
            currentClip = currentClip,
            onSelectEffect = { effectKey ->
              if (currentClip != null) {
                val updated = currentClip.copy(effectStyle = effectKey)
                viewModel.timelineEngine.updateTextClip(updated)
              }
            }
          )
        }

        AddTextSubTool.THREE_D -> {
          ThreeDSubToolView(
            currentClip = currentClip,
            onSelect3D = { asset ->
              if (currentClip != null) {
                val updated = if (asset == null) {
                  currentClip.copy(is3D = false, depth3D = 0f)
                } else {
                  currentClip.copy(
                    is3D = true,
                    depth3D = asset.depth3D,
                    bevelAngle3D = asset.bevelAngle3D,
                    color3D = asset.color3D
                  )
                }
                viewModel.timelineEngine.updateTextClip(updated)
              }
            }
          )
        }

        AddTextSubTool.PAINTING -> {
          // Painting row item opens real DrawToolPanel directly
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 1. TEMPLATES SUB-TOOL (4-Column Grid, First option: "None", zero dummy/fake templates)
// ------------------------------------------------------------------------------------------------
@Composable
private fun TemplatesSubToolView(
  currentClip: TextClip?,
  onApplyTemplate: (RegisteredTextTemplate?) -> Unit
) {
  val installedTemplates = remember { TextAssetRegistry.getTemplates() }

  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    // Required functional "None" option as the first/default selection
    item {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0F2F32),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E5253)),
        modifier = Modifier
          .fillMaxWidth()
          .height(70.dp)
          .clickable { onApplyTemplate(null) }
          .testTag("template_none_opt")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = "None",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
        }
      }
    }

    // Real installed templates only (empty initially, no fake templates)
    items(installedTemplates) { template ->
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0E2530),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A4557)),
        modifier = Modifier
          .fillMaxWidth()
          .height(70.dp)
          .clickable { onApplyTemplate(template) }
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
          Text(
            text = template.name,
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

// ------------------------------------------------------------------------------------------------
// 2. FONTS SUB-TOOL (Horizontal Categories, 4-Column Grid, Zero dummy fonts)
// ------------------------------------------------------------------------------------------------
@Composable
private fun FontsSubToolView(
  context: android.content.Context,
  currentClip: TextClip?,
  onSelectFont: (RegisteredFont) -> Unit
) {
  val categories = listOf(
    "My Fonts",
    "Urdu Fonts",
    "English Fonts",
    "Arabic",
    "Hindi",
    "Chinese",
    "Other supported languages"
  )
  var selectedCategory by remember { mutableStateOf("My Fonts") }

  // Query real fonts from TextAssetRegistry + real imported fonts
  var refreshTrigger by remember { mutableStateOf(0) }
  val installedFonts = remember(selectedCategory, refreshTrigger) {
    if (selectedCategory == "My Fonts") {
      val imported = FontManager.getAvailableFonts(context).filter { it.isCustom }.map {
        RegisteredFont(
          id = it.id,
          name = it.name,
          category = "My Fonts",
          fontFamilyName = it.id,
          fontFilePath = it.filePath,
          isCustom = true
        )
      }
      imported + TextAssetRegistry.getFonts("My Fonts")
    } else {
      TextAssetRegistry.getFonts(selectedCategory)
    }
  }

  // File Picker for importing real font files into "My Fonts"
  val fontPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      FontManager.importFont(context, uri)
      refreshTrigger++
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // Horizontal Categories
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(bottom = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      categories.forEach { cat ->
        val isSelected = selectedCategory == cat
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) Color(0xFF10B981) else Color(0xFF0F2C2C),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF34D399) else Color(0xFF194444)
          ),
          modifier = Modifier.clickable { selectedCategory = cat }
        ) {
          Text(
            text = cat,
            color = if (isSelected) Color(0xFF06221D) else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    }

    // 4-Column Card Grid
    if (installedFonts.isEmpty() && selectedCategory != "My Fonts") {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "No fonts installed in $selectedCategory.",
          color = Color(0xFF94A3B8),
          fontSize = 13.sp
        )
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        // In "My Fonts", offer an import card to add real .ttf/.otf fonts
        if (selectedCategory == "My Fonts") {
          item {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFF123B37),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
              modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clickable {
                  fontPickerLauncher.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "*/*"))
                }
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = "Import",
                  tint = Color(0xFF10B981),
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "Import Font",
                  color = Color.White,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }
        }

        items(installedFonts) { font ->
          val isSelected = currentClip?.fontFamily == font.fontFamilyName
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFF0E2530),
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              if (isSelected) Color(0xFF10B981) else Color(0xFF1A4557)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(68.dp)
              .clickable { onSelectFont(font) }
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
              Text(
                text = font.name,
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
}

// ------------------------------------------------------------------------------------------------
// 3. STYLES SUB-TOOL (Stroke, Color Circles, Color Slider 0-100, Text Size Slider 1-100)
// ------------------------------------------------------------------------------------------------
@Composable
private fun StylesSubToolView(
  currentClip: TextClip?,
  onUpdateClip: (TextClip) -> Unit
) {
  if (currentClip == null) return

  var activeColorTarget by remember { mutableStateOf("Text") } // "Text" or "Stroke"

  val colorSwatches = listOf(
    0xFFFFFFFF to "White",
    0xFF000000 to "Black",
    0xFFEF4444 to "Red",
    0xFFFACC15 to "Yellow",
    0xFF10B981 to "Green",
    0xFF00E5FF to "Cyan",
    0xFF3B82F6 to "Blue",
    0xFF8B5CF6 to "Purple",
    0xFFEC4899 to "Pink",
    0xFFF97316 to "Orange"
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // --- Stroke Width Controls ---
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = "Stroke Width",
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf(
          0f to "None",
          2f to "Thin",
          4f to "Medium",
          6f to "Bold",
          8f to "Heavy"
        ).forEach { (width, label) ->
          val isSelected = currentClip.strokeWidth == width
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) Color(0xFF10B981) else Color(0xFF0F2F32),
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              if (isSelected) Color(0xFF34D399) else Color(0xFF1E5253)
            ),
            modifier = Modifier
              .weight(1f)
              .height(34.dp)
              .clickable {
                onUpdateClip(currentClip.copy(strokeWidth = width))
              }
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = label,
                color = if (isSelected) Color(0xFF06221D) else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
              )
            }
          }
        }
      }
    }

    // --- Color Circles: Selectable for Text or Stroke Color ---
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "$activeColorTarget Color",
          color = Color.White,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("Text", "Stroke").forEach { target ->
            val isTarget = activeColorTarget == target
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (isTarget) Color(0xFF10B981) else Color(0xFF0F2F32),
              modifier = Modifier.clickable { activeColorTarget = target }
            ) {
              Text(
                text = target,
                color = if (isTarget) Color(0xFF06221D) else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        colorSwatches.forEach { (colorVal, _) ->
          val isCurrent = if (activeColorTarget == "Text") {
            currentClip.textColor == colorVal
          } else {
            currentClip.strokeColor == colorVal
          }

          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(Color(colorVal))
              .border(
                width = if (isCurrent) 2.5.dp else 1.dp,
                color = if (isCurrent) Color(0xFF10B981) else Color(0xFF475569),
                shape = CircleShape
              )
              .clickable {
                val updated = if (activeColorTarget == "Text") {
                  currentClip.copy(textColor = colorVal)
                } else {
                  currentClip.copy(strokeColor = colorVal)
                }
                onUpdateClip(updated)
              }
          )
        }
      }
    }

    // --- Color Adjustment Slider (0 -> 100): Modifies Opacity / Intensity ---
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      val currentAlpha = (currentClip.opacity * 100f).toInt().coerceIn(0, 100)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(text = "Color Adjustment", color = Color.White, fontSize = 13.sp)
        Text(text = "$currentAlpha%", color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Bold)
      }
      Slider(
        value = currentAlpha.toFloat(),
        onValueChange = { newVal ->
          onUpdateClip(currentClip.copy(opacity = newVal / 100f))
        },
        valueRange = 0f..100f,
        colors = SliderDefaults.colors(
          thumbColor = Color(0xFF10B981),
          activeTrackColor = Color(0xFF10B981),
          inactiveTrackColor = Color(0xFF1E3A3A)
        ),
        modifier = Modifier.testTag("color_adjustment_slider")
      )
    }

    // --- Text Size Slider (1 -> 100): Modifies fontSizeSp in Real-Time ---
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      val currentSize = currentClip.fontSizeSp.toInt().coerceIn(1, 100)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(text = "Text Size", color = Color.White, fontSize = 13.sp)
        Text(text = "${currentSize}sp", color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Bold)
      }
      Slider(
        value = currentSize.toFloat(),
        onValueChange = { newSize ->
          onUpdateClip(currentClip.copy(fontSizeSp = newSize))
        },
        valueRange = 1f..100f,
        colors = SliderDefaults.colors(
          thumbColor = Color(0xFF10B981),
          activeTrackColor = Color(0xFF10B981),
          inactiveTrackColor = Color(0xFF1E3A3A)
        ),
        modifier = Modifier.testTag("text_size_slider")
      )
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 4. ANIMATIONS SUB-TOOL (Categories: In, Out, Combo; 4-Column Grid, First: "None")
// ------------------------------------------------------------------------------------------------
@Composable
private fun AnimationsSubToolView(
  currentClip: TextClip?,
  onSelectAnimation: (category: String, animationKey: String) -> Unit
) {
  val animCategories = listOf("In", "Out", "Combo")
  var selectedCategory by remember { mutableStateOf("In") }

  val registeredAnims = remember(selectedCategory) {
    TextAssetRegistry.getAnimations(selectedCategory)
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // Categories: In, Out, Combo
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      animCategories.forEach { cat ->
        val isSelected = selectedCategory == cat
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) Color(0xFF10B981) else Color(0xFF0F2C2C),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF34D399) else Color(0xFF194444)
          ),
          modifier = Modifier.clickable { selectedCategory = cat }
        ) {
          Text(
            text = cat,
            color = if (isSelected) Color(0xFF06221D) else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
          )
        }
      }
    }

    LazyVerticalGrid(
      columns = GridCells.Fixed(4),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxSize()
    ) {
      // First option: None
      item {
        val isCurrentNone = when (selectedCategory) {
          "In" -> currentClip?.animationIn == "None"
          "Out" -> currentClip?.animationOut == "None"
          else -> currentClip?.animationType == "None"
        }
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (isCurrentNone) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFF0F2F32),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrentNone) Color(0xFF10B981) else Color(0xFF1E5253)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable { onSelectAnimation(selectedCategory, "None") }
            .testTag("anim_none_opt")
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = "None",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }
        }
      }

      // Real registered animation cards only (empty initially, no fake cards)
      items(registeredAnims) { anim ->
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFF0E2530),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A4557)),
          modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable { onSelectAnimation(selectedCategory, anim.animationKey) }
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
            Text(
              text = anim.name,
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

// ------------------------------------------------------------------------------------------------
// 5. EFFECTS SUB-TOOL (4-Column Grid, First: "None")
// ------------------------------------------------------------------------------------------------
@Composable
private fun EffectsSubToolView(
  currentClip: TextClip?,
  onSelectEffect: (String) -> Unit
) {
  val registeredEffects = remember { TextAssetRegistry.getEffects() }

  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    // First option: None
    item {
      val isNone = currentClip?.effectStyle == "None" || currentClip?.effectStyle.isNullOrBlank()
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isNone) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFF0F2F32),
        border = androidx.compose.foundation.BorderStroke(
          1.dp,
          if (isNone) Color(0xFF10B981) else Color(0xFF1E5253)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(68.dp)
          .clickable { onSelectEffect("None") }
          .testTag("effect_none_opt")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = "None",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }
      }
    }

    // Real registered effects only (empty initially, zero fake effects)
    items(registeredEffects) { eff ->
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0E2530),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A4557)),
        modifier = Modifier
          .fillMaxWidth()
          .height(68.dp)
          .clickable { onSelectEffect(eff.effectKey) }
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
          Text(
            text = eff.name,
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

// ------------------------------------------------------------------------------------------------
// 6. 3D TEXT SUB-TOOL (4-Column Grid, First: "None")
// ------------------------------------------------------------------------------------------------
@Composable
private fun ThreeDSubToolView(
  currentClip: TextClip?,
  onSelect3D: (Registered3DText?) -> Unit
) {
  val registered3D = remember { TextAssetRegistry.get3DAssets() }

  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    // First option: None
    item {
      val isNone = currentClip?.is3D != true
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isNone) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFF0F2F32),
        border = androidx.compose.foundation.BorderStroke(
          1.dp,
          if (isNone) Color(0xFF10B981) else Color(0xFF1E5253)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(68.dp)
          .clickable { onSelect3D(null) }
          .testTag("3d_none_opt")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = "None",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }
      }
    }

    // Real registered 3D assets only (empty initially, zero fake 3D presets)
    items(registered3D) { asset ->
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0E2530),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A4557)),
        modifier = Modifier
          .fillMaxWidth()
          .height(68.dp)
          .clickable { onSelect3D(asset) }
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
          Text(
            text = asset.name,
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
