package com.example.engine.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * High-performance, thread-safe memory bounded Bitmap pool.
 * Eliminates garbage collection pauses and prevents OutOfMemory errors
 * during 4K, 2K, and heavy multi-layer video rendering.
 */
class RenderBitmapPool(
  private val maxPoolSizeBytes: Long = 128 * 1024 * 1024L // 128 MB default cap
) {
  private val tag = "RenderBitmapPool"

  private val pools = ConcurrentHashMap<String, ConcurrentLinkedQueue<Bitmap>>()
  private var currentPoolSizeBytes = 0L

  private fun makeKey(width: Int, height: Int, config: Bitmap.Config): String {
    return "${width}x${height}_$config"
  }

  /**
   * Acquires a reusable Bitmap with the specified dimensions, or allocates a new one.
   */
  @Synchronized
  fun acquire(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
    val key = makeKey(width, height, config)
    val queue = pools[key]
    if (queue != null) {
      val pooled = queue.poll()
      if (pooled != null && !pooled.isRecycled) {
        currentPoolSizeBytes -= pooled.allocationByteCount
        return pooled
      }
    }

    // Check available JVM heap memory
    val runtime = Runtime.getRuntime()
    val availableMemory = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
    if (availableMemory < 32 * 1024 * 1024L) {
      trimToSize(0L) // Under severe memory pressure, flush pool immediately
      System.gc()
    }

    return try {
      Bitmap.createBitmap(width, height, config)
    } catch (e: OutOfMemoryError) {
      Log.w(tag, "OOM in RenderBitmapPool. Evicting all cached bitmaps and retrying allocation...", e)
      trimToSize(0L)
      System.gc()
      Bitmap.createBitmap(width, height, config)
    }
  }

  /**
   * Releases a Bitmap back into the pool for future reuse.
   */
  @Synchronized
  fun release(bitmap: Bitmap?) {
    if (bitmap == null || bitmap.isRecycled || !bitmap.isMutable) return

    val key = makeKey(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
    val sizeBytes = bitmap.allocationByteCount.toLong()

    if (currentPoolSizeBytes + sizeBytes > maxPoolSizeBytes) {
      trimToSize(maxPoolSizeBytes / 2)
    }

    if (currentPoolSizeBytes + sizeBytes <= maxPoolSizeBytes) {
      val queue = pools.getOrPut(key) { ConcurrentLinkedQueue() }
      queue.offer(bitmap)
      currentPoolSizeBytes += sizeBytes
    } else {
      try {
        bitmap.recycle()
      } catch (ignored: Exception) {}
    }
  }

  /**
   * Trims pool size to target byte budget.
   */
  @Synchronized
  fun trimToSize(targetBytes: Long) {
    for ((_, queue) in pools) {
      while (currentPoolSizeBytes > targetBytes && queue.isNotEmpty()) {
        val bmp = queue.poll() ?: break
        if (!bmp.isRecycled) {
          currentPoolSizeBytes -= bmp.allocationByteCount
          try {
            bmp.recycle()
          } catch (ignored: Exception) {}
        }
      }
    }
    if (targetBytes == 0L) {
      pools.clear()
      currentPoolSizeBytes = 0L
    }
  }

  /**
   * Completely clears and recycles all pooled bitmaps.
   */
  @Synchronized
  fun clear() {
    trimToSize(0L)
  }
}
