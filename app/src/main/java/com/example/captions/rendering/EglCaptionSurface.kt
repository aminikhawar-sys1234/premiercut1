package com.ahstudio.captions.rendering

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

class EglCaptionSurface {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null

    fun initialize(): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return false
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT, EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1); val num = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) return false
        config = configs[0]
        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        return context != EGL14.EGL_NO_CONTEXT
    }

    fun createPbuffer(w: Int, h: Int): Boolean {
        val attribs = intArrayOf(EGL14.EGL_WIDTH, w, EGL14.EGL_HEIGHT, h, EGL14.EGL_NONE)
        surface = EGL14.eglCreatePbufferSurface(display, config, attribs, 0)
        return makeCurrent()
    }

    fun createFromSurface(target: Surface): Boolean {
        surface = EGL14.eglCreateWindowSurface(display, config, target, intArrayOf(EGL14.EGL_NONE), 0)
        return makeCurrent()
    }

    fun makeCurrent(): Boolean =
        EGL14.eglMakeCurrent(display, surface, surface, context) && surface != EGL14.EGL_NO_SURFACE

    fun swap(): Boolean = EGL14.eglSwapBuffers(display, surface)

    fun setPresentationTime(nanos: Long) = EGLExt.eglPresentationTimeANDROID(display, surface, nanos)

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY; context = EGL14.EGL_NO_CONTEXT; surface = EGL14.EGL_NO_SURFACE
    }
}

class CaptionGpuSurfaceTarget(private val renderer: CaptionGpuRenderer = CaptionGpuRenderer()) {
    private val egl = EglCaptionSurface()
    private var ready = false

    fun attach(surface: Surface): Boolean {
        ready = egl.initialize() && egl.createFromSurface(surface)
        return ready
    }

    fun drawFrame(
        renderables: List<RenderableCaption>, w: Float, h: Float,
        paintFactory: (com.ahstudio.captions.core.model.CaptionStyle) -> android.graphics.Paint,
    ): Boolean {
        if (!ready) return false
        renderer.drawFrame(renderables, w, h, paintFactory)
        return egl.swap()
    }

    fun detach() { egl.release(); ready = false }
    fun renderer() = renderer
}
