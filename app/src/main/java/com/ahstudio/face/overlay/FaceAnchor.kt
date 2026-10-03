package com.ahstudio.face.overlay

import com.ahstudio.face.core.TrackedFace
import com.ahstudio.face.core.Vec2

enum class FaceAnchor {
    FACE_CENTER, LEFT_EYE, RIGHT_EYE, EYES_CENTER, NOSE, MOUTH_CENTER,
    LEFT_CHEEK, RIGHT_CHEEK, FOREHEAD, CHIN, LEFT_EAR, RIGHT_EAR,
}

object AnchorCalculator {

    private fun lateralFrac(b: com.ahstudio.face.core.FaceBounds, mirrored: Boolean, subjectLeftFrac: Float): Float {
        val frac = if (mirrored) 1f - subjectLeftFrac else subjectLeftFrac
        return b.left + frac * b.width
    }

    fun anchorPoint(face: TrackedFace, anchor: FaceAnchor, mirrored: Boolean): Vec2 {
        val b = face.bounds; val L = face.landmarks; val h = b.height
        return when (anchor) {
            FaceAnchor.FACE_CENTER -> Vec2(b.centerX, b.centerY)
            FaceAnchor.EYES_CENTER -> L?.eyesCenter ?: Vec2(b.centerX, b.top + 0.38f * h)
            FaceAnchor.LEFT_EYE -> L?.get(com.ahstudio.face.core.FaceLandmarkType.LEFT_EYE)
                ?: Vec2(lateralFrac(b, mirrored, 0.68f), b.top + 0.38f * h)
            FaceAnchor.RIGHT_EYE -> L?.get(com.ahstudio.face.core.FaceLandmarkType.RIGHT_EYE)
                ?: Vec2(lateralFrac(b, mirrored, 0.32f), b.top + 0.38f * h)
            FaceAnchor.NOSE -> L?.get(com.ahstudio.face.core.FaceLandmarkType.NOSE_BASE)
                ?: Vec2(b.centerX, b.top + 0.55f * h)
            FaceAnchor.MOUTH_CENTER -> L?.mouthCenter ?: Vec2(b.centerX, b.top + 0.76f * h)
            FaceAnchor.FOREHEAD -> {
                val eyes = L?.eyesCenter ?: Vec2(b.centerX, b.top + 0.38f * h)
                Vec2(eyes.x, eyes.y - 0.30f * h)
            }
            FaceAnchor.CHIN -> Vec2(b.centerX, b.bottom)
            FaceAnchor.LEFT_CHEEK -> L?.get(com.ahstudio.face.core.FaceLandmarkType.LEFT_CHEEK)
                ?: Vec2(lateralFrac(b, mirrored, 0.72f), b.top + 0.58f * h)
            FaceAnchor.RIGHT_CHEEK -> L?.get(com.ahstudio.face.core.FaceLandmarkType.RIGHT_CHEEK)
                ?: Vec2(lateralFrac(b, mirrored, 0.28f), b.top + 0.58f * h)
            FaceAnchor.LEFT_EAR -> L?.get(com.ahstudio.face.core.FaceLandmarkType.LEFT_EAR_LOBE)
                ?: L?.get(com.ahstudio.face.core.FaceLandmarkType.LEFT_EAR_TIP)
                ?: Vec2(lateralFrac(b, mirrored, 0.92f), b.top + 0.45f * h)
            FaceAnchor.RIGHT_EAR -> L?.get(com.ahstudio.face.core.FaceLandmarkType.RIGHT_EAR_LOBE)
                ?: L?.get(com.ahstudio.face.core.FaceLandmarkType.RIGHT_EAR_TIP)
                ?: Vec2(lateralFrac(b, mirrored, 0.08f), b.top + 0.45f * h)
        }
    }

    fun interOcular(face: TrackedFace): Float =
        face.interOcular.takeIf { it > 1e-5f } ?: (face.bounds.width * 0.46f)
}
