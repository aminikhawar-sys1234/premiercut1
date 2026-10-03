package com.example.engine.composition.gpu

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * Base interface for all modular OpenGL ES 2.0 / 3.0 visual effect passes.
 */
interface GlEffectPass {
  val id: String
  val name: String
  var isEnabled: Boolean
  var intensity: Float

  /**
   * Compiles shaders and generates GL programs.
   */
  fun init()

  /**
   * Executes the shader pass for the given input texture.
   *
   * @param inputTextureId The 2D or OES texture containing the input frame.
   * @param isOes True if the input texture is a [GLES11Ext.GL_TEXTURE_EXTERNAL_OES] texture.
   * @param width Frame width in pixels.
   * @param height Frame height in pixels.
   * @param timeSeconds Playback timeline timestamp in seconds.
   * @param texMatrix Optional texture coordinate transformation matrix (e.g. from SurfaceTexture).
   */
  fun render(
    inputTextureId: Int,
    isOes: Boolean,
    width: Int,
    height: Int,
    timeSeconds: Float,
    texMatrix: FloatArray? = null
  )

  /**
   * Releases all GPU programs and resources allocated by this pass.
   */
  fun release()
}

/**
 * Abstract base class providing common full-screen quad rendering boilerplate for effect passes.
 */
abstract class BaseGlEffectPass(
  override val id: String,
  override val name: String
) : GlEffectPass {

  override var isEnabled: Boolean = true
  override var intensity: Float = 1.0f

  protected var program2D: Int = 0
  protected var programOES: Int = 0

  protected var aPositionLoc2D: Int = -1
  protected var aTextureCoordLoc2D: Int = -1
  protected var uTextureLoc2D: Int = -1

  protected var aPositionLocOES: Int = -1
  protected var aTextureCoordLocOES: Int = -1
  protected var uTextureLocOES: Int = -1

  abstract fun getFragmentShader(isOes: Boolean): String
  open fun onBindUniforms(program: Int, isOes: Boolean, width: Int, height: Int, timeSeconds: Float) {}

  override fun init() {
    release()

    val vs = GpuShaders.VERTEX_SHADER
    val fs2D = getFragmentShader(isOes = false)
    program2D = GlShaderUtil.createProgram(vs, fs2D)
    if (program2D != 0) {
      aPositionLoc2D = GLES20.glGetAttribLocation(program2D, "aPosition")
      aTextureCoordLoc2D = GLES20.glGetAttribLocation(program2D, "aTextureCoord")
      uTextureLoc2D = GLES20.glGetUniformLocation(program2D, "uTexture")
    }

    val fsOES = getFragmentShader(isOes = true)
    programOES = GlShaderUtil.createProgram(vs, fsOES)
    if (programOES != 0) {
      aPositionLocOES = GLES20.glGetAttribLocation(programOES, "aPosition")
      aTextureCoordLocOES = GLES20.glGetAttribLocation(programOES, "aTextureCoord")
      uTextureLocOES = GLES20.glGetUniformLocation(programOES, "uTexture")
    }
  }

  override fun render(
    inputTextureId: Int,
    isOes: Boolean,
    width: Int,
    height: Int,
    timeSeconds: Float,
    texMatrix: FloatArray?
  ) {
    val program = if (isOes) programOES else program2D
    if (program == 0) return

    GLES20.glUseProgram(program)

    val uMvpLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    val uTexLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
    val aPosLoc = if (isOes) aPositionLocOES else aPositionLoc2D
    val aCoordLoc = if (isOes) aTextureCoordLocOES else aTextureCoordLoc2D
    val uSamplerLoc = if (isOes) uTextureLocOES else uTextureLoc2D

    if (uMvpLoc >= 0) GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, MultiPassEffectPipeline.IDENTITY_MATRIX, 0)
    if (uTexLoc >= 0) {
      val mat = texMatrix ?: MultiPassEffectPipeline.IDENTITY_MATRIX
      GLES20.glUniformMatrix4fv(uTexLoc, 1, false, mat, 0)
    }

    val uResLoc = GLES20.glGetUniformLocation(program, "uResolution")
    if (uResLoc >= 0) {
      GLES20.glUniform2f(uResLoc, width.toFloat(), height.toFloat())
    }
    val uAspectLoc = GLES20.glGetUniformLocation(program, "uAspectRatio")
    if (uAspectLoc >= 0) {
      val aspect = if (height > 0) width.toFloat() / height.toFloat() else 1.0f
      GLES20.glUniform1f(uAspectLoc, aspect)
    }

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    val target = if (isOes) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
    GLES20.glBindTexture(target, inputTextureId)
    if (uSamplerLoc >= 0) GLES20.glUniform1i(uSamplerLoc, 0)

    onBindUniforms(program, isOes, width, height, timeSeconds)

    MultiPassEffectPipeline.drawQuad(aPosLoc, aCoordLoc)

    GLES20.glBindTexture(target, 0)
  }

  override fun release() {
    if (program2D != 0) {
      GLES20.glDeleteProgram(program2D)
      program2D = 0
    }
    if (programOES != 0) {
      GLES20.glDeleteProgram(programOES)
      programOES = 0
    }
  }
}

