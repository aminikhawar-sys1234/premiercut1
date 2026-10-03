package com.ahstudio.audio.master.waveform

import com.ahstudio.audio.master.cache.DecodedAudioCache
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class WaveformData(
    val sourceId: String,
    val bucketCount: Int,
    val min: FloatArray,
    val max: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WaveformData
        if (sourceId != other.sourceId) return false
        if (bucketCount != other.bucketCount) return false
        if (!min.contentEquals(other.min)) return false
        if (!max.contentEquals(other.max)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sourceId.hashCode()
        result = 31 * result + bucketCount
        result = 31 * result + min.contentHashCode()
        result = 31 * result + max.contentHashCode()
        return result
    }
}

object WaveformGenerator {
    fun generate(
        sourceId: String,
        pcm: Array<FloatArray>,
        sampleRate: Int,
        bucketsPerSecond: Int
    ): WaveformData {
        if (pcm.isEmpty() || pcm[0].isEmpty()) {
            return WaveformData(sourceId, 0, FloatArray(0), FloatArray(0))
        }

        val frames = pcm[0].size
        val channels = pcm.size
        val totalSec = frames.toDouble() / sampleRate
        val bucketCount = maxOf(1, (totalSec * bucketsPerSecond).roundToInt())

        val minArr = FloatArray(bucketCount)
        val maxArr = FloatArray(bucketCount)

        for (i in 0 until bucketCount) {
            val startFrame = ((i.toLong() * frames) / bucketCount).toInt()
            val endFrame = (((i.toLong() + 1) * frames) / bucketCount).toInt().coerceAtMost(frames)

            var curMin = Float.MAX_VALUE
            var curMax = -Float.MAX_VALUE

            if (startFrame >= endFrame) {
                curMin = 0f
                curMax = 0f
            } else {
                for (f in startFrame until endFrame) {
                    for (c in 0 until channels) {
                        val sample = pcm[c][f]
                        if (sample < curMin) curMin = sample
                        if (sample > curMax) curMax = sample
                    }
                }
            }

            minArr[i] = if (curMin == Float.MAX_VALUE) 0f else curMin
            maxArr[i] = if (curMax == -Float.MAX_VALUE) 0f else curMax
        }

        return WaveformData(sourceId, bucketCount, minArr, maxArr)
    }
}

class WaveformEngine(
    private val cache: DecodedAudioCache,
    private val cacheDir: File,
    private val scope: CoroutineScope
) {
    private val memoryCache = ConcurrentHashMap<String, WaveformData>()

    fun requestAsync(sourceId: String, bucketsPerSecond: Int) {
        scope.launch {
            runCatching {
                val pcm = cache.get(sourceId) ?: return@launch
                val data = WaveformGenerator.generate(sourceId, pcm.data, pcm.sampleRate, bucketsPerSecond)
                memoryCache[sourceId] = data
            }
        }
    }

    fun invalidate(sourceId: String) {
        memoryCache.remove(sourceId)
    }

    fun getWaveform(sourceId: String): WaveformData? = memoryCache[sourceId]
}
