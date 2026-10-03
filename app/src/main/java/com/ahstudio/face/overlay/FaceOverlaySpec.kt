package com.ahstudio.face.overlay

import com.ahstudio.face.core.TrackedFace
import com.ahstudio.face.core.Vec2
import com.ahstudio.face.deformation.DeformationParams

enum class BlendMode { NORMAL, ADDITIVE, MULTIPLY, SCREEN }

/** Static part of a face effect (serialized). Keyframes sample on top of this. */
data class FaceOverlaySpec(
    val effectId: String,
    val clipId: String,
    val stickerId: String? = null,
    val filterId: String? = null,
    val filterIntensity: Float = 1f,
    val anchor: FaceAnchor = FaceAnchor.FACE_CENTER,
    val offset: Vec2 = Vec2.ZERO,       // in IOD units, face-local
    val scale: Float = 1f,              // sticker width = scale x 2 x IOD
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val mirrorWithFace: Boolean = true,
    val followPosition: Float = 1f,     // 1 = rigidly on anchor, 0 = face center
    val followRotation: Float = 1f,
    val followScale: Float = 1f,
    val zOrder: Int = 0,
    val startSourceUs: Long = 0L,
    val endSourceUs: Long = Long.MAX_VALUE,
    val enabled: Boolean = true,
    val deform: DeformationParams? = null,
)

data class KeyframeSample(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val intensity: Float = 1f,
) { companion object { val IDENTITY = KeyframeSample() } }

data class OverlayPlacement(
    val trackId: com.ahstudio.face.core.FaceTrackId,
    val stickerId: String?,
    val centerFrame: Vec2,   // normalized frame space
    val rotationDeg: Float,
    val widthFrame: Float,   // fraction of frame width
    val opacity: Float,
    val mirrored: Boolean,
    val blendMode: BlendMode,
    val zOrder: Int,
)

object OverlayTransformSolver {

    private fun rotate(v: Vec2, deg: Float): Vec2 {
        val r = Math.toRadians(deg.toDouble())
        val c = kotlin.math.cos(r).toFloat(); val s = kotlin.math.sin(r).toFloat()
        return Vec2(v.x * c - v.y * s, v.x * s + v.y * c)
    }

    fun solve(face: TrackedFace, spec: FaceOverlaySpec, kf: KeyframeSample, mirroredVideo: Boolean): OverlayPlacement {
        val iod = AnchorCalculator.interOcular(face)
        val effectiveIod = iod * spec.followScale + 0.12f * (1f - spec.followScale)
        val anchorPt = AnchorCalculator.anchorPoint(face, spec.anchor, mirroredVideo)
        val b = face.bounds
        val pt = anchorPt.lerp(Vec2(b.centerX, b.centerY), 1f - spec.followPosition)
        val roll = face.rotation.eulerZ * spec.followRotation
        val offset = (spec.offset + Vec2(kf.offsetX, kf.offsetY)) * effectiveIod
        val rotatedOffset = rotate(offset, roll)
        return OverlayPlacement(
            trackId = face.trackId,
            stickerId = spec.stickerId,
            centerFrame = pt + rotatedOffset,
            rotationDeg = roll + spec.rotationDeg + kf.rotation,
            widthFrame = effectiveIod * spec.scale * kf.scale * 2f,
            opacity = (spec.opacity * kf.opacity).coerceIn(0f, 1f),
            mirrored = mirroredVideo && spec.mirrorWithFace,
            blendMode = spec.blendMode,
            zOrder = spec.zOrder,
        )
    }
}
