package com.ahstudio.color.primary

import com.ahstudio.color.math.Mat3
import com.ahstudio.color.math.ColorMath
import com.ahstudio.color.space.RgbColorSpace
import kotlin.math.*

/**
 * Physically-based temperature/tint (§12):
 *  - temperature slider maps to target correlated color temperature on the Planckian locus (Krystek approx)
 *  - tint shifts target white along green-magenta (y) axis
 *  - gains derived through the WORKING space matrix + luminance normalization
 *  - slider = 0 -> exactly (1,1,1) — guaranteed neutral, cache-friendly
 */
object WhiteBalance {
    private const val REF_KELVIN = 6504f
    private val gainCache = HashMap<Long, FloatArray>()

    fun computeGains(temperature: Float, tint: Float, working: RgbColorSpace): FloatArray {
        if (temperature == 0f && tint == 0f) return floatArrayOf(1f, 1f, 1f)
        val key = (temperature.toRawBits().toLong() shl 32) or (tint.toRawBits().toLong() and 0xFFFFFFFFL)
        gainCache[key]?.let { return it }

        val targetK = (REF_KELVIN - temperature * 4000f).coerceIn(1500f, 20000f)
        // reference white (slider-neutral): locus at REF_KELVIN, tint 0
        val refXy = planckianXy(REF_KELVIN)
        val tgtXy = planckianXy(targetK).copyOf()
        tgtXy[1] += tint * 0.015f   // tint: green/magenta axis

        val invM = Mat3.inverse(working.rgbToXyz)
        val rgbRef = normalizeLuma(Mat3.mulVec(invM, ColorMath.xyToXyz(refXy[0], refXy[1])), working)
        val rgbTgt = normalizeLuma(Mat3.mulVec(invM, ColorMath.xyToXyz(tgtXy[0].coerceIn(0f, 0.9f), tgtXy[1].coerceIn(0.05f, 0.9f))), working)
        val gains = floatArrayOf(rgbTgt[0] / rgbRef[0], rgbTgt[1] / rgbRef[1], rgbTgt[2] / rgbRef[2])
        synchronized(gainCache) { if (gainCache.size > 256) gainCache.clear(); gainCache[key] = gains }
        return gains
    }

    private fun normalizeLuma(rgb: FloatArray, space: RgbColorSpace): FloatArray {
        val lum = space.lumaCoefficients()
        val y = lum[0] * rgb[0] + lum[1] * rgb[1] + lum[2] * rgb[2]
        val k = 1f / max(y, 1e-6f)
        return floatArrayOf(rgb[0] * k, rgb[1] * k, rgb[2] * k)
    }

    /** Krystek Planckian locus approximation (valid 1000K..15000K, extended conservatively). */
    fun planckianXy(kelvin: Float): FloatArray {
        val t = kelvin.coerceIn(1000f, 20000f)
        val u = (0.860117757f + 1.54118254e-4f * t + 1.28641212e-7f * t * t) /
                (1f + 8.42420235e-4f * t + 7.08146163e-7f * t * t)
        val v = (0.317398726f + 4.22806245e-5f * t + 4.20481691e-8f * t * t) /
                (1f - 2.89741816e-5f * t + 1.61456053e-7f * t * t)
        val den = 2f * u - 8f * v + 4f
        return floatArrayOf(3f * u / den, 2f * v / den)
    }
}
