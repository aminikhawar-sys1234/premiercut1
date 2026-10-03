package com.ahstudio.color.core

import kotlinx.serialization.Serializable

/** Monotone-safe curve point, 0..1 normalized domain. */
@Serializable
data class CurvePoint(val x: Float, val y: Float) {
    init { require(x in 0f..1f && !x.isNaN() && !y.isNaN() && y in -0.5f..1.5f) { "CurvePoint out of domain" } }
}

@Serializable
data class CurveSet(
    val master: List<CurvePoint> = DEFAULT,
    val luma: List<CurvePoint> = DEFAULT,
    val red: List<CurvePoint> = DEFAULT,
    val green: List<CurvePoint> = DEFAULT,
    val blue: List<CurvePoint> = DEFAULT
) {
    companion object {
        val DEFAULT = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
        val IDENTITY = CurveSet()
    }
    fun isIdentity(): Boolean = this == IDENTITY
}

/** One HSL band: hueShift -0.5..0.5 (of full circle), sat -1..1, lum -1..1 */
@Serializable
data class HslAdjust(
    val hueShift: Float = 0f,
    val sat: Float = 0f,
    val lum: Float = 0f
) { fun isZero() = hueShift == 0f && sat == 0f && lum == 0f }

@Serializable
data class HslBands(
    val reds: HslAdjust = HslAdjust(),
    val oranges: HslAdjust = HslAdjust(),
    val yellows: HslAdjust = HslAdjust(),
    val greens: HslAdjust = HslAdjust(),
    val aquas: HslAdjust = HslAdjust(),
    val blues: HslAdjust = HslAdjust(),
    val purples: HslAdjust = HslAdjust(),
    val magentas: HslAdjust = HslAdjust()
) {
    fun toArray(): List<HslAdjust> = listOf(reds, oranges, yellows, greens, aquas, blues, purples, magentas)
    fun isZero(): Boolean = toArray().all { it.isZero() }
}

/** Color wheel vector stored as centered RGB (-1..1) + master scalar. */
@Serializable
data class WheelVec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f, val master: Float = 0f) {
    fun isZero() = x == 0f && y == 0f && z == 0f && master == 0f
    companion object {
        /** Build from hue(deg)/sat so UI can present a color wheel. */
        fun fromHsl(hueDeg: Float, sat: Float, master: Float = 0f): WheelVec3 {
            val rgb = com.ahstudio.color.math.ColorMath.hslToRgb(hueDeg / 360f, sat.coerceIn(0f, 1f), 0.5f)
            return WheelVec3(rgb[0] - 0.5f, rgb[1] - 0.5f, rgb[2] - 0.5f, master)
        }
    }
}

@Serializable
data class WheelState(
    val lift: WheelVec3 = WheelVec3(),
    val gamma: WheelVec3 = WheelVec3(),
    val gain: WheelVec3 = WheelVec3(),
    val offset: WheelVec3 = WheelVec3()
) { fun isZero() = lift.isZero() && gamma.isZero() && gain.isZero() && offset.isZero() }

/** LUT is referenced by id; binary data lives in LutRepository (host-provided). */
@Serializable
data class LutState(
    val lutId: String = "",
    val intensity: Float = 1f,
    /** Technical (log->display) | Creative | Display — placement differs. */
    val kind: String = "creative",
    /** "before_grade" or "after_grade" — configurable placement. */
    val placement: String = "after_grade"
) { fun isValid() = lutId.isNotBlank() }

@Serializable
data class SplitToneState(
    val shadowHue: Float = 215f / 360f,
    val shadowSat: Float = 0f,
    val highlightHue: Float = 40f / 360f,
    val highlightSat: Float = 0f,
    val balance: Float = 0f,
    val strength: Float = 0.5f
) { fun isZero() = shadowSat == 0f && highlightSat == 0f }

@Serializable
data class ChannelMixerState(
    val rr: Float = 1f, val rg: Float = 0f, val rb: Float = 0f,
    val gr: Float = 0f, val gg: Float = 1f, val gb: Float = 0f,
    val br: Float = 0f, val bg: Float = 0f, val bb: Float = 1f
) { fun isIdentity() = rr == 1f && gg == 1f && bb == 1f && rg == 0f && rb == 0f && gr == 0f && gb == 0f && br == 0f && bg == 0f }