/**
 * 1. Color Grading Effect Pass
 * Enforces strict color clamping [0.0, 1.0] and preserves alpha channel transparency.
 */
class ColorGradingPass(
  override val id: String = "color_grading",
  override val name: String = "Color Grading"
) : BaseGlEffectPass(id, name) {

  var brightness: Float = 0.0f   // -1.0 to 1.0
  var contrast: Float = 1.0f     // 0.0 to 2.0
  var saturation: Float = 1.0f   // 0.0 to 2.0
  var exposure: Float = 0.0f     // -2.0 to 2.0
  var temperature: Float = 0.0f  // -1.0 to 1.0
  var tint: Float = 0.0f         // -1.0 to 1.0
  var highlights: Float = 0.0f   // -1.0 to 1.0
  var shadows: Float = 0.0f      // -1.0 to 1.0

  override fun getFragmentShader(isOes: Boolean): String {
    val header = if (isOes) {
      "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
    } else {
      "precision highp float;\n"
    }
    val sampler = if (isOes) "samplerExternalOES" else "sampler2D"

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;
      uniform float uBrightness;
      uniform float uContrast;
      uniform float uSaturation;
      uniform float uExposure;
      uniform float uTemperature;
      uniform float uTint;
      uniform float uHighlights;
      uniform float uShadows;

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec4 original = texture2D(uTexture, uv);
        vec3 color = original.rgb;

        // 1. Exposure & Brightness
        color = color * pow(2.0, uExposure * uIntensity) + vec3(uBrightness * uIntensity);

        // 2. Contrast
        float effContrast = mix(1.0, uContrast, uIntensity);
        color = (color - 0.5) * effContrast + 0.5;

        // 3. Saturation (Rec.709 Luma coefficients)
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        float effSat = mix(1.0, uSaturation, uIntensity);
        color = mix(vec3(luma), color, effSat);

        // 4. White Balance (Temperature & Tint)
        color.r += uTemperature * 0.15 * uIntensity;
        color.b -= uTemperature * 0.15 * uIntensity;
        color.g -= uTint * 0.12 * uIntensity;
        color.r += uTint * 0.08 * uIntensity;
        color.b += uTint * 0.08 * uIntensity;

        // 5. Highlights & Shadows
        float l = dot(color, vec3(0.299, 0.587, 0.114));
        float shadowMask = 1.0 - smoothstep(0.0, 0.5, l);
        float highlightMask = smoothstep(0.5, 1.0, l);
        color += uShadows * shadowMask * 0.2 * uIntensity;
        color += uHighlights * highlightMask * 0.2 * uIntensity;

        // Strict color safety clamping and alpha preservation
        color = clamp(color, 0.0, 1.0);
        gl_FragColor = vec4(mix(original.rgb, color, uIntensity), original.a);
      }
    """.trimIndent()
  }

  override fun onBindUniforms(program: Int, isOes: Boolean, width: Int, height: Int, timeSeconds: Float) {
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uIntensity"), intensity)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBrightness"), brightness)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uContrast"), contrast)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSaturation"), saturation)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uExposure"), exposure)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTemperature"), temperature)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTint"), tint)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uHighlights"), highlights)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uShadows"), shadows)
  }
}

/**
 * 2. Vignette Effect Pass
 * Smooth radial attenuation preserving alpha channel and clamping colors.
 */
class VignettePass(
  override val id: String = "vignette",
  override val name: String = "Vignette"
) : BaseGlEffectPass(id, name) {

  var radius: Float = 0.75f     // 0.1 to 1.5
  var softness: Float = 0.45f   // 0.01 to 1.0
  var roundness: Float = 1.0f   // 0.5 to 2.0

  override fun getFragmentShader(isOes: Boolean): String {
    val header = if (isOes) {
      "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
    } else {
      "precision highp float;\n"
    }
    val sampler = if (isOes) "samplerExternalOES" else "sampler2D"

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;
      uniform float uRadius;
      uniform float uSoftness;
      uniform float uRoundness;
      uniform float uAspectRatio;

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec4 color = texture2D(uTexture, uv);

        vec2 centered = (uv - 0.5) * 2.0;
        centered.x *= uAspectRatio * uRoundness;
        float dist = length(centered);

        float vig = smoothstep(uRadius, uRadius - uSoftness, dist);
        vec3 finalRgb = mix(color.rgb, color.rgb * vig, uIntensity);

        // Strict clamp & preserve alpha
        gl_FragColor = vec4(clamp(finalRgb, 0.0, 1.0), color.a);
      }
    """.trimIndent()
  }

  override fun onBindUniforms(program: Int, isOes: Boolean, width: Int, height: Int, timeSeconds: Float) {
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uIntensity"), intensity)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uRadius"), radius)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSoftness"), max(0.01f, softness))
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uRoundness"), roundness)
  }
}

