package com.example.engine.composition.gpu

import android.opengl.GLES20
import android.util.Log

/**
 * Encapsulates an OpenGL Framebuffer Object (FBO) with attached 2D texture.
 * Enables offscreen multi-pass rendering, intermediate transition ping-ponging,
 * and high-speed GPU texture composition.
 */
class GlFramebuffer {
  private var fboId: Int = 0
  private var textureId: Int = 0
  var width: Int = 0
    private set
  var height: Int = 0
    private set

  companion object {
    private const val TAG = "GlFramebuffer"
  }

  fun setup(w: Int, h: Int) {
    if (w == width && h == height && fboId != 0) {
      return
    }
    release()

    width = w
    height = h

    val textures = IntArray(1)
    GLES20.glGenTextures(1, textures, 0)
    textureId = textures[0]
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexImage2D(
      GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
      width, height, 0,
      GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
    )

    val framebuffers = IntArray(1)
    GLES20.glGenFramebuffers(1, framebuffers, 0)
    fboId = framebuffers[0]
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
    GLES20.glFramebufferTexture2D(
      GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
      GLES20.GL_TEXTURE_2D, textureId, 0
    )

    val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
    if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
      Log.e(TAG, "Framebuffer incomplete, status: $status")
    }

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
  }

  fun bind() {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
    GLES20.glViewport(0, 0, width, height)
  }

  fun unbind() {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
  }

  fun getTextureId(): Int = textureId

  fun release() {
    if (fboId != 0) {
      val framebuffers = intArrayOf(fboId)
      GLES20.glDeleteFramebuffers(1, framebuffers, 0)
      fboId = 0
    }
    if (textureId != 0) {
      val textures = intArrayOf(textureId)
      GLES20.glDeleteTextures(1, textures, 0)
      textureId = 0
    }
    width = 0
    height = 0
  }
}
