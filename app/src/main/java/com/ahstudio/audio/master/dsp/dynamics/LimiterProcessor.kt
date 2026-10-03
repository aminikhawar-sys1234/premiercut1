package com.ahstudio.audio.master.dsp.dynamics

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.core.dbToLin
import com.ahstudio.audio.master.dsp.AudioDspProcessor
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

class LimiterProcessor : AudioDspProcessor {
    override val name = "limiter"
    var ceilingDb = -0.3f; var lookaheadMs = 5f; var releaseMs = 80f
    private var delay = emptyArray<FloatArray>(); private var pos = 0; private var delayLen = 1
    private var gain = 1f; private var cA = 0f; private var cR = 0f; private var ceilLin = 1f

    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {
        delayLen = max(1, (lookaheadMs * format.sampleRate / 1000f).roundToInt())
        delay = Array(format.channels) { FloatArray(delayLen) }
        pos = 0; gain = 1f; ceilLin = dbToLin(ceilingDb)
        cA = (1.0 - exp(-1000.0 / (0.05f * format.sampleRate))).toFloat()
        cR = (1.0 - exp(-1000.0 / (releaseMs * format.sampleRate))).toFloat()
    }

    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        val n = ctx.frames; val chs = buffer.channels
        for (i in 0 until n) {
            var peak = 0f
            for (c in 0 until chs) { val v = abs(buffer.data[c][i]); if (v > peak) peak = v }
            val target = if (peak > ceilLin) ceilLin / peak else 1f
            gain = if (target < gain) gain + (target - gain) * cA else gain + (1f - gain) * cR
            for (c in 0 until chs) {
                val d = delay[c]
                val out = d[pos] * gain
                d[pos] = buffer.data[c][i]
                buffer.data[c][i] = if (out > ceilLin) ceilLin else if (out < -ceilLin) -ceilLin else out
            }
            if (++pos >= delayLen) pos = 0
        }
    }
    override fun reset() { for (d in delay) java.util.Arrays.fill(d, 0f); gain = 1f; pos = 0 }
    override fun setParam(key: String, value: Float) { when (key) {
        "ceiling" -> ceilingDb = value; "lookahead" -> lookaheadMs = value; "release" -> releaseMs = value } }
}
