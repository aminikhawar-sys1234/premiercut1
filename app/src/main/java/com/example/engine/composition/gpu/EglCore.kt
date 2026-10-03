package com.example.engine.composition.gpu

import android.graphics.SurfaceTexture
import android.opengl.*
import android.util.Log
import android.view.Surface

/**
 * Core EGL state manager (EGLDisplay, EGLContext, EGLConfig).
 * Handles OpenGL ES 2.0 / 3.0 context creation with support for recordable surfaces
 * (for MediaCodec encoder input surfaces) and standard window / offscreen surfaces.
 */
class EglCore(
  sharedContext: EGLContext? = null,
  flags: Int = FLAG_RECORDABLE
) {
  private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
  private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
  private var eglConfig: EGLConfig? = null
  private var glVersion = 2

  companion object {
    const val FLAG_RECORDABLE = 0x01
    const val FLAG_TRY_GLES3 = 0x02
    private const val EGL_RECORDABLE_ANDROID = 0x3142
    private const val TAG = "EglCore"
  }

  init {
    eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
      throw RuntimeException("unable to get EGL14 display")
    }

    val version = IntArray(2)
    if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
      eglDisplay = EGL14.EGL_NO_DISPLAY
      throw RuntimeException("unable to initialize EGL14")
    }

    val baseSharedContext = sharedContext ?: EGL14.EGL_NO_CONTEXT

    // Try GLES 3 if requested
    if ((flags and FLAG_TRY_GLES3) != 0) {
      val config3 = getConfig(flags, 3)
      if (config3 != null) {
        val attrib3List = intArrayOf(
          EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
          EGL14.EGL_NONE
        )
        val context3 = EGL14.eglCreateContext(eglDisplay, config3, baseSharedContext, attrib3List, 0)
        if (EGL14.eglGetError() == EGL14.EGL_SUCCESS) {
          eglConfig = config3
          eglContext = context3
          glVersion = 3
        }
      }
    }

    // Fall back to GLES 2
    if (eglContext == EGL14.EGL_NO_CONTEXT) {
      val config2 = getConfig(flags, 2) ?: throw RuntimeException("Unable to find suitable EGLConfig")
      val attrib2List = intArrayOf(
        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL14.EGL_NONE
      )
      val context2 = EGL14.eglCreateContext(eglDisplay, config2, baseSharedContext, attrib2List, 0)
      checkEglError("eglCreateContext")
      eglConfig = config2
      eglContext = context2
      glVersion = 2
    }
  }

  private fun getConfig(flags: Int, version: Int): EGLConfig? {
    var renderableType = EGL14.EGL_OPENGL_ES2_BIT
    if (version >= 3) {
      renderableType = renderableType or EGLExt.EGL_OPENGL_ES3_BIT_KHR
    }

    val attribList = intArrayOf(
      EGL14.EGL_RED_SIZE, 8,
      EGL14.EGL_GREEN_SIZE, 8,
      EGL14.EGL_BLUE_SIZE, 8,
      EGL14.EGL_ALPHA_SIZE, 8,
      EGL14.EGL_RENDERABLE_TYPE, renderableType,
      if ((flags and FLAG_RECORDABLE) != 0) EGL_RECORDABLE_ANDROID else EGL14.EGL_NONE, 1,
      EGL14.EGL_NONE
    )

    val configs = arrayOfNulls<EGLConfig>(1)
    val numConfigs = IntArray(1)
    if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)) {
      Log.w(TAG, "unable to find RGB8888 / $version EGLConfig")
      return null
    }
    return configs[0]
  }

  fun createWindowSurface(surface: Any): EGLSurface {
    if (surface !is Surface && surface !is SurfaceTexture) {
      throw RuntimeException("invalid surface: $surface")
    }
    val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
    val eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
    checkEglError("eglCreateWindowSurface")
    if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
      throw RuntimeException("surface was null")
    }
    return eglSurface
  }

  fun createOffscreenSurface(width: Int, height: Int): EGLSurface {
    val surfaceAttribs = intArrayOf(
      EGL14.EGL_WIDTH, width,
      EGL14.EGL_HEIGHT, height,
      EGL14.EGL_NONE
    )
    val eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttribs, 0)
    checkEglError("eglCreatePbufferSurface")
    if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
      throw RuntimeException("surface was null")
    }
    return eglSurface
  }

  fun makeCurrent(eglSurface: EGLSurface) {
    if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
      Log.d(TAG, "NOTE: makeCurrent w/o display")
    }
    if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
      throw RuntimeException("eglMakeCurrent failed")
    }
  }

  fun makeCurrent(drawSurface: EGLSurface, readSurface: EGLSurface) {
    if (!EGL14.eglMakeCurrent(eglDisplay, drawSurface, readSurface, eglContext)) {
      throw RuntimeException("eglMakeCurrent(draw,read) failed")
    }
  }

  fun makeNothingCurrent() {
    if (!EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
      throw RuntimeException("eglMakeNothingCurrent failed")
    }
  }

  fun swapBuffers(eglSurface: EGLSurface): Boolean {
    return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
  }

  fun setPresentationTime(eglSurface: EGLSurface, nsecs: Long) {
    EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
  }

  fun releaseSurface(eglSurface: EGLSurface) {
    EGL14.eglDestroySurface(eglDisplay, eglSurface)
  }

  fun release() {
    if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
      EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
      EGL14.eglDestroyContext(eglDisplay, eglContext)
      EGL14.eglReleaseThread()
      EGL14.eglTerminate(eglDisplay)
    }
    eglDisplay = EGL14.EGL_NO_DISPLAY
    eglContext = EGL14.EGL_NO_CONTEXT
    eglConfig = null
  }

  private fun checkEglError(msg: String) {
    val error = EGL14.eglGetError()
    if (error != EGL14.EGL_SUCCESS) {
      throw RuntimeException("$msg: EGL error: 0x${Integer.toHexString(error)}")
    }
  }
}
