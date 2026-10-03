package com.example.engine.memory

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.opengl.GLES20
import android.util.Log
import com.example.engine.export.RenderBitmapPool
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max

/**
 * Production-Grade Centralized Memory Manager for Video Composition, GPU, & Export.
 * Controls bitmap allocation, OpenGL texture pooling, FBO reuse, direct ByteBuffer caching,
 * and timeline frame rendering caches. Automatically handles system memory pressure.
 */
class EngineMemoryManager private constructor(context: Context) : ComponentCallbacks2 {

  companion object {
    private const val TAG = "EngineMemoryManager"

    @Volatile
    private var instance: EngineMemoryManager? = null

    fun getInstance(context: Context): EngineMemoryManager {
      return instance ?: synchronized(this) {
        instance ?: EngineMemoryManager(context.applicationContext).also {
          instance = it
          context.applicationContext.registerComponentCallbacks(it)
        }
      }
    }

    fun get(): EngineMemoryManager? = instance
  }

  val bitmapPool = RenderBitmapPool(maxPoolSizeBytes = 160 * 1024 * 1024L) // 160MB cap
  val texturePool = GlTexturePool()
  val framebufferPool = GlFramebufferPool()
  val directBufferPool = DirectBufferPool()
  val renderFrameCache = RenderFrameCache(maxCacheSizeMb = 64)

  init {
    Log.d(TAG, "Initialized Centralized Engine Memory Manager")
  }

  /**
   * Monitor JVM & Native memory usage. Returns true if system is under heavy memory pressure.
   */
  fun isMemoryPressureHigh(): Boolean {
    val runtime = Runtime.getRuntime()
    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
    val maxMemory = runtime.maxMemory()
    val usageRatio = usedMemory.toDouble() / maxMemory.toDouble()
    return usageRatio > 0.82
  }

  /**
   * Trims all internal pools to prevent OutOfMemory during high resolution (4K/8K) processing.
   */
  fun trimAll(forceFullFlush: Boolean = false) {
    Log.i(TAG, "Trimming engine memory pools (forceFullFlush=$forceFullFlush)")
    val targetBitmapBytes = if (forceFullFlush) 0L else 32 * 1024 * 1024L
    bitmapPool.trimToSize(targetBitmapBytes)
    texturePool.trim()
    framebufferPool.trim()
    renderFrameCache.clear()
    if (forceFullFlush) {
      directBufferPool.clear()
      System.gc()
    }
  }

  override fun onTrimMemory(level: Int) {
    Log.w(TAG, "System onTrimMemory callback received level=$level")
    when (level) {
      ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
      ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
      ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
        trimAll(forceFullFlush = true)
      }
      ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
      ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
        trimAll(forceFullFlush = false)
      }
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {}
  override fun onLowMemory() {
    Log.e(TAG, "System onLowMemory triggered! Flushing all video engine caches.")
    trimAll(forceFullFlush = true)
  }
}

/**
 * Thread-safe OpenGL Texture Pool to recycle GL 2D textures and reduce glGenTextures overhead.
 */
class GlTexturePool {
  private val tag = "GlTexturePool"
  private val availableTextures = ConcurrentLinkedQueue<Int>()
  private val activeTextureIds = ConcurrentHashMap.newKeySet<Int>()

  @Synchronized
  fun acquireTexture(width: Int, height: Int, format: Int = GLES20.GL_RGBA): Int {
    val pooledId = availableTextures.poll()
    if (pooledId != null && pooledId > 0 && GLES20.glIsTexture(pooledId)) {
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pooledId)
      activeTextureIds.add(pooledId)
      return pooledId
    }

