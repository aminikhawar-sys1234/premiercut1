package com.example.ui.components.elements

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.StickerClip
import com.example.ui.StudioViewModel

private val DarkPanelBg = Color(0xFF0C101A)
private val SurfaceCardBg = Color(0xFF141926)
private val BorderStrokeColor = Color(0xFF232C42)
private val CyanAccent = Color(0xFF00E5FF)
private val PurpleAccent = Color(0xFF7000FF)

@Composable
fun ElementsToolPanel(
  viewModel: StudioViewModel,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(ElementCategory.SHAPES) }
  var lastAddedTitle by remember { mutableStateOf<String?>(null) }

  val categories = remember { ElementCategory.values() }

  val displayedElements = remember(searchQuery, selectedCategory) {
    if (searchQuery.isNotBlank()) {
      ElementsCatalog.search(searchQuery)
    } else {
      ElementsCatalog.getByCategory(selectedCategory)
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .background(DarkPanelBg)
      .testTag("elements_tool_panel")
  ) {
    // -------------------------------------------------------------
    // 1. TOP HEADER: Search bar + ❌ Close button
    // -------------------------------------------------------------
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Search Bar
      Box(
        modifier = Modifier
          .weight(1f)
          .height(42.dp)
          .clip(RoundedCornerShape(21.dp))
          .background(SurfaceCardBg)
          .border(1.dp, BorderStrokeColor, RoundedCornerShape(21.dp))
          .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search Elements",
            tint = CyanAccent,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          BasicTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            textStyle = TextStyle(
              color = Color.White,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Medium
            ),
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("elements_search_bar"),
            decorationBox = { innerTextField ->
              if (searchQuery.isEmpty()) {
                Text(
                  text = "Search elements (car, bird, circle, male...)",
                  color = Color.White.copy(alpha = 0.45f),
                  fontSize = 12.5.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              innerTextField()
            }
          )
          if (searchQuery.isNotEmpty()) {
            IconButton(
              onClick = { searchQuery = "" },
              modifier = Modifier.size(24.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear search",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.width(10.dp))

      // ❌ Close Button at the top
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(SurfaceCardBg)
          .border(1.dp, BorderStrokeColor, CircleShape)
          .testTag("elements_close_btn")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close Elements Panel",
          tint = Color.White,
          modifier = Modifier.size(20.dp)
        )
      }
    }

    // -------------------------------------------------------------
    // 2. HORIZONTALLY SCROLLABLE CATEGORY ROW
    // -------------------------------------------------------------
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("elements_categories_row"),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(categories) { category ->
        val isSelected = selectedCategory == category && searchQuery.isEmpty()
        val count = remember(category) { ElementsCatalog.getByCategory(category).size }

        Surface(
          onClick = {
            selectedCategory = category
            searchQuery = "" // Clear search to view selected category
          },
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) CyanAccent.copy(alpha = 0.16f) else SurfaceCardBg,
          border = BorderStroke(
            1.dp,
            if (isSelected) CyanAccent else BorderStrokeColor
          ),
          modifier = Modifier
            .height(34.dp)
            .testTag("elements_category_${category.id}")
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp)
          ) {
            Icon(
              imageVector = category.icon,
              contentDescription = category.displayName,
              tint = if (isSelected) CyanAccent else Color.White.copy(alpha = 0.75f),
              modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = category.displayName,
              color = if (isSelected) CyanAccent else Color.White,
              fontSize = 12.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(5.dp))
            Box(
              modifier = Modifier
                .clip(CircleShape)
                .background(if (isSelected) CyanAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
              Text(
                text = "$count",
                color = if (isSelected) CyanAccent else Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }
    }

    // Category Info / Search Summary banner
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      if (searchQuery.isNotBlank()) {
        Text(
          text = "Search results for \"$searchQuery\" (${displayedElements.size})",
          color = CyanAccent,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.SemiBold
        )
      } else {
        Text(
          text = "${selectedCategory.displayName} • ${selectedCategory.description}",
          color = Color.White.copy(alpha = 0.65f),
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      if (lastAddedTitle != null) {
        Text(
          text = "Added: $lastAddedTitle",
          color = Color(0xFF00FF88),
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    // -------------------------------------------------------------
    // 3. 4-COLUMN GRID OF ELEMENT THUMBNAILS
    // -------------------------------------------------------------
    if (displayedElements.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = "No results",
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(40.dp)
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "No elements match \"$searchQuery\"",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp
          )
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(horizontal = 10.dp)
          .testTag("elements_grid_4col"),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(displayedElements, key = { it.id }) { element ->
          ElementThumbnailCard(
            element = element,
            onClick = {
              // Add to Canvas / Preview & Create Timeline Layer starting at current CTI!
              viewModel.timelineEngine.addElementClip(
                elementId = element.id,
                elementCategory = element.category.id,
                title = element.title,
                iconSymbol = element.iconSymbol,
                primaryColor = element.primaryColor,
                secondaryColor = element.secondaryColor,
                defaultScale = element.defaultScale,
                durationMs = 3000L,
                renderData = element.renderData
              )
              lastAddedTitle = element.title
            }
          )
        }
      }
    }
  }
}

/**
 * Clean, production-grade 4-column thumbnail card for each element
 */
@Composable
private fun ElementThumbnailCard(
  element: ElementItem,
  onClick: () -> Unit
) {
  val previewClip = remember(element) {
    StickerClip(
      id = element.id,
      emojiOrAsset = "${element.iconSymbol} ${element.title}",
      elementId = element.id,
      elementCategory = element.category.id,
      customColor = element.primaryColor,
      secondaryColor = element.secondaryColor,
      scale = 1.0f
    )
  }

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = SurfaceCardBg,
    border = BorderStroke(1.dp, BorderStrokeColor),
    modifier = Modifier
      .fillMaxWidth()
      .height(84.dp)
      .testTag("element_card_${element.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      // Dynamic vector preview canvas
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        contentAlignment = Alignment.Center
      ) {
        Canvas(modifier = Modifier.fillMaxSize(0.85f)) {
          drawIntoCanvas { composeCanvas ->
            ElementRenderer.draw(
              canvas = composeCanvas.nativeCanvas,
              clip = previewClip,
              scale = 0.85f,
              opacity = 1.0f,
              width = size.width.toInt(),
              height = size.height.toInt(),
              currentPosMs = 0L
            )
          }
        }
      }

      // Title label
      Text(
        text = element.title,
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
      )
    }
  }
}
