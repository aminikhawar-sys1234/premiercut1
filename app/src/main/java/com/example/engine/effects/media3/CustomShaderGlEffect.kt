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
 * A flexible, extensible Media3 [GlEffect] that runs custom OpenGL fragment shaders
 * on video frames before they reach the video encoder.
 *
 * @param name Diagnostic identifier for this custom effect.
 * @param fragmentShader Custom GLSL fragment shader source code.
 * @param vertexShader Custom GLSL vertex shader source code (defaults to Media3 full-screen quad).
 * @param uniformBinder Optional callback to dynamically bind uniforms for each rendered frame.
 * @param onRelease Optional cleanup hook (e.g. deleting custom auxiliary textures).
 */
@OptIn(UnstableApi::class)
open class CustomShaderGlEffect(
  val name: String,
  val fragmentShader: String,
  val vertexShader: String = DEFAULT_VERTEX_SHADER,
  private val uniformBinder: ((program: GlProgram, presentationTimeUs: Long, width: Int, height: Int) -> Unit)? = null,
  private val onRelease: (() -> Unit)? = null
) : GlEffect {

  companion object {
    /**
     * Standard vertex shader compatible with Media3's normalized coordinate quad.
     */
    const val DEFAULT_VERTEX_SHADER: String = """#version 100
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
  }

  override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
    return CustomShaderGlShaderProgram(
      useHdr = useHdr,
      vertexShader = vertexShader,
      fragmentShader = fragmentShader,
      uniformBinder = uniformBinder,
      onRelease = onRelease
    )
  }

  override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
}

/**
 * Implementation of [BaseGlShaderProgram] that compiles and executes custom GLSL shaders
 * within Media3's frame processing pipeline.
 */
@OptIn(UnstableApi::class)
class CustomShaderGlShaderProgram(
  useHdr: Boolean,
  vertexShader: String,
  fragmentShader: String,
  private val uniformBinder: ((program: GlProgram, presentationTimeUs: Long, width: Int, height: Int) -> Unit)? = null,
  private val onRelease: (() -> Unit)? = null
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

  private val glProgram: GlProgram
  private var currentWidth: Int = 0
  private var currentHeight: Int = 0

  init {
    try {
      glProgram = GlProgram(vertexShader, fragmentShader)
      glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
      )
      val identityMatrix = GlUtil.create4x4IdentityMatrix()
      glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix)
      glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix)
    } catch (e: Exception) {
      throw VideoFrameProcessingException("Failed to initialize custom shader program", e)
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
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnit= */ 0)

      // Bind dynamic per-frame uniforms
      uniformBinder?.invoke(glProgram, presentationTimeUs, currentWidth, currentHeight)

      glProgram.bindAttributesAndUniforms()
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
      GlUtil.checkGlError()
    } catch (e: Exception) {
      throw VideoFrameProcessingException(e, presentationTimeUs)
    }
  }

  override fun release() {
    super.release()
    try {
      glProgram.delete()
      onRelease?.invoke()
    } catch (e: Exception) {
      throw VideoFrameProcessingException("Failed to release custom shader program", e)
    }
  }
}
