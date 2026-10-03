package com.vfx.engine.effects

import android.opengl.GLES30
import com.vfx.engine.core.ShaderCompileError
import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.params.ParamConstraints
import com.vfx.engine.core.params.ParamMap
import com.vfx.engine.core.params.ParameterDescriptor
import com.vfx.engine.effects.base.MultiPassEffect
import com.vfx.engine.gpu.buffer.QuadRenderer
import com.vfx.engine.gpu.fbo.FramebufferObject
import com.vfx.engine.gpu.fbo.FramebufferPool
import com.vfx.engine.gpu.shader.ShaderCompiler
import com.vfx.engine.gpu.state.GlStateTracker

/**
 * Mobile-optimized Dual-Kawase Blur using half-pixel offset sampling
 * designed for Adreno and Mali GPUs.
 */
class DualKawaseBlurEffect(
  private val quadRenderer: QuadRenderer = QuadRenderer(),
  private val fboPool: FramebufferPool = FramebufferPool()
) : MultiPassEffect(
  EffectDefinition(
    id = "vfx_dual_kawase_blur",
    displayName = "Dual-Kawase Blur",
    category = EffectCategory.BLUR,
    parameters = listOf(
      ParameterDescriptor.FloatParam("radius", 4.0f, ParamConstraints(0.0f, 32.0f)),
      ParameterDescriptor.IntParam("passes", 3, 1, 6)
    )
  )
) {

  private var downProgram: Int = 0
  private var upProgram: Int = 0

  // Cached uniform locations
  private var downTexSamplerLoc: Int = -1
  private var downTexSizeLoc: Int = -1
  private var downOffsetLoc: Int = -1

  private var upTexSamplerLoc: Int = -1
  private var upTexSizeLoc: Int = -1
  private var upOffsetLoc: Int = -1

  companion object {
    private const val VERTEX_SHADER = """#version 300 es
      layout(location = 0) in vec4 aPosition;
      layout(location = 1) in vec2 aTexCoord;
      out vec2 vTexCoord;
      void main() {
        gl_Position = aPosition;
        vTexCoord = aTexCoord;
      }
    """

    private const val DOWNSAMPLE_FRAGMENT = """#version 300 es
      precision mediump float;
      uniform sampler2D uTexSampler;
      uniform vec2 uTexSize;
      uniform float uOffset;
      in vec2 vTexCoord;
      out vec4 fragColor;

      void main() {
        vec2 halfTexel = (vec2(0.5) + vec2(uOffset)) / uTexSize;
        vec4 sum = texture(uTexSampler, vTexCoord) * 4.0;
        sum += texture(uTexSampler, vTexCoord + vec2(-halfTexel.x, -halfTexel.y));
        sum += texture(uTexSampler, vTexCoord + vec2(halfTexel.x, -halfTexel.y));
        sum += texture(uTexSampler, vTexCoord + vec2(-halfTexel.x, halfTexel.y));
        sum += texture(uTexSampler, vTexCoord + vec2(halfTexel.x, halfTexel.y));
        fragColor = sum / 8.0;
      }
    """

    private const val UPSAMPLE_FRAGMENT = """#version 300 es
      precision mediump float;
      uniform sampler2D uTexSampler;
      uniform vec2 uTexSize;
      uniform float uOffset;
      in vec2 vTexCoord;
      out vec4 fragColor;

      void main() {
        vec2 halfTexel = (vec2(0.5) + vec2(uOffset)) / uTexSize;
        vec4 sum = vec4(0.0);
        sum += texture(uTexSampler, vTexCoord + vec2(-halfTexel.x * 2.0, 0.0));
        sum += texture(uTexSampler, vTexCoord + vec2(-halfTexel.x, halfTexel.y * 2.0)) * 2.0;
        sum += texture(uTexSampler, vTexCoord + vec2(0.0, halfTexel.y * 2.0));
        sum += texture(uTexSampler, vTexCoord + vec2(halfTexel.x, halfTexel.y * 2.0)) * 2.0;
        sum += texture(uTexSampler, vTexCoord + vec2(halfTexel.x * 2.0, 0.0));
        sum += texture(uTexSampler, vTexCoord + vec2(halfTexel.x, -halfTexel.y * 2.0)) * 2.0;
        sum += texture(uTexSampler, vTexCoord + vec2(0.0, -halfTexel.y * 2.0));
        sum += texture(uTexSampler, vTexCoord + vec2(-halfTexel.x, -halfTexel.y * 2.0)) * 2.0;
        fragColor = sum / 12.0;
      }
    """
  }

  private fun ensureShadersCompiled() {
    if (downProgram == 0) {
      val vShader = ShaderCompiler.compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
      val downFShader = ShaderCompiler.compileShader(GLES30.GL_FRAGMENT_SHADER, DOWNSAMPLE_FRAGMENT)
      downProgram = ShaderCompiler.linkProgram(vShader, downFShader)
      GLES30.glDeleteShader(downFShader)

      val upFShader = ShaderCompiler.compileShader(GLES30.GL_FRAGMENT_SHADER, UPSAMPLE_FRAGMENT)
      upProgram = ShaderCompiler.linkProgram(vShader, upFShader)
      GLES30.glDeleteShader(upFShader)
      GLES30.glDeleteShader(vShader)

      downTexSamplerLoc = GLES30.glGetUniformLocation(downProgram, "uTexSampler")
      downTexSizeLoc = GLES30.glGetUniformLocation(downProgram, "uTexSize")
      downOffsetLoc = GLES30.glGetUniformLocation(downProgram, "uOffset")

      upTexSamplerLoc = GLES30.glGetUniformLocation(upProgram, "uTexSampler")
      upTexSizeLoc = GLES30.glGetUniformLocation(upProgram, "uTexSize")
      upOffsetLoc = GLES30.glGetUniformLocation(upProgram, "uOffset")
    }
  }

  override fun render(inputFbo: FramebufferObject, outputFbo: FramebufferObject, params: ParamMap) {
    ensureShadersCompiled()

    val radius = params.getFloat("radius", 4.0f)
    val numPasses = params.getInt("passes", 3).coerceIn(1, 6)

    var currentWidth = inputFbo.width
    var currentHeight = inputFbo.height

    val downPyramid = Array<FramebufferObject?>(numPasses) { null }

    var currentInputFbo = inputFbo

    // 1. Downsample Pyramid
    GlStateTracker.bindProgram(downProgram)
    GLES30.glUniform1i(downTexSamplerLoc, 0)

    for (i in 0 until numPasses) {
      currentWidth = (currentWidth / 2).coerceAtLeast(1)
      currentHeight = (currentHeight / 2).coerceAtLeast(1)

      val passFbo = fboPool.acquire(currentWidth, currentHeight)
      downPyramid[i] = passFbo

      passFbo.bind()
      currentInputFbo.texture.bind(0)

      GLES30.glUniform2f(downTexSizeLoc, passFbo.width.toFloat(), passFbo.height.toFloat())
      GLES30.glUniform1f(downOffsetLoc, radius * 0.25f + i)

      quadRenderer.drawQuad()
      currentInputFbo = passFbo
    }

    // 2. Upsample Pyramid
    GlStateTracker.bindProgram(upProgram)
    GLES30.glUniform1i(upTexSamplerLoc, 0)

    for (i in numPasses - 1 downTo 0) {
      val targetFbo = if (i == 0) outputFbo else downPyramid[i - 1]!!
      targetFbo.bind()
      currentInputFbo.texture.bind(0)

      GLES30.glUniform2f(upTexSizeLoc, targetFbo.width.toFloat(), targetFbo.height.toFloat())
      GLES30.glUniform1f(upOffsetLoc, radius * 0.25f + i)

      quadRenderer.drawQuad()

      // Release downsampled FBO back to pool
      val prevPassFbo = downPyramid[i]
      if (prevPassFbo != null && prevPassFbo != outputFbo) {
        fboPool.release(prevPassFbo)
      }
      currentInputFbo = targetFbo
    }
  }

  fun release() {
    if (downProgram != 0) {
      GLES30.glDeleteProgram(downProgram)
      downProgram = 0
    }
    if (upProgram != 0) {
      GLES30.glDeleteProgram(upProgram)
      upProgram = 0
    }
  }
}
