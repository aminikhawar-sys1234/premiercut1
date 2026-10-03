package com.vfx.engine.core.color

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** CPU-side color math — reference for GLSL chunks; used by tests and LUT building. */
object ColorScience {
    const val LUMA_R_709 = 0.2126f
    const val LUMA_G_709 = 0.7152f
    const val LUMA_B_709 = 0.0722f

    fun luma709(r: Float, g: Float, b: Float) = LUMA_R_709 * r + LUMA_G_709 * g + LUMA_B_709 * b
    fun luma601(r: Float, g: Float, b: Float) = 0.299f * r + 0.587f * g + 0.114f * b

    // ---------- sRGB <-> linear ----------
    fun srgbToLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

    fun linearToSrgb(c: Float): Float =
        if (c <= 0.0031308f) c * 12.92f else 1.055f * max(c, 0f).pow(1f / 2.4f) - 0.055f

    fun srgbToLinear(rgb: FloatArray): FloatArray = FloatArray(3) { srgbToLinear(rgb[it]) }
    fun linearToSrgb(rgb: FloatArray): FloatArray = FloatArray(3) { linearToSrgb(rgb[it]) }

    // ---------- HSV / HSL ----------
    /** Returns (hue 0..360, sat 0..1, val 0..1). */
    fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return floatArrayOf(if (h < 0) h + 360f else h, if (mx == 0f) 0f else d / mx, mx)
    }

    fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
        val c = v * s
        val hh = (h % 360f + 360f) % 360f / 60f
        val x = c * (1f - abs(hh % 2f - 1f))
        val (r1, g1, b1) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        val m = v - c
        return floatArrayOf(r1 + m, g1 + m, b1 + m)
    }

    /** Returns (hue 0..360, sat 0..1, lum 0..1). */
    fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val s = if (mx == mn) 0f else (mx - mn) / (1f - abs(2f * l - 1f))
        val h = rgbToHsv(r, g, b)[0]
        return floatArrayOf(h, s.coerceIn(0f, 1f), l)
    }

    fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
        val c = (1f - abs(2f * l - 1f)) * s
        val hh = (h % 360f + 360f) % 360f / 60f
        val x = c * (1f - abs(hh % 2f - 1f))
        val (r1, g1, b1) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return floatArrayOf(r1 + m, g1 + m, b1 + m)
    }

    /** Shortest hue distance in degrees (0..180). */
    fun hueDistance(a: Float, b: Float): Float {
        val d = abs((a - b) % 360f)
        return min(d, 360f - d)
    }

    // ---------- exposure / temperature ----------
    /** Exposure in stops -> linear multiplier. +1 stop = 2x. */
    fun exposureMultiplier(stops: Float) = 2f.pow(stops)

    /**
     * Tanner Helland approximation of Kelvin -> RGB (normalized so green channel = 1).
     * Result: per-channel multipliers for white-balance style temperature.
     */
    fun kelvinToRgbMultipliers(kelvin: Float): FloatArray {
        val t = kelvin.coerceIn(1000f, 40000f) / 100f
        val r = if (t <= 66f) 255f else 329.698727446f * (t - 60f).pow(-0.1332047592f)
        val g = if (t <= 66f) 99.4708025861f * ln(t) - 161.1195681661f
                else 288.1221695283f * (t - 60f).pow(-0.0755148492f)
        val b = if (t >= 66f) 255f
                else if (t <= 19f) 0f
                else 138.5177312231f * ln(t - 10f) - 305.0447927307f
        val rf = (r / 255f).coerceIn(0f, 1f)
        val gf = (g / 255f).coerceIn(0f, 1f)
        val bf = (b / 255f).coerceIn(0f, 1f)
        return floatArrayOf(rf / gf, 1f, bf / gf)
    }

    /** Lift/Gamma/Gain per channel: out = (in * (gain - lift) + lift) ^ (1/gamma). */
    fun liftGammaGain(c: Float, lift: Float, gamma: Float, gain: Float): Float {
        val x = (c * (gain - lift) + lift).coerceIn(0f, 1f)
        return x.pow(1f / gamma.coerceAtLeast(0.01f))
    }

    /** Reinhard-style tone mapping (HDR-ready architecture hook). */
    fun reinhard(x: Float) = x / (1f + x)

    /** ACES-approximation filmic tone map (Narkowicz). */
    fun acesApprox(x: Float): Float {
        val a = 2.51f; val b = 0.03f; val c = 2.43f; val d = 0.59f; val e = 0.14f
        return ((x * (a * x + b)) / (x * (c * x + d) + e)).coerceIn(0f, 1f)
    }
}
