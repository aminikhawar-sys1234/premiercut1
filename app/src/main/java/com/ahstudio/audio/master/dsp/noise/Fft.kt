package com.ahstudio.audio.master.dsp.noise

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Fft(val size: Int) {
    init { require(Integer.bitCount(size) == 1) { "FFT size must be power of 2" } }

    fun transform(re: FloatArray, im: FloatArray) {
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= size) {
            val ang = -2.0 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            val half = len / 2
            var i = 0
            while (i < size) {
                var cwr = 1.0; var cwi = 0.0
                for (k in 0 until half) {
                    val a0 = i + k; val a1 = a0 + half
                    val vr = re[a1] * cwr - im[a1] * cwi
                    val vi = re[a1] * cwi + im[a1] * cwr
                    re[a1] = (re[a0] - vr).toFloat(); im[a1] = (im[a0] - vi).toFloat()
                    re[a0] = (re[a0] + vr).toFloat(); im[a0] = (im[a0] + vi).toFloat()
                    val nwr = cwr * wr - cwi * wi
                    cwi = cwr * wi + cwi * wr; cwr = nwr
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun inverse(re: FloatArray, im: FloatArray) {
        for (i in 0 until size) im[i] = -im[i]
        transform(re, im)
        val inv = 1f / size
        for (i in 0 until size) { im[i] = -im[i]; re[i] *= inv; im[i] *= inv }
    }
}
