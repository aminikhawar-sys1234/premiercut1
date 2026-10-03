package com.ahstudio.color.math

import kotlin.math.*

object ColorMath {

    @JvmStatic fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun clamp01(x: Float) = x.coerceIn(0f, 1f)

    // ---------- RGB <-> HSL (h in 0..1) ----------
    fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = 0.5f * (mx + mn); var h = 0f; var s = 0f
        val d = mx - mn
        if (d > 1e-6f) {
            s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
            h = when (mx) {
                r -> (g - b) / d + (if (g < b) 6f else 0f)
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } / 6f
        }
        return floatArrayOf(h, s, l)
    }

    fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
        if (s <= 0f) return floatArrayOf(l, l, l)
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        return floatArrayOf(hue2rgb(p, q, h + 1f / 3f), hue2rgb(p, q, h), hue2rgb(p, q, h - 1f / 3f))
    }

    private fun hue2rgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f; if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    /** Circular hue distance 0..0.5 */
    fun hueDistance(a: Float, b: Float): Float {
        val d = ((a - b) % 1f + 1f) % 1f
        return min(d, 1f - d)
    }

    // ---------- RGB <-> HSV ----------
    fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        val h = if (d <= 1e-6f) 0f else when (mx) {
            r -> (((g - b) / d + (if (g < b) 6f else 0f)) / 6f)
            g -> (((b - r) / d + 2f) / 6f)
            else -> (((r - g) / d + 4f) / 6f)
        }
        return floatArrayOf(h, if (mx <= 0f) 0f else d / mx, mx)
    }

    // ---------- XYZ <-> Lab (D65 default white) ----------
    private const val XN = 0.95047f; private const val YN = 1f; private const val ZN = 1.08883f

    fun xyzToLab(x: Float, y: Float, z: Float, white: FloatArray = floatArrayOf(XN, YN, ZN)): FloatArray {
        fun f(t: Float) = if (t > 0.008856451679f) cbrt(t) else (903.3f * t + 16f) / 116f
        val fx = f(x / white[0]); val fy = f(y / white[1]); val fz = f(z / white[2])
        return floatArrayOf(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }

    fun labToXyz(L: Float, a: Float, b: Float, white: FloatArray = floatArrayOf(XN, YN, ZN)): FloatArray {
        fun finv(t: Float): Float {
            val t3 = t * t * t
            return if (t3 > 0.008856451679f) t3 else (116f * t - 16f) / 903.3f
        }
        val fy = (L + 16f) / 116f; val fx = fy + a / 500f; val fz = fy - b / 200f
        return floatArrayOf(fx * white[0], finv(fy) * white[1], fz * white[2])
    }

    fun labToLch(L: Float, a: Float, b: Float): FloatArray {
        val c = sqrt(a * a + b * b)
        var h = atan2(b, a) * 180f / PI.toFloat()
        if (h < 0f) h += 360f
        return floatArrayOf(L, c, h)
    }

    fun lchToLab(L: Float, c: Float, hDeg: Float): FloatArray {
        val hr = hDeg * PI.toFloat() / 180f
        return floatArrayOf(L, c * cos(hr), c * sin(hr))
    }

    // ---------- Linear sRGB <-> OKLab (Björn Ottosson, public domain) ----------
    fun linearSrgbToOklab(r: Float, g: Float, b: Float): FloatArray {
        val l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b
        val m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b
        val s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b
        val l_ = cbrt(l); val m_ = cbrt(m); val s_ = cbrt(s)
        return floatArrayOf(
            0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_,
            1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_,
            0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_
        )
    }

    fun oklabToLinearSrgb(L: Float, a: Float, b: Float): FloatArray {
        val l_ = L + 0.3963377774f * a + 0.2158037573f * b
        val m_ = L - 0.1055613458f * a - 0.0638541728f * b
        val s_ = L - 0.0894841775f * a - 1.2914855480f * b
        val l = l_ * l_ * l_; val m = m_ * m_ * m_; val s = s_ * s_ * s_
        return floatArrayOf(
            +4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
            -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
            -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s
        )
    }

    fun oklabToOklch(L: Float, a: Float, b: Float): FloatArray {
        val c = sqrt(a * a + b * b)
        var h = atan2(b, a) * 180f / PI.toFloat(); if (h < 0f) h += 360f
        return floatArrayOf(L, c, h)
    }

    fun oklchToOklab(L: Float, c: Float, hDeg: Float): FloatArray {
        val hr = hDeg * PI.toFloat() / 180f
        return floatArrayOf(L, c * cos(hr), c * sin(hr))
    }

    // ---------- xy chromaticity -> XYZ (Y=1) ----------
    fun xyToXyz(x: Float, y: Float): FloatArray {
        require(y > 1e-6f)
        return floatArrayOf(x / y, 1f, (1f - x - y) / y)
    }
}