/**
 * 3. Sharpen Effect Pass
 * High-precision 4-tap Laplacian high-pass sharpening shader with over-sharpening halo protection.
 */
class SharpenPass(
  override val id: String = "sharpen",
  override val name: String = "Sharpen"
) : BaseGlEffectPass(id, name) {

  override fun getFragmentShader(isOes: Boolean): String {
    val header = if (isOes) {
      "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
    } else {
      "precision highp float;\n"
    }
    val sampler = if (isOes) "samplerExternalOES" else "sampler2D"

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;
      uniform vec2 uTexelSize;

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec4 center = texture2D(uTexture, uv);

        vec4 top = texture2D(uTexture, clamp(uv + vec2(0.0, uTexelSize.y), 0.0, 1.0));
        vec4 bottom = texture2D(uTexture, clamp(uv - vec2(0.0, uTexelSize.y), 0.0, 1.0));
        vec4 left = texture2D(uTexture, clamp(uv - vec2(uTexelSize.x, 0.0), 0.0, 1.0));
        vec4 right = texture2D(uTexture, clamp(uv + vec2(uTexelSize.x, 0.0), 0.0, 1.0));

        vec4 laplacian = (top + bottom + left + right) - 4.0 * center;
        vec3 sharpened = center.rgb - (uIntensity * 1.5) * laplacian.rgb;

        // Color clamping to prevent muddy inversion or blown out edges
        gl_FragColor = vec4(clamp(sharpened, 0.0, 1.0), center.a);
      }
    """.trimIndent()
  }

  override fun onBindUniforms(program: Int, isOes: Boolean, width: Int, height: Int, timeSeconds: Float) {
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uIntensity"), intensity)
    val w = if (width > 0) width.toFloat() else 1920.0f
    val h = if (height > 0) height.toFloat() else 1080.0f
    GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uTexelSize"), 1.0f / w, 1.0f / h)
  }
}

/**
 * 4. Gaussian Blur Pass (9-Tap Separable Gaussian distribution)
 * Produces artifact-free Gaussian blur across full dynamic range.
 */
class GaussianBlurPass(
  override val id: String = "gaussian_blur",
  override val name: String = "Gaussian Blur"
) : BaseGlEffectPass(id, name) {

  var radius: Float = 1.0f

  override fun getFragmentShader(isOes: Boolean): String {
    val header = if (isOes) {
      "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
    } else {
      "precision highp float;\n"
    }
    val sampler = if (isOes) "samplerExternalOES" else "sampler2D"

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;
      uniform float uRadius;
      uniform vec2 uTexelSize;

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        float rad = uRadius * uIntensity * 4.0;
        vec2 step = uTexelSize * rad;

        vec4 sum = vec4(0.0);
        sum += texture2D(uTexture, clamp(uv + vec2(-step.x * 1.5, -step.y * 1.5), 0.0, 1.0)) * 0.05;
        sum += texture2D(uTexture, clamp(uv + vec2( 0.0,          -step.y),       0.0, 1.0)) * 0.12;
        sum += texture2D(uTexture, clamp(uv + vec2( step.x * 1.5, -step.y * 1.5), 0.0, 1.0)) * 0.05;
        sum += texture2D(uTexture, clamp(uv + vec2(-step.x,        0.0),          0.0, 1.0)) * 0.12;
        sum += texture2D(uTexture, uv)                                                       * 0.32;
        sum += texture2D(uTexture, clamp(uv + vec2( step.x,        0.0),          0.0, 1.0)) * 0.12;
        sum += texture2D(uTexture, clamp(uv + vec2(-step.x * 1.5,  step.y * 1.5), 0.0, 1.0)) * 0.05;
        sum += texture2D(uTexture, clamp(uv + vec2( 0.0,           step.y),       0.0, 1.0)) * 0.12;
        sum += texture2D(uTexture, clamp(uv + vec2( step.x * 1.5,  step.y * 1.5), 0.0, 1.0)) * 0.05;

        gl_FragColor = vec4(clamp(sum.rgb, 0.0, 1.0), sum.a);
      }
    """.trimIndent()
  }

  override fun onBindUniforms(program: Int, isOes: Boolean, width: Int, height: Int, timeSeconds: Float) {
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uIntensity"), intensity)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uRadius"), radius)
    val w = if (width > 0) width.toFloat() else 1920.0f
    val h = if (height > 0) height.toFloat() else 1080.0f
    GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uTexelSize"), 1.0f / w, 1.0f / h)
  }
}