    val texArr = IntArray(1)
    GLES20.glGenTextures(1, texArr, 0)
    val texId = texArr[0]
    if (texId > 0) {
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, format, width, height, 0, format, GLES20.GL_UNSIGNED_BYTE, null)
      activeTextureIds.add(texId)
    }
    return texId
  }

  @Synchronized
  fun releaseTexture(textureId: Int) {
    if (textureId <= 0) return
    activeTextureIds.remove(textureId)
    if (availableTextures.size < 32 && GLES20.glIsTexture(textureId)) {
      availableTextures.offer(textureId)
    } else {
      val arr = intArrayOf(textureId)
      GLES20.glDeleteTextures(1, arr, 0)
    }
  }

  @Synchronized
  fun trim() {
    val toDelete = mutableListOf<Int>()
    while (availableTextures.isNotEmpty()) {
      availableTextures.poll()?.let { toDelete.add(it) }
    }
    if (toDelete.isNotEmpty()) {
      val arr = toDelete.toIntArray()
      GLES20.glDeleteTextures(arr.size, arr, 0)
      Log.d(tag, "Freed ${toDelete.size} pooled GL textures")
    }
  }
}

/**
 * Reusable Framebuffer Pool for Offscreen Multi-Pass Shaders.
 */
class GlFramebufferPool {
  private val pool = ConcurrentLinkedQueue<com.example.engine.composition.gpu.GlFramebuffer>()

  fun acquire(width: Int, height: Int): com.example.engine.composition.gpu.GlFramebuffer {
    val fbo = pool.poll() ?: com.example.engine.composition.gpu.GlFramebuffer()
    fbo.setup(width, height)
    return fbo
  }

  fun release(fbo: com.example.engine.composition.gpu.GlFramebuffer?) {
    if (fbo == null) return
    if (pool.size < 12) {
      pool.offer(fbo)
    } else {
      fbo.release()
    }
  }

  fun trim() {
    while (pool.isNotEmpty()) {
      pool.poll()?.release()
    }
  }
}

/**
 * Direct ByteBuffer & FloatBuffer Pool for Zero-Allocation Attribute Transfers.
 */
class DirectBufferPool {
  private val floatBufferPool = ConcurrentHashMap<Int, ConcurrentLinkedQueue<FloatBuffer>>()

  fun acquireFloatBuffer(capacityFloats: Int): FloatBuffer {
    val queue = floatBufferPool.getOrPut(capacityFloats) { ConcurrentLinkedQueue() }
    val buf = queue.poll()
    if (buf != null) {
      buf.clear()
      return buf
    }
    return ByteBuffer.allocateDirect(capacityFloats * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
  }

  fun releaseFloatBuffer(buffer: FloatBuffer?) {
    if (buffer == null) return
    buffer.clear()
    val queue = floatBufferPool.getOrPut(buffer.capacity()) { ConcurrentLinkedQueue() }
    if (queue.size < 16) {
      queue.offer(buffer)
    }
  }

  fun clear() {
    floatBufferPool.clear()
  }
}

/**
 * Cache rendered frame bitmaps based on timeline position and composition state hash.
 */
class RenderFrameCache(private val maxCacheSizeMb: Int = 64) {
  private val maxBytes = maxCacheSizeMb * 1024 * 1024L
  private val cache = ConcurrentHashMap<String, Bitmap>()
  private var currentSize = 0L

  @Synchronized
  fun get(key: String): Bitmap? {
    val bmp = cache[key]
    if (bmp != null && !bmp.isRecycled) {
      return bmp
    }
    if (bmp?.isRecycled == true) {
      cache.remove(key)
    }
    return null
  }

  @Synchronized
  fun put(key: String, bitmap: Bitmap) {
    if (bitmap.isRecycled) return
    val bytes = bitmap.allocationByteCount.toLong()
    if (currentSize + bytes > maxBytes) {
      clear()
    }
    cache[key] = bitmap
    currentSize += bytes
  }

  @Synchronized
  fun clear() {
    for ((_, bmp) in cache) {
      if (!bmp.isRecycled) {
        try { bmp.recycle() } catch (ignored: Exception) {}
      }
    }
    cache.clear()
    currentSize = 0L
  }
}
