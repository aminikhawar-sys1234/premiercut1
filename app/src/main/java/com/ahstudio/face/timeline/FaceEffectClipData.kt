package com.ahstudio.face.timeline

import com.ahstudio.face.core.Vec2
import com.ahstudio.face.deformation.DeformationParams
import com.ahstudio.face.overlay.BlendMode
import com.ahstudio.face.overlay.FaceAnchor
import com.ahstudio.face.overlay.FaceOverlaySpec
import org.json.JSONObject

data class FaceEffectClipData(
    val effectId: String,
    val clipId: String,
    val stickerId: String? = null,
    val filterId: String? = null,
    val filterIntensity: Float = 1f,
    val anchor: String = "FACE_CENTER",
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val blendMode: String = "NORMAL",
    val followPosition: Float = 1f,
    val followRotation: Float = 1f,
    val followScale: Float = 1f,
    val zOrder: Int = 0,
    val startSourceUs: Long = 0L,
    val endSourceUs: Long = Long.MAX_VALUE,
    val deform: Map<String, Float> = emptyMap(),
    val maskSoftness: Float = 0.3f,
    val trackingSmoothness: Float = 0.35f,
    val keyframes: Map<String, List<KeyframeDto>> = emptyMap(),
    val enabled: Boolean = true,
) {
    data class KeyframeDto(val timeUs: Long, val value: Float, val easing: String = "LINEAR")

    fun isActiveAt(sourceUs: Long): Boolean = enabled && sourceUs in startSourceUs..endSourceUs

    fun toSpec(): FaceOverlaySpec = FaceOverlaySpec(
        effectId = effectId, clipId = clipId,
        stickerId = stickerId, filterId = filterId, filterIntensity = filterIntensity,
        anchor = runCatching { FaceAnchor.valueOf(anchor) }.getOrDefault(FaceAnchor.FACE_CENTER),
        offset = Vec2(offsetX, offsetY),
        scale = scale, rotationDeg = rotationDeg, opacity = opacity,
        blendMode = runCatching { BlendMode.valueOf(blendMode) }.getOrDefault(BlendMode.NORMAL),
        followPosition = followPosition, followRotation = followRotation, followScale = followScale,
        zOrder = zOrder, startSourceUs = startSourceUs, endSourceUs = endSourceUs,
        enabled = enabled,
        deform = if (deform.isEmpty()) null else DeformationParams(
            eyeEnlarge = deform["eyeEnlarge"] ?: 0f,
            faceSlim = deform["faceSlim"] ?: 0f,
            jawSharp = deform["jawSharp"] ?: 0f,
            noseReshape = deform["noseReshape"] ?: 0f,
            chinAdjust = deform["chinAdjust"] ?: 0f,
            smileAdjust = deform["smileAdjust"] ?: 0f,
        ),
    )

    fun deformParams(): DeformationParams = toSpec().deform ?: DeformationParams()
}

object FaceEffectSerializer {
    fun toJson(data: FaceEffectClipData): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"effectId\":\"${data.effectId}\",")
        sb.append("\"clipId\":\"${data.clipId}\",")
        data.stickerId?.let { sb.append("\"stickerId\":\"$it\",") }
        data.filterId?.let { sb.append("\"filterId\":\"$it\",") }
        sb.append("\"filterIntensity\":${data.filterIntensity},")
        sb.append("\"anchor\":\"${data.anchor}\",")
        sb.append("\"offsetX\":${data.offsetX},")
        sb.append("\"offsetY\":${data.offsetY},")
        sb.append("\"scale\":${data.scale},")
        sb.append("\"rotationDeg\":${data.rotationDeg},")
        sb.append("\"opacity\":${data.opacity},")
        sb.append("\"blendMode\":\"${data.blendMode}\",")
        sb.append("\"followPosition\":${data.followPosition},")
        sb.append("\"followRotation\":${data.followRotation},")
        sb.append("\"followScale\":${data.followScale},")
        sb.append("\"zOrder\":${data.zOrder},")
        sb.append("\"startSourceUs\":${data.startSourceUs},")
        sb.append("\"endSourceUs\":${data.endSourceUs},")
        sb.append("\"maskSoftness\":${data.maskSoftness},")
        sb.append("\"trackingSmoothness\":${data.trackingSmoothness},")
        sb.append("\"enabled\":${data.enabled}")
        if (data.deform.isNotEmpty()) {
            sb.append(",\"deform\":{")
            data.deform.forEach { (k, v) -> sb.append("\"$k\":$v,") }
            sb.setLength(sb.length - 1)
            sb.append("}")
        }
        sb.append("}")
        return sb.toString()
    }

    fun fromJson(text: String): FaceEffectClipData {
        println("Deserializing: $text")
        val obj = JSONObject(text)
        val deformMap = mutableMapOf<String, Float>()
        if (obj.has("deform")) {
            val deformObj = obj.getJSONObject("deform")
            val keys = deformObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                deformMap[key] = deformObj.getDouble(key).toFloat()
            }
        }
        return FaceEffectClipData(
            effectId = obj.optString("effectId", ""),
            clipId = obj.optString("clipId", ""),
            stickerId = if (obj.has("stickerId") && !obj.isNull("stickerId")) obj.optString("stickerId").ifEmpty { null } else null,
            filterId = if (obj.has("filterId") && !obj.isNull("filterId")) obj.optString("filterId").ifEmpty { null } else null,
            filterIntensity = obj.optDouble("filterIntensity", 1.0).toFloat(),
            anchor = obj.optString("anchor", "FACE_CENTER"),
            offsetX = obj.optDouble("offsetX", 0.0).toFloat(),
            offsetY = obj.optDouble("offsetY", 0.0).toFloat(),
            scale = obj.optDouble("scale", 1.0).toFloat(),
            rotationDeg = obj.optDouble("rotationDeg", 0.0).toFloat(),
            opacity = obj.optDouble("opacity", 1.0).toFloat(),
            blendMode = obj.optString("blendMode", "NORMAL"),
            followPosition = obj.optDouble("followPosition", 1.0).toFloat(),
            followRotation = obj.optDouble("followRotation", 1.0).toFloat(),
            followScale = obj.optDouble("followScale", 1.0).toFloat(),
            zOrder = obj.optInt("zOrder", 0),
            startSourceUs = obj.optLong("startSourceUs", 0L),
            endSourceUs = obj.optLong("endSourceUs", Long.MAX_VALUE),
            deform = deformMap,
            maskSoftness = obj.optDouble("maskSoftness", 0.3).toFloat(),
            trackingSmoothness = obj.optDouble("trackingSmoothness", 0.35).toFloat(),
            enabled = obj.optBoolean("enabled", true),
        )
    }
}
