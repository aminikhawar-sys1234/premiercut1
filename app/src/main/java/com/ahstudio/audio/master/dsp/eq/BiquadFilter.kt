package com.ahstudio.audio.master.dsp.eq

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.dsp.AudioDspProcessor
import java.util.Arrays
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class BiquadType { LOW_PASS, HIGH_PASS, BAND_PASS, NOTCH, PEAKING, LOW_SHELF, HIGH_SHELF }

open class BiquadFilter(private var type: BiquadType = BiquadType.PEAKING) : AudioDspProcessor {
    override val name = "biquad"
    var frequencyHz = 1000f; var gainDb = 0f; var q = 0.7071f
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
    private var x1 = FloatArray(0); private var x2 = FloatArray(0)
    private var y1 = FloatArray(0); private var y2 = FloatArray(0)
    private var needsDesign = true; private var designRate = 0

    fun configure(type: BiquadType, freqHz: Float, gainDb: Float = 0f, q: Float = 0.7071f) {
        this.type = type; this.frequencyHz = freqHz; this.gainDb = gainDb
        this.q = q.coerceAtLeast(0.05f); needsDesign = true
    }

    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {
        if (x1.size != format.channels) {
            x1 = FloatArray(format.channels); x2 = FloatArray(format.channels)
            y1 = FloatArray(format.channels); y2 = FloatArray(format.channels)
        }
        if (needsDesign || designRate != format.sampleRate) { design(format.sampleRate); designRate = format.sampleRate }
    }

    fun design(sampleRate: Int) {
        val c = designCoefficients(type, frequencyHz, gainDb, q, sampleRate.toFloat())
        b0 = c[0]; b1 = c[1]; b2 = c[2]; a1 = c[3]; a2 = c[4]; needsDesign = false
    }

    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        if (needsDesign) design(ctx.sampleRate)
        val n = ctx.frames
        for (ch in 0 until buffer.channels) {
            val d = buffer.data[ch]
            var x1c = x1[ch]; var x2c = x2[ch]; var y1c = y1[ch]; var y2c = y2[ch]
            for (i in 0 until n) {
                val x = d[i]
                val y = b0 * x + b1 * x1c + b2 * x2c - a1 * y1c - a2 * y2c
                x2c = x1c; x1c = x; y2c = y1c; y1c = y
                d[i] = y
            }
            x1[ch] = x1c; x2[ch] = x2c; y1[ch] = y1c; y2[ch] = y2c
        }
    }

    fun processMono(d: FloatArray, n: Int) {
        var x1c = 0f; var x2c = 0f; var y1c = 0f; var y2c = 0f
        for (i in 0 until n) {
            val x = d[i]
            val y = b0 * x + b1 * x1c + b2 * x2c - a1 * y1c - a2 * y2c
            x2c = x1c; x1c = x; y2c = y1c; y1c = y
            d[i] = y
        }
    }

    override fun reset() {
        Arrays.fill(x1, 0f); Arrays.fill(x2, 0f)
        Arrays.fill(y1, 0f); Arrays.fill(y2, 0f)
    }

    companion object {
        fun designCoefficients(type: BiquadType, f0: Float, gainDb: Float, q: Float, sr: Float): FloatArray {
            val w0 = 2.0 * PI * f0.coerceIn(1f, sr * 0.49f) / sr.toDouble()
            val cw = cos(w0); val sw = sin(w0)
            val A = Math.pow(10.0, gainDb / 40.0)
            val alpha = sw / (2.0 * q)
            val sA = sqrt(A)
            var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var a0 = 1.0; var a1 = 0.0; var a2 = 0.0
            when (type) {
                BiquadType.LOW_PASS -> { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2; a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha }
                BiquadType.HIGH_PASS -> { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = (1 + cw) / 2; a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha }
                BiquadType.BAND_PASS -> { b0 = alpha; b1 = 0.0; b2 = -alpha; a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha }
                BiquadType.NOTCH -> { b0 = 1.0; b1 = -2 * cw; b2 = 1.0; a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha }
                BiquadType.PEAKING -> { b0 = 1 + alpha * A; b1 = -2 * cw; b2 = 1 - alpha * A; a0 = 1 + alpha / A; a1 = -2 * cw; a2 = 1 - alpha / A }
                BiquadType.LOW_SHELF -> {
                    val al = sw / 2.0 * sqrt((A + 1 / A) * 2.0)
                    b0 = A * ((A + 1) - (A - 1) * cw + 2 * sA * al); b1 = 2 * A * ((A - 1) - (A + 1) * cw)
                    b2 = A * ((A + 1) - (A - 1) * cw - 2 * sA * al)
                    a0 = (A + 1) + (A - 1) * cw + 2 * sA * al; a1 = -2 * ((A - 1) + (A + 1) * cw); a2 = (A + 1) + (A - 1) * cw - 2 * sA * al
                }
                BiquadType.HIGH_SHELF -> {
                    val al = sw / 2.0 * sqrt((A + 1 / A) * 2.0)
                    b0 = A * ((A + 1) + (A - 1) * cw + 2 * sA * al); b1 = -2 * A * ((A - 1) + (A + 1) * cw)
                    b2 = A * ((A + 1) + (A - 1) * cw - 2 * sA * al)
                    a0 = (A + 1) - (A - 1) * cw + 2 * sA * al; a1 = 2 * ((A - 1) - (A + 1) * cw); a2 = (A + 1) - (A - 1) * cw - 2 * sA * al
                }
            }
            return floatArrayOf((b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(), (a1 / a0).toFloat(), (a2 / a0).toFloat())
        }
    }
}

class HighPassFilter(f: Float, q: Float = 0.7071f) : BiquadFilter(BiquadType.HIGH_PASS) { init { configure(BiquadType.HIGH_PASS, f, 0f, q) } }
class LowPassFilter(f: Float, q: Float = 0.7071f) : BiquadFilter(BiquadType.LOW_PASS) { init { configure(BiquadType.LOW_PASS, f, 0f, q) } }
class BandPassFilter(f: Float, q: Float = 0.7071f) : BiquadFilter(BiquadType.BAND_PASS) { init { configure(BiquadType.BAND_PASS, f, 0f, q) } }
class NotchFilter(f: Float, q: Float = 0.7071f) : BiquadFilter(BiquadType.NOTCH) { init { configure(BiquadType.NOTCH, f, 0f, q) } }
