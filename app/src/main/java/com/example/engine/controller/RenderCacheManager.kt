package com.example.engine.controller

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance, memory-bounded rendering cache for video preview frames,
 * text overlays, sticker bitmaps, and GPU texture references.
 *
 * Implements granular layer invalidation so lightweight edits (e.g., text position,
 * scale, rotation, opacity) invalidate only the affected layer rather than
 * forcing a full preview reload.
 */
class RenderCacheManager(
  maxMemoryBytes: Int = (Runtime.getRuntime().maxMemory() / 8).toInt().coerceIn(16 * 1024 * 1024, 64 * 1024 * 1024)
) {

  companion object {
    private const val TAG = "RenderCacheManager"
  }

  // LRU Bitmap cache for extracted/decoded preview frames
  private val frameBitmapCache = object : LruCache<String, Bitmap>(maxMemoryBytes) {
    override fun sizeOf(key: String, bitmap: Bitmap): Int {
      return bitmap.byteCount
    }

    override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
      if (evicted && oldValue != newValue && !oldValue.isRecycled) {
        try {
          oldValue.recycle()
        } catch (ignored: Exception) {}
      }
    }
  }

  // Clip-to-cache keys index for granular invalidation
  private val clipKeyIndex = ConcurrentHashMap<String, MutableSet<String>>()

  // Layer dirty flags
  private val dirtyLayerMap = ConcurrentHashMap<String, Long>()

  fun putFrame(clipId: String, timestampMs: Long, bitmap: Bitmap) {
    if (bitmap.isRecycled) return
    val key = "${clipId}_${timestampMs}"
    frameBitmapCache.put(key, bitmap)
    clipKeyIndex.computeIfAbsent(clipId) { ConcurrentHashMap.newKeySet() }.add(key)
  }

  fun getFrame(clipId: String, timestampMs: Long): Bitmap? {
    val key = "${clipId}_${timestampMs}"
    val bmp = frameBitmapCache.get(key)
    return if (bmp != null && !bmp.isRecycled) bmp else null
  }

  /**
   * Granularly invalidates only the cached frames and layer artifacts belonging
   * to a specific clip or overlay layer.
   */
  fun invalidateClip(clipId: String) {
    dirtyLayerMap[clipId] = System.currentTimeMillis()
    val keys = clipKeyIndex.remove(clipId) ?: return
    for (key in keys) {
      frameBitmapCache.remove(key)
    }
    Log.d(TAG, "Granularly invalidated cache for clip: $clipId (${keys.size} entries)")
  }

  fun isLayerDirty(clipId: String, lastRenderTime: Long): Boolean {
    val dirtyTime = dirtyLayerMap[clipId] ?: return false
    return dirtyTime > lastRenderTime
  }

  fun markLayerClean(clipId: String) {
    dirtyLayerMap.remove(clipId)
  }

  fun clear() {
    clipKeyIndex.clear()
    dirtyLayerMap.clear()
    frameBitmapCache.evictAll()
    Log.d(TAG, "RenderCacheManager all frame caches evicted cleanly")
  }
}
