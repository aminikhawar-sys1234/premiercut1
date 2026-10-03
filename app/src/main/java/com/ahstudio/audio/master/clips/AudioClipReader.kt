package com.ahstudio.audio.master.clips

import com.ahstudio.audio.master.cache.DecodedAudioCache
import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.model.AudioClipModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ClipRead(val offsetFrames: Int, val frames: Int)

class AudioClipReader(private val cache: DecodedAudioCache) : AudioClipSource {

    override fun readClip(clip: AudioClipModel, ctx: AudioRenderContext, out: AudioBuffer): ClipRead {
        val entry = cache.getForClip(clip) ?: return ClipRead(0, 0)
        val sr = entry.sampleRate
        val speed = clip.transform.speed.toDouble()
        val tStart = max(ctx.timelineStartSec, clip.timelineStartSec)
        val tEnd = min(ctx.timelineEndSec, clip.timelineEndSec)
        if (tEnd <= tStart) return ClipRead(0, 0)
        val offset = ((tStart - ctx.timelineStartSec) * ctx.sampleRate).roundToInt().coerceAtLeast(0)
        val count = ((tEnd - tStart) * ctx.sampleRate).roundToInt()
            .coerceAtMost(out.frames - offset).coerceAtLeast(0)
        if (count == 0) return ClipRead(offset, 0)

        val srcPos0 = (cache.effectiveSourceStartSec(clip) + (tStart - clip.timelineStartSec) * speed) * sr
        val avail = entry.frames
        if (speed == 1.0) {
            val s0 = srcPos0.roundToInt()
            for (ch in 0 until out.channels) {
                val src = entry.data[ch.coerceAtMost(entry.data.size - 1)]
                val dst = out.data[ch]
                var remaining = count
                var di = offset; var si = s0
                if (si < 0) { val skip = min(remaining, -si); di += skip; remaining -= skip; si = 0 }
                val n = min(remaining, (avail - si).coerceAtLeast(0))
                if (n > 0) System.arraycopy(src, si, dst, di, n)
            }
        } else {
            var pos = srcPos0
            for (i in 0 until count) {
                val i0 = pos.toInt(); val frac = (pos - i0).toFloat()
                val i1 = i0 + 1
                for (ch in 0 until out.channels) {
                    val src = entry.data[ch.coerceAtMost(entry.data.size - 1)]
                    val a = if (i0 in 0 until avail) src[i0] else 0f
                    val b = if (i1 in 0 until avail) src[i1] else 0f
                    out.data[ch][offset + i] = a + (b - a) * frac
                }
                pos += speed
            }
        }
        return ClipRead(offset, count)
    }
}