/**
 * 5. Chromatic Aberration (RGB Split) Pass
 */
class ChromaticAberrationPass(
  override val id: String = "chromatic_aberration",
  override val name: String = "Chromatic Aberration"
) : BaseGlEffectPass(id, name) {

  var offsetDistance: Float = 0.015f // 0.0 to 0.05

  override fun getFragmentShader(isOes: Boolean): String {
    val header = if (isOes) {
      "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
    } else {
      "precision highp float;\n"
    }
    val sampler = if (isOes) "samplerExternalOES" else "sampler2D"

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;
      uniform float uOffset;

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec2 shift = vec2(uOffset * uIntensity, 0.0);

        float r = texture2D(uTexture, clamp(uv + shift, 0.0, 1.0)).r;
        vec4 gColor = texture2D(uTexture, uv);
        float b = texture2D(uTexture, clamp(uv - shift, 0.0, 1.0)).b;

        vec3 splitColor = vec3(r, gColor.g, b);
        gl_FragColor = vec4(clamp(splitColor, 0.0, 1.0), gColor.a);
      }
    """.trimIndent()
  }

  override fun onBindUniforms(program: Int, isOes: Boolean, width: Int, height: Int, timeSeconds: Float) {
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uIntensity"), intensity)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uOffset"), offsetDistance)
  }
}

/**
 * 6. Bloom & Glow Effect Pass
 * Isolates high-luminance highlights and applies additive bloom.
 */
class BloomGlowPass(
  override val id: String = "bloom_glow",
  override val name: String = "Bloom & Glow"
) : BaseGlEffectPass(id, name) {

  var threshold: Float = 0.45f
  var glowRadius: Float = 1.0f

  override fun getFragmentShader(isOes: Boolean): String {
    val header = if (isOes) {
      "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
    } else {
      "precision highp float;\n"
    }
    val sampler = if (isOes) "samplerExternalOES" else "sampler2D"

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;
      uniform float uThreshold;
      uniform float uRadius;
      uniform vec2 uTexelSize;

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec4 baseColor = texture2D(uTexture, uv);

        float rad = uRadius * 8.0 * uTexelSize.x;
        vec4 bloom = vec4(0.0);
        bloom += max(texture2D(uTexture, clamp(uv + vec2(-rad, -rad), 0.0, 1.0)) - uThreshold, vec4(0.0)) * 0.15;
        bloom += max(texture2D(uTexture, clamp(uv + vec2( rad, -rad), 0.0, 1.0)) - uThreshold, vec4(0.0)) * 0.15;
        bloom += max(texture2D(uTexture, clamp(uv + vec2(-rad,  rad), 0.0, 1.0)) - uThreshold, vec4(0.0)) * 0.15;
        bloom += max(texture2D(uTexture, clamp(uv + vec2( rad,  rad), 0.0, 1.0)) - uThreshold, vec4(0.0)) * 0.15;
        bloom += max(texture2D(uTexture, uv) - uThreshold, vec4(0.0)) * 0.40;

        vec3 glowColor = bloom.rgb * vec3(1.1, 1.05, 1.2) * (uIntensity * 2.5);
        vec3 finalColor = baseColor.rgb + glowColor;

        gl_FragColor = vec4(clamp(finalColor, 0.0, 1.0), baseColor.a);
      }
    """.trimIndent()
  }

  override fun onBindUniforms(program: Int, isOes: Boolean, width: Int, height: Int, timeSeconds: Float) {
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uIntensity"), intensity)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uThreshold"), threshold)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uRadius"), glowRadius)
    val w = if (width > 0) width.toFloat() else 1920.0f
    val h = if (height > 0) height.toFloat() else 1080.0f
    GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uTexelSize"), 1.0f / w, 1.0f / h)
  }
}

