package com.ahstudio.animation.properties

import com.ahstudio.animation.keyframes.EvaluatedValue
import com.ahstudio.animation.math.Vec2

enum class PropertyType { FLOAT, VEC2 }

/** Stable identifier for one animatable property of one target (layer/clip/effect/mask/text). */
data class BindingKey(val targetId: String, val property: String) {
    val unique: String get() = "$targetId|$property"
}

/** Standard property paths. Effect/Mask/Color instances use prefixed instance ids. */
object Props {
    const val POSITION = "Transform.Position"          // VEC2
    const val POSITION_X = "Transform.Position.X"      // FLOAT
    const val POSITION_Y = "Transform.Position.Y"
    const val SCALE = "Transform.Scale"                // VEC2 (percent)
    const val SCALE_X = "Transform.Scale.X"
    const val SCALE_Y = "Transform.Scale.Y"
    const val ROTATION = "Transform.Rotation"          // FLOAT degrees
    const val ANCHOR = "Transform.Anchor"              // VEC2
    const val OPACITY = "Transform.Opacity"            // FLOAT 0..100
    const val SKEW = "Transform.Skew"                  // VEC2
    const val CROP_LEFT = "Transform.Crop.Left"
    const val CROP_TOP = "Transform.Crop.Top"
    const val CROP_RIGHT = "Transform.Crop.Right"
    const val CROP_BOTTOM = "Transform.Crop.Bottom"

    const val EXPOSURE = "Color.Exposure"
    const val CONTRAST = "Color.Contrast"
    const val SATURATION = "Color.Saturation"
    const val TEMPERATURE = "Color.Temperature"
    const val TINT = "Color.Tint"
    const val HIGHLIGHTS = "Color.Highlights"
    const val SHADOWS = "Color.Shadows"
    const val LUT_INTENSITY = "Color.LutIntensity"

    const val EFFECT_PARAM_PREFIX = "Effect."
    const val MASK_PREFIX = "Mask."
    const val TEXT_PREFIX = "Text."

    fun effectParam(effectId: String, param: String) = "Effect.$effectId.$param"
    fun maskParam(maskId: String, param: String) = "Mask.$maskId.$param"
    fun textParam(param: String) = "Text.$param"
}

/**
 * A registered animatable property. Host supplies the apply lambda -- engine never touches render classes directly.
 */
class AnimatableProperty(
    val key: BindingKey,
    val type: PropertyType,
    val defaultValue: Double = 0.0,
    val defaultVec: Vec2 = Vec2.ZERO,
    val min: Double = Double.NEGATIVE_INFINITY,
    val max: Double = Double.POSITIVE_INFINITY,
    val applyFloat: ((Double) -> Unit)? = null,
    val applyVec2: ((Vec2) -> Unit)? = null
) {
    /** Applies an evaluated value to the host engine. Returns false if type/apply missing. */
    fun apply(ev: EvaluatedValue): Boolean = when {
        type == PropertyType.FLOAT && ev is EvaluatedValue.FloatV && applyFloat != null -> {
            applyFloat(ev.value.coerceIn(min, max)); true
        }
        type == PropertyType.VEC2 && ev is EvaluatedValue.Vec2V && applyVec2 != null -> {
            applyVec2(Vec2(ev.value.x.coerceIn(min, max), ev.value.y.coerceIn(min, max))); true
        }
        else -> false
    }
}
