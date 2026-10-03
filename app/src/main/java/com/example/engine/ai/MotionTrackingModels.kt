package com.example.engine.ai

import android.graphics.RectF

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = (right - left).coerceAtLeast(0.01f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.01f)

    fun toRectF(viewWidth: Float, viewHeight: Float): RectF {
        return RectF(
            left * viewWidth,
            top * viewHeight,
            right * viewWidth,
            bottom * viewHeight
        )
    }

    fun clamped(): NormalizedRect {
        val l = left.coerceIn(0f, 0.95f)
        val t = top.coerceIn(0f, 0.95f)
        val r = right.coerceIn(l + 0.02f, 1f)
        val b = bottom.coerceIn(t + 0.02f, 1f)
        return NormalizedRect(l, t, r, b)
    }

    companion object {
        val DEFAULT_CENTER = NormalizedRect(0.35f, 0.35f, 0.65f, 0.65f)
        val SMALL_CENTER = NormalizedRect(0.42f, 0.42f, 0.58f, 0.58f)
        val LARGE_CENTER = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f)
    }
}

data class MotionKeyframe(
    val timestampUs: Long,
    val centerX: Float,
    val centerY: Float,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotationDeg: Float = 0.0f,
    val confidence: Float = 1.0f,
    val landmarkPoints: List<Pair<Float, Float>> = emptyList()
)

data class TrackingResult(
    val targetId: String,
    val clipId: String,
    val startTimestampUs: Long,
    val endTimestampUs: Long,
    val keyframes: List<MotionKeyframe>,
    val targetCategory: TrackingCategory = TrackingCategory.OBJECT
) {
    val isEmpty: Boolean get() = keyframes.isEmpty()
    val averageConfidence: Float
        get() = if (keyframes.isEmpty()) 0f else keyframes.map { it.confidence }.average().toFloat()
}

enum class TrackingCategory(val title: String, val iconEmoji: String) {
    OBJECT("Object", "🎯"),
    FACE("Face", "👤"),
    BODY("Body", "🧍"),
    MOTION("Motion", "📌"),
    ATTACH("Attach", "✨"),
    CONTROLS("Controls", "🛠️")
}

enum class FaceTrackingFeature(val title: String) {
    FACE_TRACK("Face Track"),
    FACE_POSITION("Face Position"),
    FACE_SCALE("Face Scale"),
    FACE_ROTATION("Face Rotation"),
    LANDMARKS("Face Landmarks"),
    FACE_BLUR("Face Blur"),
    FACE_MOSAIC("Face Mosaic"),
    FACE_STICKER("Face Sticker")
}

enum class BodyTrackingFeature(val title: String) {
    FULL_BODY("Full Body"),
    POSE_TRACKING("Pose Tracking"),
    UPPER_BODY("Upper Body"),
    ARMS("Arms & Hands"),
    LEGS("Legs & Feet"),
    BODY_BLUR("Body Blur")
}

enum class MotionFeatureType(val title: String) {
    POINT_TRACKING("Point Tracking"),
    MULTI_POINT("Multi-Point"),
    PLANAR("Planar Tracking"),
    POSITION_ONLY("Position Only"),
    ROTATION_SCALE("Rotation & Scale"),
    MOTION_PATH("Motion Path")
}

enum class AttachmentTarget(val title: String, val description: String) {
    TEXT("Text Layer", "Attach active or new text layer to tracked motion"),
    STICKER("Sticker", "Attach sticker or emoji to follow tracked motion"),
    OVERLAY("Overlay / PIP", "Attach video/photo overlay to tracked target"),
    EFFECT_BLUR("Blur Effect", "Attach dynamic blur box to tracked area"),
    EFFECT_MOSAIC("Mosaic Effect", "Attach pixelated mosaic to tracked area"),
    MASK("Clip Mask", "Anchor clip shape mask to tracked motion")
}

enum class TrackingEngineState {
    IDLE,
    DETECTING,
    TRACKING,
    REDETECTING,
    RECOVERING,
    LOST,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class TrackingSession(
    val sessionId: String,
    val targetCategory: TrackingCategory,
    val targetId: String,
    val startTimestampUs: Long,
    val endTimestampUs: Long,
    val currentTimeUs: Long,
    val confidence: Float,
    val boundingBox: NormalizedRect,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float,
    val scale: Float,
    val landmarks: List<Pair<Float, Float>> = emptyList(),
    val state: TrackingEngineState = TrackingEngineState.IDLE,
    val keyframes: List<MotionKeyframe> = emptyList()
)

data class MotionTrackingSettings(
    val searchWindowFactor: Float = 2.0f,
    val accuracyFps: Int = 24,
    val smoothingFactor: Float = 0.35f,
    val followPosition: Boolean = true,
    val followScale: Boolean = true,
    val followRotation: Boolean = true,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val trackForward: Boolean = true,
    val trackBackward: Boolean = false
)

data class MotionTrackingUiState(
    val isTracking: Boolean = false,
    val engineState: TrackingEngineState = TrackingEngineState.IDLE,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val activeCategory: TrackingCategory = TrackingCategory.OBJECT,
    val activeResult: TrackingResult? = null,
    val activeSession: TrackingSession? = null,
    val targetRegion: NormalizedRect = NormalizedRect.DEFAULT_CENTER,
    val isRegionSelectorActive: Boolean = true,
    val selectedAttachment: AttachmentTarget = AttachmentTarget.TEXT,
    val settings: MotionTrackingSettings = MotionTrackingSettings(),
    val errorMessage: String? = null,
    val detectedFaceCount: Int = 0,
    val detectedBodyCount: Int = 0,
    val detectedPointsCount: Int = 0
)
