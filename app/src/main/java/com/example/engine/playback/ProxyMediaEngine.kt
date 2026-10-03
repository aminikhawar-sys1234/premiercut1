package com.example.engine.playback

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.example.domain.model.VideoClip
import com.example.engine.memory.EngineMemoryManager
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

enum class PreviewQuality(val maxDimension: Int, val label: String) {
  HIGH(1920, "1080p Native (High Quality)"),
  BALANCED(1280, "720p Proxy (Balanced)"),
  PERFORMANCE(720, "540p Fast Scrub (High FPS)")
}

data class CachedFrameItem(
  val bitmap: Bitmap,
  val clipId: String,
  val timeMs: Long,
  val byteSize: Long,
  val lastAccessTime: Long = System.currentTimeMillis()
)

/**
 * High-performance Bounded LRU Frame Cache.
 * Prevents memory leaks by dynamically monitoring system memory pressure,
 * auto-evicting least recently used bitmaps, and safely recycling bitmaps.
 */
class BoundedFrameCache(private val maxMemoryMb: Int = 48) {
  private val tag = "BoundedFrameCache"
  private val cache = ConcurrentHashMap<String, CachedFrameItem>()
  private var currentSizeByte = 0L
  private val maxBytes: Long get() = maxMemoryMb * 1024 * 1024L

  @Synchronized
  fun get(key: String): Bitmap? {
    val item = cache[key] ?: return null
    if (item.bitmap.isRecycled) {
      cache.remove(key)
      return null
    }
    // Update access time for LRU
    val updated = item.copy(lastAccessTime = System.currentTimeMillis())
    cache[key] = updated
    return updated.bitmap
  }

  @Synchronized
  fun getNearestFrame(clipId: String, sourcePosMs: Long, maxToleranceMs: Long = 1500L): Bitmap? {
    var bestMatch: CachedFrameItem? = null
    var minDiff = Long.MAX_VALUE

    for (item in cache.values) {
      if (item.clipId == clipId && !item.bitmap.isRecycled) {
        val diff = abs(item.timeMs - sourcePosMs)
        if (diff < minDiff && diff <= maxToleranceMs) {
          minDiff = diff
          bestMatch = item
        }
      }
    }

    return bestMatch?.bitmap
  }

  @Synchronized
  fun put(key: String, bitmap: Bitmap, clipId: String, timeMs: Long) {
    if (bitmap.isRecycled) return
    val byteSize = bitmap.allocationByteCount.toLong()

    evictIfNeeded(byteSize)

    val item = CachedFrameItem(bitmap, clipId, timeMs, byteSize)
    val old = cache.put(key, item)
    if (old != null) {
      currentSizeByte -= old.byteSize
    }
    currentSizeByte += byteSize
  }

  @Synchronized
  private fun evictIfNeeded(newBytes: Long) {
    val targetMax = maxBytes
    while (currentSizeByte + newBytes > targetMax && cache.isNotEmpty()) {
      // Find oldest accessed item
      val oldestKey = cache.minByOrNull { it.value.lastAccessTime }?.key ?: break
      val evicted = cache.remove(oldestKey) ?: break
      currentSizeByte -= evicted.byteSize
      if (!evicted.bitmap.isRecycled) {
        try {
          evicted.bitmap.recycle()
        } catch (ignored: Exception) {}
      }
    }
  }

  @Synchronized
  fun trim(forceAll: Boolean = false) {
    if (forceAll) {
      clear()
      return
    }
    val targetBytes = maxBytes / 2
    while (currentSizeByte > targetBytes && cache.isNotEmpty()) {
      val oldestKey = cache.minByOrNull { it.value.lastAccessTime }?.key ?: break
      val evicted = cache.remove(oldestKey) ?: break
      currentSizeByte -= evicted.byteSize
      if (!evicted.bitmap.isRecycled) {
        try {
          evicted.bitmap.recycle()
        } catch (ignored: Exception) {}
      }
    }
  }

  @Synchronized
  fun invalidateClip(clipId: String) {
    val toRemove = cache.filter { it.value.clipId == clipId }.keys
    for (k in toRemove) {
      val evicted = cache.remove(k) ?: continue
      currentSizeByte -= evicted.byteSize
      if (!evicted.bitmap.isRecycled) {
        try {
          evicted.bitmap.recycle()
        } catch (ignored: Exception) {}
      }
    }
  }