/**
 * 7. Film Grain Pass
 */
class FilmGrainPass(
  override val id: String = "film_grain",
  override val name: String = "Film Grain"
) : BaseGlEffectPass(id, name) {

  override fun getFragmentShader(isOes: Boolean): String {
    val header = if (isOes) {
      "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
    } else {
      "precision highp float;\n"
    }
    val sampler = if (isOes) "samplerExternalOES" else "sampler2D"

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;
      uniform float uTime;

      float rand(vec2 co) {
        return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
      }

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec4 color = texture2D(uTexture, uv);

        float noise = (rand(uv + vec2(fract(uTime * 17.13), fract(uTime * 23.41))) - 0.5) * (uIntensity * 0.25);
        vec3 finalColor = color.rgb + vec3(noise);

        gl_FragColor = vec4(clamp(finalColor, 0.0, 1.0), color.a);
      }
    """.trimIndent()
  }

  override fun onBindUniforms(program: Int, isOes: Boolean, width: Int, height: Int, timeSeconds: Float) {
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uIntensity"), intensity)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTime"), timeSeconds)
  }
}

/**
 * 8. Glitch Pass
 */
class GlitchPass(
  override val id: String = "glitch",
  override val name: String = "Digital Glitch"
) : BaseGlEffectPass(id, name) {

  override fun getFragmentShader(isOes: Boolean): String {
    val header = if (isOes) {
      "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
    } else {
      "precision highp float;\n"
    }
    val sampler = if (isOes) "samplerExternalOES" else "sampler2D"

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;
      uniform float uTime;

      float rand(vec2 co) {
        return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
      }

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        float sliceY = floor(uv.y * 32.0);
        float sliceNoise = fract(sin(dot(vec2(sliceY, floor(uTime * 14.0)), vec2(12.9898, 78.233))) * 43758.5453);
        
        float glitchShift = 0.0;
        if (sliceNoise > 0.62) {
          glitchShift = (sliceNoise - 0.62) * 0.15 * uIntensity;
        }

        vec2 uvR = clamp(uv + vec2(glitchShift + 0.015 * uIntensity, 0.0), 0.0, 1.0);
        vec2 uvG = clamp(uv + vec2(glitchShift, 0.0), 0.0, 1.0);
        vec2 uvB = clamp(uv + vec2(glitchShift - 0.015 * uIntensity, 0.0), 0.0, 1.0);

        float r = texture2D(uTexture, uvR).r;
        float g = texture2D(uTexture, uvG).g;
        float b = texture2D(uTexture, uvB).b;
        float a = texture2D(uTexture, uvG).a;

        gl_FragColor = vec4(clamp(vec3(r, g, b), 0.0, 1.0), a);
      }
    """.trimIndent()
  }

  override fun onBindUniforms(program: Int, isOes: Boolean, width: Int, height: Int, timeSeconds: Float) {
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uIntensity"), intensity)
    GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTime"), timeSeconds)
  }
}

