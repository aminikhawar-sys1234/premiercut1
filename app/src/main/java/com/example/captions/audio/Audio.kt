package com.ahstudio.captions.audio

import android.net.Uri
import com.ahstudio.captions.core.time.TimelineUs
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs
import kotlin.math.sqrt

data class AudioChunk(
    val samples: FloatArray,
    val startMicros: Long,
    val sampleRateHz: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioChunk
        return startMicros == other.startMicros && sampleRateHz == other.sampleRateHz && samples.contentEquals(other.samples)
    }
    override fun hashCode(): Int = 31 * (31 * startMicros.hashCode() + sampleRateHz.hashCode()) + samples.contentHashCode()
}

data class AudioInfo(
    val hasAudioTrack: Boolean,
    val durationUs: Long,
    val sampleRateHint: Int? = null,
)

interface AudioExtractor {
    fun probe(uri: Uri): AudioInfo
    fun extract(uri: Uri, targetSampleRate: Int = 16_000): Flow<AudioChunk>
}

class LinearResampler(private val sourceRate: Int, private val targetRate: Int) {
    fun process(samples: FloatArray): FloatArray {
        if (sourceRate == targetRate || samples.isEmpty()) return samples
        val ratio = sourceRate.toDouble() / targetRate.toDouble()
        val outLen = (samples.size / ratio).toInt()
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val idx = srcPos.toInt()
            val frac = (srcPos - idx).toFloat()
            val s0 = samples.getOrElse(idx) { samples.last() }
            val s1 = samples.getOrElse(idx + 1) { s0 }
            out[i] = s0 + (s1 - s0) * frac
        }
        return out
    }
}

class AudioPreprocessor(
    private val highPassHz: Float = 80f,
    private val normalizePeak: Boolean = true,
) {
    private var prevX = 0f
    private var prevY = 0f

    fun process(chunk: AudioChunk): AudioChunk {
        val s = chunk.samples
        if (s.isEmpty()) return chunk
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * highPassHz * (1.0f / chunk.sampleRateHz))
        val dt = 1.0f / chunk.sampleRateHz
        val alpha = rc / (rc + dt)

        val out = FloatArray(s.size)
        var sum = 0f
        for (i in s.indices) sum += s[i]
        val dc = sum / s.size

        var maxVal = 0f
        for (i in s.indices) {
            val x = s[i] - dc
            val y = alpha * (prevY + x - prevX)
            prevX = x; prevY = y
            out[i] = y
            maxVal = maxOf(maxVal, abs(y))
        }

        if (normalizePeak && maxVal > 1e-5f) {
            val gain = minOf(0.96f / maxVal, 5.0f)
            for (i in out.indices) out[i] *= gain
        }
        return chunk.copy(samples = out)
    }
}

data class SpeechRegion(val startMicros: Long, val endMicros: Long)
data class SilenceRegion(val start: TimelineUs, val end: TimelineUs)

class EnergyVad(
    private val frameDurationMs: Int = 30,
    private val energyThreshold: Float = 0.008f,
) {
    fun analyze(samples: FloatArray, sampleRate: Int): List<SpeechRegion> {
        val frameSize = sampleRate * frameDurationMs / 1000
        if (samples.size < frameSize) return emptyList()
        val out = ArrayList<SpeechRegion>()
        var inSpeech = false
        var speechStart = 0L

        for (i in 0..samples.size - frameSize step frameSize) {
            var energy = 0f
            for (j in 0 until frameSize) {
                val v = samples[i + j]
                energy += v * v
            }
            energy = sqrt(energy / frameSize)
            val timeUs = (i.toLong() * 1_000_000L) / sampleRate

            if (energy >= energyThreshold) {
                if (!inSpeech) { inSpeech = true; speechStart = timeUs }
            } else {
                if (inSpeech) {
                    inSpeech = false
                    out += SpeechRegion(speechStart, timeUs)
                }
            }
        }
        if (inSpeech) {
            out += SpeechRegion(speechStart, (samples.size.toLong() * 1_000_000L) / sampleRate)
        }
        return out
    }

    companion object {
        fun merge(regions: List<SpeechRegion>, maxGapUs: Long = 300_000L): List<SpeechRegion> {
            if (regions.isEmpty()) return emptyList()
            val sorted = regions.sortedBy { val r: SpeechRegion = it; r.startMicros }
            val merged = ArrayList<SpeechRegion>()
            var cur = sorted[0]
            for (i in 1 until sorted.size) {
                val next = sorted[i]
                if (next.startMicros - cur.endMicros <= maxGapUs) {
                    cur = SpeechRegion(cur.startMicros, maxOf(cur.endMicros, next.endMicros))
                } else {
                    merged += cur; cur = next
                }
            }
            merged += cur
            return merged
        }
    }
}

object SilenceAnalyzer {
    fun silences(speechRegions: List<SpeechRegion>, mediaDurationUs: Long, minSilenceUs: Long = 200_000L): List<SilenceRegion> {
        if (mediaDurationUs <= 0) return emptyList()
        val silences = ArrayList<SilenceRegion>()
        val sorted = speechRegions.sortedBy { it.startMicros }
        var cursor = 0L
        for (r in sorted) {
            if (r.startMicros - cursor >= minSilenceUs) {
                silences += SilenceRegion(TimelineUs(cursor), TimelineUs(r.startMicros))
            }
            cursor = maxOf(cursor, r.endMicros)
        }
        if (mediaDurationUs - cursor >= minSilenceUs) {
            silences += SilenceRegion(TimelineUs(cursor), TimelineUs(mediaDurationUs))
        }
        return silences
    }
}
