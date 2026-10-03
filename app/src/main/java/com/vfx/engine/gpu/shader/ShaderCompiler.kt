package com.vfx.engine.gpu.shader

import android.opengl.GLES20
import com.vfx.engine.core.ShaderCompileError

object GlslChunks {
  const val FULLSCREEN_VERTEX_SHADER = """#version 100
    attribute vec4 aFramePosition;
    varying vec2 vTexCoord;
    void main() {
      gl_Position = aFramePosition;
      vTexCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
    }
  """

  const val COMMON_LUMA_FUNCTIONS = """
    const vec3 LUMA_REC709 = vec3(0.2126, 0.7152, 0.0722);
    float getLuminance(vec3 rgb) {
      return dot(rgb, LUMA_REC709);
    }
  """
}

object ShaderCompiler {
  fun compileShader(type: Int, source: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
      val log = GLES20.glGetShaderInfoLog(shader)
      GLES20.glDeleteShader(shader)
      throw ShaderCompileError("GLShader", log ?: "Unknown compilation error")
    }
    return shader
  }

  fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertexShader)
    GLES20.glAttachShader(program, fragmentShader)
    GLES20.glLinkProgram(program)
    val status = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
    if (status[0] == 0) {
      val log = GLES20.glGetProgramInfoLog(program)
      GLES20.glDeleteProgram(program)
      throw ShaderCompileError("GLProgram", log ?: "Link error")
    }
    return program
  }
}
