package com.ahstudio.face.gl

import android.opengl.GLES20.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object GlUtil {
    fun createProgram(vsSrc: String, fsSrc: String): Int {
        val p = glCreateProgram()
        glAttachShader(p, compileShader(GL_VERTEX_SHADER, vsSrc))
        glAttachShader(p, compileShader(GL_FRAGMENT_SHADER, fsSrc))
        glLinkProgram(p)
        val ok = IntArray(1); glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
        check(ok[0] == 1) { "Program link failed: ${glGetProgramInfoLog(p)}" }
        return p
    }

    private fun compileShader(type: Int, src: String): Int {
        val s = glCreateShader(type)
        glShaderSource(s, src); glCompileShader(s)
        val ok = IntArray(1); glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
        check(ok[0] == 1) { "Shader compile failed: ${glGetShaderInfoLog(s)}" }
        return s
    }

    fun direct(f: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(f.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(f); position(0) }
}

/** Fullscreen quad helper for texture->texture passes. */
class FullscreenQuad {
    private val buf: FloatBuffer = GlUtil.direct(floatArrayOf(
        -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))

    fun draw(aPos: Int) {
        buf.position(0)
        glVertexAttribPointer(aPos, 2, GL_FLOAT, false, 0, buf)
        glEnableVertexAttribArray(aPos)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(aPos)
    }
}
