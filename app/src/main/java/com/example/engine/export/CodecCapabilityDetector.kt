package com.example.engine.export

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import com.example.domain.model.Resolution

data class DeviceCodecCapabilities(
  val supportsH264Hardware: Boolean = true,
  val supportsH265Hardware: Boolean = false,
  val supportsVp9Hardware: Boolean = false,
  val supportsAv1Hardware: Boolean = false,
  val supports4kDecoding: Boolean = true,
  val supports2kDecoding: Boolean = true,
  val supports1080pDecoding: Boolean = true,
  val maxSupportedWidth: Int = 1920,
  val maxSupportedHeight: Int = 1080,
  val maxSupportedFps: Int = 60,
  val maxSupportedBitrateBps: Int = 20_000_000,
  val isLowEndDevice: Boolean = false,
  val recommendedResolution: Resolution = Resolution.RES_1080P
)

object CodecCapabilityDetector {
  private const val TAG = "CodecCapabilityDetector"

  fun detectCapabilities(): DeviceCodecCapabilities {
    var h264Hw = false
    var h265Hw = false
    var vp9Hw = false
    var av1Hw = false
    var maxWidth = 1920
    var maxHeight = 1080
    var maxFps = 30
    var maxBitrate = 15_000_000

    try {
      val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
      val codecInfos = codecList.codecInfos

      for (info in codecInfos) {
        val types = info.supportedTypes
        val isHw = isHardwareCodec(info)

        for (type in types) {
          if (type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
            if (isHw) h264Hw = true
            try {
              val caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
              val videoCaps = caps.videoCapabilities
              if (videoCaps != null) {
                maxWidth = maxOf(maxWidth, videoCaps.supportedWidths.upper)
                maxHeight = maxOf(maxHeight, videoCaps.supportedHeights.upper)
                maxFps = maxOf(maxFps, videoCaps.supportedFrameRates.upper)
                maxBitrate = maxOf(maxBitrate, 25_000_000)
              }
            } catch (ignored: Exception) {}
          } else if (type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
            if (isHw) h265Hw = true
          } else if (type.equals(MediaFormat.MIMETYPE_VIDEO_VP9, ignoreCase = true)) {
            if (isHw) vp9Hw = true
          } else if (type.equals("video/av01", ignoreCase = true)) {
            if (isHw) av1Hw = true
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error detecting hardware codec capabilities", e)
    }

    val isLowEnd = maxWidth < 1920 || !h264Hw
    val recommendedRes = when {
      maxWidth >= 3840 -> Resolution.RES_4K
      maxWidth >= 2560 -> Resolution.RES_2K
      else -> Resolution.RES_1080P
    }

    Log.d(
      TAG,
      "Codec capability scan complete: H264_HW=$h264Hw, H265_HW=$h265Hw, VP9_HW=$vp9Hw, AV1_HW=$av1Hw, MaxRes=${maxWidth}x${maxHeight}, MaxFps=$maxFps, LowEnd=$isLowEnd"
    )

    return DeviceCodecCapabilities(
      supportsH264Hardware = h264Hw,
      supportsH265Hardware = h265Hw,
      supportsVp9Hardware = vp9Hw,
      supportsAv1Hardware = av1Hw,
      supports4kDecoding = maxWidth >= 3840,
      supports2kDecoding = maxWidth >= 2560,
      supports1080pDecoding = maxWidth >= 1920,
      maxSupportedWidth = maxWidth,
      maxSupportedHeight = maxHeight,
      maxSupportedFps = maxFps,
      maxSupportedBitrateBps = maxBitrate,
      isLowEndDevice = isLowEnd,
      recommendedResolution = recommendedRes
    )
  }

  fun isDecoderHardwareAccelerated(mimeType: String): Boolean {
    return try {
      val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
      for (info in codecList.codecInfos) {
        if (info.isEncoder) continue
        if (info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) {
          if (isHardwareCodec(info)) return true
        }
      }
      false
    } catch (e: Exception) {
      false
    }
  }

  fun isHardwareCodec(info: MediaCodecInfo): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return info.isHardwareAccelerated
    }
    val name = info.name.lowercase()
    return !name.startsWith("omx.google.") &&
      !name.startsWith("c2.android.") &&
      !name.contains("sw")
  }
}
