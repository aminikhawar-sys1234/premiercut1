package com.ahstudio.face.deformation

import com.ahstudio.face.core.FaceLandmarkType
import com.ahstudio.face.core.TrackedFace
import com.ahstudio.face.core.Vec2

enum class WarpType { MAGNIFY_EYE, SLIM_LATERAL, PUSH }

data class WarpOp(
    val type: WarpType,
    val center: Vec2,   // normalized frame space
    val radius: Float,  // in IOD units
    val strength: Float, // user intensity x keyframe intensity
    val dir: Vec2 = Vec2.ZERO,
)

data class DeformationParams(
    val eyeEnlarge: Float = 0f,
    val faceSlim: Float = 0f,
    val jawSharp: Float = 0f,
    val noseReshape: Float = 0f,
    val chinAdjust: Float = 0f,
    val smileAdjust: Float = 0f,
) {
    operator fun plus(o: DeformationParams) = DeformationParams(
        eyeEnlarge = (eyeEnlarge + o.eyeEnlarge).coerceIn(0f, 1f),
        faceSlim = (faceSlim + o.faceSlim).coerceIn(0f, 1f),
        jawSharp = (jawSharp + o.jawSharp).coerceIn(0f, 1f),
        noseReshape = (noseReshape + o.noseReshape).coerceIn(0f, 1f),
        chinAdjust = (chinAdjust + o.chinAdjust).coerceIn(0f, 1f),
        smileAdjust = (smileAdjust + o.smileAdjust).coerceIn(0f, 1f),
    )
}

/** Translates user deformation parameters + landmarks into GPU warp ops. */
object WarpOps {
    fun forFace(face: TrackedFace, params: DeformationParams): List<WarpOp> {
        val iod = face.interOcular
        val L = face.landmarks
        val ops = ArrayList<WarpOp>(6)
        if (params.eyeEnlarge > 0.001f) {
            listOfNotNull(L?.get(FaceLandmarkType.LEFT_EYE), L?.get(FaceLandmarkType.RIGHT_EYE))
                .forEach { e -> ops += WarpOp(WarpType.MAGNIFY_EYE, e, iod * 0.85f, params.eyeEnlarge * 0.30f) }
        }
        if (params.faceSlim > 0.001f) {
            val b = face.bounds
            val y = b.top + 0.58f * b.height
            ops += WarpOp(WarpType.SLIM_LATERAL, Vec2(b.left + 0.22f * b.width, y), iod * 1.4f, params.faceSlim * 0.06f, Vec2(1f, 0f))
            ops += WarpOp(WarpType.SLIM_LATERAL, Vec2(b.right - 0.22f * b.width, y), iod * 1.4f, params.faceSlim * 0.06f, Vec2(-1f, 0f))
        }
        if (params.jawSharp > 0.001f) {
            val b = face.bounds
            ops += WarpOp(WarpType.PUSH, Vec2(b.centerX, b.bottom - 0.10f * b.height), iod * 1.2f,
                params.jawSharp * 0.04f, Vec2(0f, 1f))
        }
        return ops
    }
}
