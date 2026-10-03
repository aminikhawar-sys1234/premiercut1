package com.example.engine.composition.gpu

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.util.Log
import android.view.Surface
import com.example.domain.model.VideoClip
import com.example.engine.controller.DecoderManager
import com.example.engine.media.MediaMetadataHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production-grade hardware and software fallback video frame decoder and OpenGL OES texture source.
 *
 * Provides:
 * - MediaExtractor initialization & track selection for content/file/raw URIs.
 * - Hardware MediaCodec decoding with automatic graceful software fallback via DecoderManager.
 * - Direct zero-copy hardware rendering to SurfaceTexture / external OES texture.
 * - Frame-accurate seeking with pre-roll frame dropping (not rendering pre-roll frames to Surface).
 * - CFR & VFR presentation timestamp synchronization and frame repetition upon EOF.
 * - Video geometry handling: orientation metadata (0°, 90°, 180°, 270°), aspect ratio, scaling.
 * - Robust error recovery, codec flush, and leak-free resource release in reverse allocation order.
 */
class HardwareVideoTextureSource : SurfaceTexture.OnFrameAvailableListener {
  companion object {
    private const val TAG = "HwVideoTextureSource"
    private const val DEFAULT_TIMEOUT_US = 2_000L
    private const val MAX_DECODE_ATTEMPTS = 150
  }

  var oesTextureId: Int = 0
    private set
  var surfaceTexture: SurfaceTexture? = null
    private set
  var decoderSurface: Surface? = null
    private set

  private var codec: MediaCodec? = null
  private var extractor: MediaExtractor? = null

  var width: Int = 1920
    private set
  var height: Int = 1080
    private set
  var rotationDegrees: Int = 0
    private set
  val effectiveWidth: Int get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
  val effectiveHeight: Int get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height

  var frameRate: Float = 30f
    private set
  var isHardwareAccelerated: Boolean = true
    private set
  var isInitialized: Boolean = false
    private set

  private var lastRequestUs = Long.MIN_VALUE
  private var isInputEos = false
  private var isOutputEos = false
  private var lastRenderedPtsUs = 0L
  private val frameAvailable = AtomicBoolean(false)
  private val surfaceLock = Any()

  val transformMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
  private var glHandler: Handler? = null

  /**
   * Initializes the decoder and OES texture surface for the given VideoClip.
   */
  fun initialize(
    context: Context,
    clip: VideoClip,
    glHandler: Handler? = null,
    decoderHandler: Handler? = null,
    decoderManager: DecoderManager = DecoderManager()
  ): Boolean {
    if (isInitialized) return true
    this.glHandler = glHandler

    try {
      // 1. Initialize MediaExtractor
      val ex = MediaExtractor()
      val uri = try { Uri.parse(clip.uri) } catch (_: Exception) { null }
      if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) {
        ex.setDataSource(context, uri, null)
      } else {
        ex.setDataSource(clip.uri)
      }

      // 2. Locate video track
      var trackIndex = -1
      var trackFormat: MediaFormat? = null
      for (i in 0 until ex.trackCount) {
        val format = ex.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("video/")) {
          trackIndex = i
          trackFormat = format
          break
        }
      }

      if (trackIndex < 0 || trackFormat == null) {
        Log.w(TAG, "No valid video track found in ${clip.uri}")
        ex.release()
        return false
      }

      ex.selectTrack(trackIndex)
      extractor = ex

      // 3. Extract dimensions and rotation metadata
      val rawW = trackFormat.getInteger(MediaFormat.KEY_WIDTH).coerceAtLeast(1)
      val rawH = trackFormat.getInteger(MediaFormat.KEY_HEIGHT).coerceAtLeast(1)
      val meta = MediaMetadataHelper.extractMetadata(context, clip.uri)
      rotationDegrees = meta.rotationDegrees
      frameRate = if (meta.frameRate in 10f..120f) meta.frameRate else 30f
      width = if (clip.width > 0) clip.width else rawW
      height = if (clip.height > 0) clip.height else rawH

