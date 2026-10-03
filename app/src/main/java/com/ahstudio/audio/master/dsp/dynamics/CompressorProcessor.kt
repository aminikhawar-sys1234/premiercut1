package com.ahstudio.audio.master.dsp.dynamics

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.core.dbToLin
import com.ahstudio.audio.master.dsp.AudioDspProcessor
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10

class CompressorProcessor : AudioDspProcessor {
    override val name = "compressor"
    var thresholdDb = -18f; var ratio = 3f; var attackMs = 10f; var releaseMs = 120f
    var kneeDb = 6f; var makeupDb = 0f; var autoMakeup = false
    private var envDb = -120f; private var cA = 0f; private var cR = 0f

    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {
        cA = timeCoef(attackMs, format.sampleRate); cR = timeCoef(releaseMs, format.sampleRate)
    }
    private fun timeCoef(ms: Float, sr: Int): Float =
        (1.0 - exp(-1000.0 / (ms.coerceAtLeast(0.01f) * sr))).toFloat()

    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        val n = ctx.frames; val chs = buffer.channels
        val makeup = if (autoMakeup) -(thresholdDb * (1f - 1f / ratio)) * 0.5f + makeupDb else makeupDb
        val halfKnee = kneeDb / 2f
        for (i in 0 until n) {
            var peak = 0f
            for (c in 0 until chs) { val v = abs(buffer.data[c][i]); if (v > peak) peak = v }
            val levelDb = 20f * log10(peak.coerceAtLeast(1e-6f))
            envDb += (if (levelDb > envDb) cA else cR) * (levelDb - envDb)
            val x = envDb - thresholdDb
            val y = when {
                2f * x < -kneeDb -> x
                abs(x) <= halfKnee -> { val t = x + halfKnee; x + (1f / ratio - 1f) * t * t / (2f * kneeDb) }
                else -> x / ratio
            }
            val g = dbToLin(y - x + makeup)
            for (c in 0 until chs) buffer.data[c][i] *= g
        }
    }
    override fun reset() { envDb = -120f }
    override fun setParam(key: String, value: Float) { when (key) {
        "threshold" -> thresholdDb = value; "ratio" -> ratio = value; "attack" -> attackMs = value
        "release" -> releaseMs = value; "knee" -> kneeDb = value; "makeup" -> makeupDb = value } }
}
