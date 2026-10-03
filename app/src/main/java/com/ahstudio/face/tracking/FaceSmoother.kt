package com.ahstudio.face.tracking

import com.ahstudio.face.core.*
import com.ahstudio.face.detection.RawFaceDetection
import com.ahstudio.face.temporal.OneEuroFilter
import com.ahstudio.face.temporal.unwrapAngle

data class NormalizedDet(
    val bounds: FaceBounds,
    val rotation: FaceRotation,
    val landmarks: FaceLandmarks,
    val classification: FaceClassification,
    val trackingId: Int?,
    val confidence: Float,
)

/** ML Kit exposes no confidence — synthesize from size + landmark presence. */
fun RawFaceDetection.normalized(frameW: Int, frameH: Int): NormalizedDet {
    val w = frameW.toFloat(); val h = frameH.toFloat()
    val b = FaceBounds(boundsPx.left / w, boundsPx.top / h, boundsPx.right / w, boundsPx.bottom / h)
    val sizeConf = (b.area / 0.10f).coerceIn(0f, 1f)
    val lmConf = if (landmarksPx.isNotEmpty()) 1f else 0.6f
    return NormalizedDet(
        bounds = b,
        rotation = FaceRotation(eulerX, eulerY, eulerZ),
        landmarks = FaceLandmarks(landmarksPx.mapValues { Vec2(it.value.x / w, it.value.y / h) }),
        classification = FaceClassification(smiling ?: 0f, leftEyeOpen ?: 1f, rightEyeOpen ?: 1f),
        trackingId = trackingId,
        confidence = (0.45f + 0.35f * sizeConf + 0.20f * lmConf).coerceIn(0f, 1f),
    )
}

/** Smooths center, size, rotation (angle-unwrapped) and landmark points for one face. */
class FaceSmoother(private val cfg: SmoothingConfig) {
    private val cx = OneEuroFilter(cfg.positionMinCutoff, cfg.positionBeta)
    private val cy = OneEuroFilter(cfg.positionMinCutoff, cfg.positionBeta)
    private val bw = OneEuroFilter(cfg.scaleMinCutoff, cfg.scaleBeta)
    private val bh = OneEuroFilter(cfg.scaleMinCutoff, cfg.scaleBeta)
    private val rx = OneEuroFilter(cfg.rotationMinCutoff, cfg.rotationBeta)
    private val ry = OneEuroFilter(cfg.rotationMinCutoff, cfg.rotationBeta)
    private val rz = OneEuroFilter(cfg.rotationMinCutoff, cfg.rotationBeta)
    private val lm = HashMap<FaceLandmarkType, Pair<OneEuroFilter, OneEuroFilter>>()
    private var contRz = 0f
    private var rzSeeded = false

    fun reset() {
        listOf(cx, cy, bw, bh, rx, ry, rz).forEach { it.reset() }
        lm.values.forEach { it.first.reset(); it.second.reset() }
        contRz = 0f; rzSeeded = false
    }

    fun smooth(det: NormalizedDet, dt: Float, out: FloatArray): NormalizedDet {
        val d = dt.toDouble()
        val dampen = if (det.confidence < 0.45f) 0.5 else 1.0
        cx.retune(cfg.positionMinCutoff * dampen, cfg.positionBeta)
        val sx = cx.filter(det.bounds.centerX.toDouble(), d).toFloat()
        val sy = cy.filter(det.bounds.centerY.toDouble(), d).toFloat()
        val sw = bw.filter(det.bounds.width.toDouble(), d).toFloat()
        val sh = bh.filter(det.bounds.height.toDouble(), d).toFloat()
        contRz = if (!rzSeeded) { rzSeeded = true; det.rotation.eulerZ }
                 else unwrapAngle(contRz, det.rotation.eulerZ)
        val srx = rx.filter(det.rotation.eulerX.toDouble(), d).toFloat()
        val sry = ry.filter(det.rotation.eulerY.toDouble(), d).toFloat()
        val srz = rz.filter(contRz.toDouble(), d).toFloat()
        val slm = FaceLandmarks(det.landmarks.points.mapValues { (t, p) ->
            val f = lm.getOrPut(t) {
                OneEuroFilter(cfg.positionMinCutoff, cfg.positionBeta) to
                OneEuroFilter(cfg.positionMinCutoff, cfg.positionBeta)
            }
            Vec2(f.first.filter(p.x.toDouble(), d).toFloat(), f.second.filter(p.y.toDouble(), d).toFloat())
        })
        out[0] = sx; out[1] = sy; out[2] = sw; out[3] = sh
        out[4] = srx; out[5] = sry; out[6] = srz
        return det.copy(
            bounds = FaceBounds(sx - sw / 2, sy - sh / 2, sx + sw / 2, sy + sh / 2),
            rotation = FaceRotation(srx, sry, srz),
            landmarks = slm,
        )
    }
}
