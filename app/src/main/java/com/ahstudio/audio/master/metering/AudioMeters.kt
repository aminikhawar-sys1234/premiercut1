package com.ahstudio.audio.master.metering

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.dsp.eq.BiquadFilter
import com.ahstudio.audio.master.dsp.eq.BiquadType
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioMeterSnapshot(
    val peakDbPerChannel: FloatArray, val rmsDbPerChannel: FloatArray,
    val peakHoldDbPerChannel: FloatArray, val clipped: Boolean,
    val momentaryLufs: Double, val timestampMs: Long,
)

/** K-weighted (BS.1770-style) loudness with EBU absolute/relative gating. */
class LoudnessMeter {
    private class BiquadState(c: FloatArray) {
        private val b0 = c[0]; private val b1 = c[1]; private val b2 = c[2]; private val a1 = c[3]; private val a2 = c[4]
        private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f
        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y; return y
        }
        fun reset() { x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f }
    }
    private var channels = 0
    private var stages: Array<Array<BiquadState>> = emptyArray()
    private val hopMeanSq = ArrayDeque<Double>()
    private val blockMeanSq = mutableListOf<Double>()
    private var hopAcc = 0.0; private var hopCount = 0; private var hopFrames = 4800

    fun configure(sampleRate: Int, ch: Int) {
        if (channels == ch && stages.isNotEmpty() && hopFrames == (sampleRate * 0.1).toInt()) return
        channels = ch; hopFrames = (sampleRate * 0.1).toInt().coerceAtLeast(1)
        val s1 = BiquadFilter.designCoefficients(BiquadType.HIGH_SHELF, 1500f, 4f, 0.7071f, sampleRate.toFloat())
        val s2 = BiquadFilter.designCoefficients(BiquadType.HIGH_PASS, 38f, 0f, 0.5f, sampleRate.toFloat())
        stages = Array(ch) { arrayOf(BiquadState(s1), BiquadState(s2)) }
        reset()
    }

    fun process(buffer: AudioBuffer, frames: Int) {
        if (stages.size != buffer.channels) return
        for (i in 0 until frames) {
            var sum = 0.0
            for (ch in 0 until buffer.channels) {
                var x = buffer.data[ch][i]
                x = stages[ch][0].process(x); x = stages[ch][1].process(x)
                val d = x.toDouble(); sum += d * d
            }
            hopAcc += sum / buffer.channels; hopCount++
            if (hopCount >= hopFrames) {
                hopMeanSq.addLast(hopAcc / hopFrames)
                if (hopMeanSq.size > 4) hopMeanSq.removeFirst()
                if (hopMeanSq.size == 4) blockMeanSq.add(hopMeanSq.average())
                hopAcc = 0.0; hopCount = 0
            }
        }
    }

    fun momentaryLufs(): Double {
        if (hopMeanSq.isEmpty()) return -70.0
        val ms = hopMeanSq.average()
        return if (ms <= 0.0) -70.0 else -0.691 + 10 * log10(ms)
    }

    fun integratedLufs(): Double {
        if (blockMeanSq.isEmpty()) return momentaryLufs()
        val lufs = blockMeanSq.map { if (it <= 0.0) -70.0 else -0.691 + 10 * log10(it) }
        val gated1 = blockMeanSq.filterIndexed { i, _ -> lufs[i] > -70.0 }
        if (gated1.isEmpty()) return -70.0
        val mean1 = gated1.mapIndexed { i, ms -> -0.691 + 10 * log10(ms) }.average()
        val gated2 = blockMeanSq.filterIndexed { i, _ -> lufs[i] > mean1 - 10.0 }
        val meanSq = (if (gated2.isEmpty()) gated1 else gated2).average()
        return if (meanSq <= 0.0) -70.0 else -0.691 + 10 * log10(meanSq)
    }

    fun reset() { stages.forEach { s -> s.forEach { it.reset() } }; hopMeanSq.clear(); blockMeanSq.clear(); hopAcc = 0.0; hopCount = 0 }
}

class PeakMeter(private val channels: Int) {
    private val last = FloatArray(channels)
    fun process(buffer: AudioBuffer, frames: Int): FloatArray {
        for (c in 0 until channels.coerceAtMost(buffer.channels)) {
            val d = buffer.data[c]; var pk = 0f
            for (i in 0 until frames) { val v = d[i]; val a = if (v < 0f) -v else v; if (a > pk) pk = a }
            last[c] = pk
        }
        return last.copyOf()
    }
    fun reset() { java.util.Arrays.fill(last, 0f) }
}

class RmsMeter(private val channels: Int) {
    private val last = FloatArray(channels)
    fun process(buffer: AudioBuffer, frames: Int): FloatArray {
        for (c in 0 until channels.coerceAtMost(buffer.channels)) {
            val d = buffer.data[c]; var sq = 0.0
            for (i in 0 until frames) { val v = d[i].toDouble(); sq += v * v }
            last[c] = sqrt(sq / frames.coerceAtLeast(1)).toFloat()
        }
        return last.copyOf()
    }
    fun reset() { java.util.Arrays.fill(last, 0f) }
}

class AudioLevelMeter {
    private var peak = FloatArray(0); private var rms = FloatArray(0)
    private var hold = FloatArray(0); private var holdCountdown = IntArray(0)
    @Volatile var clipped = false; private set
    private val loudness = LoudnessMeter()

    fun configure(sampleRate: Int, ch: Int) {
        peak = FloatArray(ch); rms = FloatArray(ch); hold = FloatArray(ch); holdCountdown = IntArray(ch)
        loudness.configure(sampleRate, ch)
    }

    fun update(buffer: AudioBuffer, ctx: com.ahstudio.audio.master.core.AudioRenderContext) {
        if (peak.size != buffer.channels) configure(ctx.sampleRate, buffer.channels)
        val n = ctx.frames
        for (ch in 0 until buffer.channels) {
            val d = buffer.data[ch]; var pk = 0f; var sq = 0.0
            for (i in 0 until n) { val v = d[i]; val a = if (v < 0f) -v else v; if (a > pk) pk = a; sq += v.toDouble() * v }
            peak[ch] = pk; rms[ch] = sqrt(sq / n.coerceAtLeast(1)).toFloat()
            if (pk >= hold[ch] || holdCountdown[ch] <= 0) { hold[ch] = pk; holdCountdown[ch] = ctx.sampleRate } else holdCountdown[ch] -= n
            if (pk > 0.999f) clipped = true
        }
        loudness.process(buffer, n)
    }
    fun momentaryLufs() = loudness.momentaryLufs()
    fun integratedLufs() = loudness.integratedLufs()
    fun snapshot() = AudioMeterSnapshot(
        peak.map { com.ahstudio.audio.master.core.linToDb(it) }.toFloatArray(),
        rms.map { com.ahstudio.audio.master.core.linToDb(it) }.toFloatArray(),
        hold.map { com.ahstudio.audio.master.core.linToDb(it) }.toFloatArray(),
        clipped, momentaryLufs(), System.currentTimeMillis())
    fun reset() { java.util.Arrays.fill(peak, 0f); java.util.Arrays.fill(rms, 0f); java.util.Arrays.fill(hold, 0f)
        java.util.Arrays.fill(holdCountdown, 0); clipped = false; loudness.reset() }
}
