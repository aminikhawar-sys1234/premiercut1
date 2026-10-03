package com.example.ui.components.ar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import com.ahstudio.face.core.FaceLandmarkType
import com.ahstudio.face.overlay.FaceAnchor
import com.example.engine.ai.MotionTrackingUiState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArOverlayPreviewOverlay(
    activeFilter: ArFilterItem?,
    showMeshGrid: Boolean,
    scaleFactor: Float,
    offsetYFactor: Float,
    opacity: Float,
    customColor: Color?,
    uiState: MotionTrackingUiState,
    modifier: Modifier = Modifier
) {
    if (activeFilter == null && !showMeshGrid) return

    val faceOverlayManager = remember { FaceOverlayManager() }
    DisposableEffect(faceOverlayManager) {
        onDispose {
            faceOverlayManager.close()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val activeColor = customColor ?: activeFilter?.primaryColor ?: Color(0xFF00E5FF)
        val secondaryColor = activeFilter?.secondaryColor ?: Color(0xFF00D1B2)

        val activeMeshes = remember(uiState.activeResult, uiState.activeSession, width, height) {
            when {
                uiState.activeResult != null -> {
                    listOf(faceOverlayManager.createMeshFromTrackingResult(uiState.activeResult, width, height))
                }
                uiState.activeSession != null -> {
                    listOf(faceOverlayManager.createMeshFromTrackingSession(uiState.activeSession, width, height))
                }
                else -> emptyList()
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            faceOverlayManager.drawFaceMeshOverlay(
                drawScope = this,
                canvasWidth = width,
                canvasHeight = height,
                detectedMeshes = activeMeshes,
                activeFilter = activeFilter,
                showMeshGrid = showMeshGrid,
                primaryColor = activeColor,
                secondaryColor = secondaryColor,
                opacity = opacity,
                scaleFactor = scaleFactor,
                offsetYFactor = offsetYFactor
            )
        }
    }
}

private fun DrawScope.drawFaceMeshWireframe(
    centerX: Float,
    centerY: Float,
    w: Float,
    h: Float,
    color: Color,
    secondaryColor: Color
) {
    val halfW = w / 2f
    val halfH = h / 2f

    // Face Oval Contour Points
    val ovalPoints = listOf(
        Offset(centerX, centerY - halfH),                     // Forehead top
        Offset(centerX + halfW * 0.65f, centerY - halfH * 0.8f),
        Offset(centerX + halfW, centerY - halfH * 0.2f),      // Right cheekbone
        Offset(centerX + halfW * 0.85f, centerY + halfH * 0.5f),// Right jaw
        Offset(centerX + halfW * 0.4f, centerY + halfH * 0.9f), // Right chin
        Offset(centerX, centerY + halfH),                     // Chin bottom
        Offset(centerX - halfW * 0.4f, centerY + halfH * 0.9f), // Left chin
        Offset(centerX - halfW * 0.85f, centerY + halfH * 0.5f),// Left jaw
        Offset(centerX - halfW, centerY - halfH * 0.2f),      // Left cheekbone
        Offset(centerX - halfW * 0.65f, centerY - halfH * 0.8f)
    )

    // Inner facial landmarks
    val leftEye = Offset(centerX - halfW * 0.32f, centerY - halfH * 0.18f)
    val rightEye = Offset(centerX + halfW * 0.32f, centerY - halfH * 0.18f)
    val noseTip = Offset(centerX, centerY + halfH * 0.12f)
    val mouthLeft = Offset(centerX - halfW * 0.25f, centerY + halfH * 0.52f)
    val mouthRight = Offset(centerX + halfW * 0.25f, centerY + halfH * 0.52f)
    val mouthBottom = Offset(centerX, centerY + halfH * 0.62f)

    // 1. Draw Mesh Contour Wireframe Polygon Lines
    val meshPath = Path().apply {
        if (ovalPoints.isNotEmpty()) {
            moveTo(ovalPoints[0].x, ovalPoints[0].y)
            for (i in 1 until ovalPoints.size) {
                lineTo(ovalPoints[i].x, ovalPoints[i].y)
            }
            close()
        }
        // Connect Nose to Eyes and Mouth
        moveTo(leftEye.x, leftEye.y)
        lineTo(noseTip.x, noseTip.y)
        lineTo(rightEye.x, rightEye.y)
        lineTo(leftEye.x, leftEye.y)

        moveTo(noseTip.x, noseTip.y)
        lineTo(mouthLeft.x, mouthLeft.y)
        lineTo(mouthBottom.x, mouthBottom.y)
        lineTo(mouthRight.x, mouthRight.y)
        lineTo(noseTip.x, noseTip.y)
    }

    drawPath(
        path = meshPath,
        color = color,
        style = Stroke(width = 1.8f)
    )

    // 2. Draw Eye Circles
    drawCircle(secondaryColor, radius = halfW * 0.12f, center = leftEye, style = Stroke(width = 1.5f))
    drawCircle(secondaryColor, radius = halfW * 0.12f, center = rightEye, style = Stroke(width = 1.5f))

    // 3. Draw Glowing Landmark Node Dots
    val allLandmarks = ovalPoints + listOf(leftEye, rightEye, noseTip, mouthLeft, mouthRight, mouthBottom)
    for (pt in allLandmarks) {
        drawCircle(color = secondaryColor, radius = 3f, center = pt)
    }
}

private fun DrawScope.drawCyberVisor(
    centerX: Float,
    centerY: Float,
    w: Float,
    h: Float,
    primary: Color,
    secondary: Color
) {
    val halfW = w / 2f
    val halfH = h / 2f

    // Visor Shield Body
    val visorPath = Path().apply {
        moveTo(centerX - halfW, centerY - halfH * 0.4f)
        lineTo(centerX + halfW, centerY - halfH * 0.4f)
        lineTo(centerX + halfW * 0.85f, centerY + halfH)
        lineTo(centerX - halfW * 0.85f, centerY + halfH)
        close()
    }

    // Semi-transparent visor fill
    drawPath(
        path = visorPath,
        brush = Brush.verticalGradient(
            colors = listOf(primary.copy(alpha = 0.4f), secondary.copy(alpha = 0.75f))
        )
    )

    // Glowing Neon Border
    drawPath(
        path = visorPath,
        color = primary,
        style = Stroke(width = 3f)
    )

    // Center Glint & Tech Lines
    drawLine(
        color = Color.White.copy(alpha = 0.8f),
        start = Offset(centerX - halfW * 0.6f, centerY - halfH * 0.1f),
        end = Offset(centerX + halfW * 0.6f, centerY - halfH * 0.1f),
        strokeWidth = 2f
    )
}

private fun DrawScope.drawNeonCrown(
    centerX: Float,
    centerY: Float,
    w: Float,
    h: Float,
    primary: Color,
    secondary: Color
) {
    val halfW = w / 2f
    val crownPath = Path().apply {
        moveTo(centerX - halfW, centerY + h)
        lineTo(centerX - halfW * 0.9f, centerY)                 // Left peak
        lineTo(centerX - halfW * 0.45f, centerY + h * 0.5f)
        lineTo(centerX, centerY - h * 0.3f)                     // Center high peak
        lineTo(centerX + halfW * 0.45f, centerY + h * 0.5f)
        lineTo(centerX + halfW * 0.9f, centerY)                 // Right peak
        lineTo(centerX + halfW, centerY + h)
        close()
    }

    drawPath(
        path = crownPath,
        brush = Brush.verticalGradient(listOf(primary, secondary))
    )
    drawPath(
        path = crownPath,
        color = Color.White,
        style = Stroke(width = 2.5f)
    )

    // Crown Gem Jewels
    drawCircle(Color.White, radius = 5f, center = Offset(centerX, centerY - h * 0.3f))
    drawCircle(secondary, radius = 4f, center = Offset(centerX - halfW * 0.9f, centerY))
    drawCircle(secondary, radius = 4f, center = Offset(centerX + halfW * 0.9f, centerY))
}

private fun DrawScope.drawMatrixShades(
    centerX: Float,
    centerY: Float,
    w: Float,
    h: Float,
    primary: Color,
    secondary: Color
) {
    val halfW = w / 2f
    val halfH = h / 2f

    // Left Lens
    drawRoundRect(
        color = primary,
        topLeft = Offset(centerX - halfW, centerY - halfH),
        size = Size(halfW * 0.88f, h),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = secondary,
        topLeft = Offset(centerX - halfW, centerY - halfH),
        size = Size(halfW * 0.88f, h),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 2.5f)
    )

    // Right Lens
    drawRoundRect(
        color = primary,
        topLeft = Offset(centerX + halfW * 0.12f, centerY - halfH),
        size = Size(halfW * 0.88f, h),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = secondary,
        topLeft = Offset(centerX + halfW * 0.12f, centerY - halfH),
        size = Size(halfW * 0.88f, h),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 2.5f)
    )

    // Nose Bridge
    drawLine(
        color = secondary,
        start = Offset(centerX - halfW * 0.12f, centerY - halfH * 0.2f),
        end = Offset(centerX + halfW * 0.12f, centerY - halfH * 0.2f),
        strokeWidth = 3f
    )
}

