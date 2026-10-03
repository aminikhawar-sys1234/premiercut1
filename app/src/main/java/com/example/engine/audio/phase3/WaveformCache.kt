package com.example.engine.audio.phase3

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

/** Immutable multi-resolution peak cache. UI asks only for the visible source range. */
class WaveformCache {
  data class Key(val sourceId: String, val level: Int)
  data class Peaks(val durationMs: Long, val samplesPerPeak: Long, val amplitudes: FloatArray)

  private val cache = ConcurrentHashMap<Key, Peaks>()

  fun put(sourceId: String, level: Int, durationMs: Long, samplesPerPeak: Long, amplitudes: FloatArray) {
    cache[Key(sourceId, level.coerceAtLeast(0))] = Peaks(durationMs, samplesPerPeak.coerceAtLeast(1L), amplitudes.copyOf())
  }

  fun getVisible(sourceId: String, level: Int, startMs: Long, endMs: Long): FloatArray {
    val p = cache[Key(sourceId, level.coerceAtLeast(0))] ?: return FloatArray(0)
    val start = max(0L, startMs) / p.samplesPerPeak
    val end = max(start, endMs) / p.samplesPerPeak
    val from = start.coerceAtMost(p.amplitudes.size.toLong()).toInt()
    val to = (end + 1).coerceAtMost(p.amplitudes.size.toLong()).toInt()
    return if (to > from) p.amplitudes.copyOfRange(from, to) else FloatArray(0)
  }

  fun contains(sourceId: String, level: Int): Boolean = cache.containsKey(Key(sourceId, level.coerceAtLeast(0)))
  fun clear(sourceId: String? = null) {
    if (sourceId == null) cache.clear() else cache.keys.removeIf { it.sourceId == sourceId }
  }

  companion object {
    /** Downsample PCM to peak amplitudes once; never perform this in the timeline draw loop. */
    fun build(sourceId: String, pcm: FloatArray, channels: Int, durationMs: Long, targetPeaks: Int): Pair<String, PeaksResult> {
      val c = channels.coerceAtLeast(1)
      val frames = pcm.size / c
      val bucket = max(1, frames / targetPeaks.coerceAtLeast(1))
      val out = FloatArray((frames + bucket - 1) / bucket)
      for (i in out.indices) {
        val from = i * bucket
        val to = minOf(frames, from + bucket)
        var peak = 0f
        for (f in from until to) for (ch in 0 until c) peak = max(peak, abs(pcm[f * c + ch]))
        out[i] = peak.coerceIn(0f, 1f)
      }
      return sourceId to PeaksResult(durationMs, bucket.toLong(), out)
    }
  }

  data class PeaksResult(val durationMs: Long, val samplesPerPeak: Long, val amplitudes: FloatArray)
}