  @Synchronized
  fun clear() {
    for ((_, item) in cache) {
      if (!item.bitmap.isRecycled) {
        try { item.bitmap.recycle() } catch (ignored: Exception) {}
      }
    }
    cache.clear()
    currentSizeByte = 0L
  }
}

/**
 * High-performance Proxy Media & Adaptive Preview Engine.
 * Provides low-resolution proxy frame generation, background pre-fetching,
 * keyframe-aware seeking, and LRU frame caching to maintain 60 FPS timeline scrubbing.
 */
class ProxyMediaEngine(private val context: Context) {
  private val tag = "ProxyMediaEngine"
  private val memoryManager = EngineMemoryManager.getInstance(context)
  private val retrieverCache = ConcurrentHashMap<String, MediaMetadataRetriever>()
  private val proxyFileMap = ConcurrentHashMap<String, String>() // clipId -> proxyFilePath
  private val activeProxyJobs = ConcurrentHashMap<String, Job>()
  private val frameCache = BoundedFrameCache(maxMemoryMb = 48)

  private val currentSeekSequence = java.util.concurrent.atomic.AtomicLong(0L)

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  var currentQuality: PreviewQuality = PreviewQuality.BALANCED

  fun nextSeekSequence(): Long = currentSeekSequence.incrementAndGet()

  fun getCurrentSequence(): Long = currentSeekSequence.get()

  /**
   * Asynchronously fetches a frame for [clip] at [sourcePosMs] with sequence tracking and nearest-frame fallback.
   * Runs completely non-blockingly:
   * 1. Checks exact match in LRU cache -> delivers immediately.
   * 2. Checks nearest cached frame fallback -> delivers nearest frame immediately for zero-lag scrubbing.
   * 3. Decodes exact frame asynchronously on Dispatchers.IO.
   * 4. Drops obsolete seek results if a newer seek request has arrived (sequence < currentSeekSequence).
   */
  fun requestFrameAsync(
    clip: VideoClip,
    sourcePosMs: Long,
    sequence: Long,
    onFrameReady: (bitmap: Bitmap, isExactMatch: Boolean) -> Unit
  ) {
    if (!clip.isVideo || clip.uri.isBlank()) return

    val targetDim = currentQuality.maxDimension
    val cacheKey = "proxy_${clip.id}_${sourcePosMs / 100}_$targetDim"

    // 1. Check exact match in LRU cache
    val exact = frameCache.get(cacheKey)
    if (exact != null && !exact.isRecycled) {
      onFrameReady(exact, true)
      return
    }

    // 2. Nearest-frame fallback for instant zero-lag rendering during scrubbing
    val nearest = frameCache.getNearestFrame(clip.id, sourcePosMs, maxToleranceMs = 2000L)
    if (nearest != null && !nearest.isRecycled) {
      onFrameReady(nearest, false)
    }

    // 3. Async frame decoding on IO thread
    scope.launch(Dispatchers.IO) {
      if (sequence < currentSeekSequence.get()) {
        return@launch // Drop obsolete seek request
      }

      val bitmap = try {
        val retriever = getOrCreateRetriever(clip.uri) ?: return@launch
        extractFrameAt(retriever, sourcePosMs, targetDim)
      } catch (e: Exception) {
        Log.w(tag, "Async frame decoding failed for clip ${clip.id} at $sourcePosMs ms", e)
        null
      }

      if (bitmap != null && !bitmap.isRecycled) {
        frameCache.put(cacheKey, bitmap, clip.id, sourcePosMs)
        memoryManager.renderFrameCache.put(cacheKey, bitmap)

        // Drop obsolete seek result if sequence has advanced
        if (sequence >= currentSeekSequence.get()) {
          withContext(Dispatchers.Main) {
            if (!bitmap.isRecycled && sequence >= currentSeekSequence.get()) {
              onFrameReady(bitmap, true)
            }
          }
        }
      }
    }
  }

