package com.example.ui.components.ar

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.engine.ai.TrackingResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.common.Triangle
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetector
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.google.mlkit.vision.facemesh.FaceMeshPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ProcessedFaceMesh(
    val boundingBox: RectF,
    val allPoints: List<FaceMeshPoint> = emptyList(),
    val triangles: List<Triangle<FaceMeshPoint>> = emptyList(),
    val leftEyePoints: List<FaceMeshPoint> = emptyList(),
    val rightEyePoints: List<FaceMeshPoint> = emptyList(),
    val leftEyebrowPoints: List<FaceMeshPoint> = emptyList(),
    val rightEyebrowPoints: List<FaceMeshPoint> = emptyList(),
    val upperLipPoints: List<FaceMeshPoint> = emptyList(),
    val lowerLipPoints: List<FaceMeshPoint> = emptyList(),
    val noseBridgePoints: List<FaceMeshPoint> = emptyList(),
    val faceOvalPoints: List<FaceMeshPoint> = emptyList(),
    val imageWidth: Float = 1f,
    val imageHeight: Float = 1f,
    val rotationZ: Float = 0f
) {
    val centerX: Float get() = boundingBox.centerX()
    val centerY: Float get() = boundingBox.centerY()
    val width: Float get() = boundingBox.width().coerceAtLeast(1f)
    val height: Float get() = boundingBox.height().coerceAtLeast(1f)
}

/**
 * Manages ML Kit FaceMeshDetector initialization, frame processing,
 * and real-time augmented reality mesh drawing over video preview surfaces.
 */
class FaceOverlayManager : AutoCloseable {

    private val detectorOptions = FaceMeshDetectorOptions.Builder()
        .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
        .build()

    private val detector: FaceMeshDetector by lazy {
        FaceMeshDetection.getClient(detectorOptions)
    }

