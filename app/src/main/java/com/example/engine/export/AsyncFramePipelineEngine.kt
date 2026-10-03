package com.example.engine.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.domain.model.*
import com.example.engine.composition.ComposedOverlay
import com.example.engine.composition.VideoCompositionEngine
import com.example.engine.composition.gpu.EglCore
import com.example.engine.composition.gpu.GpuCompositionRenderer
import com.example.engine.composition.gpu.HardwareVideoTextureSource
import com.example.engine.composition.gpu.WindowSurface
import com.example.engine.controller.DecoderManager
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Performance metrics for the hardware video export pipeline.
 */
class AsyncFramePipelineMetrics {
  val decodedFrames = AtomicLong()
  val gpuFrames = AtomicLong()
  val encodedFrames = AtomicLong()
  val zeroCopyFrames = AtomicLong()
  val gpuToCpuCopies = AtomicLong()
  val decodeTimeNs = AtomicLong()
  val gpuRenderTimeNs = AtomicLong()

  fun snapshot() = mapOf(
    "decodedFrames" to decodedFrames.get(),
    "gpuFrames" to gpuFrames.get(),
    "encodedFrames" to encodedFrames.get(),
    "zeroCopyFrames" to zeroCopyFrames.get(),
    "gpuToCpuCopies" to gpuToCpuCopies.get()
  )
}

/**
 * Thread-safe bounded queue for pipeline frame synchronization.
 */
class FramePacketQueue<T>(val capacity: Int) {
  private val queue = java.util.concurrent.ArrayBlockingQueue<T>(capacity)

  fun put(item: T, cancelled: AtomicBoolean): Boolean {
    while (!cancelled.get()) {
      if (queue.offer(item, 50, TimeUnit.MILLISECONDS)) return true
    }
    return false
  }

  fun take(cancelled: AtomicBoolean): T? {
    while (!cancelled.get()) {
      val item = queue.poll(50, TimeUnit.MILLISECONDS)
      if (item != null) return item
    }
    return null
  }

  fun depth(): Int = queue.size
}

/**
 * Hardware-accelerated clip decoder rendering directly to an OpenGL OES texture with software fallback.
 */
private class HardwareClipDecoder(
  private val context: Context,
  val clip: VideoClip,
  private val glHandler: Handler,
  private val decoderHandler: Handler,
  private val decoderManager: DecoderManager = DecoderManager()
) {
  private val tag = "HardwareClipDecoder"
  private val textureSource = HardwareVideoTextureSource()

  val textureId: Int get() = textureSource.oesTextureId
  val width: Int get() = textureSource.effectiveWidth
  val height: Int get() = textureSource.effectiveHeight
  val rotationDegrees: Int get() = textureSource.rotationDegrees
  val isHardwareAccelerated: Boolean get() = textureSource.isHardwareAccelerated
  val transformMatrix: FloatArray get() = textureSource.transformMatrix

  fun init(): Boolean {
    return textureSource.initialize(
      context = context,
      clip = clip,
      glHandler = glHandler,
      decoderHandler = decoderHandler,
      decoderManager = decoderManager
    )
  }

  fun decodeFrame(targetUs: Long, cancelled: AtomicBoolean): Boolean {
    return textureSource.decodeFrame(targetUs, cancelled)
  }

  fun updateTexImageOnGl() {
    textureSource.updateTexImage()
  }

  fun release() {
    textureSource.release()
  }
}

/**
 * High-performance CapCut-level hardware GPU video & audio export pipeline.
 */
class AsyncFramePipelineEngine(private val context: Context) {
  private val tag = "AsyncFramePipeline"
  private val cancelled = AtomicBoolean(false)
  private val glThread = HandlerThread("AH-GPU-Pipeline").apply { start() }
  private val glHandler = Handler(glThread.looper)
  private val decoderThread = HandlerThread("AH-Decoder-Pipeline").apply { start() }
  private val decoderHandler = Handler(decoderThread.looper)
  private val composition = VideoCompositionEngine(context)
  private val audioProcessor = AudioExportProcessor(context)
  private val decoderManager = DecoderManager()
  val metrics = AsyncFramePipelineMetrics()

  fun cancel() {
    cancelled.set(true)
  }

  private data class PendingMuxerSample(
    val isAudio: Boolean,
    val data: ByteArray,
    val offset: Int,
    val size: Int,
    val presentationTimeUs: Long,
    val flags: Int
  )

