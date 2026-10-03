package com.ahstudio.color

import com.ahstudio.color.core.ColorConfig
import com.ahstudio.color.core.ColorState
import com.ahstudio.color.curves.CurveBaker
import com.ahstudio.color.hsl.HslEngine
import com.ahstudio.color.lut.Lut1D
import com.ahstudio.color.lut.Lut3D
import com.ahstudio.color.lut.LutSampler
import com.ahstudio.color.math.ColorMath
import com.ahstudio.color.math.Mat3
import com.ahstudio.color.primary.PipelineConsts as PC
import com.ahstudio.color.primary.WhiteBalance
import com.ahstudio.color.space.ColorSpaceConverter
import com.ahstudio.color.space.GamutMapping
import com.ahstudio.color.space.TransferFunctions
import kotlin.math.*

/**
 * CPU reference implementation of the EXACT GPU pipeline order. Used for:
 *  - golden tests + preview/export parity verification
 *  - CPU export fallback on devices without usable GL
 * Stage order (identical to shader):
 *  decode -> toWorking -> exposure -> WB -> tone -> contrast -> wheels
 *  -> encodeWorking -> curves -> HSL -> sat/vib -> split/BW -> mixer -> LUT
 *  -> secondaries -> decodeWorking -> toneMap -> toOutput -> gamut -> encodeOut
 */
class ColorPipelineCpu(private val cfg: ColorConfig) {

    fun process(state: ColorState, rgba: FloatArray, lut3d: Lut3D? = null, lut1d: Lut1D? = null) {
        val work = cfg.workingSpace
        val lum = work.lumaCoefficients()
        val inSpace = cfg.inputMetadata.colorSpace ?: work
        val inTf = cfg.inputMetadata.transfer ?: TransferFunctions.REC709
        val mIn = ColorSpaceConverter.inputToWorking(inSpace, work)
        val mOut = ColorSpaceConverter.workingToOutput(work, cfg.outputSpace)
        val wb = WhiteBalance.computeGains(state.temperature, state.tint, work)
        val exposureMul = 2.0.pow(state.exposure.toDouble()).toFloat()
        val curveMain: FloatArray?; val curveLuma: FloatArray?
        if (!state.curves.isIdentity()) { val p = CurveBaker.bakeCombined(state.curves); curveMain = p.first; curveLuma = p.second }
        else { curveMain = null; curveLuma = null }
        val hasLut = state.lut?.isValid() == true
        val lutIntensity = state.lut?.intensity ?: 0f

        val tmp = FloatArray(3)
        var i = 0
        while (i < rgba.size) {
            var c = floatArrayOf(rgba[i], rgba[i + 1], rgba[i + 2])

            // 1. decode
            c = floatArrayOf(inTf.decode(c[0]), inTf.decode(c[1]), inTf.decode(c[2]))
            // 2. to working
            c = Mat3.mulVec(mIn, c)
            // 3-6. primaries
            c[0] *= exposureMul; c[1] *= exposureMul; c[2] *= exposureMul
            c[0] *= wb[0]; c[1] *= wb[1]; c[2] *= wb[2]
            c = toneControls(c, state, lum)
            c = contrast(c, state.contrast, state.contrastPivot)
            // 7. wheels
            c = wheels(c, state, lum)
            // 8. encode working (perceptual domain)
            val wt = cfg.workingTransfer
            c = floatArrayOf(wt.encode(max(c[0], 0f)), wt.encode(max(c[1], 0f)), wt.encode(max(c[2], 0f)))
            // 9. curves
            if (curveMain != null) c = curves(c, curveMain, curveLuma!!, lum)
            // 10. HSL
            if (!state.hsl.isZero()) {
                val hsl = ColorMath.rgbToHsl(c[0].coerceIn(0f, 1f), c[1].coerceIn(0f, 1f), c[2].coerceIn(0f, 1f))
                val adj = HslEngine.applyHsl(hsl[0], hsl[1], hsl[2], state.hsl)
                c = ColorMath.hslToRgb(adj[0], adj[1], adj[2])
            }
            // 11. sat + vibrance
            c = satVib(c, state, lum)
            // 12. split / BW / mixer
            c = splitBw(c, state, lum)
            val m = state.mixer
            tmp[0] = m.rr * c[0] + m.rg * c[1] + m.rb * c[2]
            tmp[1] = m.gr * c[0] + m.gg * c[1] + m.gb * c[2]
            tmp[2] = m.br * c[0] + m.bg * c[1] + m.bb * c[2]
            c = tmp.copyOf()
            // 13. LUT
            if (hasLut) {
                val s = when {
                    lut3d != null -> LutSampler.sampleTrilinear(lut3d, c[0], c[1], c[2])
                    lut1d != null -> LutSampler.sample1d(lut1d, c[0], c[1], c[2])
                    else -> c
                }
                c = floatArrayOf(c[0] + (s[0] - c[0]) * lutIntensity, c[1] + (s[1] - c[1]) * lutIntensity, c[2] + (s[2] - c[2]) * lutIntensity)
            }
            // 14. secondaries
            for (sec in state.secondaries) {
                if (!sec.enabled) continue
                val hsl = ColorMath.rgbToHsl(c[0].coerceIn(0f, 1f), c[1].coerceIn(0f, 1f), c[2].coerceIn(0f, 1f))
                val mask = HslEngine.qualifierMask(hsl[0], hsl[1], hsl[2],
                    sec.hueCenter, sec.hueWidth, sec.hueSoftness,
                    sec.satCenter, sec.satWidth, sec.satSoftness,
                    sec.lumCenter, sec.lumWidth, sec.lumSoftness, sec.invert)
                val g = floatArrayOf(sec.gainR, sec.gainG, sec.gainB)
                c = floatArrayOf(
                    c[0] + (c[0] * g[0] - c[0]) * mask * sec.intensity,
                    c[1] + (c[1] * g[1] - c[1]) * mask * sec.intensity,
                    c[2] + (c[2] * g[2] - c[2]) * mask * sec.intensity)
            }
            // 15-19. output
            c = floatArrayOf(wt.decode(c[0]), wt.decode(c[1]), wt.decode(c[2]))
            c = if (cfg.hdrOutput) c else com.ahstudio.color.hdr.ToneMappers.apply(state.hdr.toneMap, c)
            c = Mat3.mulVec(mOut, c)
            if (state.hdr.gamutCompress || cfg.outputSpace !== work) c = GamutMapping.compress(c, cfg.outputSpace.lumaCoefficients())
            val ot = cfg.outputTransfer
            c = floatArrayOf(ot.encode(c[0]), ot.encode(c[1]), ot.encode(c[2]))

            rgba[i] = c[0]; rgba[i + 1] = c[1]; rgba[i + 2] = c[2]
            i += 4
        }
    }

