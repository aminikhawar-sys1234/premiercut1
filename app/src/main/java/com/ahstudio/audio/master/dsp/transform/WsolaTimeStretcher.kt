package com.ahstudio.audio.master.dsp.transform

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow

object WsolaTimeStretcher {
    fun stretch(input: Array<FloatArray>, channels: Int, stretch: Float, sampleRate: Int): Array<FloatArray> {
        val inLen = input[0].size
        if (inLen == 0 || abs(stretch - 1f) < 1e-3f) return input
        val st = stretch.coerceIn(0.25f, 4f)
        val n = 2048; val hop = n / 2; val overlap = n - hop
        val maxDelta = (0.006 * sampleRate).toInt()
        val outLen = max(1, (inLen * st).toInt())
        val out = Array(channels) { FloatArray(outLen + n) }
        val win = FloatArray(n) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))).toFloat() }
        val mid = if (channels >= 2) FloatArray(inLen) { i -> (input[0][i] + input[1][i]) * 0.5f } else input[0]
        var synth = 0; var first = true
        while (synth < outLen) {
            val nominal = (synth / st).toInt()
            var delta = 0
            if (!first) {
                var best = -Double.MAX_VALUE
                for (d in -maxDelta..maxDelta) {
                    val p = nominal + d
                    if (p < 0 || p + overlap > inLen) continue
                    var c = 0.0
                    for (i in 0 until overlap) c += out[0][synth + i].toDouble() * mid[p + i]
                    if (c > best) { best = c; delta = d }
                }
            }
            first = false
            for (ch in 0 until channels) {
                val src = input[ch]; val dst = out[ch]
                for (i in 0 until n) {
                    val p = nominal + delta + i
                    val s = if (p in 0 until inLen) src[p] else 0f
                    dst[synth + i] += s * win[i]
                }
            }
            synth += hop
        }
        return Array(channels) { ch -> out[ch].copyOf(outLen) }
    }
}

object AudioClipResampler {
    fun resample(input: Array<FloatArray>, channels: Int, step: Double): Array<FloatArray> {
        val inLen = input[0].size
        if (inLen < 2 || abs(step - 1.0) < 1e-9) return input
        val outLen = max(1, ((inLen - 1) / step).toInt())
        val out = Array(channels) { FloatArray(outLen) }
        var pos = 0.0
        for (i in 0 until outLen) {
            val i0 = pos.toInt(); val frac = (pos - i0).toFloat()
            val i1 = minOf(i0 + 1, inLen - 1)
            for (ch in 0 until channels) {
                val a = input[ch][i0]; val b = input[ch][i1]
                out[ch][i] = a + (b - a) * frac
            }
            pos += step
        }
        return out
    }
    fun resampleRate(input: Array<FloatArray>, channels: Int, inRate: Int, outRate: Int): Array<FloatArray> =
        resample(input, channels, inRate.toDouble() / outRate)
}

object PitchShifter {
    fun shift(input: Array<FloatArray>, channels: Int, semitones: Float, sampleRate: Int): Array<FloatArray> {
        if (abs(semitones) < 0.01f) return input
        val targetLen = input[0].size
        val ratio = 2.0.pow(semitones / 12.0).toFloat()
        val stretched = WsolaTimeStretcher.stretch(input, channels, ratio, sampleRate)
        val resampled = AudioClipResampler.resample(stretched, channels, ratio.toDouble())
        return Array(channels) { ch ->
            val src = resampled[ch]
            if (src.size == targetLen) src else src.copyOf(targetLen)
        }
    }
}

object ReverseAudio {
    fun reverse(input: Array<FloatArray>): Array<FloatArray> =
        Array(input.size) { ch -> input[ch].reversedArray() }
}