      // 4. Create OpenGL OES texture and Surface on GL thread
      val latch = CountDownLatch(1)
      var glSuccess = false
      val setupGlAction = Runnable {
        try {
          setupTextureAndSurface(decoderHandler)
          glSuccess = true
        } catch (e: Exception) {
          Log.e(TAG, "Failed creating OES texture/surface", e)
        } finally {
          latch.countDown()
        }
      }

      if (glHandler != null && android.os.Looper.myLooper() != glHandler.looper) {
        glHandler.post(setupGlAction)
        if (!latch.await(3, TimeUnit.SECONDS) || !glSuccess) {
          release()
          return false
        }
      } else {
        setupGlAction.run()
        if (!glSuccess) {
          release()
          return false
        }
      }

      // 5. Configure MediaCodec with hardware and software fallback
      val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
      val (dec, isHw) = try {
        decoderManager.createDecoder(mime, preferHardware = true)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to create decoder for $mime, falling back to software decoder", e)
        val swName = decoderManager.findSoftwareDecoderName(mime)
        if (swName != null) {
          Pair(MediaCodec.createByCodecName(swName), false)
        } else {
          Pair(MediaCodec.createDecoderByType(mime), false)
        }
      }

      // The GPU compositor applies clip.naturalRotation itself (see GpuCompositionRenderer).
      // MediaCodec would otherwise also bake KEY_ROTATION into the SurfaceTexture transform
      // matrix, rotating the frame twice (upside-down / sideways export).
      try { trackFormat.setInteger(MediaFormat.KEY_ROTATION, 0) } catch (_: Exception) {}
      dec.configure(trackFormat, decoderSurface, null, 0)
      dec.start()