    private fun lumaOf(c: FloatArray, lum: FloatArray) = lum[0] * c[0] + lum[1] * c[1] + lum[2] * c[2]

    private fun toneDir(c: FloatArray, amt: Float, mask: Float, upK: Float, downK: Float): FloatArray {
        return if (amt >= 0f) floatArrayOf(
            c[0] + amt * mask * upK * (1f - min(c[0], 1f)),
            c[1] + amt * mask * upK * (1f - min(c[1], 1f)),
            c[2] + amt * mask * upK * (1f - min(c[2], 1f)))
        else {
            val k = 1f + amt * mask * downK
            floatArrayOf(c[0] * k, c[1] * k, c[2] * k)
        }
    }

    private fun toneControls(c: FloatArray, s: ColorState, lum: FloatArray): FloatArray {
        val ly = sqrt(max(lumaOf(c, lum), 0f))
        val mHi = ColorMath.smoothstep(PC.MASK_HI0, PC.MASK_HI1, ly)
        val mSh = 1f - ColorMath.smoothstep(PC.MASK_SH0, PC.MASK_SH1, ly)
        val mWh = ColorMath.smoothstep(PC.MASK_WH0, PC.MASK_WH1, ly)
        val mBl = 1f - ColorMath.smoothstep(PC.MASK_BL0, PC.MASK_BL1, ly)
        var o = toneDir(c, s.highlights, mHi, PC.TONE_HI_UP, PC.TONE_HI_DOWN)
        o = toneDir(o, s.shadows, mSh, PC.TONE_SH_UP, PC.TONE_SH_DOWN)
        o = toneDir(o, s.whites, mWh, PC.TONE_WH_UP, PC.TONE_WH_DOWN)
        o = toneDir(o, s.blacks, mBl, PC.TONE_BL_UP, PC.TONE_BL_DOWN)
        return o
    }

    private fun contrast(c: FloatArray, amount: Float, pivot: Float): FloatArray {
        val lp = ln(max(pivot.coerceIn(1e-4f, 1f), 1e-9f)) / ln(2f)
        return FloatArray(3) { ch ->
            val lc = ln(max(c[ch], PC.LOG_EPS)) / ln(2f)
            2.0.pow((lp + (lc - lp) * (1f + amount)).toDouble()).toFloat()
        }
    }

