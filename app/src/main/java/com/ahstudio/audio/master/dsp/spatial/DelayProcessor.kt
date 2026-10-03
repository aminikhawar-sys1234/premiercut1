package com.ahstudio.audio.master.dsp.spatial

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.dsp.AudioDspProcessor
import java.util.Arrays
import kotlin.math.roundToInt

class DelayProcessor : AudioDspProcessor {
    override val name = "delay"
    var delayMs = 250f; var feedback = 0.35f; var wet = 0.4f; var dry = 1f
    private var lines = emptyArray<FloatArray>(); private var idx = 0; private var len = 1

    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {
        len = (delayMs * format.sampleRate / 1000f).roundToInt().coerceAtLeast(1)
        lines = Array(format.channels) { FloatArray(len) }; idx = 0
    }

    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        val n = ctx.frames
        for (i in 0 until n) {
            for (c in 0 until buffer.channels) {
                val l = lines[c]
                val delayed = l[idx]
                l[idx] = buffer.data[c][i] + delayed * feedback
                buffer.data[c][i] = buffer.data[c][i] * dry + delayed * wet
            }
            if (++idx >= len) idx = 0
        }
    }
    override fun reset() { lines.forEach { Arrays.fill(it, 0f) }; idx = 0 }
    override fun setParam(key: String, value: Float) { when (key) {
        "time" -> delayMs = value; "feedback" -> feedback = value.coerceIn(0f, 0.95f); "wet" -> wet = value; "dry" -> dry = value } }
}

class EchoProcessor : AudioDspProcessor {
    override val name = "echo"
    var delayMs = 300f; var feedback = 0.3f; var wet = 0.4f; var dry = 1f
    private var lines = emptyArray<FloatArray>(); private var idx = 0; private var len = 1

    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {
        len = (delayMs * 2f * format.sampleRate / 1000f).roundToInt().coerceAtLeast(4)
        lines = Array(format.channels) { FloatArray(len) }; idx = 0
    }

    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        val n = ctx.frames; val sr = ctx.sampleRate.toFloat()
        val t1 = (delayMs * sr / 1000f).toInt().coerceIn(1, len - 1)
        val t2 = (delayMs * 1.5f * sr / 1000f).toInt().coerceIn(1, len - 1)
        val t3 = (delayMs * 2f * sr / 1000f).toInt().coerceIn(1, len - 1)
        for (i in 0 until n) {
            for (c in 0 until buffer.channels) {
                val l = lines[c]
                val r1 = l[(idx - t1 + len) % len]
                val r2 = l[(idx - t2 + len) % len]
                val r3 = l[(idx - t3 + len) % len]
                l[idx] = buffer.data[c][i] + r1 * feedback
                buffer.data[c][i] = buffer.data[c][i] * dry + (r1 + r2 * 0.6f + r3 * 0.35f) * wet
            }
            if (++idx >= len) idx = 0
        }
    }
    override fun reset() { lines.forEach { Arrays.fill(it, 0f) }; idx = 0 }
    override fun setParam(key: String, value: Float) { when (key) {
        "time" -> delayMs = value; "feedback" -> feedback = value.coerceIn(0f, 0.95f); "wet" -> wet = value; "dry" -> dry = value } }
}

class StereoWidthProcessor : AudioDspProcessor {
    override val name = "width"
    var width = 1f
    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {}
    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        if (buffer.channels < 2) return
        val n = ctx.frames
        val l = buffer.data[0]; val r = buffer.data[1]
        for (i in 0 until n) {
            val m = (l[i] + r[i]) * 0.5f; val s = (l[i] - r[i]) * 0.5f * width
            l[i] = m + s; r[i] = m - s
        }
    }
    override fun reset() {}
    override fun setParam(key: String, value: Float) { if (key == "width") width = value.coerceIn(0f, 3f) }
}
