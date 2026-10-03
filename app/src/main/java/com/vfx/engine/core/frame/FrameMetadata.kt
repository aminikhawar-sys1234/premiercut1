package com.vfx.engine.core.frame

import com.vfx.engine.core.Microseconds

enum class ColorSpace {
  BT709,
  BT2020,
  DISPLAY_P3,
  SRGB
}

data class FrameMetadata(
  val pts: Microseconds,
  val duration: Microseconds,
  val sequenceIndex: Long,
  val width: Int,
  val height: Int,
  val format: PixelFormat = PixelFormat.RGBA_8888,
  val colorSpace: ColorSpace = ColorSpace.BT709,
  val alphaMode: AlphaMode = AlphaMode.PREMULTIPLIED,
  val orientation: Orientation = Orientation.UP,
  val hdrMetadata: HdrMetadata = HdrMetadata()
)
