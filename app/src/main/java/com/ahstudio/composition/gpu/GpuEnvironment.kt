package com.ahstudio.composition.gpu

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread

interface GpuEnvironment {
    fun <T> runOnGlThread(await: Boolean, block: () -> T): T
    fun release()
}

/** Fallback GL host. If Ah Studio already owns a GL thread/EGL context, implement this interface over it instead. */
class DefaultGpuEnvironment : GpuEnvironment {
    private val thread = HandlerThread("CompositionGL").apply { start() }
    private val handler = Handler(thread.looper)
    private var context_: EGLContext
    private var display_: EGLDisplay
    private var config_: EGLConfig
    private var surface_: EGLSurface? = null

    val display: EGLDisplay get() = display_
    val config: EGLConfig get() = config_
    val context: EGLContext get() = context_
    fun expose(): DefaultGpuEnvironment = this

    init {
        display_ = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        EGL14.eglInitialize(display_, ver, 0, ver, 1)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE
        )
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        EGL14.eglChooseConfig(display_, attribs, 0, cfgs, 0, 1, n, 0)
        config_ = cfgs[0]!!
        context_ = EGL14.eglCreateContext(
            display_, config_, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0
        )
    }

    override fun <T> runOnGlThread(await: Boolean, block: () -> T): T {
        if (await) {
            val r = arrayOfNulls<Any?>(1)
            val done = java.util.concurrent.CountDownLatch(1)
            handler.post {
                val s = surface_ ?: EGL14.eglCreatePbufferSurface(
                    display_, config_,
                    intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE), 0
                )
                EGL14.eglMakeCurrent(display_, s, s, context_)
                r[0] = runCatching(block).getOrNull()
                done.countDown()
            }
            done.await()
            @Suppress("UNCHECKED_CAST")
            return (r[0] as T)
        }
        handler.post { block() }
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    override fun release() {
        handler.post {
            EGL14.eglMakeCurrent(display_, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(display_, context_)
            EGL14.eglTerminate(display_)
        }
        thread.quitSafely()
    }
}
