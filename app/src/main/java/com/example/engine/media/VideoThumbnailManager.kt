package com.example.engine.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * High-Performance, Multi-Tiered Video Thumbnail & Filmstrip Extraction Subsystem.
 *
 * Key Architecture Highlights:
 * - 2-Level Caching: Instant In-Memory LRU Cache (48MB) + Persistent Disk Cache on Local Storage.
 * - Hardware Decoder Concurrency Limiter (Semaphore) preventing native MediaServer starvation/crashes.
 * - In-flight job deduplication across timeline scrubbing and filmstrip tile requests.
 * - Automatic frame regeneration if cache is cleared or invalidated.
 * - Hardware rotation metadata detection with automatic orientation correction.
 * - Multi-stage fallback extraction (Closest Sync -> Closest -> First Frame -> Procedural Preview).
 */
object VideoThumbnailManager {

  private const val TAG = "VideoThumbnailManager"

  // Level 1: 64MB In-Memory Cache for instant UI tile rendering
  private val maxMemoryCacheBytes = 64 * 1024 * 1024
  private val memoryCache = object : LruCache<String, Bitmap>(maxMemoryCacheBytes) {
    override fun sizeOf(key: String, bitmap: Bitmap): Int {
      return bitmap.byteCount
    }
  }

  // Active in-flight coroutine Deferred tasks to deduplicate concurrent requests for the same key
  private val inFlightTasks = ConcurrentHashMap<String, Deferred<Bitmap?>>()

  // Dedicated bounded thread pool: Maximum 2 concurrent decoding workers
  private val decoderExecutor = java.util.concurrent.Executors.newFixedThreadPool(2) { runnable ->
    Thread(runnable, "ThumbnailDecoderPool").apply {
      priority = Thread.NORM_PRIORITY - 1 // Lower priority to keep UI thread 100% responsive
      isDaemon = true
    }
  }
  private val decoderDispatcher = decoderExecutor.asCoroutineDispatcher()

  // Concurrency limiter: Exactly 2 concurrent MediaMetadataRetriever decodes at once
  private val decoderSemaphore = Semaphore(2)

  // Dedicated background decoding scope with supervisor job
  private val thumbnailScope = CoroutineScope(decoderDispatcher + SupervisorJob())

  /**
   * Generates a quantized cache key based on URI, timestamp, target resolution, and rotation.
   */
  fun makeKey(uri: String, sourceTimeMs: Long, targetWidth: Int, targetHeight: Int, rotation: Int = 0): String {
    val quantizedTime = (sourceTimeMs.coerceAtLeast(0L) / 100L) * 100L
    return "${uri}_${quantizedTime}_${targetWidth}x${targetHeight}_r${rotation}"
  }

  /**
   * Retrieves a cached thumbnail synchronously from Memory Cache if present.
   */
  fun getCachedThumbnail(key: String): Bitmap? {
    val cached = memoryCache.get(key)
    if (cached != null && !cached.isRecycled) {
      return cached
    }
    return null
  }

  /**
   * Suspend function to retrieve or extract a thumbnail. Cooperates with coroutine cancellation.
   */
  suspend fun getThumbnail(
    context: Context,
    uri: String,
    sourceTimeMs: Long,
    targetWidth: Int = 240,
    targetHeight: Int = 240,
    isVideo: Boolean = true,
    rotation: Int = 0
  ): Bitmap? {
    val key = makeKey(uri, sourceTimeMs, targetWidth, targetHeight, rotation)
    val cached = memoryCache.get(key)
    if (cached != null && !cached.isRecycled) {
      return cached
    }
    return getOrExtractThumbnail(context.applicationContext, uri, sourceTimeMs, targetWidth, targetHeight, isVideo, rotation)
  }

