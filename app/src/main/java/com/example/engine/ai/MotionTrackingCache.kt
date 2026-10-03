package com.example.engine.ai

import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance in-memory cache for motion tracking results.
 * Prevents redundant video frame extraction and tracking re-computation.
 */
object MotionTrackingCache {

    private val cache = ConcurrentHashMap<String, TrackingResult>()

    fun makeKey(
        clipId: String,
        videoUri: String,
        category: TrackingCategory,
        region: NormalizedRect,
        startUs: Long,
        durationUs: Long
    ): String {
        val regionHash = "${(region.left * 100).toInt()}_${(region.top * 100).toInt()}_${(region.right * 100).toInt()}_${(region.bottom * 100).toInt()}"
        return "${clipId}_${videoUri.hashCode()}_${category.name}_${regionHash}_${startUs}_${durationUs}"
    }

    fun get(key: String): TrackingResult? {
        return cache[key]
    }

    fun put(key: String, result: TrackingResult) {
        cache[key] = result
    }

    fun invalidateClip(clipId: String) {
        val iterator = cache.keys().asIterator()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key.startsWith("${clipId}_")) {
                cache.remove(key)
            }
        }
    }

    fun clear() {
        cache.clear()
    }
}
