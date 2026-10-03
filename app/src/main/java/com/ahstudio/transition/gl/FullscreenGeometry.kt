package com.ahstudio.transition.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One shared fullscreen triangle-strip quad. Layout contract (bound in GlslCompiler):
 * location 0 = aPosition (vec2), location 1 = aUv (vec2). Created once per context.
 */
class FullscreenGeometry {
    private var vao = -1
    private var vbo = -1

    fun ensure() {
        if (vao != -1) return
        val data = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).apply {
            asFloatBuffer().apply {
                // x, y, u, v — strip order
                put(floatArrayOf(
                    -1f, -1f, 0f, 0f,
                     1f, -1f, 1f, 0f,
                    -1f,  1f, 0f, 1f,
                     1f,  1f, 1f, 1f))
                position(0)
            }
        }
        val vaos = IntArray(1); GLES30.glGenVertexArrays(1, vaos, 0); vao = vaos[0]
        val vbos = IntArray(1); GLES30.glGenBuffers(1, vbos, 0); vbo = vbos[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 16 * 4, data, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        if (vao != -1) { GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0); vao = -1 }
        if (vbo != -1) { GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0); vbo = -1 }
    }
}
