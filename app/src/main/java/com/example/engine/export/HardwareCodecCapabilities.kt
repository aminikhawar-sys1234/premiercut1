package com.example.engine.export

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * Device-aware MediaCodec capability inspection used by the hardware export path.
 * It deliberately prefers surface-input encoders because those allow EGL/GPU output
 * without a per-frame CPU YUV upload.
 */
data class VideoEncoderCapability(
  val codecName: String,
  val mimeType: String,
  val supportsSurfaceInput: Boolean,
  val maxWidth: Int,
  val maxHeight: Int,
  val maxFrameRate: Double,
  val supportsVbr: Boolean
)

object HardwareCodecCapabilities {
  fun findVideoEncoder(
    preferredMime: String,
    width: Int,
    height: Int,
    frameRate: Int
  ): VideoEncoderCapability? {
    val candidates = linkedSetOf(preferredMime, MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC)
    for (mime in candidates) {
      for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
        if (!info.isEncoder) continue
        val caps = try { info.getCapabilitiesForType(mime) } catch (_: Throwable) { continue }
        val video = caps.videoCapabilities ?: continue
        if (!video.isSizeSupported(width, height)) continue
        val rate = try { video.getSupportedFrameRatesFor(width, height).upper } catch (_: Throwable) { frameRate.toDouble() }
        if (rate + 0.001 < frameRate) continue
        val surface = caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        val vbr = caps.encoderCapabilities?.isBitrateModeSupported(
          MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
        ) == true
        return VideoEncoderCapability(
          codecName = info.name,
          mimeType = mime,
          supportsSurfaceInput = surface,
          maxWidth = video.supportedWidths.upper,
          maxHeight = video.supportedHeights.upper,
          maxFrameRate = rate,
          supportsVbr = vbr
        )
      }
    }
    return null
  }

  fun supportsSurfaceEncoding(mime: String, width: Int, height: Int, frameRate: Int): Boolean =
    findVideoEncoder(mime, width, height, frameRate)?.supportsSurfaceInput == true
}
