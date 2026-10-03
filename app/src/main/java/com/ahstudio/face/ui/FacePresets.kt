package com.ahstudio.face.ui

import com.ahstudio.face.overlay.FaceAnchor
import com.ahstudio.face.timeline.FaceEffectClipData
import java.util.UUID

data class SpecDefaults(
    val anchor: FaceAnchor,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val followRotation: Float = 1f,
)

object FacePresets {
    val stickers = mapOf(
        "sunglasses" to SpecDefaults(FaceAnchor.EYES_CENTER, offsetY = 0.02f, scale = 1.25f),
        "crown" to SpecDefaults(FaceAnchor.FOREHEAD, offsetY = -0.30f, scale = 1.2f, followRotation = 0.35f),
        "mustache" to SpecDefaults(FaceAnchor.NOSE, offsetY = 0.35f, scale = 0.8f),
        "mask" to SpecDefaults(FaceAnchor.FACE_CENTER, scale = 1.1f),
        "earring_left" to SpecDefaults(FaceAnchor.LEFT_EAR, scale = 0.25f),
        "earring_right" to SpecDefaults(FaceAnchor.RIGHT_EAR, scale = 0.25f),
    )

    fun buildStickerData(clipId: String, stickerId: String,
                         effectId: String = UUID.randomUUID().toString()): FaceEffectClipData {
        val d = stickers[stickerId] ?: SpecDefaults(FaceAnchor.FACE_CENTER)
        return FaceEffectClipData(
            effectId = effectId, clipId = clipId, stickerId = stickerId,
            anchor = d.anchor.name, offsetY = d.offsetY, scale = d.scale,
            followRotation = d.followRotation,
        )
    }
}