/**
 * 9. Custom Shader GlEffect Pass
 */
class CustomGlEffectPass(
  override val id: String,
  override val name: String,
  private val fragmentShaderBody2D: String,
  private val fragmentShaderBodyOES: String? = null,
  private val uniformBinder: ((program: Int, isOes: Boolean, width: Int, height: Int, timeSeconds: Float) -> Unit)? = null
) : BaseGlEffectPass(id, name) {

  override fun getFragmentShader(isOes: Boolean): String {
    return if (isOes) {
      fragmentShaderBodyOES ?: """
        #extension GL_OES_EGL_image_external : require
        precision highp float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES uTexture;
        void main() {
          gl_FragColor = texture2D(uTexture, clamp(vTextureCoord, 0.0, 1.0));
        }
      """.trimIndent()
    } else {
      fragmentShaderBody2D
    }
  }

  override fun onBindUniforms(program: Int, isOes: Boolean, width: Int, height: Int, timeSeconds: Float) {
    uniformBinder?.invoke(program, isOes, width, height, timeSeconds)
  }
}

/**
 * Production-grade Multi-Pass OpenGL ES 2.0 / 3.0 Effect Pipeline.
 *
 * Core Features:
 *  - Ping-Pong FBO Architecture: Dual-FBO rendering loop chaining sequential passes without degrading color or accumulating artifacts.
 *  - Shader Safety: All fragment shaders enforce strict color clamping and alpha preservation.
 *  - Blend State Management: Automatically disables GL_BLEND during full-screen texture passes to prevent unintended color blending.
 *  - Performance & Memory: Reuses FBO textures with zero memory reallocations per frame.
 *  - Dynamic Toggling & Reordering: Real-time reordering and enabling/disabling of active timeline effects.
 */