private fun DrawScope.drawHoloGoggles(
    centerX: Float,
    centerY: Float,
    w: Float,
    h: Float,
    primary: Color
) {
    val halfW = w / 2f
    drawRoundRect(
        color = primary.copy(alpha = 0.3f),
        topLeft = Offset(centerX - halfW, centerY - h / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(16f, 16f)
    )
    drawRoundRect(
        color = primary,
        topLeft = Offset(centerX - halfW, centerY - h / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(16f, 16f),
        style = Stroke(width = 3f)
    )
    drawCircle(primary, radius = h * 0.4f, center = Offset(centerX - halfW * 0.5f, centerY), style = Stroke(2f))
    drawCircle(primary, radius = h * 0.4f, center = Offset(centerX + halfW * 0.5f, centerY), style = Stroke(2f))
}

private fun DrawScope.drawCatWhiskersAndEars(
    centerX: Float,
    centerY: Float,
    w: Float,
    h: Float,
    primary: Color,
    secondary: Color
) {
    val halfW = w / 2f
    val halfH = h / 2f

    // 1. Left & Right Ears above head
    val leftEar = Path().apply {
        moveTo(centerX - halfW * 0.8f, centerY - halfH * 0.6f)
        lineTo(centerX - halfW * 0.95f, centerY - halfH * 1.3f)
        lineTo(centerX - halfW * 0.35f, centerY - halfH * 0.8f)
        close()
    }
    val rightEar = Path().apply {
        moveTo(centerX + halfW * 0.8f, centerY - halfH * 0.6f)
        lineTo(centerX + halfW * 0.95f, centerY - halfH * 1.3f)
        lineTo(centerX + halfW * 0.35f, centerY - halfH * 0.8f)
        close()
    }
    drawPath(leftEar, primary)
    drawPath(rightEar, primary)

    // 2. Nose Pink Heart
    val noseCenter = Offset(centerX, centerY + halfH * 0.15f)
    drawCircle(secondary, radius = 8f, center = noseCenter)

    // 3. Left Whiskers
    val wStartL = Offset(centerX - halfW * 0.35f, centerY + halfH * 0.2f)
    drawLine(primary, wStartL, Offset(centerX - halfW * 1.1f, centerY + halfH * 0.05f), 2.5f)
    drawLine(primary, wStartL, Offset(centerX - halfW * 1.15f, centerY + halfH * 0.2f), 2.5f)
    drawLine(primary, wStartL, Offset(centerX - halfW * 1.05f, centerY + halfH * 0.35f), 2.5f)

    // 4. Right Whiskers
    val wStartR = Offset(centerX + halfW * 0.35f, centerY + halfH * 0.2f)
    drawLine(primary, wStartR, Offset(centerX + halfW * 1.1f, centerY + halfH * 0.05f), 2.5f)
    drawLine(primary, wStartR, Offset(centerX + halfW * 1.15f, centerY + halfH * 0.2f), 2.5f)
    drawLine(primary, wStartR, Offset(centerX + halfW * 1.05f, centerY + halfH * 0.35f), 2.5f)
}

private fun DrawScope.drawBunnyEars(
    centerX: Float,
    centerY: Float,
    w: Float,
    h: Float,
    primary: Color
) {
    val halfW = w / 2f
    drawRoundRect(primary, Offset(centerX - halfW * 0.5f, centerY - h), Size(halfW * 0.35f, h), CornerRadius(20f, 20f))
    drawRoundRect(primary, Offset(centerX + halfW * 0.15f, centerY - h), Size(halfW * 0.35f, h), CornerRadius(20f, 20f))
}

private fun DrawScope.drawTechHudOverlay(
    centerX: Float,
    centerY: Float,
    w: Float,
    h: Float,
    primary: Color,
    secondary: Color
) {
    val halfW = w / 2f
    val halfH = h / 2f

    // Outer Target Circle
    drawCircle(primary, radius = halfW, center = Offset(centerX, centerY), style = Stroke(width = 2f))
    drawCircle(secondary, radius = halfW * 0.7f, center = Offset(centerX, centerY), style = Stroke(width = 1.5f))

    // Crosshair Lines
    drawLine(primary, Offset(centerX - halfW * 1.1f, centerY), Offset(centerX + halfW * 1.1f, centerY), 2f)
    drawLine(primary, Offset(centerX, centerY - halfH * 1.1f), Offset(centerX, centerY + halfH * 1.1f), 2f)
}

private fun DrawScope.drawAngelHalo(
    centerX: Float,
    centerY: Float,
    w: Float,
    h: Float,
    primary: Color
) {
    drawOval(
        color = primary,
        topLeft = Offset(centerX - w / 2f, centerY - h / 2f),
        size = Size(w, h),
        style = Stroke(width = 6f)
    )
}

private fun DrawScope.drawDevilHorns(
    centerX: Float,
    centerY: Float,
    w: Float,
    h: Float,
    primary: Color
) {
    val halfW = w / 2f
    val leftHorn = Path().apply {
        moveTo(centerX - halfW * 0.5f, centerY + h)
        quadraticTo(centerX - halfW * 0.9f, centerY + h * 0.3f, centerX - halfW, centerY)
        quadraticTo(centerX - halfW * 0.6f, centerY + h * 0.5f, centerX - halfW * 0.2f, centerY + h)
        close()
    }
    val rightHorn = Path().apply {
        moveTo(centerX + halfW * 0.5f, centerY + h)
        quadraticTo(centerX + halfW * 0.9f, centerY + h * 0.3f, centerX + halfW, centerY)
        quadraticTo(centerX + halfW * 0.6f, centerY + h * 0.5f, centerX + halfW * 0.2f, centerY + h)
        close()
    }

    drawPath(leftHorn, primary)
    drawPath(rightHorn, primary)
}

private fun DrawScope.drawVenetianMask(
    centerX: Float,
    centerY: Float,
    w: Float,
    h: Float,
    primary: Color,
    secondary: Color
) {
    val halfW = w / 2f
    drawRoundRect(primary, Offset(centerX - halfW, centerY - h / 2f), Size(w, h), CornerRadius(12f, 12f))
    drawRoundRect(secondary, Offset(centerX - halfW, centerY - h / 2f), Size(w, h), CornerRadius(12f, 12f), style = Stroke(3f))
    drawCircle(Color.Black, radius = h * 0.3f, center = Offset(centerX - halfW * 0.45f, centerY))
    drawCircle(Color.Black, radius = h * 0.3f, center = Offset(centerX + halfW * 0.45f, centerY))
}

private fun DrawScope.drawPrivacyMosaic(
    centerX: Float,
    centerY: Float,
    w: Float,
    h: Float,
    primary: Color
) {
    val halfW = w / 2f
    val halfH = h / 2f
    val rows = 8
    val cols = 8
    val cellW = w / cols
    val cellH = h / rows

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val alphaVal = if ((r + c) % 2 == 0) 0.85f else 0.65f
            drawRect(
                color = primary.copy(alpha = alphaVal),
                topLeft = Offset(centerX - halfW + c * cellW, centerY - halfH + r * cellH),
                size = Size(cellW, cellH)
            )
        }
    }
}
