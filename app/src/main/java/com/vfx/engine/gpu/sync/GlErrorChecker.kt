package com.vfx.engine.gpu.sync

import android.opengl.GLES20
import android.util.Log

object GlErrorChecker {
  fun checkGlError(opTag: String) {
    val error = GLES20.glGetError()
    if (error != GLES20.GL_NO_ERROR) {
      Log.e("GlErrorChecker", "GL Error after $opTag: 0x${Integer.toHexString(error)}")
    }
  }
}
