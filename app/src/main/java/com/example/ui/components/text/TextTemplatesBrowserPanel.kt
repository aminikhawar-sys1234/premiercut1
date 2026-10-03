package com.example.ui.components.text

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.text.registry.RegisteredTextTemplate
import com.example.engine.text.registry.TextAssetRegistry
import com.example.ui.StudioViewModel

/**
 * Text Templates Browser Panel.
 * Opened when user selects "Text Templates" from the main Text Tools navigation.
 * 4-column card grid showing real installed Text Templates.
 * Initial state: COMPLETELY EMPTY (zero dummy/sample/placeholder templates).
 */
@Composable
fun TextTemplatesBrowserPanel(
  viewModel: StudioViewModel,
  onClose: () -> Unit,
  onApplyTemplate: (RegisteredTextTemplate) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val installedTemplates = remember { TextAssetRegistry.getTemplates() }

  val panelBackground = Brush.verticalGradient(
    colors = listOf(
      Color(0xFF0F2537), // Deep blue
      Color(0xFF092825)  // Deep subtle emerald
    )
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
      .background(panelBackground)
      .border(1.dp, Color(0xFF1B4450), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
      .padding(horizontal = 14.dp, vertical = 6.dp)
      .testTag("text_templates_browser_panel")
  ) {
    // Header: Slim Close Button + Title
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .size(32.dp)
          .testTag("text_templates_close_btn")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close",
          tint = Color.White,
          modifier = Modifier.size(20.dp)
        )
      }

      Text(
        text = "Text Templates",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.size(32.dp)) // Symmetry spacing
    }

    // 4-Column Card Grid (Empty initially, zero mock templates)
    if (installedTemplates.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = false)
          .padding(16.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "No Text Templates Installed",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
          )
          Text(
            text = "Installed template plugins and downloaded packages will appear in this 4-column browser.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
          )
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        items(installedTemplates) { template ->
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF102834),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1C3B49)),
            modifier = Modifier
              .fillMaxWidth()
              .height(72.dp)
              .clickable {
                onApplyTemplate(template)
                onClose()
              }
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.padding(4.dp)
            ) {
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
  }
}
