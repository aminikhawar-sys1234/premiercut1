package com.ahstudio.face

import com.ahstudio.face.core.FaceTrackingEngine
import com.ahstudio.face.core.FaceTrackingResult
import com.ahstudio.face.core.TrackedFace
import com.ahstudio.face.deformation.DeformationParams
import com.ahstudio.face.deformation.WarpOp
import com.ahstudio.face.deformation.WarpOps
import com.ahstudio.face.filters.FilterModes
import com.ahstudio.face.geometry.VideoTransform
import com.ahstudio.face.overlay.KeyframeSample
import com.ahstudio.face.overlay.FaceOverlaySpec
import com.ahstudio.face.overlay.OverlayTransformSolver
import com.ahstudio.face.rendering.FaceMaskBaker
import com.ahstudio.face.rendering.FaceOverlayRenderPass
import com.ahstudio.face.rendering.OverlayDrawItem
import com.ahstudio.face.timeline.FaceEffectProperty
import com.ahstudio.face.timeline.FaceKeyframeSource
import com.ahstudio.face.timeline.FaceTimelineBridge

data class FaceFilterRequest(
    val glMode: Int,
    val strength: Float,
    val brightness: Float,
    val contrast: Float,
    val faces: List<TrackedFace>,
    val maskSoftness: Float,
)

/**
 * The primary host class coordinating face tracking, overlays, filters, and deformations.
 */
class FaceEngineHost(
    private val bridge: FaceTimelineBridge,
    private val keyframes: FaceKeyframeSource,
    val tracking: FaceTrackingEngine,
    private val overlayPass: FaceOverlayRenderPass,
    private val maskBaker: FaceMaskBaker,
) {
    var stickerTextures: Map<String, Int> = emptyMap()
    var stickerAspects: Map<String, Float> = emptyMap()
    private val effectsByClip = HashMap<String, List<FaceOverlaySpec>>()

    fun onEffectsChanged(clipId: String, specs: List<FaceOverlaySpec>) {
        effectsByClip[clipId] = specs
    }

    private fun resolve(masterTimeUs: Long, seekHint: Boolean): Triple<String, Long, FaceTrackingResult>? {
        val clipId = bridge.activeClipIdAt(masterTimeUs) ?: return null
        val sourceTimeUs = bridge.sourceTimeUsOf(clipId, masterTimeUs) ?: return null
        val hash = bridge.transformHash(clipId)
        val mirrored = bridge.isMirroredSource(clipId)
        val result = if (seekHint) tracking.resultAtBlocking(clipId, sourceTimeUs, hash, mirrored)
                     else tracking.resultAtRenderTime(clipId, sourceTimeUs, hash, mirrored)
        return Triple(clipId, sourceTimeUs, result)
    }

    private fun activeEffects(clipId: String, sourceTimeUs: Long): List<FaceOverlaySpec> =
        (effectsByClip[clipId] ?: emptyList())
            .filter { it.enabled && sourceTimeUs in it.startSourceUs..it.endSourceUs }

    private fun sample(effectId: String, clipLocalUs: Long): KeyframeSample = KeyframeSample(
        offsetX = keyframes.evaluate(effectId, FaceEffectProperty.OFFSET_X, clipLocalUs) ?: 0f,
        offsetY = keyframes.evaluate(effectId, FaceEffectProperty.OFFSET_Y, clipLocalUs) ?: 0f,
        scale = keyframes.evaluate(effectId, FaceEffectProperty.SCALE, clipLocalUs) ?: 1f,
        rotation = keyframes.evaluate(effectId, FaceEffectProperty.ROTATION, clipLocalUs) ?: 0f,
        opacity = keyframes.evaluate(effectId, FaceEffectProperty.OPACITY, clipLocalUs) ?: 1f,
        intensity = keyframes.evaluate(effectId, FaceEffectProperty.INTENSITY, clipLocalUs) ?: 1f,
    )

    fun renderFrame(masterTimeUs: Long, transform: VideoTransform, seekHint: Boolean = false) {
        val (clipId, sourceUs, result) = resolve(masterTimeUs, seekHint) ?: return
        if (result.faces.isEmpty()) return
        val items = ArrayList<OverlayDrawItem>(8)
        for (fx in activeEffects(clipId, sourceUs)) {
            val stickerId = fx.stickerId ?: continue
            val tex = stickerTextures[stickerId] ?: continue
            val aspect = stickerAspects[stickerId] ?: 1f
            for (face in result.faces) {
                val kf = sample(fx.effectId, sourceUs)
                items += OverlayDrawItem(
                    OverlayTransformSolver.solve(face, fx, kf, result.isMirrored), tex, aspect)
            }
        }
        if (items.isNotEmpty()) overlayPass.render(transform, items)
    }

    fun collectWarpOps(masterTimeUs: Long, seekHint: Boolean = false): List<WarpOp> {
        val (clipId, sourceUs, result) = resolve(masterTimeUs, seekHint) ?: return emptyList()
        var acc = DeformationParams()
        for (fx in activeEffects(clipId, sourceUs)) fx.deform?.let { acc += it }
        if (result.faces.isEmpty()) return emptyList()
        return result.faces.flatMap { WarpOps.forFace(it, acc) }
    }

    fun collectFilterRequest(masterTimeUs: Long, seekHint: Boolean = false): FaceFilterRequest? {
        val (clipId, sourceUs, result) = resolve(masterTimeUs, seekHint) ?: return null
        val fx = activeEffects(clipId, sourceUs).firstOrNull { it.filterId != null } ?: return null
        val mode = FilterModes.glModeOf(fx.filterId!!) ?: return null
        val kf = sample(fx.effectId, sourceUs)
        return FaceFilterRequest(
            glMode = mode,
            strength = (fx.filterIntensity * kf.intensity).coerceIn(0f, 1f),
            brightness = 0f, contrast = 0f,
            faces = result.faces,
            maskSoftness = 0.3f,
        )
    }

    fun bakeMask(request: FaceFilterRequest, frameAspect: Float): Int =
        maskBaker.bake(request.faces, frameAspect, request.maskSoftness)
}
