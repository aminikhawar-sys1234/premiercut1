package com.vfx.engine.effects

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.params.ParamMap
import com.vfx.engine.effects.base.SinglePassEffect
import com.vfx.engine.gpu.buffer.QuadRenderer
import com.vfx.engine.gpu.fbo.FramebufferObject
import com.vfx.engine.gpu.shader.ShaderCompiler
import com.vfx.engine.gpu.state.GlStateTracker

/**
 * GLSL 300 es shader converter for converting samplerExternalOES (camera/MediaCodec surfaces)
 * into standard 2D FBO target textures.
 */
class OesTo2dConverterEffect(
  private val quadRenderer: QuadRenderer = QuadRenderer()
) : SinglePassEffect(
  EffectDefinition(
    id = "vfx_oes_converter",
    displayName = "OES External Surface Converter",
    category = EffectCategory.TRANSFORM,
    parameters = emptyList()
  )
) {

  private var programId: Int = 0
  private var oesTexSamplerLoc: Int = -1
  private var texMatrixLoc: Int = -1

  private val identityMatrix = FloatArray(16) { if (it % 5 == 0) 1.0f else 0.0f }

  companion object {
    private const val VERTEX_SHADER = """#version 300 es
      layout(location = 0) in vec4 aPosition;
      layout(location = 1) in vec2 aTexCoord;
      uniform mat4 uTexMatrix;
      out vec2 vTexCoord;

      void main() {
        gl_Position = aPosition;
        vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
      }
    """

    private const val FRAGMENT_SHADER = """#version 300 es
      #extension GL_OES_EGL_image_external_essl3 : require
      precision mediump float;
      uniform samplerExternalOES uOesTexSampler;
      in vec2 vTexCoord;
      out vec4 fragColor;

      void main() {
        fragColor = texture(uOesTexSampler, vTexCoord);
      }
    """
  }

  private fun ensureCompiled() {
    if (programId == 0) {
      val vShader = ShaderCompiler.compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
      val fShader = ShaderCompiler.compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
      programId = ShaderCompiler.linkProgram(vShader, fShader)
      GLES30.glDeleteShader(fShader)
      GLES30.glDeleteShader(vShader)

      oesTexSamplerLoc = GLES30.glGetUniformLocation(programId, "uOesTexSampler")
      texMatrixLoc = GLES30.glGetUniformLocation(programId, "uTexMatrix")
    }
  }

  fun renderOesToFbo(
    oesTextureId: Int,
    texMatrix: FloatArray?,
    outputFbo: FramebufferObject
  ) {
    ensureCompiled()

    outputFbo.bind()
    GlStateTracker.bindProgram(programId)

    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
    GLES30.glUniform1i(oesTexSamplerLoc, 0)

    val matrixToUse = texMatrix ?: identityMatrix
    GLES30.glUniformMatrix4fv(texMatrixLoc, 1, false, matrixToUse, 0)

    quadRenderer.drawQuad()
  }

  override fun render(inputFbo: FramebufferObject, outputFbo: FramebufferObject, params: ParamMap) {
    // Regular 2D fallback path if called with standard FBO
    renderOesToFbo(inputFbo.texture.textureId, null, outputFbo)
  }

  fun release() {
    if (programId != 0) {
      GLES30.glDeleteProgram(programId)
      programId = 0
    }
  }
}