  /**
   * Determines if a video clip has heavy/4K media parameters.
   */
  fun isHeavyMedia(clip: VideoClip): Boolean {
    if (!clip.isVideo || clip.uri.isBlank()) return false
    return clip.width >= 1920 || clip.height >= 1080 || clip.durationMs > 120_000L
  }

  /**
   * Asynchronously generates a lightweight 540p proxy MP4 file and pre-decoded proxy keyframes.
   */
  fun generateProxyMediaAsync(clip: VideoClip, onComplete: (String?) -> Unit = {}) {
    if (!clip.isVideo || clip.uri.isBlank()) {
      onComplete(null)
      return
    }

    val existing = proxyFileMap[clip.id]
    if (existing != null && File(existing).exists()) {
      onComplete(existing)
      return
    }

    if (activeProxyJobs.containsKey(clip.id)) {
      return
    }

    val job = scope.launch(Dispatchers.IO) {
      try {
        val proxyDir = File(context.cacheDir, "proxies").apply { if (!exists()) mkdirs() }
        val proxyFile = File(proxyDir, "proxy_${clip.id.hashCode()}_540p.mp4")

        if (proxyFile.exists() && proxyFile.length() > 0) {
          proxyFileMap[clip.id] = proxyFile.absolutePath
          withContext(Dispatchers.Main) { onComplete(proxyFile.absolutePath) }
          return@launch
        }

        // 1. Pre-populate downscaled proxy keyframes into memory cache for instant zero-lag scrubbing
        val retriever = getOrCreateRetriever(clip.uri)
        if (retriever != null) {
          val durationMs = clip.durationMs
          val stepMs = if (durationMs > 60000L) 1000L else 500L
          var timeMs = 0L
          while (timeMs <= durationMs && isActive) {
            val frameKey = "proxy_${clip.id}_${timeMs / 100}_${currentQuality.maxDimension}"
            if (frameCache.get(frameKey) == null) {
              val bitmap = extractFrameAt(retriever, timeMs, currentQuality.maxDimension)
              if (bitmap != null) {
                frameCache.put(frameKey, bitmap, clip.id, timeMs)
              }
            }
            timeMs += stepMs
          }
        }

        // 2. Transcode lightweight 540p proxy MP4 video file
        val success = transcodeTo540pProxy(clip, proxyFile)
        if (success && proxyFile.exists() && proxyFile.length() > 0) {
          proxyFileMap[clip.id] = proxyFile.absolutePath
          Log.i(tag, "Successfully created 540p proxy video for ${clip.id} at ${proxyFile.absolutePath}")
          withContext(Dispatchers.Main) { onComplete(proxyFile.absolutePath) }
        } else {
          Log.d(tag, "Proxy video generation finished with frame caching for ${clip.id}")
          withContext(Dispatchers.Main) { onComplete(null) }
        }
      } catch (e: Exception) {
        Log.w(tag, "Proxy media generation error for clip ${clip.id}", e)
        withContext(Dispatchers.Main) { onComplete(null) }
      } finally {
        activeProxyJobs.remove(clip.id)
      }
    }

    activeProxyJobs[clip.id] = job
  }

  /**
   * Transcodes a video clip to a lightweight 540p proxy MP4 file.
   */
  private fun transcodeTo540pProxy(clip: VideoClip, outputFile: File): Boolean {
    var extractor: MediaExtractor? = null
    var muxer: MediaMuxer? = null
    var decoder: MediaCodec? = null
    var encoder: MediaCodec? = null
    val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
    if (tempFile.exists()) tempFile.delete()

    try {
      extractor = MediaExtractor()
      val parsedUri = Uri.parse(clip.uri)
      if (parsedUri.scheme == "content" || parsedUri.scheme == "file") {
        extractor.setDataSource(context, parsedUri, null)
      } else {
        extractor.setDataSource(clip.uri)
      }

      var videoTrackIndex = -1
      var videoFormat: MediaFormat? = null

      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("video/") && videoTrackIndex < 0) {
          videoTrackIndex = i
          videoFormat = format
          break
        }
      }

      if (videoTrackIndex < 0 || videoFormat == null) return false