@Serializable
data class BlackWhiteState(
    val enabled: Boolean = false,
    val weightR: Float = 0.30f,
    val weightG: Float = 0.59f,
    val weightB: Float = 0.11f,
    val strength: Float = 1f,
    val contrast: Float = 0f,
    val toneHue: Float = 35f / 360f,
    val toneSat: Float = 0f
)

/** Secondary (qualified) correction. Soft HSL-qualifier + gain. */
@Serializable
data class SecondaryCorrection(
    val enabled: Boolean = true,
    // qualifier
    val hueCenter: Float = 0f, val hueWidth: Float = 0.08f, val hueSoftness: Float = 0.06f,
    val satCenter: Float = 0.5f, val satWidth: Float = 0.5f, val satSoftness: Float = 0.2f,
    val lumCenter: Float = 0.5f, val lumWidth: Float = 0.5f, val lumSoftness: Float = 0.2f,
    val invert: Boolean = false,
    // correction
    val gainR: Float = 1f, val gainG: Float = 1f, val gainB: Float = 1f,
    val intensity: Float = 1f,
    /** Optional external mask id resolved by host (Bezier/tracked/ML mask). */
    val maskId: String? = null
)

enum class ToneMapMode(val id: Int) { NONE(0), ACES(1), REINHARD(2), HABLE(3) }

@Serializable
data class HdrState(
    val toneMap: ToneMapMode = ToneMapMode.NONE,
    /** 0..1 — extra highlight roll-off before output, protects SDR targets. */
    val highlightRollOff: Float = 0f,
    val gamutCompress: Boolean = false
)

/**
 * THE non-destructive color state. Never mutates media; fully serializable.
 */
@Serializable
data class ColorState(
    val version: Int = CURRENT_VERSION,
    // Primaries
    val exposure: Float = 0f,        // stops, -5..5
    val contrast: Float = 0f,        // -1..1
    val contrastPivot: Float = 0.4f, // display-referred pivot
    val highlights: Float = 0f,      // -1..1
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,     // -1..1
    val tint: Float = 0f,            // -1..1 (green+ / magenta-)
    val saturation: Float = 0f,      // -1..1
    val vibrance: Float = 0f,
    val skinProtect: Float = 0.5f,   // 0..1 vibrance skin protection
    // Structured
    val curves: CurveSet = CurveSet.IDENTITY,
    val hsl: HslBands = HslBands(),
    val wheels: WheelState = WheelState(),
    val lut: LutState? = null,
    val splitTone: SplitToneState = SplitToneState(),
    val mixer: ChannelMixerState = ChannelMixerState(),
    val bw: BlackWhiteState = BlackWhiteState(),
    val secondaries: List<SecondaryCorrection> = emptyList(),
    val hdr: HdrState = HdrState()
) {
    companion object { const val CURRENT_VERSION = 2 }

    fun isIdentity(): Boolean = this == DEFAULT
    val DEFAULT get() = ColorState()

    /** Fingerprint for pipeline caching (§60). */
    fun fingerprint(): Int {
        var h = exposure.toRawBits()
        h = 31 * h + contrast.toRawBits(); h = 31 * h + contrastPivot.toRawBits()
        h = 31 * h + highlights.toRawBits(); h = 31 * h + shadows.toRawBits()
        h = 31 * h + whites.toRawBits(); h = 31 * h + blacks.toRawBits()
        h = 31 * h + temperature.toRawBits(); h = 31 * h + tint.toRawBits()
        h = 31 * h + saturation.toRawBits(); h = 31 * h + vibrance.toRawBits()
        h = 31 * h + curves.hashCode(); h = 31 * h + hsl.hashCode()
        h = 31 * h + wheels.hashCode(); h = 31 * h + (lut?.hashCode() ?: 0)
        h = 31 * h + splitTone.hashCode(); h = 31 * h + mixer.hashCode()
        h = 31 * h + bw.hashCode(); h = 31 * h + secondaries.hashCode()
        h = 31 * h + hdr.hashCode()
        return h
    }

    /** Animatable property paths for KeyframeAnimationEngine (§30). */
    val ANIMATABLE: List<String> = listOf(
        "exposure", "contrast", "contrastPivot", "highlights", "shadows", "whites", "blacks",
        "temperature", "tint", "saturation", "vibrance", "skinProtect",
        "splitTone.shadowHue", "splitTone.shadowSat", "splitTone.highlightHue", "splitTone.highlightSat",
        "splitTone.balance", "splitTone.strength"
    )
}