  suspend fun export(
    timeline: Timeline,
    config: ExportConfig,
    outputFile: File
  ): File? = withContext(Dispatchers.IO) {
    cancelled.set(false)
    val fps = config.frameRate.fps.coerceIn(15, 120)
    val durationMs = timeline.totalDurationMs
    val totalFrames = max(1L, ((durationMs.toDouble() / 1000.0) * fps).toLong())
    val (exportWidth, exportHeight) = dimensions(config.resolution, timeline.aspectRatio)

    Log.i(tag, "Starting Hardware GPU Export: ${exportWidth}x${exportHeight} @ ${fps}fps ($durationMs ms, $totalFrames frames)")

    // Pre-calculate / Mix Audio
    var hasAudio = audioProcessor.hasActiveAudio(timeline)
    val audioSampleRate = audioProcessor.sampleRate
    val audioChannels = audioProcessor.channelCount
    val masterPcm = if (hasAudio) {
      audioProcessor.mixTimelineAudio(timeline, durationMs) { cancelled.get() }
    } else {
      ShortArray(0)
    }
    if (masterPcm.isEmpty()) {
      hasAudio = false
    }

    var eglCore: EglCore? = null
    var windowSurface: WindowSurface? = null
    var gpuRenderer: GpuCompositionRenderer? = null
    var videoEncoder: MediaCodec? = null
    var audioEncoder: MediaCodec? = null
    var encoderInputSurface: Surface? = null
    var muxer: MediaMuxer? = null
    var muxerCoordinator: MuxerCoordinator? = null

    val isMuxStarted = AtomicBoolean(false)
    val videoTrack = AtomicInteger(-1)
    val audioTrack = AtomicInteger(-1)
    val videoEos = AtomicBoolean(false)
    val audioEos = AtomicBoolean(false)
    val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)

    val decoders = mutableMapOf<String, HardwareClipDecoder>()
    val imageBitmaps = mutableMapOf<String, Bitmap>()
    val imageTextures = mutableMapOf<String, Int>()
    val pendingSamples = ConcurrentLinkedQueue<PendingMuxerSample>()

    try {
      // 1. Pre-load Images & Fallback Bitmaps (Clamped strictly to export resolution to avoid texture bloat)
      for (clip in timeline.videoClips + timeline.overlayClips) {
        if (!clip.isVideo && clip.uri.isNotBlank()) {
          try {
            val bmp = decodeSampledBitmap(context, clip.uri, exportWidth, exportHeight)
            if (bmp != null) {
              imageBitmaps[clip.uri] = bmp
            }
          } catch (e: Exception) {
            Log.w(tag, "Failed to load image for ${clip.uri}", e)
          }
        }
      }

      // 2. Configure Hardware-Accelerated Video Encoder
      val videoMime = selectEncoder(config, exportWidth, exportHeight, fps) ?: MediaFormat.MIMETYPE_VIDEO_AVC
      val bitrateBps = bitrate(config)
      val videoFormat = MediaFormat.createVideoFormat(videoMime, exportWidth, exportHeight).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        try {
          setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        } catch (_: Exception) {}
      }

