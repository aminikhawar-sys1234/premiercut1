package com.example.ui.components.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.StudioViewModel
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class DrawnPath(
  val points: List<Offset>,
  val color: Color,
  val strokeWidth: Float,
  val isEraser: Boolean = false,
  val isNeon: Boolean = false
)

enum class BrushStyle(val displayName: String, val iconEmoji: String) {
  PENCIL("Pencil", "✏️"),
  MARKER("Marker", "🖊️"),
  HIGHLIGHTER("Highlighter", "🖍️"),
  NEON("Neon Glow", "✨"),
  ERASER("Eraser", "🧹")
}

@Composable
fun DrawToolPanel(
  viewModel: StudioViewModel,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var selectedBrush by remember { mutableStateOf(BrushStyle.MARKER) }
  var selectedColor by remember { mutableStateOf(Color(0xFF00E5FF)) }
  var strokeWidth by remember { mutableStateOf(12f) }

  val paths = remember { mutableStateListOf<DrawnPath>() }
  val redoPaths = remember { mutableStateListOf<DrawnPath>() }
  var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

  val palette = listOf(
    Color(0xFFFFFFFF),
    Color(0xFF00E5FF),
    Color(0xFFFF007F),
    Color(0xFFFFD700),
    Color(0xFF10B981),
    Color(0xFF8B5CF6),
    Color(0xFFFF5722),
    Color(0xFF00F0FF),
    Color(0xFF000000)
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Top Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CyanAccent.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Brush, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
        }
        Column {
          Text("Draw & Doodle Tool", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
          Text("Freehand brush, neon strokes & animations", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Undo
        IconButton(
          onClick = {
            if (paths.isNotEmpty()) {
              val last = paths.removeLast()
              redoPaths.add(last)
            }
          },
          enabled = paths.isNotEmpty(),
          modifier = Modifier.size(32.dp)
        ) {
          Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (paths.isNotEmpty()) TextPrimary else TextSecondary.copy(alpha = 0.3f))
        }

        // Redo
        IconButton(
          onClick = {
            if (redoPaths.isNotEmpty()) {
              val last = redoPaths.removeLast()
              paths.add(last)
            }
          },
          enabled = redoPaths.isNotEmpty(),
          modifier = Modifier.size(32.dp)
        ) {
          Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (redoPaths.isNotEmpty()) TextPrimary else TextSecondary.copy(alpha = 0.3f))
        }

        // Clear
        IconButton(
          onClick = {
            paths.clear()
            redoPaths.clear()
          },
          modifier = Modifier.size(32.dp)
        ) {
          Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = Color(0xFFEF4444))
        }

        // Save & Add
        FilledTonalButton(
          onClick = {
            if (paths.isNotEmpty()) {
              // Render drawing to a transparent PNG file and add as OverlayClip
              val bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888)
              val canvas = Canvas(bitmap)
              val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
              }

              for (p in paths) {
                if (p.isEraser) continue
                paint.color = p.color.hashCode()
                paint.strokeWidth = p.strokeWidth * 2f
                val path = Path()
                if (p.points.isNotEmpty()) {
                  path.moveTo(p.points.first().x * 3f, p.points.first().y * 3f)
                  for (pt in p.points.drop(1)) {
                    path.lineTo(pt.x * 3f, pt.y * 3f)
                  }
                  canvas.drawPath(path, paint)
                }
              }

              val doodleFile = File(context.filesDir, "doodle_${System.currentTimeMillis()}.png")
              FileOutputStream(doodleFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
              }

              viewModel.timelineEngine.addOverlayClip(
                uri = doodleFile.absolutePath,
                name = "Drawing Doodle",
                isVideo = false,
                durationMs = 4000L,
                scale = 0.8f,
                posX = 0f,
                posY = 0f
              )
            }
            onDismiss()
          },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          colors = ButtonDefaults.filledTonalButtonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    // Mini Canvas Area for Drawing
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(140.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFF090B10))
        .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
        .pointerInput(selectedBrush, selectedColor, strokeWidth) {
          detectDragGestures(
            onDragStart = { offset ->
              currentPoints = listOf(offset)
              redoPaths.clear()
            },
            onDrag = { change, _ ->
              change.consume()
              currentPoints = currentPoints + change.position
            },
            onDragEnd = {
              if (currentPoints.size > 1) {
                paths.add(
                  DrawnPath(
                    points = currentPoints,
                    color = selectedColor,
                    strokeWidth = when (selectedBrush) {
                      BrushStyle.PENCIL -> strokeWidth * 0.5f
                      BrushStyle.MARKER -> strokeWidth
                      BrushStyle.HIGHLIGHTER -> strokeWidth * 1.8f
                      BrushStyle.NEON -> strokeWidth * 1.2f
                      BrushStyle.ERASER -> strokeWidth * 2f
                    },
                    isEraser = selectedBrush == BrushStyle.ERASER,
                    isNeon = selectedBrush == BrushStyle.NEON
                  )
                )
              }
              currentPoints = emptyList()
            }
          )
        }
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        // Draw existing paths
        for (p in paths) {
          if (p.points.size > 1) {
            val drawPath = androidx.compose.ui.graphics.Path().apply {
              moveTo(p.points.first().x, p.points.first().y)
              for (pt in p.points.drop(1)) {
                lineTo(pt.x, pt.y)
              }
            }

            if (p.isNeon) {
              // Neon Outer Glow
              drawPath(
                path = drawPath,
                color = p.color.copy(alpha = 0.35f),
                style = Stroke(width = p.strokeWidth * 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
              )
            }

            drawPath(
              path = drawPath,
              color = if (p.isEraser) Color(0xFF090B10) else p.color,
              style = Stroke(
                width = p.strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
              )
            )
          }
        }

        // Draw current actively dragged path
        if (currentPoints.size > 1) {
          val activePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(currentPoints.first().x, currentPoints.first().y)
            for (pt in currentPoints.drop(1)) {
              lineTo(pt.x, pt.y)
            }
          }
          if (selectedBrush == BrushStyle.NEON) {
            drawPath(
              path = activePath,
              color = selectedColor.copy(alpha = 0.35f),
              style = Stroke(width = strokeWidth * 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
          }
          drawPath(
            path = activePath,
            color = if (selectedBrush == BrushStyle.ERASER) Color(0xFF090B10) else selectedColor,
            style = Stroke(
              width = strokeWidth,
              cap = StrokeCap.Round,
              join = StrokeJoin.Round
            )
          )
        }
      }

      if (paths.isEmpty() && currentPoints.isEmpty()) {
        Text(
          text = "Draw anywhere in this box to create doodles",
          color = TextSecondary.copy(alpha = 0.5f),
          fontSize = 11.sp,
          modifier = Modifier.align(Alignment.Center)
        )
      }
    }

    // Brush Selection Row
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(BrushStyle.values()) { brush ->
        val isSelected = selectedBrush == brush
        FilterChip(
          selected = isSelected,
          onClick = { selectedBrush = brush },
          label = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(brush.iconEmoji, fontSize = 12.sp)
              Text(brush.displayName, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyanAccent,
            selectedLabelColor = Color.Black,
            containerColor = StudioSurfaceVariant,
            labelColor = TextPrimary
          )
        )
      }
    }

    // Colors Row & Size Slider
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.weight(1f)
      ) {
        items(palette) { color ->
          val isSelected = selectedColor == color
          Box(
            modifier = Modifier
              .size(24.dp)
              .clip(CircleShape)
              .background(color)
              .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else StudioBorder, CircleShape)
              .clickable { selectedColor = color }
          )
        }
      }

      Slider(
        value = strokeWidth,
        onValueChange = { strokeWidth = it },
        valueRange = 4f..36f,
        modifier = Modifier.width(100.dp),
        colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
      )
    }
  }
}
