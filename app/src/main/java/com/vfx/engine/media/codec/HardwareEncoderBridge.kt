package com.vfx.engine.media.codec

import android.media.MediaFormat
import com.vfx.engine.gpu.context.EglCore

class HardwareEncoderBridge(
  val mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
  val bitrate: Int = 12_000_000,
  val frameRate: Int = 30,
  val iFrameIntervalSec: Float = 1.0f
) {
  fun createMediaFormat(width: Int, height: Int): MediaFormat {
    return MediaFormat.createVideoFormat(mimeType, width, height).apply {
      setInteger(MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
      setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
      setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
      setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameIntervalSec)
    }
  }
}
