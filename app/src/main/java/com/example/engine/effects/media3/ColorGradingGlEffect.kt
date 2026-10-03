package com.example.engine.effects.media3

import android.content.Context
import android.graphics.ColorMatrix
import androidx.annotation.OptIn
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.domain.model.FilterSettings
import com.example.domain.model.FilterType
import com.example.domain.model.VideoAdjustments
import com.example.engine.composition.ColorFilterGenerator
import kotlin.math.abs

/**
 * OpenGL-based Color Grading effect for Media3 Effect API.
 *
 * Applies professional cinema-grade color adjustments, film grain, vignette,
 * sharpness enhancement, and color matrix transformations (such as Teal & Orange,
 * Golden Autumn, Almond, Vintage, etc.) directly on video frames before they reach the encoder.
 */
@OptIn(UnstableApi::class)
class ColorGradingGlEffect(
  val brightness: Float = 0f,         // -1f to 1f
  val contrast: Float = 1f,           // 0.1f to 3f
  val saturation: Float = 1f,         // 0f to 3f
  val exposure: Float = 0f,           // -2f to 2f
  val temperature: Float = 0f,        // -1f to 1f (Kelvin warm/cool)
  val tint: Float = 0f,               // -1f to 1f (green/magenta)
  val highlights: Float = 0f,         // -1f to 1f
  val shadows: Float = 0f,            // -1f to 1f
  val whites: Float = 0f,             // -1f to 1f
  val blacks: Float = 0f,             // -1f to 1f
  val vignette: Float = 0f,           // 0f to 1f
  val grain: Float = 0f,              // 0f to 1f
  val sharpness: Float = 0f,          // 0f to 1f
  val autoEnhance: Float = 0f,        // 0f to 1f
  val hdrBoost: Float = 0f,           // 0f to 1f
  val colorMatrix4x4: FloatArray? = null,
  val colorOffset: FloatArray? = null
) : GlEffect {

  companion object {
    /**
     * Creates a [ColorGradingGlEffect] from domain [VideoAdjustments] and [FilterSettings].
     */
    fun fromTimeline(
      adjustments: VideoAdjustments,
      filterSettings: FilterSettings = FilterSettings()
    ): ColorGradingGlEffect {
      var mat4x4: FloatArray? = null
      var offset: FloatArray? = null

      if (filterSettings.type != FilterType.NONE && filterSettings.intensity > 0.01f) {
        val colorMatrix = ColorFilterGenerator.getFilterMatrix(filterSettings.type, filterSettings.intensity)
        if (colorMatrix != null) {
          val (m4, off) = convertColorMatrixToGl(colorMatrix)
          mat4x4 = m4
          offset = off
        }
      }

      return ColorGradingGlEffect(
        brightness = adjustments.brightness,
        contrast = adjustments.contrast,
        saturation = adjustments.saturation,
        exposure = adjustments.exposure,
        temperature = adjustments.temperature,
        tint = adjustments.tint,
        highlights = adjustments.highlights,
        shadows = adjustments.shadows,
        whites = adjustments.whites,
        blacks = adjustments.blacks,
        vignette = adjustments.vignette,
        grain = adjustments.grain,
        sharpness = adjustments.sharpness,
        autoEnhance = adjustments.autoEnhance,
        hdrBoost = adjustments.hdrBoost,
        colorMatrix4x4 = mat4x4,
        colorOffset = offset
      )
    }

    /**
     * Converts a 20-element Android [ColorMatrix] (4 rows x 5 cols) into
     * an OpenGL 4x4 column-major matrix and a 4-element normalized offset vector.
     */
    fun convertColorMatrixToGl(colorMatrix: ColorMatrix): Pair<FloatArray, FloatArray> {
      val arr = colorMatrix.array
      // arr is: [a, b, c, d, e,
      //          f, g, h, i, j,
      //          k, l, m, n, o,
      //          p, q, r, s, t]
      val gl4x4 = FloatArray(16)
      // Column 0
      gl4x4[0] = arr[0]
      gl4x4[1] = arr[5]
      gl4x4[2] = arr[10]
      gl4x4[3] = arr[15]

      // Column 1
      gl4x4[4] = arr[1]
      gl4x4[5] = arr[6]
      gl4x4[6] = arr[11]
      gl4x4[7] = arr[16]

      // Column 2
      gl4x4[8] = arr[2]
      gl4x4[9] = arr[7]
      gl4x4[10] = arr[12]
      gl4x4[11] = arr[17]

      // Column 3
      gl4x4[12] = arr[3]
      gl4x4[13] = arr[8]
      gl4x4[14] = arr[13]
      gl4x4[15] = arr[18]

      // Offset normalized by 255.0
      val off = floatArrayOf(
        arr[4] / 255f,
        arr[9] / 255f,
        arr[14] / 255f,
        arr[19] / 255f
      )

      return Pair(gl4x4, off)
    }

    const val COLOR_GRADING_FRAGMENT_SHADER: String = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
varying vec2 vTexSamplingCoord;

// Color Grading Parameters
uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;
uniform float uExposure;
uniform float uTemperature;
uniform float uTint;
uniform float uHighlights;
uniform float uShadows;
uniform float uVignette;
uniform float uGrain;
uniform float uGrainSeed;
uniform float uSharpness;
uniform vec2 uTexelSize;

// Color Matrix for artistic presets
uniform mat4 uColorMatrix;
uniform vec4 uColorOffset;
uniform int uUseColorMatrix;

float rand(vec2 co) {
  return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
  vec2 uv = clamp(vTexSamplingCoord, 0.0, 1.0);
  vec4 color = texture2D(uTexSampler, uv);

  // 1. Sharpness enhancement
  if (uSharpness > 0.01) {
    vec4 n = texture2D(uTexSampler, clamp(uv + vec2(0.0, uTexelSize.y), 0.0, 1.0));
    vec4 s = texture2D(uTexSampler, clamp(uv - vec2(0.0, uTexelSize.y), 0.0, 1.0));
    vec4 e = texture2D(uTexSampler, clamp(uv + vec2(uTexelSize.x, 0.0), 0.0, 1.0));
    vec4 w = texture2D(uTexSampler, clamp(uv - vec2(uTexelSize.x, 0.0), 0.0, 1.0));
    vec4 laplacian = (n + s + e + w) * 0.25;
    color.rgb = color.rgb + (color.rgb - laplacian.rgb) * (uSharpness * 1.5);
  }

  // 2. Exposure & Brightness
  float totalBright = uBrightness + uExposure * 0.5;
  color.rgb += vec3(totalBright);

  // 3. Contrast
  if (abs(uContrast - 1.0) > 0.001) {
    color.rgb = (color.rgb - 0.5) * uContrast + 0.5;
  }

  // 4. Saturation
  if (abs(uSaturation - 1.0) > 0.001) {
    float lum = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    color.rgb = mix(vec3(lum), color.rgb, uSaturation);
  }

  // 5. Temperature & Tint
  if (abs(uTemperature) > 0.001 || abs(uTint) > 0.001) {
    vec3 tempTint = vec3(
      uTemperature * 0.12,
      uTint * 0.08,
      -uTemperature * 0.12
    );
    color.rgb += tempTint;
  }

  // 6. Highlights & Shadows
  if (abs(uHighlights) > 0.001 || abs(uShadows) > 0.001) {
    float lum = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    float shadowWeight = 1.0 - smoothstep(0.0, 0.6, lum);
    float highlightWeight = smoothstep(0.4, 1.0, lum);
    color.rgb += vec3(uShadows * shadowWeight * 0.2 + uHighlights * highlightWeight * 0.2);
  }

  // 7. Color Matrix transformation
  if (uUseColorMatrix == 1) {
    color = (uColorMatrix * color) + uColorOffset;
  }

  // 8. Vignette
  if (uVignette > 0.01) {
    vec2 coord = (uv - 0.5) * 2.0;
    float dist = length(coord);
    float vig = 1.0 - smoothstep(0.7, 1.4, dist * (0.8 + uVignette * 0.8));
    color.rgb *= vig;
  }

  // 9. Film Grain
  if (uGrain > 0.01) {
    float noise = (rand(uv + vec2(uGrainSeed * 0.13, uGrainSeed * 0.17)) - 0.5) * uGrain * 0.25;
    color.rgb += vec3(noise);
  }

  color.rgb = clamp(color.rgb, 0.0, 1.0);
  gl_FragColor = color;
}
"""
  }

  override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
    val isDefaultAdjustments = abs(brightness) < 0.001f &&
        abs(contrast - 1f) < 0.001f &&
        abs(saturation - 1f) < 0.001f &&
        abs(exposure) < 0.001f &&
        abs(temperature) < 0.001f &&
        abs(tint) < 0.001f &&
        abs(highlights) < 0.001f &&
        abs(shadows) < 0.001f &&
        abs(whites) < 0.001f &&
        abs(blacks) < 0.001f &&
        vignette < 0.01f &&
        grain < 0.01f &&
        sharpness < 0.01f &&
        autoEnhance < 0.01f &&
        hdrBoost < 0.01f

    val hasColorMatrix = colorMatrix4x4 != null && colorOffset != null
    return isDefaultAdjustments && !hasColorMatrix
  }

  override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
    val matrix = colorMatrix4x4 ?: GlUtil.create4x4IdentityMatrix()
    val offset = colorOffset ?: floatArrayOf(0f, 0f, 0f, 0f)
    val useMatrix = if (colorMatrix4x4 != null && colorOffset != null) 1 else 0

    return CustomShaderGlShaderProgram(
      useHdr = useHdr,
      vertexShader = CustomShaderGlEffect.DEFAULT_VERTEX_SHADER,
      fragmentShader = COLOR_GRADING_FRAGMENT_SHADER,
      uniformBinder = { program: GlProgram, presentationTimeUs: Long, width: Int, height: Int ->
        program.setFloatUniform("uBrightness", brightness)
        program.setFloatUniform("uContrast", contrast)
        program.setFloatUniform("uSaturation", saturation)
        program.setFloatUniform("uExposure", exposure)
        program.setFloatUniform("uTemperature", temperature)
        program.setFloatUniform("uTint", tint)
        program.setFloatUniform("uHighlights", highlights)
        program.setFloatUniform("uShadows", shadows)
        program.setFloatUniform("uVignette", vignette)
        program.setFloatUniform("uGrain", grain)
        program.setFloatUniform("uGrainSeed", (presentationTimeUs / 1000L % 10000).toFloat() / 1000f)
        program.setFloatUniform("uSharpness", sharpness)

        val texelW = if (width > 0) 1f / width else 1f / 1920f
        val texelH = if (height > 0) 1f / height else 1f / 1080f
        program.setFloatsUniform("uTexelSize", floatArrayOf(texelW, texelH))

        program.setIntUniform("uUseColorMatrix", useMatrix)
        program.setFloatsUniform("uColorMatrix", matrix)
        program.setFloatsUniform("uColorOffset", offset)
      }
    )
  }
}
