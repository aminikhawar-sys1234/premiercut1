package com.ahstudio.face.performance

import com.ahstudio.face.core.FaceTrackingResult

/**
 * Timestamp-bucketed, LRU, generation-invalidated tracking cache.
 */
class TrackingCache(private val maxEntries: Int = 900, private val bucketUs: Long = 33_333) {
    private var generation = 0
    private val map = object : LinkedHashMap<Long, FaceTrackingResult>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, FaceTrackingResult>) = size > maxEntries
    }
    @Volatile var hits = 0L
    @Volatile var misses = 0L

    @Synchronized fun bumpGeneration() { generation++; map.clear() }

    private fun key(clipId: String, hash: Int, timeUs: Long) =
        (clipId.hashCode().toLong() * 1_000_003L + hash) * 31L + timeUs / bucketUs

    @Synchronized fun get(clipId: String, hash: Int, sourceTimeUs: Long,
                          toleranceUs: Long = bucketUs / 2): FaceTrackingResult? {
        val r = map[key(clipId, hash, sourceTimeUs)]
        if (r != null && kotlin.math.abs(r.timestampUs - sourceTimeUs) <= toleranceUs) { hits++; return r }
        misses++
        return null
    }

    @Synchronized fun put(clipId: String, hash: Int, result: FaceTrackingResult) {
        map[key(clipId, hash, result.timestampUs)] = result
    }

    @Synchronized fun hitRate(): Float {
        val t = hits + misses
        return if (t == 0L) 0f else hits.toFloat() / t
    }
}
