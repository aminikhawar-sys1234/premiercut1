package com.ahstudio.composition.gpu

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface

/** Standard encoder/preview window surface. Ownership: created & released on GL thread only. */
class EglWindowSurface(display: EGLDisplay, config: EGLConfig, context: EGLContext, surface: Surface) {
    private val eglSurface: EGLSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
    init { check(eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" } }

    fun makeCurrent(display: EGLDisplay, context: EGLContext) {
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent failed" }
    }

    fun swap(display: EGLDisplay): Boolean = EGL14.eglSwapBuffers(display, eglSurface)

    fun release(display: EGLDisplay) {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, eglSurface)
    }
}
