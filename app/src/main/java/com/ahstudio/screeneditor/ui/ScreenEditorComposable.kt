package com.ahstudio.screeneditor.ui

import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahstudio.screeneditor.core.ScreenEditorController
import com.ahstudio.screeneditor.gesture.EditorRegions
import com.ahstudio.screeneditor.gesture.GestureOwner
import com.ahstudio.screeneditor.gesture.PointerSnapshot
import com.ahstudio.screeneditor.gesture.ScreenEditorGestureEngine
import com.ahstudio.screeneditor.gesture.TimelineHitTester
import com.ahstudio.screeneditor.interaction.Gizmo
import com.ahstudio.screeneditor.interaction.ScreenEditorInteractionEngine
import com.ahstudio.screeneditor.ports.TrimEdge
import com.ahstudio.screeneditor.transform.ScreenEditorTransformEngine
import com.ahstudio.screeneditor.transform.TransformSnapping
import kotlin.math.roundToInt

@Composable
fun ScreenEditorViewportContainer(
    controller: ScreenEditorController,
    modifier: Modifier = Modifier,
    videoPreviewContent: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val densityPx = density.density
    val state by controller.state.collectAsState()
    val canUndo by controller.undo.canUndo.collectAsState()
    val canRedo by controller.undo.canRedo.collectAsState()

    var containerWidth by remember { mutableStateOf(1080) }
    var containerHeight by remember { mutableStateOf(1920) }

    val regions = remember {
        EditorRegions(
            canvas = RectF(0f, 0f, containerWidth.toFloat(), containerHeight.toFloat()),
            timeline = RectF()
        )
    }

    val transformEngine = remember(controller.viewport) {
        ScreenEditorTransformEngine(controller.viewport)
    }

    val snapping = remember(controller.viewport) {
        TransformSnapping(controller.viewport)
    }

    val timelineHitTester = remember(controller.timelinePort) {
        object : TimelineHitTester {
            override fun hit(screenX: Float, screenY: Float, timelineRect: RectF): TimelineHitTester.Zone {
                val totalDurationUs = controller.timelinePort.durationUs.value
                if (totalDurationUs <= 0L || timelineRect.width() <= 0f) return TimelineHitTester.Zone.Empty

                val currentUs = controller.timelinePort.positionUs.value
                val playheadFrac = (currentUs.toFloat() / totalDurationUs.toFloat()).coerceIn(0f, 1f)
                val playheadX = timelineRect.left + playheadFrac * timelineRect.width()
                if (kotlin.math.abs(screenX - playheadX) <= 24f) {
                    return TimelineHitTester.Zone.Playhead
                }

                val touchFrac = ((screenX - timelineRect.left) / timelineRect.width()).coerceIn(0f, 1f)
                val touchUs = (touchFrac * totalDurationUs).toLong()

                val candidateClips = controller.timelinePort.clipsAt(touchUs)
                val hitClip = candidateClips.firstOrNull()
                if (hitClip != null) {
                    val clipStartFrac = (hitClip.startUs.toFloat() / totalDurationUs.toFloat()).coerceIn(0f, 1f)
                    val clipEndFrac = ((hitClip.startUs + hitClip.durationUs).toFloat() / totalDurationUs.toFloat()).coerceIn(0f, 1f)
                    val clipStartX = timelineRect.left + clipStartFrac * timelineRect.width()
                    val clipEndX = timelineRect.left + clipEndFrac * timelineRect.width()
                    val edgeThreshold = 18f

                    return when {
                        kotlin.math.abs(screenX - clipStartX) <= edgeThreshold ->
                            TimelineHitTester.Zone.ClipEdge(hitClip.id, TrimEdge.START)
                        kotlin.math.abs(screenX - clipEndX) <= edgeThreshold ->
                            TimelineHitTester.Zone.ClipEdge(hitClip.id, TrimEdge.END)
                        else ->
                            TimelineHitTester.Zone.ClipBody(hitClip.id)
                    }
                }

                return TimelineHitTester.Zone.Empty
            }
        }
    }

    val gestureEngine = remember(controller.viewport, timelineHitTester) {
        ScreenEditorGestureEngine(
            viewport = controller.viewport,
            regions = { regions },
            timelineHitTester = timelineHitTester
        )
    }

    val interactionEngine = remember(controller, gestureEngine) {
        ScreenEditorInteractionEngine(
            controller = controller,
            viewport = controller.viewport,
            transformEngine = transformEngine,
            snapping = snapping,
            gestureEngine = gestureEngine,
            timelineBridge = controller.timelineBridge,
            timelinePort = controller.timelinePort,
            densityPxPerDp = densityPx,
            regionsProvider = { regions }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    containerWidth = size.width
                    containerHeight = size.height
                    regions.canvas.set(0f, 0f, size.width.toFloat(), size.height.toFloat())
                    controller.viewport.onScreenBoundsChanged(size.width, size.height)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    if (state.selectedLayerId != null) {
                        // Two-finger / gesture on object
                        val selectedId = state.selectedLayerId!!
                        val currentT = controller.layers.transformOf(selectedId)
                        val size = controller.layers.sourceSizeOf(selectedId)
                        val crop = controller.layers.cropOf(selectedId)

                        val nextScale = (currentT.scaleX * zoom).coerceIn(0.05f, 20f)
                        val nextRot = currentT.rotationDeg + rotation
                        val nextTx = currentT.translationX + pan.x / controller.viewport.state.value.scale
                        val nextTy = currentT.translationY + pan.y / controller.viewport.state.value.scale

                        val nextT = currentT.copy(
                            scaleX = nextScale,
                            scaleY = nextScale,
                            rotationDeg = nextRot,
                            translationX = nextTx,
                            translationY = nextTy
                        )
                        val snapped = snapping.snap(nextT, size.width, size.height, crop, state.snappingEnabled).corrected
                        controller.updateTransformLive(selectedId, snapped)
                    } else {
                        // Viewport pan/zoom
                        controller.viewport.panBy(pan.x, pan.y)
                        if (zoom != 1f) {
                            controller.viewport.zoomAt(centroid.x, centroid.y, zoom)
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        controller.viewport.reset()
                    },
                    onTap = { offset ->
                        val ex = controller.viewport.screenToEditorX(offset.x)
                        val ey = controller.viewport.screenToEditorY(offset.y)
                        val hitId = Gizmo.hitLayerAt(
                            ex,
                            ey,
                            controller.layers.hitShapes(transformEngine),
                            controller.layers::isSelectable
                        )
                        controller.selectLayer(hitId)
                    }
                )
            }
    ) {
        // 1. Underlying Media / Video Preview
        videoPreviewContent()

        // 2. Interactive Overlay Canvas (Zero-GPU, pure high-precision vector overlays)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val vp = controller.viewport
            val vpScale = vp.state.value.scale

            // Draw Project Canvas Frame Boundary
            val frameL = vp.editorToScreenX(0f)
            val frameT = vp.editorToScreenY(0f)
            val frameR = vp.editorToScreenX(vp.editorWidth)
            val frameB = vp.editorToScreenY(vp.editorHeight)

            drawRect(
                color = Color(0x33FFFFFF),
                topLeft = Offset(frameL, frameT),
                size = Size(frameR - frameL, frameB - frameT),
                style = Stroke(width = 1.5f * densityPx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            )

            // Draw Active Selected Layer Gizmo & Bounding Box
            val selectedId = state.selectedLayerId
            if (selectedId != null && controller.layers.isSelectable(selectedId)) {
                val t = controller.layers.transformOf(selectedId)
                val size = controller.layers.sourceSizeOf(selectedId)
                val crop = controller.layers.cropOf(selectedId)
                val corners = Array(4) { PointF() }
                transformEngine.boundingBox(t, size.width, size.height, crop, corners)

                // Map corners to screen space
                val sc = Array(4) { i ->
                    Offset(
                        vp.editorToScreenX(corners[i].x),
                        vp.editorToScreenY(corners[i].y)
                    )
                }

                val path = Path().apply {
                    moveTo(sc[0].x, sc[0].y)
                    lineTo(sc[1].x, sc[1].y)
                    lineTo(sc[2].x, sc[2].y)
                    lineTo(sc[3].x, sc[3].y)
                    close()
                }

                // Bounding box border
                drawPath(
                    path = path,
                    color = Color(0xFF00E5FF),
                    style = Stroke(width = 2f * densityPx)
                )

                // Corner Handles
                val handleRadius = 6f * densityPx
                for (i in 0..3) {
                    drawCircle(
                        color = Color.White,
                        radius = handleRadius,
                        center = sc[i]
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = handleRadius,
                        center = sc[i],
                        style = Stroke(width = 2f * densityPx)
                    )
                }

                // Rotation Stalk and Handle
                val topMid = Offset((sc[0].x + sc[1].x) / 2f, (sc[0].y + sc[1].y) / 2f)
                val ux = sc[0].y - sc[1].y
                val uy = sc[1].x - sc[0].x
                val len = kotlin.math.hypot(ux, uy)
                if (len > 0f) {
                    val rotOffset = 36f * densityPx
                    val rotCenter = Offset(topMid.x + (ux / len) * rotOffset, topMid.y + (uy / len) * rotOffset)

                    // Stalk line
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = topMid,
                        end = rotCenter,
                        strokeWidth = 1.5f * densityPx
                    )

                    // Rotation circle handle
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 8f * densityPx,
                        center = rotCenter
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4f * densityPx,
                        center = rotCenter
                    )
                }
            }
        }

        // 3. Floating Modern HUD Controls (Fit, Zoom In/Out, Snapping, Undo, Redo, Info)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Color(0xCC1A1D24), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { controller.viewport.reset() },
                modifier = Modifier.size(36.dp).testTag("hud_fit_viewport")
            ) {
                Icon(Icons.Default.FitScreen, contentDescription = "Fit Viewport", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = { controller.viewport.zoomIn() },
                modifier = Modifier.size(36.dp).testTag("hud_zoom_in")
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = { controller.viewport.zoomOut() },
                modifier = Modifier.size(36.dp).testTag("hud_zoom_out")
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            VerticalDivider(modifier = Modifier.height(20.dp), color = Color(0x33FFFFFF))

            IconButton(
                onClick = { controller.setSnapping(!state.snappingEnabled) },
                modifier = Modifier.size(36.dp).testTag("hud_toggle_snapping")
            ) {
                Icon(
                    Icons.Default.CropRotate,
                    contentDescription = "Toggle Snapping",
                    tint = if (state.snappingEnabled) Color(0xFF00E5FF) else Color(0x66FFFFFF),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { controller.undo() },
                enabled = canUndo,
                modifier = Modifier.size(36.dp).testTag("hud_undo")
            ) {
                Icon(
                    Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) Color.White else Color(0x44FFFFFF),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { controller.redo() },
                enabled = canRedo,
                modifier = Modifier.size(36.dp).testTag("hud_redo")
            ) {
                Icon(
                    Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) Color.White else Color(0x44FFFFFF),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 4. Transform Status Indicator / Pill
        if (state.selectedLayerId != null) {
            val selectedId = state.selectedLayerId!!
            val t = controller.layers.transformOf(selectedId)
            Surface(
                color = Color(0xEE1E222B),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4400E5FF)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scale: ${(t.scaleX * 100).roundToInt()}%",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Rot: ${t.rotationDeg.roundToInt()}°",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Pos: (${t.translationX.roundToInt()}, ${t.translationY.roundToInt()})",
                        color = Color(0xFFB0B7C3),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