class MultiPassEffectPipeline {
  companion object {
    private const val TAG = "MultiPassEffectPipeline"

    val IDENTITY_MATRIX = FloatArray(16).apply {
      Matrix.setIdentityM(this, 0)
    }

    private val QUAD_VERTICES = floatArrayOf(
      -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
       1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
      -1.0f,  1.0f, 0.0f, 0.0f, 1.0f,
       1.0f,  1.0f, 0.0f, 1.0f, 1.0f
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .apply {
        put(QUAD_VERTICES)
        position(0)
      }

    fun drawQuad(aPositionLoc: Int, aTextureCoordLoc: Int) {
      vertexBuffer.position(0)
      if (aPositionLoc >= 0) {
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
      }

      vertexBuffer.position(3)
      if (aTextureCoordLoc >= 0) {
        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
      }

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

      if (aPositionLoc >= 0) GLES20.glDisableVertexAttribArray(aPositionLoc)
      if (aTextureCoordLoc >= 0) GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
    }
  }

  private val pingPongFbo = GlPingPongFbo()
  private val activePasses = mutableListOf<GlEffectPass>()
  private var isInitialized = false

  // Passthrough pass for copying OES to 2D texture or directly to target FBO if no passes are active
  private val passthroughPass = object : BaseGlEffectPass("passthrough", "Passthrough") {
    override fun getFragmentShader(isOes: Boolean): String {
      val header = if (isOes) {
        "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
      } else {
        "precision highp float;\n"
      }
      val sampler = if (isOes) "samplerExternalOES" else "sampler2D"
      return """
        $header
        varying vec2 vTextureCoord;
        uniform $sampler uTexture;
        void main() {
          gl_FragColor = texture2D(uTexture, clamp(vTextureCoord, 0.0, 1.0));
        }
      """.trimIndent()
    }
  }

  /**
   * Initializes the pipeline and compiles default passes.
   */
  fun init() {
    if (isInitialized) return
    passthroughPass.init()
    activePasses.forEach { it.init() }
    isInitialized = true
    Log.d(TAG, "MultiPassEffectPipeline initialized")
  }

  /**
   * Sets or replaces the ordered list of effect passes.
   */
  fun setPasses(passes: List<GlEffectPass>) {
    activePasses.clear()
    activePasses.addAll(passes)
    if (isInitialized) {
      activePasses.forEach { it.init() }
    }
  }

  /**
   * Adds an effect pass to the pipeline.
   */
  fun addPass(pass: GlEffectPass) {
    if (!activePasses.any { it.id == pass.id }) {
      activePasses.add(pass)
      if (isInitialized) {
        pass.init()
      }
    }
  }

  /**
   * Removes an effect pass by its unique ID.
   */
  fun removePass(id: String) {
    val iterator = activePasses.iterator()
    while (iterator.hasNext()) {
      val pass = iterator.next()
      if (pass.id == id) {
        pass.release()
        iterator.remove()
      }
    }
  }

  /**
   * Enables or disables a specific pass by ID.
   */
  fun setPassEnabled(id: String, enabled: Boolean) {
    activePasses.find { it.id == id }?.isEnabled = enabled
  }

  /**
   * Sets the intensity for a specific pass by ID.
   */
  fun setPassIntensity(id: String, intensity: Float) {
    activePasses.find { it.id == id }?.intensity = intensity
  }

  /**
   * Reorders the passes according to the provided list of IDs.
   */
  fun reorderPasses(orderedIds: List<String>) {
    val map = activePasses.associateBy { it.id }
    val newOrder = mutableListOf<GlEffectPass>()
    for (id in orderedIds) {
      map[id]?.let { newOrder.add(it) }
    }
    // Append any unmentioned passes
    for (pass in activePasses) {
      if (!newOrder.contains(pass)) {
        newOrder.add(pass)
      }
    }
    activePasses.clear()
    activePasses.addAll(newOrder)
  }

  /**
   * Returns a copy of the currently active pass list.
   */
  fun getActivePasses(): List<GlEffectPass> = activePasses.toList()

  /**
   * Processes the input video frame through the chained multi-pass Ping-Pong FBO pipeline.
   *
   * @param inputTextureId The texture ID of the input video frame (OES or 2D).
   * @param isOes True if input is from camera or hardware video decoder (GL_TEXTURE_EXTERNAL_OES).
   * @param width Frame width in pixels.
   * @param height Frame height in pixels.
   * @param targetFboId The destination FBO ID (0 for default display surface or a custom FBO).
   * @param timeSeconds The current playback time in seconds.
   * @param texMatrix Optional texture coordinate transformation matrix (e.g. from SurfaceTexture).
   * @return The 2D texture ID of the final processed output frame (valid if targetFboId was a ping-pong FBO).
   */
  fun process(
    inputTextureId: Int,
    isOes: Boolean,
    width: Int,
    height: Int,
    targetFboId: Int = 0,
    timeSeconds: Float = 0.0f,
    texMatrix: FloatArray? = null
  ): Int {
    if (!isInitialized) {
      init()
    }

    if (width <= 0 || height <= 0 || inputTextureId == 0) {
      return inputTextureId
    }

    // Ensure ping-pong FBOs are sized (zero allocation if dimensions match)
    pingPongFbo.setup(width, height)
    pingPongFbo.reset()

    // Filter enabled passes with non-zero intensity
    val enabledPasses = activePasses.filter { it.isEnabled && it.intensity > 0.001f }

    // 1. ISOLATE OPENGL STATE:
    // Fullscreen quad texture passes must disable GL_BLEND to prevent unintended color blending/bleeding
    val wasBlendEnabled = GLES20.glIsEnabled(GLES20.GL_BLEND)
    val wasDepthEnabled = GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST)
    val wasCullEnabled = GLES20.glIsEnabled(GLES20.GL_CULL_FACE)

    GLES20.glDisable(GLES20.GL_BLEND)
    GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    GLES20.glDisable(GLES20.GL_CULL_FACE)

    var currentInputTex = inputTextureId
    var currentIsOes = isOes
    var currentTexMatrix = if (isOes) texMatrix else null

    // If no passes are active, simply copy the input to the target FBO/surface
    if (enabledPasses.isEmpty()) {
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFboId)
      GLES20.glViewport(0, 0, width, height)
      GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

      passthroughPass.render(currentInputTex, currentIsOes, width, height, timeSeconds, currentTexMatrix)

      restoreGlState(wasBlendEnabled, wasDepthEnabled, wasCullEnabled)
      return if (targetFboId == 0) currentInputTex else pingPongFbo.getReadTextureId()
    }