      val (chosenEncoder, isHardwareEncoder) = try {
        decoderManager.createEncoder(
          mimeType = videoMime,
          width = exportWidth,
          height = exportHeight,
          requireSurface = true
        )
      } catch (e: Exception) {
        Log.w(tag, "Failed creating encoder for $videoMime, attempting AVC fallback", e)
        decoderManager.createEncoder(
          mimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
          width = exportWidth,
          height = exportHeight,
          requireSurface = true
        )
      }
      videoEncoder = chosenEncoder
      try {
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoderInputSurface = videoEncoder.createInputSurface()
      } catch (cfgEx: Exception) {
        Log.w(tag, "Video encoder configure failed for $videoMime: ${cfgEx.message}, retrying standard surface configuration", cfgEx)
        try { videoEncoder.reset() } catch (_: Exception) {}
        val fallbackFormat = MediaFormat.createVideoFormat(videoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC, exportWidth, exportHeight).apply {
          setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
          setInteger(MediaFormat.KEY_BIT_RATE, (bitrateBps * 0.85f).toInt())
          setInteger(MediaFormat.KEY_FRAME_RATE, fps)
          setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        videoEncoder.configure(fallbackFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoderInputSurface = videoEncoder.createInputSurface()
      }
      Log.i(tag, "Video Encoder initialized: ${videoEncoder.name} (hardwareAccelerated=$isHardwareEncoder, surface=true)")

      // 3. Initialize EGL & GPU Composition on Dedicated GL Thread
      val glInitLatch = CountDownLatch(1)
      glHandler.post {
        try {
          val core = EglCore(null, EglCore.FLAG_RECORDABLE)
          val inputSurface = encoderInputSurface ?: throw IllegalStateException("Encoder input surface is null")
          val winSurface = WindowSurface(core, inputSurface, false)
          winSurface.makeCurrent()

          val rend = GpuCompositionRenderer(context)
          rend.initGl()

          // Upload image textures to GPU
          for ((uri, bmp) in imageBitmaps) {
            val texId = rend.uploadImageTexture(uri, bmp)
            imageTextures[uri] = texId
          }

          eglCore = core
          windowSurface = winSurface
          gpuRenderer = rend
        } catch (t: Throwable) {
          failure.set(t)
        } finally {
          glInitLatch.countDown()
        }
      }
      if (!glInitLatch.await(5, TimeUnit.SECONDS)) {
        throw IllegalStateException("Timed out initializing EGL GPU surface")
      }
      failure.get()?.let { throw it }

      // 4. Configure Audio Encoder if Audio is Present
      if (hasAudio) {
        try {
          val audioMime = MediaFormat.MIMETYPE_AUDIO_AAC
          val aacFormat = MediaFormat.createAudioFormat(audioMime, audioSampleRate, audioChannels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
          }
          audioEncoder = MediaCodec.createEncoderByType(audioMime).apply {
            configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
          }
        } catch (e: Exception) {
          Log.w(tag, "Audio encoder configuration failed: ${e.message}", e)
          audioEncoder = null
          hasAudio = false
        }
      }

      // 5. Initialize MediaMuxer & MuxerCoordinator
      outputFile.parentFile?.mkdirs()
      if (outputFile.exists()) outputFile.delete()
      val localMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      muxer = localMuxer
      // Authoritative orientation policy: The GPU composition pipeline normalizes all video clips,
      // overlays, text, and stickers into the canonical upright project canvas orientation.
      // Setting a non-zero orientation hint on MediaMuxer would cause double-rotation (e.g. 90° GPU + 90° hint = 180° upside-down).
      try {
        localMuxer.setOrientationHint(0)
        Log.i(tag, "AsyncFramePipelineEngine normalized MediaMuxer orientation hint: 0 degrees")
      } catch (e: Exception) {
        Log.w(tag, "Failed to set orientation hint on MediaMuxer", e)
      }
      val localCoordinator = MuxerCoordinator(localMuxer, hasAudio)
      localCoordinator.setOrientationHint(0)
      muxerCoordinator = localCoordinator

      videoEncoder.start()

      // 6. Start Asynchronous Drain Thread
      val drainDone = CountDownLatch(1)
      val renderCompleteLatch = CountDownLatch(1)
      val drainExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "AH-GPU-MuxDrain") }
      drainExecutor.execute {
        try {
          val vInfo = MediaCodec.BufferInfo()
          val aInfo = MediaCodec.BufferInfo()
          var consecutiveIdlePasses = 0

          while (!cancelled.get()) {
            val videoDone = videoEos.get()
            val audioDone = !hasAudio || audioEncoder == null || audioEos.get()

            if (videoDone && audioDone) {
              Log.i(tag, "Drain thread: Both video and audio tracks reached BUFFER_FLAG_END_OF_STREAM")
              break
            }

            var drainedSomething = false

            // Drain Video: burst-drain all available encoder output buffers
            if (!videoEos.get()) {
              while (!videoEos.get() && !cancelled.get()) {
                val vIndex = videoEncoder.dequeueOutputBuffer(vInfo, 5_000L)
                if (vIndex >= 0) {
                  drainedSomething = true
                  val isEos = (vInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                  if ((vInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && vInfo.size > 0) {
                    val out = videoEncoder.getOutputBuffer(vIndex)
                    if (out != null) {
                      localCoordinator.writeVideoSample(out, vInfo)
                      metrics.encodedFrames.incrementAndGet()
                    }
                  }
                  videoEncoder.releaseOutputBuffer(vIndex, false)
                  if (isEos) {
                    videoEos.set(true)
                    Log.i(tag, "Video encoder signaled BUFFER_FLAG_END_OF_STREAM")
                    break
                  }
                } else if (vIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                  drainedSomething = true
                  val newFormat = videoEncoder.outputFormat
                  Log.i(tag, "Video encoder output format changed: $newFormat")
                  localCoordinator.setVideoFormat(newFormat)
                } else {
                  break // INFO_TRY_AGAIN_LATER
                }
              }
            }

            // Drain Audio: burst-drain all available audio output buffers
            if (hasAudio && audioEncoder != null && !audioEos.get()) {
              while (!audioEos.get() && !cancelled.get()) {
                val aIndex = audioEncoder.dequeueOutputBuffer(aInfo, 5_000L)
                if (aIndex >= 0) {
                  drainedSomething = true
                  val isEos = (aInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                  if ((aInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && aInfo.size > 0) {
                    val out = audioEncoder.getOutputBuffer(aIndex)
                    if (out != null) {
                      localCoordinator.writeAudioSample(out, aInfo)
                    }
                  }
                  audioEncoder.releaseOutputBuffer(aIndex, false)
                  if (isEos) {
                    audioEos.set(true)
                    Log.i(tag, "Audio encoder signaled BUFFER_FLAG_END_OF_STREAM")
                    break
                  }
                } else if (aIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                  drainedSomething = true
                  val newFormat = audioEncoder.outputFormat
                  Log.i(tag, "Audio encoder output format changed: $newFormat")
                  localCoordinator.setAudioFormat(newFormat)
                } else {
                  break // INFO_TRY_AGAIN_LATER
                }
              }
            }

            if (!drainedSomething) {
              consecutiveIdlePasses++
              if (renderCompleteLatch.count == 0L && metrics.encodedFrames.get() >= totalFrames && consecutiveIdlePasses > 100) {
                Log.i(tag, "Drain thread: all $totalFrames frames encoded and pipeline idle. Completing drain.")
                videoEos.set(true)
                if (hasAudio) audioEos.set(true)
                break
              }
              Thread.sleep(3)
            } else {
              consecutiveIdlePasses = 0
            }
          }
        } catch (t: Throwable) {
          Log.e(tag, "Error in drain thread", t)
          failure.set(t)
          cancelled.set(true)
        } finally {
          drainDone.countDown()
        }
      }

      // 7. Video Frame Hardware Composition Loop
      val totalAudioFrames = if (hasAudio) masterPcm.size / audioChannels else 0
      var fedAudioFrames = 0

      // Pre-prime the Audio Encoder with initial samples so output format is determined immediately
      if (hasAudio && audioEncoder != null && totalAudioFrames > 0) {
        val primeFrames = min(2048, totalAudioFrames)
        val primeIndex = audioEncoder.dequeueInputBuffer(10_000L)
        if (primeIndex >= 0) {
          val inputBuffer = audioEncoder.getInputBuffer(primeIndex)
          if (inputBuffer != null) {
            inputBuffer.clear()
            inputBuffer.order(ByteOrder.nativeOrder())
            val samplesToFeed = primeFrames * audioChannels
            for (k in 0 until samplesToFeed) {
              val sample = if (k < masterPcm.size) masterPcm[k] else 0.toShort()
              inputBuffer.putShort(sample)
            }
            audioEncoder.queueInputBuffer(primeIndex, 0, samplesToFeed * 2, 0L, 0)
            fedAudioFrames += primeFrames
          }
        }
      }

      val maxConcurrentDecoders = DecoderManager.MAX_RECOMMENDED_HARDWARE_DECODERS

      fun getOrCreateDecoder(clip: VideoClip, currentActiveIds: Set<String>): HardwareClipDecoder? {
        val existing = decoders[clip.id]
        if (existing != null) return existing

        if (decoders.size >= maxConcurrentDecoders) {
          val evictCandidate = decoders.keys.firstOrNull { it !in currentActiveIds }
          if (evictCandidate != null) {
            val evicted = decoders.remove(evictCandidate)
            evicted?.release()
            Log.d(tag, "Evicted idle decoder for clip $evictCandidate to avoid codec exhaustion")
          }
        }

        val newDecoder = HardwareClipDecoder(context, clip, glHandler, decoderHandler, decoderManager)
        return if (newDecoder.init()) {
          decoders[clip.id] = newDecoder
          newDecoder
        } else {
          newDecoder.release()
          null
        }
      }

      glHandler.post {
        try {
          var lastEglPtsNs = -1L
          for (frameIndex in 0 until totalFrames) {
            if (cancelled.get()) break

            // Enforce keyframe generation on the first rendered frame so MP4 moov/stss index table is clean
            if (frameIndex == 0L) {
              try {
                val syncParams = android.os.Bundle().apply {
                  putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                }
                videoEncoder?.setParameters(syncParams)
              } catch (ignored: Exception) {}
            }

            val ptsUs = (frameIndex * 1_000_000L) / fps
            val timelinePosMs = (ptsUs / 1000L).coerceAtMost(durationMs - 1L)
            val frame = composition.evaluateFrame(timeline, timelinePosMs)
            val activeClip = frame.activeClip

            val currentNeededClipIds = mutableSetOf<String>()
            if (activeClip != null && activeClip.isVideo) currentNeededClipIds.add(activeClip.id)
            for (ov in frame.activeOverlays) {
              if (ov.clip.isVideo) currentNeededClipIds.add(ov.clip.id)
            }

            var mainTexId = 0
            var isMainOes = false
            var mainTexMatrix: FloatArray? = null

            var effectiveFrame = frame

            // 1. Process Main Clip
            if (activeClip != null) {
              if (activeClip.isVideo && activeClip.uri.isNotBlank()) {
                val decoder = getOrCreateDecoder(activeClip, currentNeededClipIds)
                if (decoder != null && decoder.textureId != 0) {
                  decoder.decodeFrame(frame.clipSourcePosMs * 1000L, cancelled)
                  decoder.updateTexImageOnGl()
                  mainTexId = decoder.textureId
                  isMainOes = true
                  mainTexMatrix = decoder.transformMatrix
                  if (decoder.width > 0 && decoder.height > 0 && (activeClip.width <= 0 || activeClip.height <= 0)) {
                    effectiveFrame = effectiveFrame.copy(
                      activeClip = activeClip.copy(width = decoder.width, height = decoder.height)
                    )
                  }
                  metrics.decodedFrames.incrementAndGet()
                  metrics.zeroCopyFrames.incrementAndGet()
                }
                // Resilient fallback: if hardware decoder is missing or failed, extract frame via MediaMetadataRetriever
                if (mainTexId == 0) {
                  val fallbackBmp = fetchFallbackBitmap(activeClip, frame.clipSourcePosMs, exportWidth, exportHeight)
                  if (fallbackBmp != null) {
                    val texId = gpuRenderer?.uploadImageTexture("fallback_main_${activeClip.id}", fallbackBmp) ?: 0
                    if (texId > 0) {
                      mainTexId = texId
                      isMainOes = false
                      mainTexMatrix = null
                    }
                  }
                }
              } else {
                mainTexId = imageTextures[activeClip.uri] ?: 0
                isMainOes = false
              }
            }

            // 2. Process Overlays
            val overlayTextures = HashMap<String, Int>()
            val overlayTexMatrices = HashMap<String, FloatArray>()
            val updatedOverlays = mutableListOf<ComposedOverlay>()
            for (overlay in frame.activeOverlays) {
              if (overlay.clip.isVideo && overlay.clip.uri.isNotBlank()) {
                val ovDecoder = getOrCreateDecoder(overlay.clip, currentNeededClipIds)
                var ovTexId = 0
                if (ovDecoder != null && ovDecoder.textureId != 0) {
                  ovDecoder.decodeFrame(overlay.sourcePosMs * 1000L, cancelled)
                  ovDecoder.updateTexImageOnGl()
                  ovTexId = ovDecoder.textureId
                  overlayTextures[overlay.clip.id] = ovTexId
                  overlayTexMatrices[overlay.clip.id] = ovDecoder.transformMatrix
                  if (ovDecoder.width > 0 && ovDecoder.height > 0 && (overlay.clip.width <= 0 || overlay.clip.height <= 0)) {
                    updatedOverlays.add(overlay.copy(clip = overlay.clip.copy(width = ovDecoder.width, height = ovDecoder.height)))
                  } else {
                    updatedOverlays.add(overlay)
                  }
                }
                if (ovTexId == 0) {
                  val ovBmp = fetchFallbackBitmap(overlay.clip, overlay.sourcePosMs, exportWidth, exportHeight)
                  if (ovBmp != null) {
                    val texId = gpuRenderer?.uploadImageTexture("fallback_ov_${overlay.clip.id}", ovBmp) ?: 0
                    if (texId > 0) {
                      overlayTextures[overlay.clip.id] = texId
                    }
                  }
                  updatedOverlays.add(overlay)
                }
              } else {
                val texId = imageTextures[overlay.clip.uri]
                if (texId != null && texId > 0) {
                  overlayTextures[overlay.clip.id] = texId
                }
                updatedOverlays.add(overlay)
              }
            }
            if (updatedOverlays.isNotEmpty()) {
              effectiveFrame = effectiveFrame.copy(activeOverlays = updatedOverlays)
            }

            // 3. Render Composition to EGL Surface
            val renderStart = System.nanoTime()
            gpuRenderer?.render(
              frame = effectiveFrame,
              mainTextureId = mainTexId,
              isMainOes = isMainOes,
              mainTexMatrix = mainTexMatrix,
              overlayTextures = overlayTextures,
              overlayTexMatrices = overlayTexMatrices,
              viewportWidth = exportWidth,
              viewportHeight = exportHeight,
              timelineAdjustments = timeline.adjustments,
              timelineFilter = timeline.filter,
              chromaKey = timeline.chromaKey,
              flipYForEncoder = false,
              flipXForEncoder = false
            )
            // Complete all rendering commands before handing the buffer to MediaCodec
            GLES20.glFinish()
            var targetPtsNs = ptsUs * 1000L
            if (targetPtsNs <= lastEglPtsNs) {
              targetPtsNs = lastEglPtsNs + 1000L
            }
            lastEglPtsNs = targetPtsNs
            windowSurface?.setPresentationTime(targetPtsNs)
            windowSurface?.swapBuffers()
            metrics.gpuRenderTimeNs.addAndGet(System.nanoTime() - renderStart)
            metrics.gpuFrames.incrementAndGet()

            // 4. Feed Audio Pro-Rata with Exact PTS (Non-blocking so GL thread is never stalled)
            if (hasAudio && audioEncoder != null) {
              val targetAudioFrames = (((frameIndex + 1).toDouble() * audioSampleRate) / fps).toInt().coerceAtMost(totalAudioFrames)
              while (fedAudioFrames < targetAudioFrames && !cancelled.get()) {
                val framesToFeed = min(1024, targetAudioFrames - fedAudioFrames)
                if (framesToFeed <= 0) break
                val inputIndex = audioEncoder.dequeueInputBuffer(2_000L)
                if (inputIndex >= 0) {
                  val inputBuffer = audioEncoder.getInputBuffer(inputIndex)
                  if (inputBuffer != null) {
                    inputBuffer.clear()
                    inputBuffer.order(ByteOrder.nativeOrder())
                    val samplesToFeed = framesToFeed * audioChannels
                    val startIdx = fedAudioFrames * audioChannels
                    for (k in 0 until samplesToFeed) {
                      val idx = startIdx + k
                      val sample = if (idx < masterPcm.size) masterPcm[idx] else 0.toShort()
                      inputBuffer.putShort(sample)
                    }
                    val audioPtsUs = (fedAudioFrames.toLong() * 1_000_000L) / audioSampleRate
                    audioEncoder.queueInputBuffer(inputIndex, 0, framesToFeed * audioChannels * 2, audioPtsUs, 0)
                    fedAudioFrames += framesToFeed
                  }
                } else break
              }
            }
          }
        } catch (t: Throwable) {
          Log.e(tag, "Render loop error", t)
          failure.set(t)
          cancelled.set(true)
        } finally {
          renderCompleteLatch.countDown()
        }
      }

      renderCompleteLatch.await()
      failure.get()?.let { throw it }

      // 8. Signal EOS on both video and audio encoders
      if (!cancelled.get()) {
        try {
          Log.i(tag, "Signaling end of stream to video encoder input surface...")
          videoEncoder.signalEndOfInputStream()
        } catch (e: Exception) {
          Log.w(tag, "signalEndOfInputStream error", e)
        }

        if (hasAudio && audioEncoder != null) {
          // Flush any remaining audio frames before sending EOS
          var flushAttempts = 0
          while (fedAudioFrames < totalAudioFrames && !cancelled.get() && flushAttempts < 100) {
            val framesToFeed = min(1024, totalAudioFrames - fedAudioFrames)
            if (framesToFeed <= 0) break
            val inputIndex = audioEncoder.dequeueInputBuffer(10_000L)
            if (inputIndex >= 0) {
              val inputBuffer = audioEncoder.getInputBuffer(inputIndex)
              if (inputBuffer != null) {
                inputBuffer.clear()
                inputBuffer.order(ByteOrder.nativeOrder())
                val samplesToFeed = framesToFeed * audioChannels
                val startIdx = fedAudioFrames * audioChannels
                for (k in 0 until samplesToFeed) {
                  val idx = startIdx + k
                  val sample = if (idx < masterPcm.size) masterPcm[idx] else 0.toShort()
                  inputBuffer.putShort(sample)
                }
                val audioPtsUs = (fedAudioFrames.toLong() * 1_000_000L) / audioSampleRate
                audioEncoder.queueInputBuffer(inputIndex, 0, framesToFeed * audioChannels * 2, audioPtsUs, 0)
                fedAudioFrames += framesToFeed
              }
            } else {
              flushAttempts++
              Thread.sleep(5)
            }
          }

          var audioEosSent = false
          var attempts = 0
          while (!audioEosSent && attempts < 100 && !cancelled.get()) {
            val inputIndex = audioEncoder.dequeueInputBuffer(10_000L)
            if (inputIndex >= 0) {
              val audioPtsUs = (fedAudioFrames.toLong() * 1_000_000L) / audioSampleRate
              audioEncoder.queueInputBuffer(inputIndex, 0, 0, audioPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              audioEosSent = true
              Log.i(tag, "Audio encoder EOS buffer queued at audioPtsUs=$audioPtsUs")
            } else {
              attempts++
              Thread.sleep(5)
            }
          }
        }
      }

      // 9. Await complete drain of both video and audio encoders
      val drainSuccess = drainDone.await(60, TimeUnit.SECONDS)
      drainExecutor.shutdown()
      if (!drainSuccess) {
        Log.w(tag, "Drain thread timed out waiting for EOS on encoders")
      }

      failure.get()?.let { throw it }

      if (cancelled.get()) {
        outputFile.delete()
        return@withContext null
      }

      // 10. Safe MediaMuxer stop & release guaranteeing complete MOOV box generation
      val muxerFinalized = try {
        localCoordinator.stopAndRelease()
      } catch (e: Exception) {
        Log.w(tag, "stopAndRelease caught exception", e)
        false
      }
      if (!muxerFinalized && !localCoordinator.isStarted) {
        Log.w(tag, "MediaMuxer was not formally started, checking if output file was written")
      }

      Log.i(tag, "Hardware Export Finished: ${outputFile.absolutePath} (${outputFile.length()} bytes, ${metrics.encodedFrames.get()} frames)")
      if (outputFile.exists() && outputFile.length() > 0L) outputFile else null
    } catch (t: Throwable) {
      Log.e(tag, "Async hardware export pipeline error", t)
      if (outputFile.exists() && outputFile.length() <= 0L) {
        outputFile.delete()
      }
      if (outputFile.exists() && outputFile.length() > 0L) outputFile else null
    } finally {
      cancelled.set(true)
      decoders.values.forEach { runCatching { it.release() } }
      decoders.clear()

      val cleanLatch = CountDownLatch(1)
      glHandler.post {
        runCatching { gpuRenderer?.release() }
        runCatching { windowSurface?.release() }
        runCatching { eglCore?.release() }
        cleanLatch.countDown()
      }
      cleanLatch.await(2, TimeUnit.SECONDS)

      for (retriever in fallbackRetrievers.values) {
        runCatching { retriever.release() }
      }
      fallbackRetrievers.clear()

      runCatching { encoderInputSurface?.release() }
      runCatching { videoEncoder?.stop() }; runCatching { videoEncoder?.release() }
      runCatching { audioEncoder?.stop() }; runCatching { audioEncoder?.release() }
      if (muxerCoordinator?.isStopped == false) {
        runCatching { muxerCoordinator?.stopAndRelease() }
      }
      glThread.quitSafely()
      decoderThread.quitSafely()
    }
  }

  private val fallbackRetrievers = mutableMapOf<String, MediaMetadataRetriever>()

  private fun fetchFallbackBitmap(clip: VideoClip, sourcePosMs: Long, maxW: Int, maxH: Int): Bitmap? {
    return try {
      val retriever = fallbackRetrievers.getOrPut(clip.uri) {
        MediaMetadataRetriever().apply {
          val uri = try { Uri.parse(clip.uri) } catch (_: Exception) { null }
          if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) {
            setDataSource(context, uri)
          } else {
            setDataSource(clip.uri)
          }
        }
      }
      val sourceUs = sourcePosMs * 1000L
      val rawBmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        retriever.getScaledFrameAtTime(sourceUs, MediaMetadataRetriever.OPTION_CLOSEST, maxW, maxH)
          ?: retriever.getFrameAtTime(sourceUs, MediaMetadataRetriever.OPTION_CLOSEST)
      } else {
        retriever.getFrameAtTime(sourceUs, MediaMetadataRetriever.OPTION_CLOSEST)
      }
      rawBmp
    } catch (e: Exception) {
      Log.w(tag, "Failed to retrieve fallback frame for ${clip.uri}", e)
      null
    }
  }

  private fun selectEncoder(config: ExportConfig, w: Int, h: Int, fps: Int): String? {
    val isHighRes = w >= 2160 || h >= 2160
    val mimes = when (config.codecProfile) {
      CodecProfile.H265_HEVC -> listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)
      CodecProfile.H264_AVC -> listOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC)
      CodecProfile.AUTO -> if (isHighRes) {
        listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)
      } else {
        listOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC)
      }
    }
    for (mime in mimes) {
      for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
        if (!info.isEncoder || !info.supportedTypes.any { it.equals(mime, true) }) continue
        val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
        val vc = caps.videoCapabilities ?: continue
        val sizeOk = runCatching { vc.isSizeSupported(w, h) || vc.isSizeSupported(h, w) }.getOrDefault(true)
        val formatOk = caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        if (formatOk && sizeOk) return mime
      }
    }
    return if (isHighRes) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
  }

  private fun bitrate(config: ExportConfig): Int {
    if (config.quality == ExportQuality.CUSTOM && config.customBitrateKbps > 0) {
      return config.customBitrateKbps * 1000
    }
    return when (config.resolution) {
      Resolution.RES_480P -> 2_500_000
      Resolution.RES_720P -> 5_000_000
      Resolution.RES_1080P -> 10_000_000
      Resolution.RES_2K, Resolution.RES_VERTICAL_2K -> 18_000_000
      Resolution.RES_4K, Resolution.RES_VERTICAL_4K -> 35_000_000
      Resolution.RES_SQUARE_2K -> 22_000_000
    }
  }

  private fun decodeSampledBitmap(context: Context, uriString: String, maxW: Int, maxH: Int): Bitmap? {
    return try {
      val uri = Uri.parse(uriString)
      val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      if (uri.scheme == "content") {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
      } else {
        val path = if (uri.scheme == "file") uri.path ?: uriString else uriString
        BitmapFactory.decodeFile(path, boundsOpts)
      }
      var sampleSize = 1
      val srcW = boundsOpts.outWidth
      val srcH = boundsOpts.outHeight
      if (srcW > 0 && srcH > 0 && maxW > 0 && maxH > 0) {
        while ((srcW / (sampleSize * 2)) >= maxW && (srcH / (sampleSize * 2)) >= maxH) {
          sampleSize *= 2
        }
      }
      val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
      val rawBmp = if (uri.scheme == "content") {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
      } else {
        val path = if (uri.scheme == "file") uri.path ?: uriString else uriString
        BitmapFactory.decodeFile(path, decodeOpts)
      } ?: return null

      // Scale to strictly fit within timeline export resolution if still larger
      if (rawBmp.width > maxW || rawBmp.height > maxH) {
        val scale = minOf(maxW.toFloat() / rawBmp.width, maxH.toFloat() / rawBmp.height)
        val targetW = (rawBmp.width * scale).toInt().coerceAtLeast(1)
        val targetH = (rawBmp.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(rawBmp, targetW, targetH, true)
        if (scaled != rawBmp) rawBmp.recycle()
        scaled
      } else {
        rawBmp
      }
    } catch (e: Exception) {
      Log.w(tag, "Failed to decode sampled bitmap for $uriString: ${e.message}")
      null
    }
  }

  private fun dimensions(resolution: Resolution, aspect: AspectRatio): Pair<Int, Int> {
    val (w, h) = when (resolution) {
      Resolution.RES_SQUARE_2K -> Pair(2048, 2048)
      Resolution.RES_VERTICAL_2K -> Pair(1440, 2560)
      Resolution.RES_VERTICAL_4K -> Pair(2160, 3840)
      else -> {
        val shortSide = resolution.width
        val longSide = resolution.height
        when (aspect) {
          AspectRatio.RATIO_9_16 -> Pair(shortSide, longSide)
          AspectRatio.RATIO_16_9 -> Pair(longSide, shortSide)
          AspectRatio.RATIO_1_1 -> Pair(shortSide, shortSide)
          AspectRatio.RATIO_4_5 -> Pair(shortSide, (shortSide * 5) / 4)
          AspectRatio.RATIO_4_3 -> Pair((shortSide * 4) / 3, shortSide)
          AspectRatio.RATIO_3_4 -> Pair((shortSide * 3) / 4, shortSide)
          AspectRatio.RATIO_21_9 -> Pair((shortSide * 21) / 9, shortSide)
          AspectRatio.CUSTOM -> {
            val customH = (shortSide / aspect.ratio).toInt().coerceAtLeast(1)
            Pair(shortSide, customH)
          }
        }
      }
    }
    val alignedW = ((w + 15) / 16) * 16
    val alignedH = ((h + 15) / 16) * 16
    return alignedW to alignedH
  }
}
