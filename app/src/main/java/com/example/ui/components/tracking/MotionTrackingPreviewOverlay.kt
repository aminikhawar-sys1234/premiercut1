package com.example.ui.components.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ai.MotionTrackingUiState
import com.example.engine.ai.NormalizedRect
import com.example.engine.ai.TrackingCategory
import kotlin.math.roundToInt

private val EmeraldAccent = Color(0xFF00D1B2)
private val CyanBlue = Color(0xFF00B4D8)
private val BoxBorder = Color(0xFF00E5FF)

/**
 * Lightweight, non-intrusive interactive tracking target overlay.
 * Rendered directly over the video preview without shrinking or altering video Surface dimensions.
 */
@Composable
fun MotionTrackingPreviewOverlay(
    uiState: MotionTrackingUiState,
    currentPosMs: Long,
    clipStartMs: Long,
    clipDurationMs: Long,
    onUpdateRegion: (NormalizedRect) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current

        val targetRegion = uiState.targetRegion
        val leftPx = targetRegion.left * w
        val topPx = targetRegion.top * h
        val widthPx = (targetRegion.width * w).coerceAtLeast(40f)
        val heightPx = (targetRegion.height * h).coerceAtLeast(40f)

        // Draw Motion Path Trajectory if active result exists
        val activeResult = uiState.activeResult
        if (activeResult != null && activeResult.keyframes.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                var first = true
                for (kf in activeResult.keyframes) {
                    val kfX = kf.centerX * w
                    val kfY = kf.centerY * h
                    if (first) {
                        path.moveTo(kfX, kfY)
                        first = false
                    } else {
                        path.lineTo(kfX, kfY)
                    }
                    drawCircle(
                        color = CyanBlue.copy(alpha = 0.7f),
                        radius = 3.dp.toPx(),
                        center = Offset(kfX, kfY)
                    )
                }
                drawPath(
                    path = path,
                    color = EmeraldAccent.copy(alpha = 0.85f),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Current tracking keyframe indicator
                val currentUs = currentPosMs * 1000L
                val currentKf = activeResult.keyframes.minByOrNull {
                    kotlin.math.abs(it.timestampUs - currentUs)
                }
                if (currentKf != null) {
                    val curX = currentKf.centerX * w
                    val curY = currentKf.centerY * h
                    drawCircle(
                        color = Color.Yellow,
                        radius = 6.dp.toPx(),
                        center = Offset(curX, curY),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Tap on preview to set target center
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(w, h) {
                    detectTapGestures { tapOffset ->
                        val halfW = targetRegion.width / 2f
                        val halfH = targetRegion.height / 2f
                        val newNormX = tapOffset.x / w
                        val newNormY = tapOffset.y / h
                        val newRect = NormalizedRect(
                            left = (newNormX - halfW).coerceIn(0f, 1f - targetRegion.width),
                            top = (newNormY - halfH).coerceIn(0f, 1f - targetRegion.height),
                            right = (newNormX + halfW).coerceIn(targetRegion.width, 1f),
                            bottom = (newNormY + halfH).coerceIn(targetRegion.height, 1f)
                        )
                        onUpdateRegion(newRect)
                    }
                }
        )

        // Draggable Target Bounding Box
        var dragOffset by remember(targetRegion) { mutableStateOf(Offset(leftPx, topPx)) }

        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .size((widthPx / density.density).dp, (heightPx / density.density).dp)
                .pointerInput(w, h, widthPx, heightPx) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = (dragOffset.x + dragAmount.x).coerceIn(0f, w - widthPx)
                            val newY = (dragOffset.y + dragAmount.y).coerceIn(0f, h - heightPx)
                            dragOffset = Offset(newX, newY)
                        },
                        onDragEnd = {
                            val newNorm = NormalizedRect(
                                left = (dragOffset.x / w).coerceIn(0f, 1f),
                                top = (dragOffset.y / h).coerceIn(0f, 1f),
                                right = ((dragOffset.x + widthPx) / w).coerceIn(0f, 1f),
                                bottom = ((dragOffset.y + heightPx) / h).coerceIn(0f, 1f)
                            )
                            onUpdateRegion(newNorm)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxW = size.width
                val boxH = size.height

                // Bounding rectangle
                drawRoundRect(
                    color = BoxBorder,
                    size = Size(boxW, boxH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Corner brackets
                val cornerLen = 14.dp.toPx()
                val strokeW = 3.dp.toPx()

                // Top-Left
                drawLine(EmeraldAccent, Offset(0f, 0f), Offset(cornerLen, 0f), strokeW)
                drawLine(EmeraldAccent, Offset(0f, 0f), Offset(0f, cornerLen), strokeW)
                // Top-Right
                drawLine(EmeraldAccent, Offset(boxW, 0f), Offset(boxW - cornerLen, 0f), strokeW)
                drawLine(EmeraldAccent, Offset(boxW, 0f), Offset(boxW, cornerLen), strokeW)
                // Bottom-Left
                drawLine(EmeraldAccent, Offset(0f, boxH), Offset(cornerLen, boxH), strokeW)
                drawLine(EmeraldAccent, Offset(0f, boxH), Offset(0f, boxH - cornerLen), strokeW)
                // Bottom-Right
                drawLine(EmeraldAccent, Offset(boxW, boxH), Offset(boxW - cornerLen, boxH), strokeW)
                drawLine(EmeraldAccent, Offset(boxW, boxH), Offset(boxW, boxH - cornerLen), strokeW)

                // Center crosshair
                val cx = boxW / 2f
                val cy = boxH / 2f
                val crossLen = 8.dp.toPx()
                drawLine(EmeraldAccent, Offset(cx - crossLen, cy), Offset(cx + crossLen, cy), 1.5.dp.toPx())
                drawLine(EmeraldAccent, Offset(cx, cy - crossLen), Offset(cx, cy + crossLen), 1.5.dp.toPx())
            }

            // Target Label
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = (-20).dp)
            ) {
                Text(
                    text = "${uiState.activeCategory.iconEmoji} ${uiState.activeCategory.title} Target",
                    color = EmeraldAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
