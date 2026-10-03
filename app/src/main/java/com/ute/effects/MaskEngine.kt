package com.ute.effects

import com.ute.core.MathUtil

enum class MaskShape { RECTANGLE, ROUNDED_RECT, CIRCLE, ELLIPSE, LINEAR_GRADIENT, TEXT, IMAGE, VIDEO }

data class MaskSpec(
    val shape: MaskShape,
    /** Normalized center and half-size of the mask in layer space. */
    val centerX: Float = 0.5f, val centerY: Float = 0.5f,
    val halfW: Float = 0.5f,   val halfH: Float = 0.5f,
    val cornerRadius: Float = 0f,
    val featherPx: Float = 4f,
    val invert: Boolean = false,
    /** Animated masks: parameter keyed by track name, evaluated by LayerAnimator. */
    val animatedParameters: Set<String> = emptySet(),
    /** For TEXT masks: the mask text is shaped and rendered to its own SDF atlas. */
    val maskText: String? = null,
    /** For IMAGE/VIDEO masks: texture handle supplied by the host at render time. */
    val externalTextureId: Int = 0,
) {
    init {
        halfW.coerceAtLeast(0.001f); halfH.coerceAtLeast(0.001f)
        MathUtil.sanitize(featherPx, 0f)
    }
}
