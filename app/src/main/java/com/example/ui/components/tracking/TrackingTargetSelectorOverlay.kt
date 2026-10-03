package com.example.ui.components.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ai.NormalizedRect
import kotlin.math.roundToInt

@Composable
fun TrackingTargetSelectorOverlay(
    videoWidth: Int = 1080,
    videoHeight: Int = 1920,
    onConfirmTarget: (NormalizedRect) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var boxOffset by remember { mutableStateOf(Offset(100f, 200f)) }
    val boxWidthPx = 220f
    val boxHeightPx = 220f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.40f))
    ) {
        val screenW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val screenH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // Central Bounding Target Box with Drag Handlers
        Box(
            modifier = Modifier
                .offset { IntOffset(boxOffset.x.roundToInt(), boxOffset.y.roundToInt()) }
                .size((boxWidthPx / (LocalDensity.current.density)).dp, (boxHeightPx / (LocalDensity.current.density)).dp)
                .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                .pointerInput(screenW, screenH) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        boxOffset = Offset(
                            (boxOffset.x + dragAmount.x).coerceIn(0f, screenW - boxWidthPx),
                            (boxOffset.y + dragAmount.y).coerceIn(0f, screenH - boxHeightPx)
                        )
                    }
                }
        ) {
            // Target Crosshair
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawLine(Color(0xFF00E5FF), Offset(cx - 20f, cy), Offset(cx + 20f, cy), strokeWidth = 2.5f)
                drawLine(Color(0xFF00E5FF), Offset(cx, cy - 20f), Offset(cx, cy + 20f), strokeWidth = 2.5f)
            }
        }

        // Bottom Action Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
            ) {
                Text("Cancel", color = Color.White)
            }

            Button(
                onClick = {
                    val normalized = NormalizedRect(
                        left = (boxOffset.x / screenW).coerceIn(0f, 1f),
                        top = (boxOffset.y / screenH).coerceIn(0f, 1f),
                        right = ((boxOffset.x + boxWidthPx) / screenW).coerceIn(0f, 1f),
                        bottom = ((boxOffset.y + boxHeightPx) / screenH).coerceIn(0f, 1f)
                    )
                    onConfirmTarget(normalized)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("Start Tracking", color = Color.Black, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
}
