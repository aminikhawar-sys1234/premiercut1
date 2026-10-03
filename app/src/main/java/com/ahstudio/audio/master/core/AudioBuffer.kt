package com.ahstudio.audio.master.core

import java.util.Arrays

class AudioBuffer(val channels: Int, val frames: Int) {
    val data: Array<FloatArray> = Array(channels) { FloatArray(frames) }

    fun clear() { for (c in data) Arrays.fill(c, 0f) }

    fun maxAbs(count: Int): Float {
        var m = 0f
        for (c in data) {
            for (i in 0 until count) {
                val v = c[i]
                val av = if (v < 0f) -v else v
                if (av > m) m = av
            }
        }
        return m
    }

    fun interleave(dst: FloatArray, count: Int) {
        var k = 0
        for (i in 0 until count) {
            for (ch in 0 until channels) {
                dst[k++] = data[ch][i]
            }
        }
    }

    fun deinterleave(src: FloatArray, count: Int) {
        var k = 0
        for (i in 0 until count) {
            for (ch in 0 until channels) {
                data[ch][i] = src[k++]
            }
        }
    }
}

class AudioBufferPool(private val channels: Int, private val frames: Int) {
    private val free = ArrayDeque<AudioBuffer>()
    @Synchronized fun obtain(): AudioBuffer = free.removeFirstOrNull() ?: AudioBuffer(channels, frames)
    @Synchronized fun recycle(b: AudioBuffer) { if (b.frames == frames && b.channels == channels) free.addLast(b) }
}
