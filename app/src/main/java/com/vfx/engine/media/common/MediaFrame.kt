package com.vfx.engine.media.common

import android.graphics.SurfaceTexture
import com.vfx.engine.core.Microseconds

data class MediaFrame(
  val surfaceTexture: SurfaceTexture,
  val transformMatrix: FloatArray = FloatArray(16),
  val pts: Microseconds
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as MediaFrame
    return pts == other.pts
  }

  override fun hashCode(): Int {
    return pts.hashCode()
  }
}

object HardwareBufferUtils {
  fun isHardwareBufferSupported(): Boolean {
    return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
  }
}
