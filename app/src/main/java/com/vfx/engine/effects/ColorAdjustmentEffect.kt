package com.vfx.engine.effects

import android.opengl.GLES30
import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.math.Color
import com.vfx.engine.core.params.ParamConstraints
import com.vfx.engine.core.params.ParamMap
import com.vfx.engine.core.params.ParameterDescriptor
import com.vfx.engine.effects.base.SinglePassEffect
import com.vfx.engine.gpu.buffer.QuadRenderer
import com.vfx.engine.gpu.fbo.FramebufferObject
import com.vfx.engine.gpu.shader.ShaderCompiler
import com.vfx.engine.gpu.state.GlStateTracker

/**
 * Mobile-optimized GLSL 300 es single-pass Color Adjustment Effect.
 * Parameters: Exposure, Contrast, Saturation, Brightness, Tint Color.
 */
class ColorAdjustmentEffect(
  private val quadRenderer: QuadRenderer = QuadRenderer()
) : SinglePassEffect(
  EffectDefinition(
    id = "vfx_color_adjustment",
    displayName = "Color Adjustment",
    category = EffectCategory.COLOR,
    parameters = listOf(
      ParameterDescriptor.FloatParam("exposure", 0.0f, ParamConstraints(-2.0f, 2.0f)),
      ParameterDescriptor.FloatParam("contrast", 1.0f, ParamConstraints(0.0f, 2.0f)),
      ParameterDescriptor.FloatParam("saturation", 1.0f, ParamConstraints(0.0f, 2.0f)),
      ParameterDescriptor.FloatParam("brightness", 0.0f, ParamConstraints(-1.0f, 1.0f)),
      ParameterDescriptor.ColorParam("tint", Color(1.0f, 1.0f, 1.0f, 1.0f))
    )
  )
) {

  private var programId: Int = 0

  // Uniform locations
  private var texSamplerLoc: Int = -1
  private var exposureLoc: Int = -1
  private var contrastLoc: Int = -1
  private var saturationLoc: Int = -1
  private var brightnessLoc: Int = -1
  private var tintLoc: Int = -1

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

    private const val FRAGMENT_SHADER = """#version 300 es
      precision mediump float;
      uniform sampler2D uTexSampler;
      uniform float uExposure;
      uniform float uContrast;
      uniform float uSaturation;
      uniform float uBrightness;
      uniform vec4 uTint;

      in vec2 vTexCoord;
      out vec4 fragColor;

      const vec3 LUMA_REC709 = vec3(0.2126, 0.7152, 0.0722);

      void main() {
        vec4 baseColor = texture(uTexSampler, vTexCoord);
        if (baseColor.a < 0.001) {
          fragColor = vec4(0.0);
          return;
        }

        // Un-premultiply alpha for color grading
        vec3 rgb = baseColor.rgb / baseColor.a;

        // 1. Exposure: rgb * 2^exposure
        rgb = rgb * pow(2.0, uExposure);

        // 2. Brightness: rgb + brightness
        rgb = rgb + vec3(uBrightness);

        // 3. Contrast: (rgb - 0.5) * contrast + 0.5
        rgb = (rgb - vec3(0.5)) * uContrast + vec3(0.5);

        // 4. Saturation: mix(luma, rgb, saturation)
        float luma = dot(rgb, LUMA_REC709);
        rgb = mix(vec3(luma), rgb, uSaturation);

        // 5. Tint Color Multiplication
        rgb = mix(rgb, rgb * uTint.rgb, uTint.a);

        // Clamp & Re-premultiply alpha
        rgb = clamp(rgb, 0.0, 1.0);
        fragColor = vec4(rgb * baseColor.a, baseColor.a);
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

      texSamplerLoc = GLES30.glGetUniformLocation(programId, "uTexSampler")
      exposureLoc = GLES30.glGetUniformLocation(programId, "uExposure")
      contrastLoc = GLES30.glGetUniformLocation(programId, "uContrast")
      saturationLoc = GLES30.glGetUniformLocation(programId, "uSaturation")
      brightnessLoc = GLES30.glGetUniformLocation(programId, "uBrightness")
      tintLoc = GLES30.glGetUniformLocation(programId, "uTint")
    }
  }

  override fun render(inputFbo: FramebufferObject, outputFbo: FramebufferObject, params: ParamMap) {
    ensureCompiled()

    outputFbo.bind()
    GlStateTracker.bindProgram(programId)

    inputFbo.texture.bind(0)
    GLES30.glUniform1i(texSamplerLoc, 0)

    val exposure = params.getFloat("exposure", 0.0f)
    val contrast = params.getFloat("contrast", 1.0f)
    val saturation = params.getFloat("saturation", 1.0f)
    val brightness = params.getFloat("brightness", 0.0f)
    val tint = params.getColor("tint", Color(1.0f, 1.0f, 1.0f, 1.0f))

    GLES30.glUniform1f(exposureLoc, exposure)
    GLES30.glUniform1f(contrastLoc, contrast)
    GLES30.glUniform1f(saturationLoc, saturation)
    GLES30.glUniform1f(brightnessLoc, brightness)
    GLES30.glUniform4f(tintLoc, tint.r, tint.g, tint.b, tint.a)

    quadRenderer.drawQuad()
  }

  fun release() {
    if (programId != 0) {
      GLES30.glDeleteProgram(programId)
      programId = 0
    }
  }
}
