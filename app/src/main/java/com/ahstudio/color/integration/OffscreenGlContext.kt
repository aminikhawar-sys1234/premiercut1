package com.ahstudio.color.integration

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real offscreen ES3 context — used by ParityHarness and on-device GPU verification. */
class OffscreenGlContext(private val width: Int, private val height: Int) : Closeable {
    private val display: EGLDisplay
    private val context: EGLContext
    private val surface: EGLSurface

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display !== EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val v = IntArray(2)
        check(EGL14.eglInitialize(display, v, 0, v, 1)) { "eglInitialize failed" }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT */, EGL14.EGL_NONE)
        val cfgs = arrayOfNulls<EGLConfig>(1); val num = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, cfgs, 0, 1, num, 0) && num[0] > 0)
        context = EGL14.eglCreateContext(display, cfgs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        check(context !== EGL14.EGL_NO_CONTEXT) { "ES3 context unavailable" }
        surface = EGL14.eglCreatePbufferSurface(display, cfgs[0],
            intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "makeCurrent failed" }
    }

    fun makeCurrent() = EGL14.eglMakeCurrent(display, surface, surface, context)

    /** Reads GL framebuffer bottom-up; returns top-down ARGB ints. */
    fun readRgbaTopDown(): IntArray {
        val bb = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, bb)
        val px = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val i = ((height - 1 - y) * width + x) * 4
            px[y * width + x] = (0xFF shl 24) or
                ((bb.get(i + 1).toInt() and 0xFF) shl 16) or
                ((bb.get(i + 2).toInt() and 0xFF) shl 8)  or
                ((bb.get(i + 3).toInt() and 0xFF) shl 0)
        }
        return px
    }

    fun readFloatRgb(): FloatArray {
        val bb = ByteBuffer.allocateDirect(width * height * 3 * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGB, GLES30.GL_FLOAT, bb)
        bb.position(0)
        val out = FloatArray(width * height * 3)
        bb.asFloatBuffer().get(out)
        return out
    }

    override fun close() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }
}
