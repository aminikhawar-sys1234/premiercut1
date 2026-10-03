package com.ahstudio.audio.master.dsp.dynamics

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.core.dbToLin
import com.ahstudio.audio.master.core.linToDb
import com.ahstudio.audio.master.dsp.AudioDspProcessor
import kotlin.math.abs
import kotlin.math.exp

class NoiseGateProcessor : AudioDspProcessor {
    override val name = "gate"
    var thresholdDb = -50f; var attackMs = 2f; var releaseMs = 80f; var holdMs = 50f; var rangeDb = -80f
    private val env = EnvelopeFollower(); private var holdFrames = 0; private var open = false
    private var curGain = 0f; private var cA = 0f; private var cR = 0f; private var sr = 48000

    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {
        sr = format.sampleRate
        env.prepare(sr, attackMs, releaseMs)
        cA = (1.0 - exp(-1000.0 / (attackMs.coerceAtLeast(0.01f) * sr))).toFloat()
        cR = (1.0 - exp(-1000.0 / (releaseMs.coerceAtLeast(0.01f) * sr))).toFloat()
        holdFrames = (holdMs * sr / 1000f).toInt()
    }

    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        val n = ctx.frames; val chs = buffer.channels
        var holdCounter = 0
        val floor = dbToLin(rangeDb)
        for (i in 0 until n) {
            var peak = 0f
            for (c in 0 until chs) { val v = abs(buffer.data[c][i]); if (v > peak) peak = v }
            val e = env.process(peak)
            val above = linToDb(e) > thresholdDb
            if (above) { open = true; holdCounter = 0 } else if (holdCounter < holdFrames) holdCounter++ else open = false
            val target = if (open) 1f else floor
            curGain += (target - curGain) * (if (target > curGain) cA else cR)
            for (c in 0 until chs) buffer.data[c][i] *= curGain
        }
    }
    override fun reset() { env.reset(); open = false; curGain = 0f }
    override fun setParam(key: String, value: Float) { when (key) {
        "threshold" -> thresholdDb = value; "range" -> rangeDb = value } }
}

class ExpanderProcessor : AudioDspProcessor {
    override val name = "expander"
    var thresholdDb = -40f; var ratio = 2f; var attackMs = 5f; var releaseMs = 100f
    private val env = EnvelopeFollower()

    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {
        env.prepare(format.sampleRate, attackMs, releaseMs)
    }
    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        val n = ctx.frames; val chs = buffer.channels
        for (i in 0 until n) {
            var peak = 0f
            for (c in 0 until chs) { val v = abs(buffer.data[c][i]); if (v > peak) peak = v }
            val envDb = linToDb(env.process(peak))
            val g = if (envDb < thresholdDb) dbToLin((ratio - 1f) * (envDb - thresholdDb)) else 1f
            for (c in 0 until chs) buffer.data[c][i] *= g
        }
    }
    override fun reset() { env.reset() }
    override fun setParam(key: String, value: Float) { when (key) {
        "threshold" -> thresholdDb = value; "ratio" -> ratio = value } }
}
