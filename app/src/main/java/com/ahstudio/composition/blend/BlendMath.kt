package com.ahstudio.composition.blend

import com.ahstudio.composition.graph.BlendMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object BlendMath {
    fun blend(mode: BlendMode, b: Float, s: Float): Float = when (mode) {
        BlendMode.NORMAL -> s
        BlendMode.MULTIPLY -> b * s
        BlendMode.SCREEN -> b + s - b * s
        BlendMode.OVERLAY -> hardLight(s, b)                       // W3C: Overlay(B,S)=HardLight(S,B)
        BlendMode.SOFT_LIGHT -> softLight(b, s)
        BlendMode.HARD_LIGHT -> hardLight(b, s)
        BlendMode.COLOR_DODGE -> when { b <= 0f -> 0f; s >= 1f -> 1f; else -> min(1f, b / (1f - s)) }
        BlendMode.COLOR_BURN  -> when { b >= 1f -> 1f; s <= 0f -> 0f; else -> 1f - min(1f, (1f - b) / s) }
        BlendMode.DARKEN -> min(b, s)
        BlendMode.LIGHTEN -> max(b, s)
        BlendMode.DIFFERENCE -> abs(b - s)
        BlendMode.EXCLUSION -> b + s - 2f * b * s
        BlendMode.ADD -> min(1f, b + s)
        BlendMode.SUBTRACT -> max(0f, b - s)
        else -> s
    }
    fun hardLight(b: Float, s: Float) = if (s <= 0.5f) b * (2f * s) else b + (2f * s - 1f) - b * (2f * s - 1f)
    fun softLight(b: Float, s: Float): Float =
        if (s <= 0.5f) b - (1f - 2f * s) * b * (1f - b)
        else b + (2f * s - 1f) * (if (b <= 0.25f) ((16f * b - 12f) * b + 4f) * b else sqrt(b))

    fun lum(c: FloatArray) = 0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2]
    fun clipColor(c: FloatArray): FloatArray {
        val l = lum(c); val n = min(c[0], min(c[1], c[2])); val x = max(c[0], max(c[1], c[2]))
        val o = c.copyOf()
        if (n < 0f) for (i in 0..2) o[i] = l + (o[i] - l) * l / (l - n)
        if (x > 1f) for (i in 0..2) o[i] = l + (o[i] - l) * (1f - l) / (x - l)
        return o
    }
    fun setLum(c: FloatArray, l: Float) = clipColor(floatArrayOf(c[0] + l - lum(c), c[1] + l - lum(c), c[2] + l - lum(c)))
    fun sat(c: FloatArray) = max(c[0], max(c[1], c[2])) - min(c[0], min(c[1], c[2]))
    fun setSat(c: FloatArray, s: Float): FloatArray {
        val mn = min(c[0], min(c[1], c[2])); val mx = max(c[0], max(c[1], c[2])); val rg = mx - mn
        return FloatArray(3) { i -> if (c[i] >= mx) s else if (rg > 0f) (c[i] - mn) * s / rg else 0f }
    }
    fun blendRgb(mode: BlendMode, cb: FloatArray, cs: FloatArray): FloatArray = when (mode) {
        BlendMode.HUE -> setLum(setSat(cs, sat(cb)), lum(cb))
        BlendMode.SATURATION -> setLum(setSat(cb, sat(cs)), lum(cb))
        BlendMode.COLOR -> setLum(cs, lum(cb))
        BlendMode.LUMINOSITY -> setLum(cb, lum(cs))
        else -> floatArrayOf(blend(mode, cb[0], cs[0]), blend(mode, cb[1], cs[1]), blend(mode, cb[2], cs[2]))
    }

    /** W3C compositing-1. Inputs: straight alpha. Output: premultiplied RGBA (no halos by construction). */
    fun composite(mode: BlendMode, cb: FloatArray, ab: Float, cs: FloatArray, asr: Float): FloatArray {
        val ao = asr + ab * (1f - asr)
        val B = blendRgb(mode, cb, cs)
        val co = FloatArray(3) { i -> asr * (1f - ab) * cs[i] + asr * ab * B[i] + (1f - asr) * ab * cb[i] }
        return floatArrayOf(co[0], co[1], co[2], ao)
    }

    /** Porter-Duff operators on premultiplied inputs → premultiplied output. */
    fun porterDuff(op: Int, CsPm: FloatArray, asVal: Float, CbPm: FloatArray, ab: Float): FloatArray {
        val o = FloatArray(4)
        when (op) {
            0 -> { for (i in 0..2) o[i] = CsPm[i] + CbPm[i] * (1f - asVal); o[3] = asVal + ab * (1f - asVal) }
            1 -> { for (i in 0..2) o[i] = CsPm[i] * ab; o[3] = asVal * ab }
            2 -> { for (i in 0..2) o[i] = CsPm[i] * (1f - ab); o[3] = asVal * (1f - ab) }
            3 -> { for (i in 0..2) o[i] = CsPm[i] * ab + CbPm[i] * (1f - asVal); o[3] = ab }
            4 -> { for (i in 0..2) o[i] = CbPm[i] + CsPm[i] * (1f - ab); o[3] = ab + asVal * (1f - ab) }
            5 -> { for (i in 0..2) o[i] = CbPm[i] * asVal; o[3] = asVal * ab }
            6 -> { for (i in 0..2) o[i] = CbPm[i] * (1f - asVal); o[3] = ab * (1f - asVal) }
            else -> { for (i in 0..2) o[i] = CbPm[i] * asVal + CsPm[i] * (1f - ab); o[3] = asVal }
        }
        return o
    }
}
