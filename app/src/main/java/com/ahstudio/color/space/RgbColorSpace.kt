package com.ahstudio.color.space

import com.ahstudio.color.math.Mat3

/**
 * RGB color space defined BY CHROMATICITIES — matrices are computed, not transcribed,
 * eliminating hard-coded constant errors. Operates in LINEAR light only.
 */
class RgbColorSpace(
    val name: String,
    rx: Float, ry: Float,
    gx: Float, gy: Float,
    bx: Float, by: Float,
    wx: Float, wy: Float,
    val transfer: TransferFunction
) {
    val rgbToXyz: FloatArray
    val xyzToRgb: FloatArray
    val whiteXy: FloatArray = floatArrayOf(wx, wy)

    init {
        val Xr = rx / ry; val Yr = 1f; val Zr = (1f - rx - ry) / ry
        val Xg = gx / gy; val Yg = 1f; val Zg = (1f - gx - gy) / gy
        val Xb = bx / by; val Yb = 1f; val Zb = (1f - bx - by) / by
        val Xw = wx / wy; val Yw = 1f; val Zw = (1f - wx - wy) / wy
        // Solve [Xr Xg Xb; Yr Yg Yb; Zr Zg Zb] * [Sr Sg Sb] = [Xw Yw Zw]
        val m = floatArrayOf(Xr, Xg, Xb, Yr, Yg, Yb, Zr, Zg, Zb)
        val s = Mat3.mulVec(Mat3.inverse(m), floatArrayOf(Xw, Yw, Zw))
        rgbToXyz = floatArrayOf(
            Xr * s[0], Xg * s[1], Xb * s[2],
            Yr * s[0], Yg * s[1], Yb * s[2],
            Zr * s[0], Zg * s[1], Zb * s[2]
        )
        xyzToRgb = Mat3.inverse(rgbToXyz)
    }

    /** Luma coefficients = second row of RGB->XYZ (Y row). Mathematically correct per-space. */
    fun lumaCoefficients(): FloatArray = floatArrayOf(rgbToXyz[3], rgbToXyz[4], rgbToXyz[5])
}
