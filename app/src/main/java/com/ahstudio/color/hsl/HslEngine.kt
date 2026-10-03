package com.ahstudio.color.hsl

import com.ahstudio.color.core.HslAdjust
import com.ahstudio.color.core.HslBands
import com.ahstudio.color.math.ColorMath
import com.ahstudio.color.primary.PipelineConsts
import kotlin.math.max

/**
 * 8-band HSL with smooth OVERLAPPING ranges. Weights are normalized (Σw),
 * so overlapping bands never double-process — pro behavior (§14).
 */
object HslEngine {

    // Band centers in hue (0..1): R, O, Y, G, A, B, P, M
    val CENTERS = floatArrayOf(
        0f, 30f / 360f, 60f / 360f, 120f / 360f,
        180f / 360f, 240f / 360f, 285f / 360f, 315f / 360f
    )

    fun bandWeights(hue: Float): FloatArray {
        val w = FloatArray(8)
        var sum = 0f
        for (i in 0 until 8) {
            val d = ColorMath.hueDistance(hue, CENTERS[i])
            val wi = 1f - ColorMath.smoothstep(PipelineConsts.HSL_CORE, PipelineConsts.HSL_CORE + PipelineConsts.HSL_FALLOFF, d)
            w[i] = wi; sum += wi
        }
        if (sum > 1e-5f) for (i in 0 until 8) w[i] /= sum
        return w
    }

    /** Apply in HSL space. h,s,l in 0..1. Returns adjusted hsl. */
    fun applyHsl(h: Float, s: Float, l: Float, bands: HslBands): FloatArray {
        if (bands.isZero()) return floatArrayOf(h, s, l)
        val w = bandWeights(h)
        val adj = bands.toArray()
        var dH = 0f; var dS = 0f; var dL = 0f
        for (i in 0 until 8) {
            val a: HslAdjust = adj[i]
            dH += a.hueShift * w[i]
            dS += a.sat * w[i]
            dL += a.lum * w[i]
        }
        var nh = h + dH; nh = ((nh % 1f) + 1f) % 1f
        val ns = (s * (1f + dS)).coerceIn(0f, 1f)
        val nl = (l + dL * 0.5f).coerceIn(0f, 1f)
        return floatArrayOf(nh, ns, nl)
    }

    /** HSL qualifier mask with softness + inversion (§15). Returns soft matte 0..1. */
    fun qualifierMask(h: Float, s: Float, l: Float,
                      hC: Float, hW: Float, hS: Float,
                      sC: Float, sW: Float, sS: Float,
                      lC: Float, lW: Float, lS: Float,
                      invert: Boolean): Float {
        val dh = ColorMath.hueDistance(h, hC)
        var m = 1f - ColorMath.smoothstep(hW, hW + max(hS, 1e-3f), dh)
        m *= 1f - ColorMath.smoothstep(sW, sW + max(sS, 1e-3f), kotlin.math.abs(s - sC))
        m *= 1f - ColorMath.smoothstep(lW, lW + max(lS, 1e-3f), kotlin.math.abs(l - lC))
        return if (invert) 1f - m else m
    }
}
