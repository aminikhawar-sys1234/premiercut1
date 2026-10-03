package com.ahstudio.color.space

import com.ahstudio.color.math.Mat3
import kotlin.math.*

object ChromaticAdaptation {
    // Bradford
    private val Bradford = floatArrayOf(
        0.8951f, 0.2664f, -0.1614f,
        -0.7502f, 1.7135f, 0.0367f,
        0.0389f, -0.0685f, 1.0296f
    )
    private val BradfordInv = Mat3.inverse(Bradford)

    /** Matrix adapting from white point [sx,sy] to [dx,dy] (von Kries in Bradford cone space). */
    fun adapt(sx: Float, sy: Float, dx: Float, dy: Float): FloatArray {
        val s = cone(Mat3.mulVec(Bradford, xyz(sx, sy)))
        val d = cone(Mat3.mulVec(Bradford, xyz(dx, dy)))
        val scale = floatArrayOf(
            d[0] / s[0], 0f, 0f,
            0f, d[1] / s[1], 0f,
            0f, 0f, d[2] / s[2]
        )
        return Mat3.mul(BradfordInv, Mat3.mul(scale, Bradford))
    }

    private fun xyz(x: Float, y: Float) = floatArrayOf(x / y, 1f, (1f - x - y) / y)
    private fun cone(v: FloatArray) = floatArrayOf(abs(v[0]) + 1e-9f, abs(v[1]) + 1e-9f, abs(v[2]) + 1e-9f)
}

object ColorSpaceConverter {
    /**
     * Convert LINEAR RGB between spaces, with Bradford white adaptation.
     * Never apply transfer functions here — callers decode/encode separately.
     */
    fun convert(linearRgb: FloatArray, from: RgbColorSpace, to: RgbColorSpace): FloatArray {
        if (from === to) return linearRgb
        val toXyz = Mat3.mulVec(from.rgbToXyz, linearRgb)
        val adapted = Mat3.mulVec(ChromaticAdaptation.adapt(from.whiteXy[0], from.whiteXy[1], to.whiteXy[0], to.whiteXy[1]), toXyz)
        return Mat3.mulVec(to.xyzToRgb, adapted)
    }

    /** Full-space matrix input->working for GPU upload. */
    fun inputToWorking(from: RgbColorSpace, to: RgbColorSpace): FloatArray {
        if (from === to) return Mat3.identity()
        val adapt = ChromaticAdaptation.adapt(from.whiteXy[0], from.whiteXy[1], to.whiteXy[0], to.whiteXy[1])
        return Mat3.mul(to.xyzToRgb, Mat3.mul(adapt, from.rgbToXyz))
    }

    fun workingToOutput(from: RgbColorSpace, to: RgbColorSpace): FloatArray =
        inputToWorking(from, to)
}

/**
 * Gamut mapping (§42): binary-search chroma compression toward luma axis.
 * Far better than channel clipping; runs 6 iterations — GPU-cheap.
 */
object GamutMapping {
    fun compress(c: FloatArray, lumaCoefs: FloatArray): FloatArray {
        val y = lumaCoefs[0] * c[0] + lumaCoefs[1] * c[1] + lumaCoefs[2] * c[2]
        val d = floatArrayOf(c[0] - y, c[1] - y, c[2] - y)
        var lo = 0f; var hi = 1f
        if (inGamut(c)) return c
        repeat(6) {
            val m = (lo + hi) * 0.5f
            val t = floatArrayOf(y + d[0] * m, y + d[1] * m, y + d[2] * m)
            if (inGamut(t)) lo = m else hi = m
        }
        return floatArrayOf(y + d[0] * lo, y + d[1] * lo, y + d[2] * lo)
    }

    private fun inGamut(c: FloatArray) = c.all { it >= -1e-4f && it <= 1f + 1e-4f }
}
