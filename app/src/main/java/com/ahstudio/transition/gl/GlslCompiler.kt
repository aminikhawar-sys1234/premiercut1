package com.ahstudio.transition.gl

import android.opengl.GLES30
import com.ahstudio.transition.core.TransitionError

object GlslCompiler {
    private const val VERSION_LINE = "#version 300 es\n"

    data class Compiled(val program: GlProgram?, val error: TransitionError.ShaderCompilation?)
    private class StageResult(val handle: Int, val log: String?)

    fun compile(vertexSrc: String, fragmentSrc: String, defines: Map<String, String>): Compiled {
        val defineBlock = StringBuilder().apply {
            defines.forEach { (k, v) -> append("#define $k $v\n") }
        }.toString()
        val vs = VERSION_LINE + defineBlock + vertexSrc
        val fs = VERSION_LINE + defineBlock + fragmentSrc

        val v = compileShader(GLES30.GL_VERTEX_SHADER, vs)
        if (v.log != null) return Compiled(null,
            TransitionError.ShaderCompilation("Vertex shader compile failed", v.log))
        val f = compileShader(GLES30.GL_FRAGMENT_SHADER, fs)
        if (f.log != null) {
            GLES30.glDeleteShader(v.handle)
            return Compiled(null,
                TransitionError.ShaderCompilation("Fragment shader compile failed", f.log))
        }

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, v.handle)
        GLES30.glAttachShader(program, f.handle)
        // Fixed geometry contract: aPosition -> location 0, aUv -> location 1
        // (matches FullscreenGeometry). Bound explicitly so the linker can't reassign.
        GLES30.glBindAttribLocation(program, 0, "aPosition")
        GLES30.glBindAttribLocation(program, 1, "aUv")
        GLES30.glLinkProgram(program)
        GLES30.glDeleteShader(v.handle)
        GLES30.glDeleteShader(f.handle)

        val linked = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)
        if (linked[0] == GLES30.GL_FALSE) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            return Compiled(null, TransitionError.ShaderCompilation("Program link failed", log))
        }
        return Compiled(GlProgram(program), null)
    }

    private fun compileShader(type: Int, src: String): StageResult {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == GLES30.GL_FALSE) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            return StageResult(0, log.ifEmpty { "unknown compile error" })
        }
        return StageResult(shader, null)
    }
}
