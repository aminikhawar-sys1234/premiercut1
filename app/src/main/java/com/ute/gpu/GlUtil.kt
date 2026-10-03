package com.ute.gpu

import android.opengl.GLES30.*
import com.ute.core.ResourceRegistry
import com.ute.core.TextEngineException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object GlUtil {

    fun compileProgram(vertexSrc: String, fragmentSrc: String, tag: String): Int {
        val vs = compileShader(GL_VERTEX_SHADER, vertexSrc, tag)
        val fs = compileShader(GL_FRAGMENT_SHADER, fragmentSrc, tag)
        val prog = glCreateProgram()
        glAttachShader(prog, vs)
        glAttachShader(prog, fs)
        glLinkProgram(prog)
        val status = IntArray(1)
        glGetProgramiv(prog, GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = glGetProgramInfoLog(prog)
            glDeleteProgram(prog)
            throw TextEngineException.GpuContextLost("program link failed [$tag]: $log")
        }
        glDeleteShader(vs)
        glDeleteShader(fs)
        return prog
    }

    private fun compileShader(type: Int, src: String, tag: String): Int {
        val sh = glCreateShader(type)
        glShaderSource(sh, src)
        glCompileShader(sh)
        val status = IntArray(1)
        glGetShaderiv(sh, GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = glGetShaderInfoLog(sh)
            glDeleteShader(sh)
            throw TextEngineException.GpuContextLost("shader compile failed [$tag]: $log")
        }
        return sh
    }

    fun floatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(data); position(0) }

    fun uploadStream(vbo: Int, data: FloatBuffer) {
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, data.capacity() * 4, null, GL_STREAM_DRAW)
        glBufferSubData(GL_ARRAY_BUFFER, 0, data.capacity() * 4, data)
    }

    object GlTexture {
        fun create(w: Int, h: Int, singleChannel: Boolean): Int {
            val ids = IntArray(1)
            glGenTextures(1, ids, 0)
            glBindTexture(GL_TEXTURE_2D, ids[0])
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            if (singleChannel) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, w, h, 0, GL_RED, GL_UNSIGNED_BYTE, null)
            } else {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
            }
            return ids[0]
        }
    }

    /** FBO wrapper for the post-processing chain. */
    class Framebuffer(val width: Int, val height: Int) : ResourceRegistry.Managed {
        private var fbo = -1
        private var tex = -1

        fun ensure() {
            if (fbo > 0) return
            tex = GlTexture.create(width, height, singleChannel = false)
            val ids = IntArray(1)
            glGenFramebuffers(1, ids, 0)
            fbo = ids[0]
            glBindFramebuffer(GL_FRAMEBUFFER, fbo)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0)
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                destroy()
                throw TextEngineException.GpuContextLost("FBO incomplete")
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
        }

        fun bind() {
            ensure()
            glBindFramebuffer(GL_FRAMEBUFFER, fbo)
            glViewport(0, 0, width, height)
        }

        fun texture(): Int {
            ensure()
            return tex
        }

        override fun resourceBytes() = width.toLong() * height * 4

        override fun destroy() {
            if (fbo > 0) { glDeleteFramebuffers(1, intArrayOf(fbo), 0); fbo = -1 }
            if (tex > 0) { glDeleteTextures(1, intArrayOf(tex), 0); tex = -1 }
        }
    }
}
