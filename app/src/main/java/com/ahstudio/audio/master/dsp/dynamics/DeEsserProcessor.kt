package com.ahstudio.audio.master.dsp.dynamics

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.core.dbToLin
import com.ahstudio.audio.master.core.linToDb
import com.ahstudio.audio.master.dsp.AudioDspProcessor
import com.ahstudio.audio.master.dsp.eq.BiquadType
import com.ahstudio.audio.master.dsp.eq.BiquadFilter
import kotlin.math.abs
import kotlin.math.min

class DeEsserProcessor : AudioDspProcessor {
    override val name = "deesser"
    var splitFreqHz = 6000f; var thresholdDb = -30f; var reductionDb = -12f
    private val hp = BiquadFilter(); private val lp = BiquadFilter()
    private val env = EnvelopeFollower()
    private var hpBand = FloatArray(0); private var lpBand = FloatArray(0)

    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {
        hp.configure(BiquadType.HIGH_PASS, splitFreqHz, 0f, 0.7071f)
        lp.configure(BiquadType.LOW_PASS, splitFreqHz, 0f, 0.7071f)
        hp.prepare(format, maxBlockFrames); lp.prepare(format, maxBlockFrames)
        env.prepare(format.sampleRate, 1f, 60f)
        if (hpBand.size != maxBlockFrames) { hpBand = FloatArray(maxBlockFrames); lpBand = FloatArray(maxBlockFrames) }
    }

    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        val n = ctx.frames; val chs = buffer.channels
        for (i in 0 until n) { var s = 0f; for (c in 0 until chs) s += buffer.data[c][i]; hpBand[i] = s / chs }
        for (c in 0 until chs) {
            val d = buffer.data[c]
            for (i in 0 until n) lpBand[i] = d[i]
            lp.processMono(lpBand, n)
            for (i in 0 until n) hpBand[i] = d[i] - lpBand[i]
            var e = 0f
            for (i in 0 until n) e = env.process(abs(hpBand[i]))
            val envDb = linToDb(e)
            val gr = if (envDb > thresholdDb) min(0f, reductionDb * ((envDb - thresholdDb) / 12f).coerceAtMost(1f)) else 0f
            val g = dbToLin(gr)
            for (i in 0 until n) d[i] = lpBand[i] + hpBand[i] * g
        }
    }
    override fun reset() { hp.reset(); lp.reset(); env.reset() }
    override fun setParam(key: String, value: Float) { when (key) {
        "threshold" -> thresholdDb = value; "reduction" -> reductionDb = value } }
}
