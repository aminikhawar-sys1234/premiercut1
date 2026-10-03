package com.vfx.engine.core.blend

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Blend modes matching the GLSL blend chunk (Part 6) 1:1.
 * Convention: b = base (below), s = source (effect/layer on top). Straight alpha.
 * The integer codes MUST stay in sync with the GLSL blendC() dispatch table.
 */
enum class BlendMode(val glslCode: Int) {
    NORMAL(0), MULTIPLY(1), SCREEN(2), OVERLAY(3), SOFT_LIGHT(4), HARD_LIGHT(5),
    DARKEN(6), LIGHTEN(7), DIFFERENCE(8), EXCLUSION(9), ADD(10), SUBTRACT(11),
    COLOR_DODGE(12), COLOR_BURN(13);

    companion object {
        fun fromCode(code: Int): BlendMode = entries.firstOrNull { it.glslCode == code } ?: NORMAL
        val ADDITIVE = ADD
    }
}

enum class PorterDuffMode {
  SRC_OVER,
  DST_OVER,
  SRC_IN,
  DST_IN,
  SRC_OUT,
  DST_OUT,
  CLEAR
}

/** CPU reference implementation — the GLSL chunk produces identical results. */
object BlendMath {

    /** Per-channel blend (non-separable approximation for the separable set). */
    fun blendChannel(mode: BlendMode, b: Float, s: Float): Float = when (mode) {
        BlendMode.NORMAL -> s
        BlendMode.MULTIPLY -> b * s
        BlendMode.SCREEN -> b + s - b * s
        BlendMode.OVERLAY -> if (b < 0.5f) 2f * b * s else 1f - 2f * (1f - b) * (1f - s)
        BlendMode.SOFT_LIGHT -> (1f - 2f * s) * b * b + 2f * s * b   // Pegtop approximation
        BlendMode.HARD_LIGHT -> if (s < 0.5f) 2f * b * s else 1f - 2f * (1f - b) * (1f - s)
        BlendMode.DARKEN -> min(b, s)
        BlendMode.LIGHTEN -> max(b, s)
        BlendMode.DIFFERENCE -> abs(b - s)
        BlendMode.EXCLUSION -> b + s - 2f * b * s
        BlendMode.ADD -> (b + s).coerceAtMost(1f)
        BlendMode.SUBTRACT -> (b - s).coerceAtLeast(0f)
        BlendMode.COLOR_DODGE -> if (s >= 1f) 1f else (b / (1f - s)).coerceAtMost(1f)
        BlendMode.COLOR_BURN -> if (s <= 0f) 0f else (1f - (1f - b) / s).coerceAtLeast(0f)
    }

    fun blendRgb(mode: BlendMode, base: FloatArray, src: FloatArray): FloatArray =
        FloatArray(3) { blendChannel(mode, base[it], src[it]) }

    /**
     * Source-over composite of a blended result with correct straight-alpha behavior:
     * blended color where source alpha covers, base shows through underneath.
     * Returns (r, g, b, a).
     */
    fun composite(
        mode: BlendMode,
        br: Float, bg: Float, bb: Float, ba: Float,
        sr: Float, sg: Float, sb: Float, sa: Float,
        opacity: Float
    ): FloatArray {
        val a = (sa * opacity).coerceIn(0f, 1f)
        if (a <= 0f) return floatArrayOf(br, bg, bb, ba)
        val outA = a + ba * (1f - a)
        if (outA <= 1e-6f) return floatArrayOf(0f, 0f, 0f, 0f)
        val r = (blendChannel(mode, br, sr) * a + br * ba * (1f - a)) / outA
        val g = (blendChannel(mode, bg, sg) * a + bg * ba * (1f - a)) / outA
        val bl = (blendChannel(mode, bb, sb) * a + bb * ba * (1f - a)) / outA
        return floatArrayOf(r, g, bl, outA)
    }

    /** Vector convenience wrapper around [composite]. */
    fun composite(mode: BlendMode, base: FloatArray, src: FloatArray, opacity: Float): FloatArray =
        composite(mode, base[0], base[1], base[2], base[3], src[0], src[1], src[2], src[3], opacity)
}
