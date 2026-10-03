package com.example.engine.effects.media3

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Custom GL Effect for WYSIWYG Color Fidelity and Optical Flare / Glow Blending.
 */
@OptIn(UnstableApi::class)
class WysiwygGlowGlEffect(
  val intensity: Float = 0.35f,
  val radius: Float = 0.012f,
  val threshold: Float = 0.65f
) : GlEffect {
  override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
    return WysiwygGlowShaderProgram(context, useHdr, intensity, radius, threshold)
  }
}

@OptIn(UnstableApi::class)
class WysiwygGlowShaderProgram(
  context: Context,
  useHdr: Boolean,
  private val intensity: Float,
  private val radius: Float,
  private val threshold: Float
) : BaseGlShaderProgram(useHdr, 1) {

  private val glProgram: GlProgram
  private var currentWidth: Int = 1080
  private var currentHeight: Int = 1920

  companion object {
    private const val VERTEX_SHADER = """#version 100
      attribute vec4 aFramePosition;
      uniform mat4 uTransformationMatrix;
      uniform mat4 uTexTransformationMatrix;
      varying vec2 vTexSamplingCoord;

      void main() {
        gl_Position = uTransformationMatrix * aFramePosition;
        vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5,
                                    aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
        vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
      }
    """

    // Prevents harsh white oval borders and cut-off flare boundaries during export
    private const val FRAGMENT_SHADER = """#version 100
      precision mediump float;
      uniform sampler2D uTexSampler;
      uniform float uIntensity;
      uniform float uRadius;
      uniform float uThreshold;
      uniform vec2 uTexSize;
      varying vec2 vTexSamplingCoord;

      const vec3 LUMA_REC709 = vec3(0.2126, 0.7152, 0.0722);

      void main() {
        vec4 baseColor = texture2D(uTexSampler, vTexSamplingCoord);
        vec3 unPremultRgb = baseColor.a > 0.001 ? baseColor.rgb / baseColor.a : baseColor.rgb;

        vec3 glowAcc = vec3(0.0);
        vec2 texel = vec2(uRadius) / uTexSize;

        for (int x = -1; x <= 1; x++) {
          for (int y = -1; y <= 1; y++) {
            vec2 offset = vec2(float(x), float(y)) * texel;
            vec4 s = texture2D(uTexSampler, vTexSamplingCoord + offset);
            vec3 sRgb = s.a > 0.001 ? s.rgb / s.a : s.rgb;
            float sLuma = dot(sRgb, LUMA_REC709);
            if (sLuma > uThreshold) {
              glowAcc += sRgb * (sLuma - uThreshold);
            }
          }
        }
        glowAcc = (glowAcc / 9.0) * uIntensity;

        vec3 finalRgb = clamp(unPremultRgb + glowAcc, 0.0, 1.0);
        gl_FragColor = vec4(finalRgb * baseColor.a, baseColor.a);
      }
    """
  }

  init {
    try {
      glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
      glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
      )
      val identityMatrix = GlUtil.create4x4IdentityMatrix()
      glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix)
      glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix)
      glProgram.setFloatUniform("uIntensity", intensity)
      glProgram.setFloatUniform("uRadius", radius)
      glProgram.setFloatUniform("uThreshold", threshold)
    } catch (e: Exception) {
      throw VideoFrameProcessingException("Failed to compile WysiwygGlowShaderProgram", e)
    }
  }

  override fun configure(inputWidth: Int, inputHeight: Int): Size {
    currentWidth = inputWidth
    currentHeight = inputHeight
    return Size(inputWidth, inputHeight)
  }

  override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
    try {
      glProgram.use()
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
      glProgram.setFloatsUniform("uTexSize", floatArrayOf(currentWidth.toFloat(), currentHeight.toFloat()))
      glProgram.bindAttributesAndUniforms()
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
      GlUtil.checkGlError()
    } catch (e: Exception) {
      throw VideoFrameProcessingException(e, presentationTimeUs)
    }
  }

  override fun release() {
    super.release()
    glProgram.delete()
  }
}
