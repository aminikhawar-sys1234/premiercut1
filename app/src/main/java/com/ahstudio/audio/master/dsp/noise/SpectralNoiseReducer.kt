package com.ahstudio.audio.master.dsp.noise

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.dsp.AudioDspProcessor
import java.util.Arrays
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow

data class NoiseReductionConfig(
    val strength: Float = 0.5f,
    val floorDb: Float = -40f,
    val voicePreservation: Boolean = true,
    val temporalSmoothing: Float = 0.5f,
    val frameSize: Int = 2048,
)

object NoiseProfileAnalyzer {
    fun analyze(
        pcm: Array<FloatArray>, channels: Int, sampleRate: Int,
        frameSize: Int = 2048, startSec: Double = 0.0, endSec: Double = -1.0,
    ): FloatArray {
        val fft = Fft(frameSize)
        val win = FloatArray(frameSize) { i -> (0.5 - 0.5 * cos(2.0 * Math.PI * i / frameSize)).toFloat() }
        val start = (startSec * sampleRate).toInt().coerceIn(0, (pcm[0].size - frameSize).coerceAtLeast(0))
        val end = if (endSec <= startSec) pcm[0].size else (endSec * sampleRate).toInt().coerceAtMost(pcm[0].size)
        val bins = frameSize / 2 + 1
        val acc = DoubleArray(bins); var frames = 0
        val re = FloatArray(frameSize); val im = FloatArray(frameSize)
        var pos = start
        while (pos + frameSize <= end) {
            for (i in 0 until frameSize) {
                var s = 0f
                for (c in 0 until channels) s += pcm[c][pos + i]
                re[i] = s / channels * win[i]; im[i] = 0f
            }
            fft.transform(re, im)
            for (k in 0 until bins) acc[k] += hypot(re[k].toDouble(), im[k].toDouble())
            frames++; pos += frameSize / 2
        }
        require(frames > 0) { "Noise region too short" }
        return FloatArray(bins) { (acc[it] / frames).toFloat() }
    }
}

class SpectralNoiseReducer(
    private val profile: FloatArray,
    private val cfg: NoiseReductionConfig = NoiseReductionConfig(),
) : AudioDspProcessor {
    override val name = "spectral_nr"
    val latencySamples: Int get() = cfg.frameSize
    private val frame = cfg.frameSize
    private val hop = frame / 4
    private val fft = Fft(frame)
    private val window = FloatArray(frame) { i -> (0.5 - 0.5 * cos(2.0 * Math.PI * i / frame)).toFloat() }
    private val prevGain = FloatArray(frame / 2 + 1) { 1f }
    private val overSub = 1f + 3f * cfg.strength
    private val floorLin = 10f.pow(cfg.floorDb / 20f)
    private var states = emptyArray<ChState>()
    private var sampleRate = 48000

    private inner class ChState {
        val inBuf = FloatArray(frame); var inFill = 0
        val acc = FloatArray(frame)
        val ring = FloatArray(frame * 2); var rW = 0; var rR = 0; var rCount = 0
        val re = FloatArray(frame); val im = FloatArray(frame)
    }

    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {
        if (states.size != format.channels) states = Array(format.channels) { ChState() }
        sampleRate = format.sampleRate
    }

    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        for (ch in 0 until buffer.channels) {
            val st = states[ch]; val d = buffer.data[ch]
            for (i in 0 until ctx.frames) {
                st.inBuf[st.inFill++] = d[i]
                if (st.inFill == frame) {
                    processFrame(st)
                    System.arraycopy(st.inBuf, hop, st.inBuf, 0, frame - hop)
                    st.inFill = frame - hop
                }
                d[i] = if (st.rCount > 0) {
                    val v = st.ring[st.rR]; st.rR = (st.rR + 1) % st.ring.size; st.rCount--; v
                } else 0f
            }
        }
    }

    private fun processFrame(st: ChState) {
        for (i in 0 until frame) { st.re[i] = st.inBuf[i] * window[i]; st.im[i] = 0f }
        fft.transform(st.re, st.im)
        val bins = frame / 2 + 1
        for (k in 0 until bins) {
            val mag = hypot(st.re[k].toDouble(), st.im[k].toDouble()).toFloat()
            val noise = if (k < profile.size) profile[k] else 0f
            var g = if (mag > 1e-9f) ((mag - overSub * noise) / mag).coerceIn(floorLin, 1f) else floorLin
            if (cfg.voicePreservation) {
                val f = k * sampleRate.toFloat() / frame
                if (f < 300f) g += (1f - g) * 0.5f
            }
            g = prevGain[k] + (g - prevGain[k]) * (1f - cfg.temporalSmoothing)
            prevGain[k] = g
            st.re[k] *= g; st.im[k] *= g
            if (k in 1 until frame / 2) { st.re[frame - k] *= g; st.im[frame - k] *= g }
        }
        fft.inverse(st.re, st.im)
        for (i in 0 until frame) st.acc[i] += st.re[i] * window[i]
        for (i in 0 until hop) {
            st.ring[st.rW] = st.acc[i] * 0.5f
            st.rW = (st.rW + 1) % st.ring.size; st.rCount++
        }
        System.arraycopy(st.acc, hop, st.acc, 0, frame - hop)
        Arrays.fill(st.acc, frame - hop, frame, 0f)
    }
    override fun reset() { states = emptyArray(); Arrays.fill(prevGain, 1f) }
}