      codec = dec
      isHardwareAccelerated = isHw
      isInitialized = true
      Log.d(TAG, "Initialized decoder for ${clip.id} ($mime, ${width}x${height}, rot=$rotationDegrees, hw=$isHw)")
      return true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize HardwareVideoTextureSource for ${clip.uri}", e)
      release()
      return false
    }
  }

  private fun setupTextureAndSurface(decoderHandler: Handler?) {
    if (oesTextureId == 0) {
      val textures = IntArray(1)
      GLES20.glGenTextures(1, textures, 0)
      oesTextureId = textures[0]
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

      val st = SurfaceTexture(oesTextureId)
      if (decoderHandler != null) {
        st.setOnFrameAvailableListener(this, decoderHandler)
      } else {
        st.setOnFrameAvailableListener(this)
      }
      surfaceTexture = st
      decoderSurface = Surface(st)
    }
  }

  override fun onFrameAvailable(st: SurfaceTexture) {
    frameAvailable.set(true)
  }

  /**
   * Decodes a video frame targeting [targetUs] microseconds in presentation time.
   *
   * Drops pre-roll frames without rendering to Surface, and renders the frame closest to
   * [targetUs] with true. Upon EOF or boundary, repeats/holds the last frame safely.
   */
  fun decodeFrame(
    targetUs: Long,
    cancelled: AtomicBoolean = AtomicBoolean(false),
    toleranceUs: Long = 40_000L
  ): Boolean {
    if (!isInitialized) return false
    val c = codec ?: return false
    val ex = extractor ?: return false

    // Seek if jumping backwards, starting fresh, or jumping forward by > 1.2 seconds
    if (targetUs < lastRequestUs || lastRequestUs == Long.MIN_VALUE || (targetUs - lastRequestUs > 1_200_000L)) {
      ex.seekTo(targetUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
      try { c.flush() } catch (_: Exception) {}
      frameAvailable.set(false)
      isInputEos = false
      isOutputEos = false
    }
    lastRequestUs = targetUs

    val info = MediaCodec.BufferInfo()
    var outputRendered = false
    var attempts = 0

    while (!outputRendered && !cancelled.get() && attempts < MAX_DECODE_ATTEMPTS) {
      attempts++

      // 1. Feed input buffer from MediaExtractor
      if (!isInputEos) {
        val inputIndex = try { c.dequeueInputBuffer(DEFAULT_TIMEOUT_US) } catch (_: Exception) { -1 }
        if (inputIndex >= 0) {
          val input = c.getInputBuffer(inputIndex)
          if (input != null) {
            input.clear()
            val sampleSize = ex.readSampleData(input, 0)
            if (sampleSize < 0) {
              c.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              isInputEos = true
            } else {
              val samplePts = ex.sampleTime.coerceAtLeast(0L)
              c.queueInputBuffer(inputIndex, 0, sampleSize, samplePts, 0)
              ex.advance()
            }
          }
        }
      }

      // 2. Dequeue decoded output buffer
      val outIndex = try { c.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US) } catch (_: Exception) { -1 }
      if (outIndex >= 0) {
        val outPts = info.presentationTimeUs.coerceAtLeast(0L)
        val isEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

        // If this is a pre-roll frame (outPts significantly before targetUs), discard without rendering to Surface
        val isPreroll = outPts < (targetUs - toleranceUs) && !isInputEos && !isEos

        if (isPreroll) {
          c.releaseOutputBuffer(outIndex, false)
        } else {
          // Target frame reached or EOS: render directly to Surface
          c.releaseOutputBuffer(outIndex, true)
          lastRenderedPtsUs = outPts
          outputRendered = true
        }

        if (isEos) {
          isOutputEos = true
          break
        }
      } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        val newFormat = c.outputFormat
        if (newFormat.containsKey(MediaFormat.KEY_WIDTH)) {
          width = newFormat.getInteger(MediaFormat.KEY_WIDTH)
        }
        if (newFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
          height = newFormat.getInteger(MediaFormat.KEY_HEIGHT)
        }
      } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && isInputEos) {
        // No more frames to decode; hold last frame
        outputRendered = true
        break
      }
    }

    return outputRendered || isOutputEos
  }

  fun updateTexImage(): FloatArray = synchronized(surfaceLock) {
    val st = surfaceTexture ?: return transformMatrix
    try {
      st.updateTexImage()
      st.getTransformMatrix(transformMatrix)
      frameAvailable.set(false)
    } catch (e: Exception) {
      // When frameAvailable wasn't caught or redundant updateTexImage, ensure transformMatrix is up-to-date
      try {
        st.getTransformMatrix(transformMatrix)
      } catch (ignored: Exception) {}
    }
    return transformMatrix
  }

  fun flush() = synchronized(surfaceLock) {
    try {
      codec?.flush()
      lastRequestUs = Long.MIN_VALUE
      isInputEos = false
      isOutputEos = false
      frameAvailable.set(false)
    } catch (e: Exception) {
      Log.w(TAG, "flush error: ${e.message}")
    }
  }

  fun release() = synchronized(surfaceLock) {
    isInitialized = false
    try { codec?.stop() } catch (_: Throwable) {}
    try { codec?.release() } catch (_: Throwable) {}
    codec = null

    try { extractor?.release() } catch (_: Throwable) {}
    extractor = null

    try { decoderSurface?.release() } catch (_: Throwable) {}
    decoderSurface = null

    try { surfaceTexture?.release() } catch (_: Throwable) {}
    surfaceTexture = null

    val texId = oesTextureId
    if (texId != 0) {
      oesTextureId = 0
      val action = Runnable {
        GLES20.glDeleteTextures(1, intArrayOf(texId), 0)
      }
      val handler = glHandler
      if (handler != null && android.os.Looper.myLooper() != handler.looper) {
        handler.post(action)
      } else {
        action.run()
      }
    }
    Log.d(TAG, "HardwareVideoTextureSource released")
  }
}
