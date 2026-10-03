package com.ahstudio.audio.master.dsp.utility

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.core.dbToLin
import com.ahstudio.audio.master.dsp.AudioDspProcessor
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh

class DcOffsetFilter : AudioDspProcessor {
    override val name = "dc_offset"
    private var a = 0.9995f
    private var x1 = FloatArray(0); private var y1 = FloatArray(0)
    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {
        if (x1.size != format.channels) { x1 = FloatArray(format.channels); y1 = FloatArray(format.channels) }
        a = exp(-2.0 * Math.PI * 10.0 / format.sampleRate).toFloat()
    }
    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        for (c in 0 until buffer.channels) {
            val d = buffer.data[c]; var x1c = x1[c]; var y1c = y1[c]
            for (i in 0 until ctx.frames) {
                val x = d[i]
                y1c = x - x1c + a * y1c; x1c = x; d[i] = y1c
            }
            x1[c] = x1c; y1[c] = y1c
        }
    }
    override fun reset() { Arrays.fill(x1, 0f); Arrays.fill(y1, 0f) }
}

class SoftClipProcessor : AudioDspProcessor {
    override val name = "softclip"
    var threshold = 0.8f
    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {}
    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        val t = threshold; val inv = 1f / (1f - t)
        for (c in 0 until buffer.channels) {
            val d = buffer.data[c]
            for (i in 0 until ctx.frames) {
                val x = d[i]; val ax = abs(x)
                d[i] = if (ax <= t) x
                    else Math.signum(x.toDouble()).toFloat() * (t + (1f - t) * tanh((ax - t) * inv))
            }
        }
    }
    override fun reset() {}
}

object SilenceProcessor {
    fun isSilent(buffer: AudioBuffer, frames: Int, thresholdDb: Float = -60f): Boolean =
        buffer.maxAbs(frames) < dbToLin(thresholdDb)

    fun detectSilenceRanges(pcm: Array<FloatArray>, sampleRate: Int, thresholdDb: Float = -50f, minDurationSec: Double = 0.4): List<ClosedFloatingPointRange<Double>> {
        val thr = dbToLin(thresholdDb); val minLen = (minDurationSec * sampleRate).toInt()
        val ranges = mutableListOf<ClosedFloatingPointRange<Double>>()
        var start = -1
        val n = pcm[0].size
        for (i in 0 until n) {
            var peak = 0f
            for (c in pcm) { val v = abs(c[i]); if (v > peak) peak = v }
            if (peak < thr) { if (start < 0) start = i }
            else { if (start >= 0 && i - start >= minLen) ranges.add((start.toDouble() / sampleRate)..(i.toDouble() / sampleRate)); start = -1 }
        }
        if (start >= 0 && n - start >= minLen) ranges.add((start.toDouble() / sampleRate)..(n.toDouble() / sampleRate))
        return ranges
    }
}

object AudioNormalizer {
    fun peakNormalize(pcm: Array<FloatArray>, targetDb: Float = -1f) {
        var peak = 0f
        for (c in pcm) for (v in c) { val a = abs(v); if (a > peak) peak = a }
        if (peak < 1e-9f) return
        val g = dbToLin(targetDb) / peak
        for (c in pcm) for (i in c.indices) c[i] *= g
    }
}
