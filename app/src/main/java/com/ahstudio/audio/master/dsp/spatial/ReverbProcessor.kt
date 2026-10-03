package com.ahstudio.audio.master.dsp.spatial

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.dsp.AudioDspProcessor
import java.util.Arrays
import kotlin.math.roundToInt

class ReverbProcessor : AudioDspProcessor {
    override val name = "reverb"
    var roomSize = 0.5f; var damping = 0.5f; var wet = 0.3f; var dry = 1f
    var preDelayMs = 20f; var stereoSpread = 0.5f

    private var combs: Array<Array<Comb>> = emptyArray()
    private var allpasses: Array<Array<Allpass>> = emptyArray()
    private var predelay = FloatArray(0); private var pdIdx = 0; private var pdLen = 1

    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {
        val scale = format.sampleRate / 44100.0
        val spread = (23 * stereoSpread * scale).roundToInt()
        val fb = roomSize * 0.28f + 0.7f
        val damp1 = damping * 0.4f; val damp2 = 1f - damp1
        combs = Array(format.channels) { ch ->
            Array(COMB_TUNINGS.size) { i ->
                val size = ((COMB_TUNINGS[i] * scale) + (if (ch == 1) spread else 0)).roundToInt().coerceAtLeast(2)
                Comb(size, fb, damp1, damp2)
            }
        }
        allpasses = Array(format.channels) { ch ->
            Array(AP_TUNINGS.size) { i ->
                val size = ((AP_TUNINGS[i] * scale) + (if (ch == 1) spread else 0)).roundToInt().coerceAtLeast(2)
                Allpass(size)
            }
        }
        pdLen = (preDelayMs * format.sampleRate / 1000f).roundToInt().coerceAtLeast(1)
        predelay = FloatArray(pdLen); pdIdx = 0
    }

    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        val n = ctx.frames; val chs = buffer.channels
        for (i in 0 until n) {
            val input = buffer.data[0][i] / chs.coerceAtLeast(1)
            val pd = predelay[pdIdx]; predelay[pdIdx] = input; if (++pdIdx >= pdLen) pdIdx = 0
            for (c in 0 until chs) {
                var out = 0f
                for (cb in combs[c]) out += cb.process(pd)
                for (ap in allpasses[c]) out = ap.process(out)
                buffer.data[c][i] = buffer.data[c][i] * dry + out * wet * 0.3f
            }
        }
    }
    override fun reset() {
        combs.forEach { it.forEach { c -> c.reset() } }
        allpasses.forEach { it.forEach { a -> a.reset() } }
        Arrays.fill(predelay, 0f)
    }

    private class Comb(val size: Int, val feedback: Float, val damp1: Float, val damp2: Float) {
        private val buf = FloatArray(size); private var idx = 0; private var store = 0f
        fun process(x: Float): Float {
            val y = buf[idx]
            store = y * damp2 + store * damp1
            buf[idx] = x + store * feedback
            if (++idx >= size) idx = 0
            return y
        }
        fun reset() { Arrays.fill(buf, 0f); store = 0f; idx = 0 }
    }
    private class Allpass(val size: Int) {
        private val buf = FloatArray(size); private var idx = 0
        fun process(x: Float): Float {
            val b = buf[idx]
            val y = -x + b
            buf[idx] = x + b * 0.5f
            if (++idx >= size) idx = 0
            return y
        }
        fun reset() { Arrays.fill(buf, 0f); idx = 0 }
    }
    companion object {
        private val COMB_TUNINGS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val AP_TUNINGS = intArrayOf(556, 441, 341, 225)
    }
    override fun setParam(key: String, value: Float) { when (key) {
        "room" -> roomSize = value.coerceIn(0f, 1f); "damp" -> damping = value.coerceIn(0f, 1f)
        "wet" -> wet = value; "dry" -> dry = value; "predelay" -> preDelayMs = value } }
}