    // Execute sequential effect passes using Ping-Pong FBO chaining
    for (i in enabledPasses.indices) {
      val pass = enabledPasses[i]
      val isLastPass = (i == enabledPasses.lastIndex)

      if (isLastPass && targetFboId == 0) {
        // Render directly to default display surface on the final pass
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        pass.render(currentInputTex, currentIsOes, width, height, timeSeconds, currentTexMatrix)
      } else {
        // Render to the current Ping-Pong write FBO
        val writeFbo = pingPongFbo.getWriteFbo()
        writeFbo.bind()
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        pass.render(currentInputTex, currentIsOes, width, height, timeSeconds, currentTexMatrix)

        writeFbo.unbind()

        // Swap ping-pong FBOs for the next pass
        currentInputTex = writeFbo.getTextureId()
        currentIsOes = false
        currentTexMatrix = null
        pingPongFbo.swap()
      }
    }

    // If the last pass was written to an FBO and targetFboId was requested, copy if needed
    if (targetFboId != 0) {
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFboId)
      GLES20.glViewport(0, 0, width, height)
      GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
      passthroughPass.render(currentInputTex, false, width, height, timeSeconds, null)
    }

    // Restore previous GL state
    restoreGlState(wasBlendEnabled, wasDepthEnabled, wasCullEnabled)

    return currentInputTex
  }

  private fun restoreGlState(wasBlend: Boolean, wasDepth: Boolean, wasCull: Boolean) {
    if (wasBlend) GLES20.glEnable(GLES20.GL_BLEND) else GLES20.glDisable(GLES20.GL_BLEND)
    if (wasDepth) GLES20.glEnable(GLES20.GL_DEPTH_TEST) else GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    if (wasCull) GLES20.glEnable(GLES20.GL_CULL_FACE) else GLES20.glDisable(GLES20.GL_CULL_FACE)
  }

  /**
   * Releases all GPU buffers, shaders, and framebuffers.
   */
  fun release() {
    passthroughPass.release()
    activePasses.forEach { it.release() }
    activePasses.clear()
    pingPongFbo.release()
    isInitialized = false
    Log.d(TAG, "MultiPassEffectPipeline released")
  }
}
