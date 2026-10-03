package com.ahstudio.face.detection

import android.graphics.Bitmap
import android.media.Image
import com.ahstudio.face.core.FaceLandmarkType
import com.ahstudio.face.core.Vec2

/** Pure-Kotlin pixel bounds — JVM-testable, no android.graphics dependency. */
data class BoundsPx(val left: Float, val top: Float, val right: Float, val bottom: Float)

sealed class DetectionFrame {
    abstract val timestampUs: Long
    data class BitmapFrame(val bitmap: Bitmap, override val timestampUs: Long) : DetectionFrame()
    data class YuvImageFrame(val image: Image, val rotationDegrees: Int, override val timestampUs: Long) : DetectionFrame()
}

/** Detector-space (pixel) detection, pre-normalization. */
data class RawFaceDetection(
    val boundsPx: BoundsPx,
    val eulerX: Float,
    val eulerY: Float,
    val eulerZ: Float,
    val landmarksPx: Map<FaceLandmarkType, Vec2>,
    val smiling: Float?,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?,
    val trackingId: Int?,
)

interface FaceDetector {
    suspend fun detect(frame: DetectionFrame, maxFaces: Int): List<RawFaceDetection>
    fun close()
}

/** Implemented against Ah Studio's existing decode path. */
interface FrameProvider {
    suspend fun frame(clipId: String, sourceTimeUs: Long): DetectionFrame?
    fun dimensionsOf(clipId: String): Pair<Int, Int>?
    fun close()
}