    /**
     * Asynchronously detects 3D face mesh contours and 468 facial points from a frame bitmap.
     */
    suspend fun processFrame(bitmap: Bitmap): List<ProcessedFaceMesh> = withContext(Dispatchers.Default) {
        return@withContext try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faceMeshes = detector.process(inputImage).await()
            mapFaceMeshes(faceMeshes, bitmap.width.toFloat(), bitmap.height.toFloat())
        } catch (t: Throwable) {
            android.util.Log.e("FaceOverlayManager", "Error running ML Kit face mesh detection", t)
            emptyList()
        }
    }

    /**
     * Creates a ProcessedFaceMesh representation from a real-time motion tracking result.
     */
    fun createMeshFromTrackingResult(
        result: TrackingResult,
        frameWidth: Float,
        frameHeight: Float
    ): ProcessedFaceMesh {
        val lastKf = result.keyframes.lastOrNull()
        val cx = (lastKf?.centerX ?: 0.5f) * frameWidth
        val cy = (lastKf?.centerY ?: 0.45f) * frameHeight
        val w = frameWidth * 0.35f * (lastKf?.scaleX ?: 1f)
        val h = frameHeight * 0.35f * (lastKf?.scaleY ?: 1f)
        val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        return ProcessedFaceMesh(
            boundingBox = rect,
            imageWidth = frameWidth.coerceAtLeast(1f),
            imageHeight = frameHeight.coerceAtLeast(1f),
            rotationZ = lastKf?.rotationDeg ?: 0f
        )
    }

    /**
     * Creates a ProcessedFaceMesh representation from an active tracking session.
     */
    fun createMeshFromTrackingSession(
        session: com.example.engine.ai.TrackingSession,
        frameWidth: Float,
        frameHeight: Float
    ): ProcessedFaceMesh {
        val rect = session.boundingBox.toRectF(frameWidth, frameHeight)
        return ProcessedFaceMesh(
            boundingBox = rect,
            imageWidth = frameWidth.coerceAtLeast(1f),
            imageHeight = frameHeight.coerceAtLeast(1f),
            rotationZ = session.rotation
        )
    }

    /**
     * Maps raw ML Kit FaceMesh objects into structured ProcessedFaceMesh domain instances.
     */
    private fun mapFaceMeshes(
        meshes: List<FaceMesh>,
        imageWidth: Float,
        imageHeight: Float
    ): List<ProcessedFaceMesh> {
        return meshes.map { mesh ->
            val bounds = RectF(mesh.boundingBox)
            val triangles = mesh.allTriangles as? List<Triangle<FaceMeshPoint>> ?: emptyList()
            val allPoints = mesh.allPoints

            val leftEye = mesh.getPoints(FaceMesh.LEFT_EYE)
            val rightEye = mesh.getPoints(FaceMesh.RIGHT_EYE)
            val leftEyebrow = mesh.getPoints(FaceMesh.LEFT_EYEBROW_TOP) + mesh.getPoints(FaceMesh.LEFT_EYEBROW_BOTTOM)
            val rightEyebrow = mesh.getPoints(FaceMesh.RIGHT_EYEBROW_TOP) + mesh.getPoints(FaceMesh.RIGHT_EYEBROW_BOTTOM)
            val upperLip = mesh.getPoints(FaceMesh.UPPER_LIP_TOP) + mesh.getPoints(FaceMesh.UPPER_LIP_BOTTOM)
            val lowerLip = mesh.getPoints(FaceMesh.LOWER_LIP_TOP) + mesh.getPoints(FaceMesh.LOWER_LIP_BOTTOM)
            val noseBridge = mesh.getPoints(FaceMesh.NOSE_BRIDGE)
            val faceOval = mesh.getPoints(FaceMesh.FACE_OVAL)

            ProcessedFaceMesh(
                boundingBox = bounds,
                allPoints = allPoints,
                triangles = triangles,
                leftEyePoints = leftEye,
                rightEyePoints = rightEye,
                leftEyebrowPoints = leftEyebrow,
                rightEyebrowPoints = rightEyebrow,
                upperLipPoints = upperLip,
                lowerLipPoints = lowerLip,
                noseBridgePoints = noseBridge,
                faceOvalPoints = faceOval,
                imageWidth = imageWidth.coerceAtLeast(1f),
                imageHeight = imageHeight.coerceAtLeast(1f)
            )
        }
    }

    /**
     * Renders 3D augmented reality mesh wireframes and AR filter graphics directly
     * onto detected faces in the video preview Canvas.
     */
    fun drawFaceMeshOverlay(
        drawScope: DrawScope,
        canvasWidth: Float,
        canvasHeight: Float,
        detectedMeshes: List<ProcessedFaceMesh>,
        activeFilter: ArFilterItem?,
        showMeshGrid: Boolean,
        primaryColor: Color = Color(0xFF00E5FF),
        secondaryColor: Color = Color(0xFF00D1B2),
        opacity: Float = 1.0f,
        scaleFactor: Float = 1.0f,
        offsetYFactor: Float = 0.0f
    ) {
        val alpha = opacity.coerceIn(0.1f, 1.0f)
        val mainColor = primaryColor.copy(alpha = alpha)
        val secColor = secondaryColor.copy(alpha = alpha * 0.75f)

        if (detectedMeshes.isEmpty()) {
            // Fallback default mesh rendering if no frame faces detected yet
            val defaultCenterX = canvasWidth * 0.5f
            val defaultCenterY = (canvasHeight * 0.45f + offsetYFactor * canvasHeight)
            val defaultW = canvasWidth * 0.35f * scaleFactor
            val defaultH = canvasHeight * 0.35f * scaleFactor

            if (showMeshGrid || activeFilter?.isMeshOverlay == true) {
                drawDefaultMeshGrid(drawScope, defaultCenterX, defaultCenterY, defaultW, defaultH, mainColor, secColor)
            }
            if (activeFilter != null) {
                drawArFilterOverlay(drawScope, activeFilter, defaultCenterX, defaultCenterY, defaultW, defaultH, mainColor, secColor)
            }
            return
        }

        for (mesh in detectedMeshes) {
            val normCx = if (mesh.imageWidth > 1f) mesh.centerX / mesh.imageWidth else mesh.centerX
            val normCy = if (mesh.imageHeight > 1f) mesh.centerY / mesh.imageHeight else mesh.centerY
            val normW = if (mesh.imageWidth > 1f) mesh.width / mesh.imageWidth else mesh.width
            val normH = if (mesh.imageHeight > 1f) mesh.height / mesh.imageHeight else mesh.height

            val cx = normCx * canvasWidth
            val cy = (normCy + offsetYFactor) * canvasHeight
            val fw = normW * canvasWidth * scaleFactor
            val fh = normH * canvasHeight * scaleFactor

            drawScope.rotate(mesh.rotationZ, pivot = Offset(cx, cy)) {
                // 1. DRAW ML KIT 3D FACE MESH TRIANGLE WIREFRAME
                if (showMeshGrid || activeFilter?.isMeshOverlay == true) {
                    if (mesh.triangles.isNotEmpty()) {
                        for (t in mesh.triangles) {
                            val points = t.allPoints
                            if (points.size == 3) {
                                val p1 = Offset(
                                    (points[0].position.x / mesh.imageWidth) * canvasWidth,
                                    (points[0].position.y / mesh.imageHeight) * canvasHeight
                                )
                                val p2 = Offset(
                                    (points[1].position.x / mesh.imageWidth) * canvasWidth,
                                    (points[1].position.y / mesh.imageHeight) * canvasHeight
                                )
                                val p3 = Offset(
                                    (points[2].position.x / mesh.imageWidth) * canvasWidth,
                                    (points[2].position.y / mesh.imageHeight) * canvasHeight
                                )

                                drawLine(mainColor, p1, p2, strokeWidth = 1.2f)
                                drawLine(mainColor, p2, p3, strokeWidth = 1.2f)
                                drawLine(mainColor, p3, p1, strokeWidth = 1.2f)
                            }
                        }
                    } else {
                        drawDefaultMeshGrid(this, cx, cy, fw, fh, mainColor, secColor)
                    }

                    // Draw Glowing Landmark Nodes
                    for (pt in mesh.faceOvalPoints + mesh.leftEyePoints + mesh.rightEyePoints + mesh.noseBridgePoints) {
                        val nodePx = Offset(
                            (pt.position.x / mesh.imageWidth) * canvasWidth,
                            (pt.position.y / mesh.imageHeight) * canvasHeight
                        )
                        drawCircle(secColor, radius = 2.5f, center = nodePx)
                    }
                }

                // 2. DRAW AUGMENTED REALITY FILTER OVERLAY
                if (activeFilter != null) {
                    drawArFilterOverlay(this, activeFilter, cx, cy, fw, fh, mainColor, secColor)
                }
            }
        }
    }

    private fun drawDefaultMeshGrid(
        drawScope: DrawScope,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        mainColor: Color,
        secColor: Color
    ) {
        val halfW = w / 2f
        val halfH = h / 2f

        val ovalPath = Path().apply {
            moveTo(cx, cy - halfH)
            lineTo(cx + halfW * 0.7f, cy - halfH * 0.7f)
            lineTo(cx + halfW, cy - halfH * 0.1f)
            lineTo(cx + halfW * 0.8f, cy + halfH * 0.6f)
            lineTo(cx, cy + halfH)
            lineTo(cx - halfW * 0.8f, cy + halfH * 0.6f)
            lineTo(cx - halfW, cy - halfH * 0.1f)
            lineTo(cx - halfW * 0.7f, cy - halfH * 0.7f)
            close()
        }

        drawScope.drawPath(ovalPath, color = mainColor, style = Stroke(width = 1.8f))

        // Inner Feature Connectors
        val leftEye = Offset(cx - halfW * 0.3f, cy - halfH * 0.2f)
        val rightEye = Offset(cx + halfW * 0.3f, cy - halfH * 0.2f)
        val nose = Offset(cx, cy + halfH * 0.1f)
        val mouth = Offset(cx, cy + halfH * 0.55f)

        drawScope.drawLine(mainColor, leftEye, nose, 1.5f)
        drawScope.drawLine(mainColor, rightEye, nose, 1.5f)
        drawScope.drawLine(mainColor, nose, mouth, 1.5f)

        drawScope.drawCircle(secColor, radius = halfW * 0.1f, center = leftEye, style = Stroke(1.5f))
        drawScope.drawCircle(secColor, radius = halfW * 0.1f, center = rightEye, style = Stroke(1.5f))
    }

    private fun drawArFilterOverlay(
        drawScope: DrawScope,
        filter: ArFilterItem,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        primary: Color,
        secondary: Color
    ) {
        val halfW = w / 2f
        val halfH = h / 2f

        when (filter.id) {
            "ar_cyber_visor" -> {
                val visorPath = Path().apply {
                    moveTo(cx - halfW * 1.1f, cy - halfH * 0.3f)
                    lineTo(cx + halfW * 1.1f, cy - halfH * 0.3f)
                    lineTo(cx + halfW * 0.9f, cy + halfH * 0.25f)
                    lineTo(cx - halfW * 0.9f, cy + halfH * 0.25f)
                    close()
                }
                drawScope.drawPath(visorPath, brush = Brush.verticalGradient(listOf(primary.copy(alpha = 0.5f), secondary)))
                drawScope.drawPath(visorPath, color = primary, style = Stroke(width = 3f))
            }
            "ar_neon_crown" -> {
                val crownPath = Path().apply {
                    moveTo(cx - halfW, cy - halfH * 0.4f)
                    lineTo(cx - halfW * 0.8f, cy - halfH * 1.2f)
                    lineTo(cx - halfW * 0.4f, cy - halfH * 0.7f)
                    lineTo(cx, cy - halfH * 1.4f)
                    lineTo(cx + halfW * 0.4f, cy - halfH * 0.7f)
                    lineTo(cx + halfW * 0.8f, cy - halfH * 1.2f)
                    lineTo(cx + halfW, cy - halfH * 0.4f)
                    close()
                }
                drawScope.drawPath(crownPath, brush = Brush.verticalGradient(listOf(primary, secondary)))
                drawScope.drawPath(crownPath, color = Color.White, style = Stroke(width = 2.5f))
            }
            "ar_matrix_shades" -> {
                drawScope.drawRoundRect(primary, Offset(cx - halfW, cy - halfH * 0.3f), Size(halfW * 0.85f, halfH * 0.6f), CornerRadius(8f, 8f))
                drawScope.drawRoundRect(primary, Offset(cx + halfW * 0.15f, cy - halfH * 0.3f), Size(halfW * 0.85f, halfH * 0.6f), CornerRadius(8f, 8f))
                drawScope.drawLine(secondary, Offset(cx - halfW * 0.15f, cy - halfH * 0.1f), Offset(cx + halfW * 0.15f, cy - halfH * 0.1f), 3f)
            }
            "ar_hologram_goggles" -> {
                val goggleRadius = halfW * 0.4f
                val leftGoggle = Offset(cx - halfW * 0.45f, cy - halfH * 0.15f)
                val rightGoggle = Offset(cx + halfW * 0.45f, cy - halfH * 0.15f)
                drawScope.drawCircle(primary.copy(alpha = 0.35f), radius = goggleRadius, center = leftGoggle)
                drawScope.drawCircle(primary.copy(alpha = 0.35f), radius = goggleRadius, center = rightGoggle)
                drawScope.drawCircle(secondary, radius = goggleRadius, center = leftGoggle, style = Stroke(2.5f))
                drawScope.drawCircle(secondary, radius = goggleRadius, center = rightGoggle, style = Stroke(2.5f))
                drawScope.drawLine(primary, Offset(cx - halfW * 0.1f, cy - halfH * 0.15f), Offset(cx + halfW * 0.1f, cy - halfH * 0.15f), 3.5f)
            }
            "ar_cat_whiskers" -> {
                // Whiskers
                drawScope.drawLine(primary, Offset(cx - halfW * 0.3f, cy + halfH * 0.2f), Offset(cx - halfW * 1.1f, cy + halfH * 0.1f), 2.5f)
                drawScope.drawLine(primary, Offset(cx - halfW * 0.3f, cy + halfH * 0.25f), Offset(cx - halfW * 1.15f, cy + halfH * 0.25f), 2.5f)
                drawScope.drawLine(primary, Offset(cx + halfW * 0.3f, cy + halfH * 0.2f), Offset(cx + halfW * 1.1f, cy + halfH * 0.1f), 2.5f)
                drawScope.drawLine(primary, Offset(cx + halfW * 0.3f, cy + halfH * 0.25f), Offset(cx + halfW * 1.15f, cy + halfH * 0.25f), 2.5f)
                drawScope.drawCircle(secondary, radius = 6f, center = Offset(cx, cy + halfH * 0.15f))
            }
            "ar_bunny_ears" -> {
                val leftEar = Path().apply {
                    moveTo(cx - halfW * 0.5f, cy - halfH * 0.5f)
                    quadraticTo(cx - halfW * 0.8f, cy - halfH * 1.6f, cx - halfW * 0.35f, cy - halfH * 1.8f)
                    quadraticTo(cx - halfW * 0.1f, cy - halfH * 1.5f, cx - halfW * 0.2f, cy - halfH * 0.5f)
                    close()
                }
                val rightEar = Path().apply {
                    moveTo(cx + halfW * 0.2f, cy - halfH * 0.5f)
                    quadraticTo(cx + halfW * 0.1f, cy - halfH * 1.5f, cx + halfW * 0.35f, cy - halfH * 1.8f)
                    quadraticTo(cx + halfW * 0.8f, cy - halfH * 1.6f, cx + halfW * 0.5f, cy - halfH * 0.5f)
                    close()
                }
                drawScope.drawPath(leftEar, primary)
                drawScope.drawPath(rightEar, primary)
                drawScope.drawCircle(secondary, radius = 7f, center = Offset(cx, cy + halfH * 0.15f))
            }
            "ar_tech_hud" -> {
                drawScope.drawCircle(primary, radius = halfW * 1.1f, center = Offset(cx, cy), style = Stroke(2f))
                drawScope.drawLine(secondary, Offset(cx - halfW * 1.2f, cy), Offset(cx + halfW * 1.2f, cy), 1.5f)
                drawScope.drawLine(secondary, Offset(cx, cy - halfH * 1.2f), Offset(cx, cy + halfH * 1.2f), 1.5f)
            }
            "ar_angel_halo" -> {
                drawScope.drawOval(primary, Offset(cx - halfW * 1.1f, cy - halfH * 1.3f), Size(halfW * 2.2f, halfH * 0.5f), style = Stroke(5f))
            }
            "ar_devil_horns" -> {
                val leftHorn = Path().apply {
                    moveTo(cx - halfW * 0.4f, cy - halfH * 0.5f)
                    quadraticTo(cx - halfW * 0.8f, cy - halfH * 1.2f, cx - halfW, cy - halfH * 1.4f)
                    quadraticTo(cx - halfW * 0.5f, cy - halfH * 0.9f, cx - halfW * 0.2f, cy - halfH * 0.5f)
                    close()
                }
                val rightHorn = Path().apply {
                    moveTo(cx + halfW * 0.4f, cy - halfH * 0.5f)
                    quadraticTo(cx + halfW * 0.8f, cy - halfH * 1.2f, cx + halfW, cy - halfH * 1.4f)
                    quadraticTo(cx + halfW * 0.5f, cy - halfH * 0.9f, cx + halfW * 0.2f, cy - halfH * 0.5f)
                    close()
                }
                drawScope.drawPath(leftHorn, primary)
                drawScope.drawPath(rightHorn, primary)
            }
            "ar_venice_mask" -> {
                val maskPath = Path().apply {
                    moveTo(cx - halfW * 1.05f, cy - halfH * 0.45f)
                    cubicTo(cx - halfW * 0.6f, cy - halfH * 0.6f, cx + halfW * 0.6f, cy - halfH * 0.6f, cx + halfW * 1.05f, cy - halfH * 0.45f)
                    quadraticTo(cx + halfW * 0.9f, cy + halfH * 0.25f, cx, cy + halfH * 0.15f)
                    quadraticTo(cx - halfW * 0.9f, cy + halfH * 0.25f, cx - halfW * 1.05f, cy - halfH * 0.45f)
                    close()
                }
                drawScope.drawPath(maskPath, brush = Brush.verticalGradient(listOf(primary, secondary)))
                drawScope.drawPath(maskPath, color = Color(0xFFFFD700), style = Stroke(2.5f))
            }
            "ar_privacy_mosaic" -> {
                drawScope.drawRoundRect(
                    color = primary.copy(alpha = 0.7f),
                    topLeft = Offset(cx - halfW * 0.8f, cy - halfH * 0.7f),
                    size = Size(halfW * 1.6f, halfH * 1.4f),
                    cornerRadius = CornerRadius(16f, 16f)
                )
                drawScope.drawRoundRect(
                    color = secondary,
                    topLeft = Offset(cx - halfW * 0.8f, cy - halfH * 0.7f),
                    size = Size(halfW * 1.6f, halfH * 1.4f),
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(2f)
                )
            }
            else -> {
                drawDefaultMeshGrid(drawScope, cx, cy, w, h, primary, secondary)
            }
        }
    }

    override fun close() {
        runCatching { detector.close() }
    }
}
