package com.example.engine.composition.gpu

import android.opengl.EGL14
import android.opengl.EGLSurface
import android.view.Surface

/**
 * Common base for EGL surfaces (window or pbuffer).
 */
open class EglSurfaceBase(protected val eglCore: EglCore) {
  protected var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
  var width: Int = -1
    protected set
  var height: Int = -1
    protected set

  fun makeCurrent() {
    eglCore.makeCurrent(eglSurface)
  }

  fun swapBuffers(): Boolean {
    return eglCore.swapBuffers(eglSurface)
  }

  fun setPresentationTime(nsecs: Long) {
    eglCore.setPresentationTime(eglSurface, nsecs)
  }

  open fun release() {
    eglCore.releaseSurface(eglSurface)
    eglSurface = EGL14.EGL_NO_SURFACE
    width = -1
    height = -1
  }
}

/**
 * Recordable or displayable window surface (wraps Surface or SurfaceTexture).
 */
class WindowSurface(
  eglCore: EglCore,
  surface: Surface,
  private val releaseSurfaceOnRelease: Boolean = false
) : EglSurfaceBase(eglCore) {
  private var rawSurface: Surface? = surface

  init {
    eglSurface = eglCore.createWindowSurface(surface)
  }

  override fun release() {
    super.release()
    if (releaseSurfaceOnRelease) {
      rawSurface?.release()
    }
    rawSurface = null
  }
}

/**
 * Offscreen pbuffer surface for headless GPU rendering.
 */
class OffscreenSurface(
  eglCore: EglCore,
  w: Int,
  h: Int
) : EglSurfaceBase(eglCore) {
  init {
    width = w
    height = h
    eglSurface = eglCore.createOffscreenSurface(w, h)
  }
}
