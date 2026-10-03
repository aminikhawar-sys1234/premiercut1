package com.ute.model

/** Fill spec: solid, gradient, or 3D-material reference. All data-driven. */
data class Appearance(
    val fill: PaintSpec = PaintSpec.Solid(0xFFFFFFFF.toInt()),
    val outline: OutlineSpec? = null,
    val shadow: ShadowSpec? = null,
    val glow: GlowSpec? = null,
    val background: BackgroundSpec? = null,
    val opacity: Float = 1f,
)

sealed class PaintSpec {
    data class Solid(val color: Int) : PaintSpec()
    data class Gradient(val gradient: GradientSpec) : PaintSpec()
    data class Material(val materialId: String, val tint: Int = 0xFFFFFFFF.toInt()) : PaintSpec()
}

enum class GradientType { LINEAR, RADIAL, ANGULAR }

data class GradientSpec(
    val type: GradientType = GradientType.LINEAR,
    val stops: List<GradientStop> = listOf(GradientStop(0f, 0xFFFFFFFF.toInt()), GradientStop(1f, 0xFF000000.toInt())),
    /** Gradient axis in normalized layer space: start→end. */
    val startX: Float = 0f, val startY: Float = 0f,
    val endX: Float = 1f,   val endY: Float = 0f,
    /** Animated gradients: hue shift in degrees per second. 0 = static. */
    val hueRotationPerSec: Float = 0f,
)

data class GradientStop(val position: Float, val color: Int)

data class OutlineSpec(
    val color: Int = 0xFF000000.toInt(),
    val widthPx: Float = 4f,
    val opacity: Float = 1f,
    /** Additional concentric outline layers, rendered behind the primary. */
    val extraLayers: List<OutlineSpec> = emptyList(),
)

data class ShadowSpec(
    val color: Int = 0x99000000.toInt(),
    val distancePx: Float = 6f,
    val angleDeg: Float = 45f,       // 0 = right, CCW positive
    val softnessPx: Float = 8f,      // 0 = hard
    val spreadPx: Float = 0f,
    val inner: Boolean = false,
)

data class GlowSpec(
    val color: Int = 0xFF00E5FF.toInt(),
    val radiusPx: Float = 12f,
    val strength: Float = 1f,
    val bloom: Boolean = false,      // adds multi-pass GPU blur bloom
)

data class BackgroundSpec(
    val color: Int = 0xCC000000.toInt(),
    val cornerRadiusPx: Float = 8f,
    val paddingH: Float = 12f,
    val paddingV: Float = 6f,
)