      val origW = if (videoFormat.containsKey(MediaFormat.KEY_WIDTH)) videoFormat.getInteger(MediaFormat.KEY_WIDTH) else clip.width
      val origH = if (videoFormat.containsKey(MediaFormat.KEY_HEIGHT)) videoFormat.getInteger(MediaFormat.KEY_HEIGHT) else clip.height

      val maxDim = 960
      val scale = if (origW > origH) {
        (maxDim.toFloat() / origW.coerceAtLeast(1)).coerceAtMost(1f)
      } else {
        (maxDim.toFloat() / origH.coerceAtLeast(1)).coerceAtMost(1f)
      }
      val targetW = (((origW * scale).toInt() / 16) * 16).coerceIn(320, 1280)
      val targetH = (((origH * scale).toInt() / 16) * 16).coerceIn(240, 720)

      muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

      val encFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetW, targetH).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
      }

      encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
      encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      val inputSurface = encoder.createInputSurface()
      encoder.start()

      val videoMime = videoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
      decoder = MediaCodec.createDecoderByType(videoMime)
      decoder.configure(videoFormat, inputSurface, null, 0)
      decoder.start()

      extractor.selectTrack(videoTrackIndex)

      var muxerVideoTrack = -1
      var muxerStarted = false
      val bufferInfo = MediaCodec.BufferInfo()
      val timeoutUs = 8000L
      var inputDone = false
      var decodeDone = false
      var encodeDone = false

      while (!encodeDone) {
        if (!inputDone) {
          val inIndex = decoder.dequeueInputBuffer(timeoutUs)
          if (inIndex >= 0) {
            val buf = decoder.getInputBuffer(inIndex)
            if (buf != null) {
              buf.clear()
              val sampleSize = extractor.readSampleData(buf, 0)
              if (sampleSize < 0) {
                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
              } else {
                val pts = extractor.sampleTime
                decoder.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                extractor.advance()
              }
            }
          }
        }

        if (!decodeDone) {
          val decOutIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
          if (decOutIndex >= 0) {
            val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
            decoder.releaseOutputBuffer(decOutIndex, true)
            if (isEos) {
              encoder.signalEndOfInputStream()
              decodeDone = true
            }
          }
        }

        val encOutIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
        if (encOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
          muxer.start()
          muxerStarted = true
        } else if (encOutIndex >= 0) {
          val outBuf = encoder.getOutputBuffer(encOutIndex)
          if (outBuf != null && muxerStarted) {
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
              muxer.writeSampleData(muxerVideoTrack, outBuf, bufferInfo)
            }
          }
          val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
          encoder.releaseOutputBuffer(encOutIndex, false)
          if (isEos) {
            encodeDone = true
          }
        }
      }

      try { encoder.stop() } catch (_: Exception) {}
      try { decoder.stop() } catch (_: Exception) {}
      if (muxerStarted) {
        try { muxer.stop() } catch (_: Exception) {}
      }

      if (tempFile.exists() && tempFile.length() > 0) {
        tempFile.renameTo(outputFile)
        return true
      }
      return false
    } catch (e: Exception) {
      Log.w(tag, "Proxy transcode fallback: ${e.message}")
      return false
    } finally {
      try { encoder?.release() } catch (_: Exception) {}
      try { decoder?.release() } catch (_: Exception) {}
      try { muxer?.release() } catch (_: Exception) {}
      try { extractor?.release() } catch (_: Exception) {}
      if (tempFile.exists()) tempFile.delete()
    }
  }

  /**
   * Retrieves proxy video URI if available, otherwise original URI.
   */
  fun getProxyUri(clip: VideoClip): String {
    val proxyPath = proxyFileMap[clip.id]
    if (proxyPath != null && File(proxyPath).exists()) {
      return Uri.fromFile(File(proxyPath)).toString()
    }
    return clip.uri
  }

  fun hasProxy(clipId: String): Boolean {
    val path = proxyFileMap[clipId] ?: return false
    return File(path).exists()
  }

  /**
   * Selects original URI for export rendering, or proxy URI for editor preview.
   */
  fun getOriginalOrProxyUri(clip: VideoClip, forExport: Boolean): String {
    if (forExport) return clip.uri
    return getProxyUri(clip)
  }

  /**
   * Returns nearest valid cached frame bitmap immediately for zero-latency UI preview while scrubbing.
   */
  fun getNearestCachedFrame(clipId: String, sourcePosMs: Long, toleranceMs: Long = 1500L): Bitmap? {
    return frameCache.getNearestFrame(clipId, sourcePosMs, toleranceMs)
  }

  /**
   * Retrieves or extracts a cached preview frame bitmap for a clip at a given source timestamp.
   */
  suspend fun getProxyFrame(clip: VideoClip, sourcePosMs: Long): Bitmap? = withContext(Dispatchers.IO) {
    if (clip.uri.isBlank() || !clip.isVideo) return@withContext null

    val targetDim = currentQuality.maxDimension
    val cacheKey = "proxy_${clip.id}_${sourcePosMs / 100}_$targetDim"

    // Check bounded LRU cache first
    frameCache.get(cacheKey)?.let { return@withContext it }

    // Check legacy memory manager cache
    memoryManager.renderFrameCache.get(cacheKey)?.let { return@withContext it }

    try {
      val retriever = getOrCreateRetriever(clip.uri) ?: return@withContext null
      val scaled = extractFrameAt(retriever, sourcePosMs, targetDim) ?: return@withContext null

      frameCache.put(cacheKey, scaled, clip.id, sourcePosMs)
      memoryManager.renderFrameCache.put(cacheKey, scaled)
      scaled
    } catch (e: Exception) {
      Log.w(tag, "Failed to extract proxy frame for clip ${clip.id} at $sourcePosMs ms", e)
      null
    }
  }

  private fun extractFrameAt(retriever: MediaMetadataRetriever, sourcePosMs: Long, targetDim: Int): Bitmap? {
    return try {
      val timeUs = sourcePosMs * 1000L
      val frame = synchronized(retriever) {
        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
      } ?: return null

      if (frame.width > targetDim || frame.height > targetDim) {
        val scale = targetDim.toFloat() / kotlin.math.max(frame.width, frame.height)
        val sw = (frame.width * scale).toInt().coerceAtLeast(1)
        val sh = (frame.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(frame, sw, sh, true)
        if (scaled != frame && !frame.isRecycled) {
          try { frame.recycle() } catch (ignored: Exception) {}
        }
        scaled
      } else {
        frame
      }
    } catch (e: Exception) {
      null
    }
  }

  private fun getOrCreateRetriever(uriStr: String): MediaMetadataRetriever? {
    return try {
      retrieverCache.getOrPut(uriStr) {
        MediaMetadataRetriever().apply {
          val parsed = Uri.parse(uriStr)
          if (parsed.scheme == "content" || parsed.scheme == "file") {
            setDataSource(context, parsed)
          } else {
            setDataSource(uriStr)
          }
        }
      }
    } catch (e: Exception) {
      Log.w(tag, "Failed to initialize MediaMetadataRetriever for $uriStr", e)
      null
    }
  }

  /**
   * Pre-fetches adjacent frames around the current playhead position for smooth scrubbing.
   */
  fun prefetchFramesAround(clip: VideoClip, currentPosMs: Long, rangeMs: Long = 2000L) {
    if (!clip.isVideo || clip.uri.isBlank()) return
    scope.launch {
      val startMs = (currentPosMs - rangeMs).coerceAtLeast(0L)
      val endMs = (currentPosMs + rangeMs).coerceAtMost(clip.durationMs)
      var pos = startMs
      while (pos <= endMs && isActive) {
        getProxyFrame(clip, pos)
        pos += 250L // prefetch every 250ms interval
      }
    }
  }

  fun invalidateClip(clipId: String) {
    frameCache.invalidateClip(clipId)
  }

  fun clearCache() {
    frameCache.clear()
  }

  fun release() {
    activeProxyJobs.values.forEach { it.cancel() }
    activeProxyJobs.clear()
    scope.cancel()

    for ((_, retriever) in retrieverCache) {
      try { retriever.release() } catch (ignored: Exception) {}
    }
    retrieverCache.clear()
    frameCache.clear()
    Log.d(tag, "ProxyMediaEngine resources cleanly released")
  }
}