  /**
   * Requests a thumbnail asynchronously.
   * Checks Memory Cache -> Checks Disk Cache -> Extracts from Video Decoder -> Persists to Caches.
   * Returns the Job so it can be cancelled if the caller is disposed.
   */
  fun requestThumbnail(
    context: Context,
    uri: String,
    sourceTimeMs: Long,
    targetWidth: Int = 240,
    targetHeight: Int = 240,
    isVideo: Boolean = true,
    rotation: Int = 0,
    onResult: (Bitmap) -> Unit
  ): kotlinx.coroutines.Job {
    val key = makeKey(uri, sourceTimeMs, targetWidth, targetHeight, rotation)
    val cached = memoryCache.get(key)
    if (cached != null && !cached.isRecycled) {
      onResult(cached)
      return kotlinx.coroutines.CompletableDeferred<Unit>().apply { complete(Unit) }
    }

    return thumbnailScope.launch {
      val bitmap = getOrExtractThumbnail(context.applicationContext, uri, sourceTimeMs, targetWidth, targetHeight, isVideo, rotation)
      if (bitmap != null && !bitmap.isRecycled) {
        withContext(Dispatchers.Main) {
          onResult(bitmap)
        }
      }
    }
  }

  /**
   * Synchronous or suspend extraction pipeline with multi-tier cache resolution.
   */
  suspend fun getOrExtractThumbnail(
    context: Context,
    uriString: String,
    sourceTimeMs: Long,
    targetWidth: Int,
    targetHeight: Int,
    isVideo: Boolean,
    rotation: Int = 0
  ): Bitmap? = withContext(Dispatchers.IO) {
    val key = makeKey(uriString, sourceTimeMs, targetWidth, targetHeight, rotation)

    // 1. Check Memory Cache
    val memCached = memoryCache.get(key)
    if (memCached != null && !memCached.isRecycled) {
      return@withContext memCached
    }

    // 2. Check Disk Cache
    val diskBitmap = loadFromDiskCache(context, key)
    if (diskBitmap != null && !diskBitmap.isRecycled) {
      memoryCache.put(key, diskBitmap)
      return@withContext diskBitmap
    }

    if (!currentCoroutineContext().isActive) return@withContext null

    // 3. Deduplicate in-flight extraction for the exact same key
    val existingDeferred = inFlightTasks[key]
    if (existingDeferred != null) {
      val result = existingDeferred.await()
      if (result != null && !result.isRecycled) {
        return@withContext result
      }
    }

    // 4. Launch new extraction task on bounded 2-thread pool
    val newDeferred = async(decoderDispatcher) {
      decoderSemaphore.withPermit {
        if (!currentCoroutineContext().isActive) return@withPermit null

        // Double-check memory cache after acquiring permit
        val doubleCheckMem = memoryCache.get(key)
        if (doubleCheckMem != null && !doubleCheckMem.isRecycled) {
          return@withPermit doubleCheckMem
        }

        val extracted = loadOrExtractRaw(context, uriString, sourceTimeMs, targetWidth, targetHeight, isVideo, rotation)
        if (extracted != null && !extracted.isRecycled && currentCoroutineContext().isActive) {
          memoryCache.put(key, extracted)
          // Save real media frames to persistent disk cache (avoid saving synthetic placeholders to disk)
          if (isVideo || (!uriString.startsWith("stock://") && !uriString.startsWith("sample://"))) {
            saveToDiskCache(context, key, extracted)
          }
        }
        extracted
      }
    }

    inFlightTasks[key] = newDeferred
    try {
      val result = newDeferred.await()
      result
    } finally {
      inFlightTasks.remove(key)
    }
  }

