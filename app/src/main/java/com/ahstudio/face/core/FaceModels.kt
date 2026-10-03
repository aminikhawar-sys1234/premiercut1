package com.ahstudio.face.core

@JvmInline value class FaceTrackId(val value: Long)

enum class FaceTrackState { DETECTED, RECOVERED, PREDICTED }
enum class DetectionSource { DETECTOR, PREDICTED, CACHE, INTERPOLATED }

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    operator fun div(s: Float) = Vec2(x / s, y / s)
    fun lerp(o: Vec2, t: Float) = Vec2(x + (o.x - x) * t, y + (o.y - y) * t)
    fun dot(o: Vec2) = x * o.x + y * o.y
    val lengthSquared get() = x * x + y * y
    val length get() = kotlin.math.sqrt(lengthSquared)
    companion object { val ZERO = Vec2(0f, 0f) }
}

/** Normalized [0..1], origin top-left, oriented (rotation-applied) frame space. */
data class FaceBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) * 0.5f
    val centerY get() = (top + bottom) * 0.5f
    val area get() = width * height
}

fun iou(a: FaceBounds, b: FaceBounds): Float {
    val x1 = maxOf(a.left, b.left); val y1 = maxOf(a.top, b.top)
    val x2 = minOf(a.right, b.right); val y2 = minOf(a.bottom, b.bottom)
    val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
    val union = a.area + b.area - inter
    return if (union <= 0f) 0f else inter / union
}

/** Anatomical sides (subject's left/right) — ML Kit semantics. */
enum class FaceLandmarkType {
    LEFT_EYE, RIGHT_EYE, NOSE_BASE,
    MOUTH_LEFT, MOUTH_BOTTOM, MOUTH_RIGHT,
    LEFT_CHEEK, RIGHT_CHEEK,
    LEFT_EAR_TIP, LEFT_EAR_LOBE, RIGHT_EAR_TIP, RIGHT_EAR_LOBE
}

class FaceLandmarks(val points: Map<FaceLandmarkType, Vec2>) {
    operator fun get(t: FaceLandmarkType): Vec2? = points[t]
    val eyesCenter: Vec2?
        get() {
            val l = points[FaceLandmarkType.LEFT_EYE]; val r = points[FaceLandmarkType.RIGHT_EYE]
            return if (l != null && r != null) l.lerp(r, 0.5f) else null
        }
    val mouthCenter: Vec2?
        get() {
            val a = points[FaceLandmarkType.MOUTH_LEFT]
            val b = points[FaceLandmarkType.MOUTH_BOTTOM]
            val c = points[FaceLandmarkType.MOUTH_RIGHT]
            return if (a != null && b != null && c != null)
                Vec2((a.x + b.x + c.x) / 3f, (a.y + b.y + c.y) / 3f) else null
        }
    val interOcular: Float?
        get() {
            val l = points[FaceLandmarkType.LEFT_EYE]; val r = points[FaceLandmarkType.RIGHT_EYE]
            return if (l != null && r != null)
                kotlin.math.sqrt((l.x - r.x) * (l.x - r.x) + (l.y - r.y) * (l.y - r.y)) else null
        }
}

data class FaceRotation(val eulerX: Float, val eulerY: Float, val eulerZ: Float) // pitch, yaw, roll (deg)
data class FaceClassification(val smiling: Float, val leftEyeOpen: Float, val rightEyeOpen: Float)

data class TrackedFace(
    val trackId: FaceTrackId,
    val detectorTrackId: Int?,
    val timestampUs: Long,
    val bounds: FaceBounds,
    val rotation: FaceRotation,
    val landmarks: FaceLandmarks?,
    val classification: FaceClassification?,
    val confidence: Float,
    val state: FaceTrackState,
    val velocity: Vec2 = Vec2.ZERO,
    val interOcular: Float, // universal face-scale reference, normalized frame units
)

data class FaceTrackingResult(
    val timestampUs: Long,
    val frameWidth: Int,
    val frameHeight: Int,
    val isMirrored: Boolean,
    val faces: List<TrackedFace>,
    val source: DetectionSource,
) {
    companion object {
        val EMPTY = FaceTrackingResult(-1L, 0, 0, false, emptyList(), DetectionSource.CACHE)
    }
}
