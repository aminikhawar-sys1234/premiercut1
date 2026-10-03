package com.ute.color

import kotlin.math.*

/** sRGB ↔ linear, WCAG luminance/contrast, and OKLab — all real, all tested. */
object ColorMath {

    fun srgbToLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055) / 1.055).toDouble(), 2.4).toFloat()

    fun linearToSrgb(c: Float): Float =
        if (c <= 0.0031308f) c * 12.92f else 1.055f * Math.pow(c.toDouble(), 1.0 / 2.4).toFloat() - 0.055f

    fun linearFromArgb(argb: Int): FloatArray = floatArrayOf(
        srgbToLinear(((argb shr 16) and 0xFF) / 255f),
        srgbToLinear(((argb shr 8) and 0xFF) / 255f),
        srgbToLinear((argb and 0xFF) / 255f),
    )

    /** WCAG relative luminance (of an sRGB int color). */
    fun relativeLuminance(argb: Int): Float {
        val r = srgbToLinear(((argb shr 16) and 0xFF) / 255f)
        val g = srgbToLinear(((argb shr 8) and 0xFF) / 255f)
        val b = srgbToLinear((argb and 0xFF) / 255f)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun contrastRatio(a: Int, b: Int): Float {
        val la = relativeLuminance(a); val lb = relativeLuminance(b)
        val hi = max(la, lb); val lo = min(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    // ---------- OKLab (Björn Ottosson's reference transforms) ----------

    fun argbToOklab(argb: Int): FloatArray {
        val r = srgbToLinear(((argb shr 16) and 0xFF) / 255f)
        val g = srgbToLinear(((argb shr 8) and 0xFF) / 255f)
        val b = srgbToLinear((argb and 0xFF) / 255f)
        val l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b
        val m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b
        val s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b
        val l_ = Math.cbrt(l.toDouble()).toFloat()
        val m_ = Math.cbrt(m.toDouble()).toFloat()
        val s_ = Math.cbrt(s.toDouble()).toFloat()
        return floatArrayOf(
            0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_,
            1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_,
            0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_,
        )
    }

    fun oklabToArgb(L: Float, a: Float, b: Float, alpha: Int = 0xFF): Int {
        val l_ = L + 0.3963377774f * a + 0.2158037573f * b
        val m_ = L - 0.1055613458f * a - 0.0638541728f * b
        val s_ = L - 0.0894841775f * a - 1.2914855480f * b
        val l = l_ * l_ * l_; val m = m_ * m_ * m_; val s = s_ * s_ * s_
        var r = +4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
        var g = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
        var bl = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s
        r = linearToSrgb(r.coerceIn(0f, 1f)); g = linearToSrgb(g.coerceIn(0f, 1f)); bl = linearToSrgb(bl.coerceIn(0f, 1f))
        return (alpha shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (bl * 255).toInt()
    }

    /** Perceptually uniform interpolation in OKLab — the default text color interpolation. */
    fun mixOklab(c0: Int, c1: Int, t: Float): Int {
        val a0 = argbToOklab(c0); val a1 = argbToOklab(c1)
        return oklabToArgb(
            a0[0] + (a1[0] - a0[0]) * t,
            a0[1] + (a1[1] - a0[1]) * t,
            a0[2] + (a1[2] - a0[2]) * t,
            ((c0 ushr 24) + ((c1 ushr 24) - (c0 ushr 24)) * t).toInt().coerceIn(0, 255),
        )
    }

    fun adjustLightness(argb: Int, targetL: Float): Int {
        val lab = argbToOklab(argb)
        return oklabToArgb(targetL.coerceIn(0f, 1f), lab[1], lab[2], argb ushr 24)
    }

    fun hsvFromArgb(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f; val g = ((argb shr 8) and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return floatArrayOf(if (h < 0) h + 360f else h, if (max == 0f) 0f else d / max, max)
    }

    fun argbFromHsv(h: Float, s: Float, v: Float, alpha: Int = 0xFF): Int {
        val c = v * s
        val hp = (h % 360f + 360f) % 360f / 60f
        val x = c * (1 - kotlin.math.abs(hp % 2f - 1))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> floatArrayOf(c, x, 0f); 1 -> floatArrayOf(x, c, 0f)
            2 -> floatArrayOf(0f, c, x); 3 -> floatArrayOf(0f, x, c)
            4 -> floatArrayOf(x, 0f, c); else -> floatArrayOf(c, 0f, x)
        }
        val m = v - c
        return (alpha shl 24) or (((r1 + m) * 255).toInt() shl 16) or
               (((g1 + m) * 255).toInt() shl 8) or ((b1 + m) * 255).toInt()
    }
}
