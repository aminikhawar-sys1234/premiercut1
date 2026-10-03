package com.ahstudio.composition.graph

import com.ahstudio.composition.core.TimeMapper

@JvmInline value class LayerId(val value: Long)
@JvmInline value class CompositionId(val value: Long)

enum class LayerType { VIDEO, IMAGE, TEXT, SHAPE, STICKER, OVERLAY, EFFECT, ADJUSTMENT, MASK, CAMERA, NULL, PRECOMPOSITION }

enum class BlendMode {
    NORMAL, MULTIPLY, SCREEN, OVERLAY, SOFT_LIGHT, HARD_LIGHT, COLOR_DODGE, COLOR_BURN,
    DARKEN, LIGHTEN, DIFFERENCE, EXCLUSION, ADD, SUBTRACT, HUE, SATURATION, COLOR, LUMINOSITY
}
enum class CompositeOp { SOURCE_OVER, SOURCE_IN, SOURCE_OUT, SOURCE_ATOP,
    DESTINATION_OVER, DESTINATION_IN, DESTINATION_OUT, DESTINATION_ATOP }
enum class TrackMatteMode { NONE, ALPHA, ALPHA_INVERTED, LUMA, LUMA_INVERTED }
enum class MaskMode { ADD, SUBTRACT, INTERSECT, DIFFERENCE }

sealed class LayerPayload {
    data class Video(val sourceUri: String, val trimInUs: Long, val trimOutUs: Long, val speed: Float = 1f) : LayerPayload()
    data class Image(val sourceUri: String) : LayerPayload()
    data class Text(val textSpec: String) : LayerPayload()
    data class Shape(val pathData: PathData, val fillColor: Long, val strokeColor: Long = 0L, val strokeWidth: Float = 0f) : LayerPayload()
    data class PreComp(val compositionRef: CompositionId, val timeMapper: TimeMapper = TimeMapper.Identity) : LayerPayload()
    object Null : LayerPayload()
}

/** Simple serializable path: M/L/Q/C/Z commands with coords in layer space. */
data class PathData(val commands: List<Cmd>) {
    sealed class Cmd {
        data class M(val x: Float, val y: Float) : Cmd()
        data class L(val x: Float, val y: Float) : Cmd()
        data class Q(val cx: Float, val cy: Float, val x: Float, val y: Float) : Cmd()
        data class C(val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x: Float, val y: Float) : Cmd()
        object Z : Cmd()
    }
}

data class MaskInstance(
    val id: Long,
    val path: PathData,
    val mode: MaskMode = MaskMode.ADD,
    val opacity: Float = 1f,
    val featherPx: Float = 0f,
    val expansionPx: Float = 0f,
    val inverted: Boolean = false,
)

/** Property tracks: time->value pairs. If a property has no track, the static value is used. */
data class PropertyTrack<T>(val staticValue: T, val keyframes: List<Kf<T>> = emptyList()) {
    data class Kf<T>(val timeUs: Long, val value: T, val interp: Interp = Interp.LINEAR)
    enum class Interp { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, HOLD, BEZIER }

    fun valueAt(t: Long, lerp: (T, T, Float) -> T): T {
        if (keyframes.isEmpty()) return staticValue
        if (t <= keyframes.first().timeUs) return keyframes.first().value
        if (t >= keyframes.last().timeUs) return keyframes.last().value
        var i = 0
        while (i < keyframes.size - 1) {
            val a = keyframes[i]; val b = keyframes[i + 1]
            if (t in a.timeUs until b.timeUs) {
                if (a.interp == Interp.HOLD) return a.value
                val raw = (t - a.timeUs).toFloat() / (b.timeUs - a.timeUs)
                val f = when (a.interp) {
                    Interp.LINEAR -> raw
                    Interp.EASE_IN -> raw * raw
                    Interp.EASE_OUT -> 1f - (1f - raw) * (1f - raw)
                    Interp.EASE_IN_OUT -> if (raw < 0.5f) 2f * raw * raw else 1f - 2f * (1f - raw) * (1f - raw)
                    Interp.HOLD -> 0f
                    Interp.BEZIER -> raw
                }
                return lerp(a.value, b.value, f)
            }
            i++
        }
        return keyframes.last().value
    }
}

data class Transform2D(
    val positionX: PropertyTrack<Float> = PropertyTrack(0f),
    val positionY: PropertyTrack<Float> = PropertyTrack(0f),
    val scaleX: PropertyTrack<Float> = PropertyTrack(1f),
    val scaleY: PropertyTrack<Float> = PropertyTrack(1f),
    val rotationDeg: PropertyTrack<Float> = PropertyTrack(0f),
    val anchorX: PropertyTrack<Float> = PropertyTrack(0f),
    val anchorY: PropertyTrack<Float> = PropertyTrack(0f),
    val skewX: PropertyTrack<Float> = PropertyTrack(0f),
    val skewY: PropertyTrack<Float> = PropertyTrack(0f),
    val flipH: Boolean = false,
    val flipV: Boolean = false,
)

data class CompositionLayer(
    val id: LayerId,
    val type: LayerType,
    val name: String,
    val startTimeUs: Long,              // composition-local timeline range [start, end)
    val endTimeUs: Long,
    val insertionIndex: Int,            // stable tie-break for equal z
    val zOrder: Int = 0,                // higher = closer to viewer (0 = background)
    val visible: Boolean = true,
    val enabled: Boolean = true,
    val parentId: LayerId? = null,
    val transform: Transform2D = Transform2D(),
    val opacity: PropertyTrack<Float> = PropertyTrack(1f),
    val blendMode: BlendMode = BlendMode.NORMAL,
    val compositeOp: CompositeOp = CompositeOp.SOURCE_OVER,
    val trackMatteLayer: LayerId? = null,
    val trackMatteMode: TrackMatteMode = TrackMatteMode.NONE,
    val masks: List<MaskInstance> = emptyList(),
    val effectIds: List<Long> = emptyList(),   // ids into EffectEngine
    val payload: LayerPayload = LayerPayload.Null,
    val sourceVersion: Long = 0,               // bumped on media/asset change → cache key input
) {
    fun activeAt(t: Long) = t >= startTimeUs && t < endTimeUs
}
