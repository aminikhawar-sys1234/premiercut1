package com.vfx.engine.gpu

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface
import com.vfx.engine.core.EffectEngineException

/**
 * Owns EGLDisplay + EGLContext for one rendering scope.
 * Tries GLES 3 first, falls back to GLES 2. Supports context sharing
 * (decoder surfaces) and RECORDABLE configs (encoder surfaces).
 */
class EglCore {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

    var contextLost = false; private set
    var glesMajor = 3; private set

    val eglDisplay: EGLDisplay get() = display
    val eglContext: EGLContext get() = context
    val isInitialized: Boolean get() = display != EGL14.EGL_NO_DISPLAY && context != EGL14.EGL_NO_CONTEXT

    fun initialize(sharedContext: EGLContext = EGL14.EGL_NO_CONTEXT) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY)
            throw EffectEngineException.EglFailure("eglGetDisplay", EGL14.eglGetError())
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1))
            throw EffectEngineException.EglFailure("eglInitialize", EGL14.eglGetError())

        config = chooseConfig()
        for (clientVersion in intArrayOf(3, 2)) {
            context = EGL14.eglCreateContext(display, config, sharedContext,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, clientVersion, EGL14.EGL_NONE), 0)
            if (context != EGL14.EGL_NO_CONTEXT) { glesMajor = clientVersion; return }
        }
        throw EffectEngineException.EglFailure("eglCreateContext", EGL14.eglGetError())
    }

    private fun chooseConfig(): EGLConfig {
        val attrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT or EGL_OPENGL_ES3_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, num, 0) || num[0] == 0) {
            // Retry without RECORDABLE (older devices / emulators).
            val attrs2 = IntArray(attrs.size)
            var skip = false
            for (i in attrs.indices) {
                if (attrs[i] == EGL_RECORDABLE_ANDROID) { skip = true; continue }
                attrs2[if (skip) i - 2 + 0 else i] = attrs[i] // compact out the pair
            }
            val trimmed = attrs2.filterIndexed { i, _ -> i < attrs.size - 2 }.toIntArray()
            val final = if (trimmed.contains(EGL14.EGL_NONE)) trimmed
            else intArrayOf(EGL_RED, EGL_GREEN, EGL_BLUE, EGL_ALPHA, EGL_RENDERABLE, EGL_WINDOW_PBUFFER, EGL14.EGL_NONE)
            if (!EGL14.eglChooseConfig(display, final, 0, configs, 0, 1, num, 0) || num[0] == 0)
                throw EffectEngineException.EglFailure("eglChooseConfig", EGL14.eglGetError())
        }
        return configs[0]!!
    }

    fun makeCurrent(surface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            val err = EGL14.eglGetError()
            if (err == EGL_CONTEXT_LOST) { contextLost = true; throw EffectEngineException.ContextLost() }
            throw EffectEngineException.EglFailure("eglMakeCurrent", err)
        }
    }

    fun makeCurrentOnPbuffer() {
        if (pbuffer == EGL14.EGL_NO_SURFACE)
            pbuffer = createPbufferSurface(1, 1)
        makeCurrent(pbuffer)
    }

    fun createWindowSurface(surface: Surface): EGLSurface {
        val s = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (s == null || s == EGL14.EGL_NO_SURFACE)
            throw EffectEngineException.EglFailure("eglCreateWindowSurface", EGL14.eglGetError())
        return s
    }

    fun createPbufferSurface(w: Int, h: Int): EGLSurface {
        val s = EGL14.eglCreatePbufferSurface(display, config,
            intArrayOf(EGL14.EGL_WIDTH, w, EGL14.EGL_HEIGHT, h, EGL14.EGL_NONE), 0)
        if (s == null || s == EGL14.EGL_NO_SURFACE)
            throw EffectEngineException.EglFailure("eglCreatePbufferSurface", EGL14.eglGetError())
        return s
    }

    fun destroySurface(s: EGLSurface) { EGL14.eglDestroySurface(display, s) }

    fun querySize(surface: EGLSurface): Pair<Int, Int> {
        val w = IntArray(1); val h = IntArray(1)
        EGL14.eglQuerySurface(display, surface, EGL14.EGL_WIDTH, w, 0)
        EGL14.eglQuerySurface(display, surface, EGL14.EGL_HEIGHT, h, 0)
        return w[0] to h[0]
    }

    fun setPresentationTime(surface: EGLSurface, ns: Long) =
        EGLExt.eglPresentationTimeANDROID(display, surface, ns)

    fun swapBuffers(surface: EGLSurface): Boolean {
        val ok = EGL14.eglSwapBuffers(display, surface)
        if (!ok && EGL14.eglGetError() == EGL_CONTEXT_LOST) contextLost = true
        return ok
    }

    fun release() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        try { EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) } catch (_: Throwable) {}
        if (pbuffer != EGL14.EGL_NO_SURFACE) { EGL14.eglDestroySurface(display, pbuffer); pbuffer = EGL14.EGL_NO_SURFACE }
        if (context != EGL14.EGL_NO_CONTEXT) { EGL14.eglDestroyContext(display, context); context = EGL14.EGL_NO_CONTEXT }
        EGL14.eglTerminate(display); display = EGL14.EGL_NO_DISPLAY
    }

    companion object {
        const val EGL_OPENGL_ES3_BIT = 0x40
        const val EGL_RECORDABLE_ANDROID = 0x3142
        const val EGL_CONTEXT_LOST = 0x300E
        private const val EGL_RED = 0x3024; private const val EGL_GREEN = 0x3023
        private const val EGL_BLUE = 0x3022; private const val EGL_ALPHA = 0x3021
        private const val EGL_RENDERABLE = 0x3040
        private const val EGL_WINDOW_PBUFFER = 0x3033
    }
}