    private fun wheels(c: FloatArray, s: ColorState, lum: FloatArray): FloatArray {
        if (s.wheels.isZero()) return c
        val ly = sqrt(max(lumaOf(c.map { it.coerceIn(0f, 1f) }.toFloatArray(), lum), 0f))
        val wLift = 1f - ColorMath.smoothstep(0f, 0.5f, ly)
        val wGain = ColorMath.smoothstep(0.4f, 1f, ly)
        val wGamma = (1f - abs(ly - 0.5f) * 2f).coerceIn(0f, 1f)
        val w = s.wheels
        var o = FloatArray(3) {
            val lift = arrayOf(w.lift.x, w.lift.y, w.lift.z)[it] * w.lift.master
            c[it] + lift * wLift * PC.WHEEL_LIFT_K
        }
        o = FloatArray(3) {
            val gam = arrayOf(w.gamma.x, w.gamma.y, w.gamma.z)[it] * w.gamma.master
            val gc = max(o[it], 0f).pow(1f / (1f + gam * PC.WHEEL_GAMMA_K))
            o[it] + (gc - o[it]) * wGamma
        }
        o = FloatArray(3) {
            val gai = arrayOf(w.gain.x, w.gain.y, w.gain.z)[it] * w.gain.master
            val gc = o[it] * (1f + gai)
            o[it] + (gc - o[it]) * wGain
        }
        o = FloatArray(3) {
            val off = arrayOf(w.offset.x, w.offset.y, w.offset.z)[it] * w.offset.master
            o[it] + off * PC.WHEEL_OFFSET_K
        }
        return o
    }

    private fun curves(c: FloatArray, main: FloatArray, luma: FloatArray, lum: FloatArray): FloatArray {
        fun sample(ch: Int, x: Float): Float {
            val p = x.coerceIn(0f, 1f) * (CurveBaker.TEX_SIZE - 1)
            val i0 = floor(p).toInt().coerceIn(0, CurveBaker.TEX_SIZE - 1)
            val i1 = (i0 + 1).coerceIn(0, CurveBaker.TEX_SIZE - 1)
            val t = p - i0
            return main[i0 * 4 + ch] * (1f - t) + main[i1 * 4 + ch] * t
        }
        var o = floatArrayOf(sample(0, c[0]), sample(1, c[1]), sample(2, c[2]))
        o = floatArrayOf(sample(3, o[0]), sample(3, o[1]), sample(3, o[2])) // master
        val y0 = lumaOf(o, lum)
        if (y0 > 1e-5f) {
            val p = y0.coerceIn(0f, 1f) * (CurveBaker.TEX_SIZE - 1)
            val i0 = floor(p).toInt().coerceIn(0, CurveBaker.TEX_SIZE - 1)
            val i1 = (i0 + 1).coerceIn(0, CurveBaker.TEX_SIZE - 1)
            val t = p - i0
            val y1 = luma[i0] * (1f - t) + luma[i1] * t
            val k = y1 / y0
            o = floatArrayOf(o[0] * k, o[1] * k, o[2] * k)
        }
        return o
    }

    private fun satVib(c: FloatArray, s: ColorState, lum: FloatArray): FloatArray {
        val y = lumaOf(c, lum)
        var o = FloatArray(3) { y + (c[it] - y) * (1f + s.saturation) }
        val mx = max(o[0], max(o[1], o[2])); val mn = min(o[0], min(o[1], o[2]))
        val sat = mx - mn
        val hsl = ColorMath.rgbToHsl(o[0].coerceIn(0f, 1f), o[1].coerceIn(0f, 1f), o[2].coerceIn(0f, 1f))
        val hueDeg = hsl[0] * 360f
        val d = hueDeg - PC.SKIN_HUE_DEG
        val skin = exp(-(d * d) / (2f * PC.SKIN_WIDTH_DEG * PC.SKIN_WIDTH_DEG))
        val amt = s.vibrance * (1f - sat) * (1f - s.skinProtect * skin)
        return FloatArray(3) { y + (o[it] - y) * (1f + amt) }
    }

    private fun splitBw(c: FloatArray, s: ColorState, lum: FloatArray): FloatArray {
        var o = c
        if (s.bw.enabled) {
            val wk = (s.bw.weightR + s.bw.weightG + s.bw.weightB).coerceAtLeast(1e-4f)
            val g = (s.bw.weightR * o[0] + s.bw.weightG * o[1] + s.bw.weightB * o[2]) / wk
            o = FloatArray(3) { o[it] + (g - o[it]) * s.bw.strength }
        }
        val st = s.splitTone
        if (st.shadowSat > 0f || st.highlightSat > 0f) {
            val y = lumaOf(o.map { it.coerceIn(0f, 1f) }.toFloatArray(), lum)
            val hsl = ColorMath.rgbToHsl(o[0].coerceIn(0f, 1f), o[1].coerceIn(0f, 1f), o[2].coerceIn(0f, 1f))
            val wSh = 1f - ColorMath.smoothstep(0f, 0.5f + st.balance * PC.SPLIT_SPREAD, y)
            val wHi = ColorMath.smoothstep(0.5f - st.balance * PC.SPLIT_SPREAD, 1f, y)
            val sh = ColorMath.hslToRgb(st.shadowHue, st.shadowSat, hsl[2])
            val hi = ColorMath.hslToRgb(st.highlightHue, st.highlightSat, hsl[2])
            o = FloatArray(3) {
                val a = o[it] + (sh[it] - o[it]) * wSh * st.strength
                a + (hi[it] - a) * wHi * st.strength
            }
        }
        return o
    }
}
