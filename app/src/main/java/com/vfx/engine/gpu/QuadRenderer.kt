package com.vfx.engine.gpu

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Static fullscreen quad (clip-space positions + 0..1 y-up UVs).
 * One VBO for the whole engine. No VAO — safe on pure ES2 contexts.
 * Attribute layout: loc 0 = a_pos (vec2), loc 1 = a_texCoord (vec2), stride 16.
 */
class QuadRenderer {

    private var vbo = 0
    private var released = false

    init {
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val buf = ByteBuffer.allocateDirect(VERTICES.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(VERTICES).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, VERTICES.size * 4, buf, GLES30.GL_STATIC_DRAW)
    }

    fun draw(program: ShaderProgram) {
        program.use()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        program.resetTextureBindings()
        GlCheck.glError("QuadRenderer.draw")
    }

    fun drawQuad() {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
    }

    fun drawQuad(positionHandle: Int, texCoordHandle: Int = -1) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        if (positionHandle >= 0) {
            GLES30.glEnableVertexAttribArray(positionHandle)
            GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 16, 0)
        }
        if (texCoordHandle >= 0) {
            GLES30.glEnableVertexAttribArray(texCoordHandle)
            GLES30.glVertexAttribPointer(texCoordHandle, 2, GLES30.GL_FLOAT, false, 16, 8)
        }
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
    }

    fun release() {
        if (released || vbo == 0) return
        val ids = IntArray(1); ids[0] = vbo
        GLES30.glDeleteBuffers(1, ids, 0)
        vbo = 0; released = true
    }

    fun abandon() { vbo = 0; released = true }

    companion object {
        // x, y, u, v — GL y-up convention
        val VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,   1f, -1f, 1f, 0f,  -1f, 1f, 0f, 1f,
            -1f,  1f, 0f, 1f,   1f, -1f, 1f, 0f,   1f, 1f, 1f, 1f)
    }
}