  /**
   * Raw extraction engine using MediaMetadataRetriever or BitmapFactory with ARGB_8888,
   * aspect-ratio preserving scaling, and high-quality center crop filtering.
   */
  private fun loadOrExtractRaw(
    context: Context,
    uriString: String,
    sourceTimeMs: Long,
    targetWidth: Int,
    targetHeight: Int,
    isVideo: Boolean,
    fallbackRotation: Int = 0
  ): Bitmap? {
    if (uriString.isBlank()) {
      return generatePlaceholderBitmap(uriString, sourceTimeMs, targetWidth, targetHeight)
    }

    if (!isVideo) {
      return decodeImageThumbnail(context, uriString, targetWidth, targetHeight, fallbackRotation)
    }

    // Decode Video Frame from original media source
    val retriever = MediaMetadataRetriever()
    try {
      val parsedUri = try { Uri.parse(uriString) } catch (e: Exception) { null }

      if (parsedUri != null && (parsedUri.scheme == "content" || parsedUri.scheme == "android.resource")) {
        retriever.setDataSource(context, parsedUri)
      } else if (parsedUri != null && parsedUri.scheme == "file") {
        retriever.setDataSource(parsedUri.path ?: uriString)
      } else if (parsedUri != null && parsedUri.scheme == "asset") {
        val assetPath = parsedUri.path?.removePrefix("/") ?: uriString.removePrefix("asset:///")
        val afd = context.assets.openFd(assetPath)
        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
      } else {
        val localFile = File(uriString)
        if (localFile.exists() && localFile.canRead()) {
          retriever.setDataSource(localFile.absolutePath)
        } else {
          retriever.setDataSource(uriString)
        }
      }

      val sourceTimeUs = (sourceTimeMs.coerceAtLeast(0L)) * 1000L

      // Query video dimensions and rotation from media metadata
      val widthStr = try { retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) } catch (_: Exception) { null }
      val heightStr = try { retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) } catch (_: Exception) { null }
      val rotationStr = try { retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) } catch (_: Exception) { null }

      val rawW = widthStr?.toIntOrNull() ?: 0
      val rawH = heightStr?.toIntOrNull() ?: 0
      val metaRot = rotationStr?.toIntOrNull() ?: 0
      val effectiveRotation = if (metaRot != 0) metaRot else fallbackRotation

      // Compute display dimensions after rotation
      val isTransposed = effectiveRotation == 90 || effectiveRotation == 270
      val dispW = if (isTransposed) rawH else rawW
      val dispH = if (isTransposed) rawW else rawH

      // Calculate extraction size matching video aspect ratio so hardware decoder does not squash frame
      val scale = if (dispW > 0 && dispH > 0) {
        maxOf(targetWidth.toFloat() / dispW.toFloat(), targetHeight.toFloat() / dispH.toFloat())
      } else 1.0f

      val reqDispW = if (dispW > 0) (dispW * scale).toInt().coerceAtLeast(targetWidth) else targetWidth
      val reqDispH = if (dispH > 0) (dispH * scale).toInt().coerceAtLeast(targetHeight) else targetHeight

      val (extractW, extractH) = if (isTransposed) {
        reqDispH to reqDispW
      } else {
        reqDispW to reqDispH
      }

      // Stage 1: Fast scaled keyframe extraction (instant performance for filmstrip tiles)
      var rawBitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && extractW > 0 && extractH > 0) {
        try {
          retriever.getScaledFrameAtTime(sourceTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, extractW, extractH)
        } catch (_: Throwable) { null }
      } else null

      // Stage 2: Scaled closest exact frame if sync keyframe is unavailable
      if (rawBitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && extractW > 0 && extractH > 0) {
        try {
          retriever.getScaledFrameAtTime(sourceTimeUs, MediaMetadataRetriever.OPTION_CLOSEST, extractW, extractH)
        } catch (_: Throwable) { null }
      }

      // Stage 3: Full-resolution unscaled frame at closest time
      if (rawBitmap == null) {
        try {
          rawBitmap = retriever.getFrameAtTime(sourceTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (_: Throwable) {}
      }

      // Stage 4: Unscaled closest sync keyframe
      if (rawBitmap == null) {
        try {
          rawBitmap = retriever.getFrameAtTime(sourceTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {}
      }

      // Stage 5: Timestamp 0 fallback
      if (rawBitmap == null) {
        try {
          rawBitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (_: Throwable) {}
      }

      if (rawBitmap != null) {
        // Correct rotation if not already rotated by OS/decoder
        val needsRotate = if (effectiveRotation != 0) {
          if (isTransposed) {
            // If already rotated by decoder, rawBitmap.width will be closer to dispW than rawW
            if (rawW > 0 && rawH > 0 && abs(rawBitmap.width - rawH) < abs(rawBitmap.width - rawW)) {
              false // Already auto-rotated by OS
            } else {
              true
            }
          } else {
            effectiveRotation == 180
          }
        } else false

        val orientedBitmap = if (needsRotate) {
          val matrix = Matrix().apply { postRotate(effectiveRotation.toFloat()) }
          val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
          if (rotated != rawBitmap && !rawBitmap.isRecycled) {
            try { rawBitmap.recycle() } catch (_: Throwable) {}
          }
          rotated
        } else {
          rawBitmap
        }

        // High-Quality Center-Crop Resize to targetWidth x targetHeight in ARGB_8888
        val finalBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)
        val paint = android.graphics.Paint(
          android.graphics.Paint.FILTER_BITMAP_FLAG or
          android.graphics.Paint.DITHER_FLAG or
          android.graphics.Paint.ANTI_ALIAS_FLAG
        ).apply {
          isFilterBitmap = true
          isDither = true
        }

        val sX = targetWidth.toFloat() / orientedBitmap.width.toFloat()
        val sY = targetHeight.toFloat() / orientedBitmap.height.toFloat()
        val cropScale = maxOf(sX, sY)
        val dx = (targetWidth - orientedBitmap.width * cropScale) * 0.5f
        val dy = (targetHeight - orientedBitmap.height * cropScale) * 0.5f

        val matrix = Matrix().apply {
          postScale(cropScale, cropScale)
          postTranslate(dx, dy)
        }
        canvas.drawBitmap(orientedBitmap, matrix, paint)

        if (orientedBitmap != finalBitmap && !orientedBitmap.isRecycled) {
          try { orientedBitmap.recycle() } catch (_: Throwable) {}
        }

        return finalBitmap
      }
    } catch (e: Throwable) {
      Log.w(TAG, "Video thumbnail extraction attempt for $uriString at ${sourceTimeMs}ms: ${e.message}")
    } finally {
      try {
        retriever.release()
      } catch (_: Exception) {}
    }

    // Fallback: Generate procedural placeholder
    return generatePlaceholderBitmap(uriString, sourceTimeMs, targetWidth, targetHeight)
  }

  /**
   * Decodes an image file as a high-quality ARGB_8888 thumbnail bitmap with rotation and center-crop.
   */
  private fun decodeImageThumbnail(
    context: Context,
    uriString: String,
    targetWidth: Int,
    targetHeight: Int,
    fallbackRotation: Int = 0
  ): Bitmap? {
    return try {
      val parsedUri = Uri.parse(uriString)
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

      if (parsedUri.scheme == "content" || parsedUri.scheme == "android.resource") {
        context.contentResolver.openInputStream(parsedUri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, options)
        }
      } else {
        val path = if (parsedUri.scheme == "file") parsedUri.path ?: uriString else uriString
        BitmapFactory.decodeFile(path, options)
      }

      var sampleSize = 1
      while ((options.outWidth / (sampleSize * 2)) >= targetWidth && (options.outHeight / (sampleSize * 2)) >= targetHeight) {
        sampleSize *= 2
      }

      val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }

      val decoded = if (parsedUri.scheme == "content" || parsedUri.scheme == "android.resource") {
        context.contentResolver.openInputStream(parsedUri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
      } else {
        val path = if (parsedUri.scheme == "file") parsedUri.path ?: uriString else uriString
        BitmapFactory.decodeFile(path, decodeOptions)
      }

      if (decoded != null) {
        val oriented = if (fallbackRotation != 0) {
          val matrix = Matrix().apply { postRotate(fallbackRotation.toFloat()) }
          val rot = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
          if (rot != decoded && !decoded.isRecycled) {
            try { decoded.recycle() } catch (_: Throwable) {}
          }
          rot
        } else decoded

        // Center-crop to target dimensions with high-quality filter
        val finalBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)
        val paint = android.graphics.Paint(
          android.graphics.Paint.FILTER_BITMAP_FLAG or
          android.graphics.Paint.DITHER_FLAG or
          android.graphics.Paint.ANTI_ALIAS_FLAG
        ).apply {
          isFilterBitmap = true
          isDither = true
        }

        val sX = targetWidth.toFloat() / oriented.width.toFloat()
        val sY = targetHeight.toFloat() / oriented.height.toFloat()
        val cropScale = maxOf(sX, sY)
        val dx = (targetWidth - oriented.width * cropScale) * 0.5f
        val dy = (targetHeight - oriented.height * cropScale) * 0.5f

        val matrix = Matrix().apply {
          postScale(cropScale, cropScale)
          postTranslate(dx, dy)
        }
        canvas.drawBitmap(oriented, matrix, paint)

        if (oriented != finalBitmap && !oriented.isRecycled) {
          try { oriented.recycle() } catch (_: Throwable) {}
        }
        finalBitmap
      } else {
        generatePlaceholderBitmap(uriString, 0L, targetWidth, targetHeight)
      }
    } catch (e: Exception) {
      generatePlaceholderBitmap(uriString, 0L, targetWidth, targetHeight)
    }
  }

  /**
   * Loads a cached frame from the persistent disk cache in ARGB_8888.
   */
  private fun loadFromDiskCache(context: Context, key: String): Bitmap? {
    return try {
      val cacheDir = MediaPersistenceManager.getThumbnailCacheDir(context)
      val hash = MediaPersistenceManager.md5(key)
      val file = File(cacheDir, "$hash.thumb")
      if (file.exists() && file.length() > 0L) {
        val options = BitmapFactory.Options().apply {
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Saves an extracted thumbnail bitmap to the persistent disk cache at high quality.
   */
  private fun saveToDiskCache(context: Context, key: String, bitmap: Bitmap) {
    try {
      val cacheDir = MediaPersistenceManager.getThumbnailCacheDir(context)
      val hash = MediaPersistenceManager.md5(key)
      val file = File(cacheDir, "$hash.thumb")
      FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        out.flush()
      }
    } catch (ignored: Exception) {}
  }

  /**
   * Generates a procedural cinematic thumbnail bitmap with dynamic scene color gradients.
   */
  fun generatePlaceholderBitmap(
    uriString: String,
    sourceTimeMs: Long,
    targetWidth: Int,
    targetHeight: Int
  ): Bitmap {
    val width = targetWidth.coerceIn(80, 480)
    val height = targetHeight.coerceIn(80, 480)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val seed = abs(uriString.hashCode() + (sourceTimeMs / 1000L).toInt() * 37)
    val color1 = Color.rgb(
      (20 + (seed * 43) % 70),
      (30 + (seed * 67) % 90),
      (60 + (seed * 89) % 120)
    )
    val color2 = Color.rgb(
      (40 + ((seed + 13) * 53) % 90),
      (15 + ((seed + 7) * 31) % 60),
      (70 + ((seed + 23) * 73) % 130)
    )

    val shader = android.graphics.LinearGradient(
      0f, 0f, width.toFloat(), height.toFloat(),
      color1, color2,
      android.graphics.Shader.TileMode.CLAMP
    )
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    return bitmap
  }

  /**
   * Invalidates memory-cached thumbnails for a specific media URI.
   */
  fun invalidateClip(uriString: String) {
    val snapshot = memoryCache.snapshot()
    for ((k, _) in snapshot) {
      if (k.startsWith(uriString)) {
        memoryCache.remove(k)
      }
    }
  }

  /**
   * Clears in-memory cache to release RAM if needed.
   */
  fun clearMemoryCache() {
    memoryCache.evictAll()
  }
}
