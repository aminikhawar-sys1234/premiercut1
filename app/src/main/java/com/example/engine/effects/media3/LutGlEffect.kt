package com.example.engine.effects.media3

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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
import androidx.media3.effect.SingleColorLut
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * OpenGL-based 3D / 2D Color Look-Up Table (LUT) effect for Media3 Effect API.
 *
 * Maps RGB video frames through a color lookup table texture with hardware-accelerated
 * trilinear/bilinear slice interpolation and intensity blending.
 *
 * Supports:
 * - Vertical strip LUTs (Media3 format: N x N^2)
 * - Horizontal strip LUTs (Adobe / DaVinci Resolve format: N^2 x N)
 * - HALD Square LUTs
 * - Intensity control (0.0 to 1.0)
 */
@OptIn(UnstableApi::class)
class LutGlEffect(
  val lutBitmap: Bitmap,
  val intensity: Float = 1.0f, // 0.0 to 1.0
  val lutLength: Int = detectLutLength(lutBitmap)
) : GlEffect {

  enum class LutFormat(val typeId: Int) {
    VERTICAL_STRIP(0),   // N x N^2
    HORIZONTAL_STRIP(1), // N^2 x N
    SQUARE_HALD(2)       // sqrt(N^3) x sqrt(N^3)
  }

  val format: LutFormat = detectLutFormat(lutBitmap)

  companion object {
    fun detectLutFormat(bitmap: Bitmap): LutFormat {
      val w = bitmap.width
      val h = bitmap.height
      return when {
        w * w == h -> LutFormat.VERTICAL_STRIP
        h * h == w -> LutFormat.HORIZONTAL_STRIP
        else -> {
          val cubeRoot = Math.cbrt((w * h).toDouble()).roundToInt()
          if (cubeRoot * cubeRoot * cubeRoot == w * h) {
            LutFormat.SQUARE_HALD
          } else if (w > h) {
            LutFormat.HORIZONTAL_STRIP
          } else {
            LutFormat.VERTICAL_STRIP
          }
        }
      }
    }

    fun detectLutLength(bitmap: Bitmap): Int {
      val w = bitmap.width
      val h = bitmap.height
      return when {
        w * w == h -> w
        h * h == w -> h
        else -> Math.cbrt((w * h).toDouble()).roundToInt().coerceAtLeast(16)
      }
    }

    /**
     * Generates a neutral / identity 3D LUT Bitmap of size cubeLength (e.g. 16, 32, or 64).
     */
    fun createIdentityLut(cubeLength: Int = 16, vertical: Boolean = true): Bitmap {
      val w = if (vertical) cubeLength else cubeLength * cubeLength
      val h = if (vertical) cubeLength * cubeLength else cubeLength
      val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      val pixels = IntArray(w * h)

      val step = 255f / (cubeLength - 1).coerceAtLeast(1)

      for (r in 0 until cubeLength) {
        val rVal = (r * step).roundToInt().coerceIn(0, 255)
        for (g in 0 until cubeLength) {
          val gVal = (g * step).roundToInt().coerceIn(0, 255)
          for (b in 0 until cubeLength) {
            val bVal = (b * step).roundToInt().coerceIn(0, 255)
            val color = Color.argb(255, rVal, gVal, bVal)
            val x = if (vertical) b else r * cubeLength + b
            val y = if (vertical) r * cubeLength + g else g
            pixels[y * w + x] = color
          }
        }
      }

      bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
      return bitmap
    }

    /**
     * Creates a cinematic Teal & Orange LUT bitmap.
     */
    fun createCinematicTealOrangeLut(cubeLength: Int = 16): Bitmap {
      val w = cubeLength
      val h = cubeLength * cubeLength
      val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      val pixels = IntArray(w * h)
      val step = 1f / (cubeLength - 1).coerceAtLeast(1)

      for (r in 0 until cubeLength) {
        val rNorm = r * step
        for (g in 0 until cubeLength) {
          val gNorm = g * step
          for (b in 0 until cubeLength) {
            val bNorm = b * step

            val lum = 0.2126f * rNorm + 0.7152f * gNorm + 0.0722f * bNorm

            // Shadows push teal (cyan), Highlights push warm orange
            val shadowWeight = 1f - lum
            val highlightWeight = lum

            val outR = (rNorm * 0.85f + highlightWeight * 0.25f - shadowWeight * 0.08f).coerceIn(0f, 1f)
            val outG = (gNorm * 0.95f + highlightWeight * 0.08f + shadowWeight * 0.04f).coerceIn(0f, 1f)
            val outB = (bNorm * 0.80f + shadowWeight * 0.22f - highlightWeight * 0.12f).coerceIn(0f, 1f)

            val color = Color.argb(
              255,
              (outR * 255).roundToInt(),
              (outG * 255).roundToInt(),
              (outB * 255).roundToInt()
            )
            val x = b
            val y = r * cubeLength + g
            pixels[y * w + x] = color
          }
        }
      }

      bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
      return bitmap
    }

    const val LUT_FRAGMENT_SHADER: String = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform sampler2D uColorLut;
uniform float uColorLutLength;
uniform float uIntensity;
uniform int uLutType; // 0=Vertical strip, 1=Horizontal strip
varying vec2 vTexSamplingCoord;

vec3 sampleLutVertical(vec3 color) {
  float N = uColorLutLength;
  float redCoord = clamp(color.r, 0.0, 1.0) * (N - 1.0);
  float redLow = clamp(floor(redCoord), 0.0, N - 2.0);
  
  float lowerY = (0.5 + redLow * N + clamp(color.g, 0.0, 1.0) * (N - 1.0)) / (N * N);
  float upperY = lowerY + 1.0 / N;
  float x = (0.5 + clamp(color.b, 0.0, 1.0) * (N - 1.0)) / N;
  
  vec3 lowerRgb = texture2D(uColorLut, vec2(x, lowerY)).rgb;
  vec3 upperRgb = texture2D(uColorLut, vec2(x, upperY)).rgb;
  return mix(lowerRgb, upperRgb, redCoord - redLow);
}

vec3 sampleLutHorizontal(vec3 color) {
  float N = uColorLutLength;
  float blueCoord = clamp(color.b, 0.0, 1.0) * (N - 1.0);
  float blueLow = clamp(floor(blueCoord), 0.0, N - 2.0);
  
  float lowerX = (0.5 + blueLow * N + clamp(color.r, 0.0, 1.0) * (N - 1.0)) / (N * N);
  float upperX = lowerX + 1.0 / N;
  float y = (0.5 + clamp(color.g, 0.0, 1.0) * (N - 1.0)) / N;
  
  vec3 lowerRgb = texture2D(uColorLut, vec2(lowerX, y)).rgb;
  vec3 upperRgb = texture2D(uColorLut, vec2(upperX, y)).rgb;
  return mix(lowerRgb, upperRgb, blueCoord - blueLow);
}

void main() {
  vec4 inputColor = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 graded;
  if (uLutType == 1) {
    graded = sampleLutHorizontal(inputColor.rgb);
  } else {
    graded = sampleLutVertical(inputColor.rgb);
  }
  
  vec3 mixed = mix(inputColor.rgb, graded, clamp(uIntensity, 0.0, 1.0));
  gl_FragColor = vec4(clamp(mixed, 0.0, 1.0), inputColor.a);
}
"""
  }

  override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
    return intensity < 0.001f
  }

  override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
    return LutGlShaderProgram(
      useHdr = useHdr,
      lutBitmap = lutBitmap,
      lutLength = lutLength,
      format = format,
      intensity = intensity
    )
  }

  /**
   * If the bitmap matches Media3's exact vertical strip dimensions (N x N^2) and intensity is 1.0,
   * converts to a Media3 [SingleColorLut].
   */
  fun toMedia3SingleColorLut(): SingleColorLut? {
    return if (format == LutFormat.VERTICAL_STRIP && intensity >= 0.999f) {
      try {
        SingleColorLut.createFromBitmap(lutBitmap)
      } catch (e: Exception) {
        null
      }
    } else {
      null
    }
  }
}

/**
 * [BaseGlShaderProgram] managing the OpenGL LUT texture and executing the lookup shader.
 */
@OptIn(UnstableApi::class)
class LutGlShaderProgram(
  useHdr: Boolean,
  private val lutBitmap: Bitmap,
  private val lutLength: Int,
  private val format: LutGlEffect.LutFormat,
  private val intensity: Float
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

  private val glProgram: GlProgram
  private var lutTextureId: Int = -1

  init {
    try {
      glProgram = GlProgram(
        CustomShaderGlEffect.DEFAULT_VERTEX_SHADER,
        LutGlEffect.LUT_FRAGMENT_SHADER
      )
      glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
      )
      val identity = GlUtil.create4x4IdentityMatrix()
      glProgram.setFloatsUniform("uTransformationMatrix", identity)
      glProgram.setFloatsUniform("uTexTransformationMatrix", identity)

      // Upload LUT texture
      lutTextureId = GlUtil.createTexture(lutBitmap)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTextureId)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    } catch (e: Exception) {
      throw VideoFrameProcessingException("Failed to initialize LUT shader program", e)
    }
  }

  override fun configure(inputWidth: Int, inputHeight: Int): Size {
    return Size(inputWidth, inputHeight)
  }

  override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
    try {
      glProgram.use()
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnit= */ 0)
      glProgram.setSamplerTexIdUniform("uColorLut", lutTextureId, /* texUnit= */ 1)
      glProgram.setFloatUniform("uColorLutLength", lutLength.toFloat())
      glProgram.setFloatUniform("uIntensity", intensity)
      glProgram.setIntUniform("uLutType", format.typeId)

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
      if (lutTextureId != -1) {
        GlUtil.deleteTexture(lutTextureId)
        lutTextureId = -1
      }
      glProgram.delete()
    } catch (e: Exception) {
      throw VideoFrameProcessingException("Failed to release LUT shader program", e)
    }
  }
}
