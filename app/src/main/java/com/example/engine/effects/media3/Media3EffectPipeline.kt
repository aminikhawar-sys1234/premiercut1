package com.example.engine.effects.media3

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import com.example.domain.model.FilterSettings
import com.example.domain.model.FilterType
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.domain.model.VideoAdjustments
import com.example.engine.composition.ColorFilterGenerator

/**
 * Supported ML-inspired and artistic style transfer effects running via hardware-accelerated OpenGL shaders.
 */
enum class StyleTransferType(val displayName: String) {
  NONE("None"),
  ANIME_CEL_SHADING("Anime Cel-Shading"),
  OIL_PAINTING_KUWAHARA("Oil Painting (Kuwahara)"),
  SKETCH_CHARCOAL("Pencil & Charcoal Sketch"),
  CYBERPUNK_NEON("Cyberpunk Neon Glow"),
  DREAMY_BLOOM("Dreamy Pastel Bloom"),
  HALFTONE_COMIC("Halftone Comic Book")
}

/**
 * High-level coordinator that integrates custom OpenGL-based shaders
 * (Color Grading, LUTs, and creative ML-style transfer shaders) into the AndroidX Media3 Effect pipeline.
 *
 * It generates an optimized sequence of [Effect] instances applied directly
 * to video frames in real-time during ExoPlayer preview and in Media3 export.
 */
@OptIn(UnstableApi::class)
object Media3EffectPipeline {

  /**
   * Applies the generated list of Media3 effects to ExoPlayer in real-time.
   */
  fun applyRealtimeEffects(exoPlayer: ExoPlayer, effects: List<Effect>) {
    try {
      exoPlayer.setVideoEffects(effects)
    } catch (e: Exception) {
      android.util.Log.w("Media3EffectPipeline", "Failed to apply real-time video effects to ExoPlayer", e)
    }
  }

  /**
   * Builds real-time playback effects for ExoPlayer live preview, combining
   * color grading (brightness, contrast, saturation, temperature, tint),
   * LUT filters, and ML style transfer shaders.
   */
  fun buildRealtimePreviewEffects(
    adjustments: VideoAdjustments = VideoAdjustments(),
    filterSettings: FilterSettings = FilterSettings(),
    styleTransfer: StyleTransferType = StyleTransferType.NONE,
    styleIntensity: Float = 1.0f,
    customLut: Bitmap? = null,
    customLutIntensity: Float = 1.0f
  ): List<Effect> {
    val effects = mutableListOf<Effect>()

    // 1. OpenGL Color Grading Shader (Brightness, Contrast, Saturation, Temp, Tint, Matrix Filter)
    val colorGrading = createColorGradingEffect(adjustments, filterSettings)
    if (colorGrading != null && !colorGrading.isNoOp(1920, 1080)) {
      effects.add(colorGrading)
    }

    // 2. Custom 3D / 2D LUT Shader
    if (customLut != null && customLutIntensity > 0.01f) {
      val lutEffect = LutGlEffect(
        lutBitmap = customLut,
        intensity = customLutIntensity
      )
      val media3Lut = lutEffect.toMedia3SingleColorLut()
      if (media3Lut != null) {
        effects.add(media3Lut)
      } else {
        effects.add(lutEffect)
      }
    }

    // 3. ML Style Transfer Effect
    val styleEffect = createStyleTransferEffect(styleTransfer, styleIntensity)
    if (styleEffect != null && !styleEffect.isNoOp(1920, 1080)) {
      effects.add(styleEffect)
    }

    return effects
  }

  /**
   * Constructs the full sequence of Media3 effects for an export timeline:
   * 1. Resolution / Aspect Ratio layout ([Presentation])
   * 2. Frame rate control ([FrameDropEffect])
   * 3. OpenGL Color Grading ([ColorGradingGlEffect])
   * 4. OpenGL 3D/2D LUT ([LutGlEffect]) if provided
   * 5. Active ML Style Transfer & custom shader effects
   */
  fun buildVideoEffects(
    context: Context,
    timeline: Timeline,
    exportWidth: Int,
    exportHeight: Int,
    targetFps: Float,
    customLut: Bitmap? = null,
    customLutIntensity: Float = 1.0f,
    styleTransfer: StyleTransferType = StyleTransferType.NONE,
    styleIntensity: Float = 1.0f
  ): List<Effect> {
    val effects = mutableListOf<Effect>()

    // 1. Presentation: Scale to fit target dimensions
    val presentation = Presentation.createForWidthAndHeight(
      exportWidth,
      exportHeight,
      Presentation.LAYOUT_SCALE_TO_FIT
    )
    effects.add(presentation)

    // 2. Framerate control
    val frameDrop = FrameDropEffect.createDefaultFrameDropEffect(targetFps)
    effects.add(frameDrop)

    // 3. OpenGL Color Grading Shader (Brightness, Contrast, Saturation, Temp, Tint, Matrix Filter)
    val colorGrading = createColorGradingEffect(timeline.adjustments, timeline.filter)
    if (colorGrading != null && !colorGrading.isNoOp(exportWidth, exportHeight)) {
      effects.add(colorGrading)
    }

    // 4. Custom 3D / 2D LUT Shader
    if (customLut != null && customLutIntensity > 0.01f) {
      val lutEffect = LutGlEffect(
        lutBitmap = customLut,
        intensity = customLutIntensity
      )
      val media3Lut = lutEffect.toMedia3SingleColorLut()
      if (media3Lut != null) {
        effects.add(media3Lut)
      } else {
        effects.add(lutEffect)
      }
    }

    // 5. ML Style Transfer
    val styleEffect = createStyleTransferEffect(styleTransfer, styleIntensity)
    if (styleEffect != null && !styleEffect.isNoOp(exportWidth, exportHeight)) {
      effects.add(styleEffect)
    }

    return effects
  }

  /**
   * Builds the effect sequence for an individual clip, combining clip-specific adjustments
   * and project-level timeline adjustments.
   */
  fun buildClipEffects(
    context: Context,
    clip: VideoClip,
    timeline: Timeline,
    exportWidth: Int,
    exportHeight: Int,
    targetFps: Float
  ): List<Effect> {
    val effects = mutableListOf<Effect>()

    // 1. Presentation
    effects.add(Presentation.createForWidthAndHeight(exportWidth, exportHeight, Presentation.LAYOUT_SCALE_TO_FIT))

    // 2. Framerate
    effects.add(FrameDropEffect.createDefaultFrameDropEffect(targetFps))

    // 3. Combined Color Grading: Clip filter or Timeline filter + adjustments
    val effectiveFilter = clip.filter ?: timeline.filter
    val colorGrading = createColorGradingEffect(timeline.adjustments, effectiveFilter)
    if (colorGrading != null && !colorGrading.isNoOp(exportWidth, exportHeight)) {
      effects.add(colorGrading)
    }

    return effects
  }

  /**
   * Creates an OpenGL [ColorGradingGlEffect] from adjustments and filter settings.
   */
  fun createColorGradingEffect(
    adjustments: VideoAdjustments,
    filterSettings: FilterSettings
  ): ColorGradingGlEffect? {
    val effect = ColorGradingGlEffect.fromTimeline(adjustments, filterSettings)
    return if (effect.isNoOp(1920, 1080)) null else effect
  }

  /**
   * Creates an OpenGL [LutGlEffect] from a 3D/2D LUT Bitmap.
   */
  fun createLutEffect(
    lutBitmap: Bitmap,
    intensity: Float = 1.0f
  ): LutGlEffect {
    return LutGlEffect(lutBitmap = lutBitmap, intensity = intensity)
  }

  /**
   * Creates an ML-inspired or artistic Style Transfer OpenGL effect.
   */
  fun createStyleTransferEffect(
    type: StyleTransferType,
    intensity: Float = 1.0f
  ): CustomShaderGlEffect? {
    if (type == StyleTransferType.NONE || intensity <= 0.01f) return null

    return when (type) {
      StyleTransferType.NONE -> null

      StyleTransferType.ANIME_CEL_SHADING -> {
        val shader = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uIntensity;
uniform vec2 uTexelSize;
varying vec2 vTexSamplingCoord;

void main() {
  vec2 uv = clamp(vTexSamplingCoord, 0.0, 1.0);
  vec4 color = texture2D(uTexSampler, uv);
  
  // Sobel luminance edge detection
  vec2 step = uTexelSize * 1.5;
  float t = dot(texture2D(uTexSampler, uv + vec2(0.0, step.y)).rgb, vec3(0.299, 0.587, 0.114));
  float b = dot(texture2D(uTexSampler, uv - vec2(0.0, step.y)).rgb, vec3(0.299, 0.587, 0.114));
  float l = dot(texture2D(uTexSampler, uv - vec2(step.x, 0.0)).rgb, vec3(0.299, 0.587, 0.114));
  float r = dot(texture2D(uTexSampler, uv + vec2(step.x, 0.0)).rgb, vec3(0.299, 0.587, 0.114));
  
  float edge = length(vec2(r - l, t - b)) * 4.0;
  edge = clamp(edge, 0.0, 1.0);
  
  // Posterization (quantized color quantization)
  vec3 posterized = floor(color.rgb * 6.0 + 0.5) / 6.0;
  // Vibrance boost
  float lum = dot(posterized, vec3(0.299, 0.587, 0.114));
  vec3 vibrant = mix(vec3(lum), posterized, 1.35);
  
  // Apply ink outline
  vec3 celColor = vibrant * (1.0 - edge * 0.9);
  gl_FragColor = vec4(mix(color.rgb, celColor, uIntensity), color.a);
}
"""
        CustomShaderGlEffect(
          name = "AnimeCelShadingEffect",
          fragmentShader = shader,
          uniformBinder = { program: GlProgram, _, width: Int, height: Int ->
            program.setFloatUniform("uIntensity", intensity)
            val w = if (width > 0) width.toFloat() else 1920f
            val h = if (height > 0) height.toFloat() else 1080f
            program.setFloatsUniform("uTexelSize", floatArrayOf(1.0f / w, 1.0f / h))
          }
        )
      }

      StyleTransferType.OIL_PAINTING_KUWAHARA -> {
        val shader = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uIntensity;
uniform vec2 uTexelSize;
varying vec2 vTexSamplingCoord;

void main() {
  vec2 uv = clamp(vTexSamplingCoord, 0.0, 1.0);
  vec4 original = texture2D(uTexSampler, uv);
  vec2 step = uTexelSize * 2.0;

  // Kuwahara multi-quadrant mean and variance approximation
  vec3 m0 = vec3(0.0); vec3 m1 = vec3(0.0); vec3 m2 = vec3(0.0); vec3 m3 = vec3(0.0);
  vec3 s0 = vec3(0.0); vec3 s1 = vec3(0.0); vec3 s2 = vec3(0.0); vec3 s3 = vec3(0.0);

  // Sector 0 (Top-Left)
  for (int j = -2; j <= 0; ++j) {
    for (int i = -2; i <= 0; ++i) {
      vec3 c = texture2D(uTexSampler, clamp(uv + vec2(float(i), float(j)) * step, 0.0, 1.0)).rgb;
      m0 += c; s0 += c * c;
    }
  }
  // Sector 1 (Top-Right)
  for (int j = -2; j <= 0; ++j) {
    for (int i = 0; i <= 2; ++i) {
      vec3 c = texture2D(uTexSampler, clamp(uv + vec2(float(i), float(j)) * step, 0.0, 1.0)).rgb;
      m1 += c; s1 += c * c;
    }
  }
  // Sector 2 (Bottom-Left)
  for (int j = 0; j <= 2; ++j) {
    for (int i = -2; i <= 0; ++i) {
      vec3 c = texture2D(uTexSampler, clamp(uv + vec2(float(i), float(j)) * step, 0.0, 1.0)).rgb;
      m2 += c; s2 += c * c;
    }
  }
  // Sector 3 (Bottom-Right)
  for (int j = 0; j <= 2; ++j) {
    for (int i = 0; i <= 2; ++i) {
      vec3 c = texture2D(uTexSampler, clamp(uv + vec2(float(i), float(j)) * step, 0.0, 1.0)).rgb;
      m3 += c; s3 += c * c;
    }
  }

  m0 /= 9.0; s0 = abs(s0 / 9.0 - m0 * m0);
  m1 /= 9.0; s1 = abs(s1 / 9.0 - m1 * m1);
  m2 /= 9.0; s2 = abs(s2 / 9.0 - m2 * m2);
  m3 /= 9.0; s3 = abs(s3 / 9.0 - m3 * m3);

  float v0 = s0.r + s0.g + s0.b;
  float v1 = s1.r + s1.g + s1.b;
  float v2 = s2.r + s2.g + s2.b;
  float v3 = s3.r + s3.g + s3.b;

  float minV = v0; vec3 chosenM = m0;
  if (v1 < minV) { minV = v1; chosenM = m1; }
  if (v2 < minV) { minV = v2; chosenM = m2; }
  if (v3 < minV) { minV = v3; chosenM = m3; }

  gl_FragColor = vec4(mix(original.rgb, chosenM, uIntensity), original.a);
}
"""
        CustomShaderGlEffect(
          name = "OilPaintingKuwaharaEffect",
          fragmentShader = shader,
          uniformBinder = { program: GlProgram, _, width: Int, height: Int ->
            program.setFloatUniform("uIntensity", intensity)
            val w = if (width > 0) width.toFloat() else 1920f
            val h = if (height > 0) height.toFloat() else 1080f
            program.setFloatsUniform("uTexelSize", floatArrayOf(1.0f / w, 1.0f / h))
          }
        )
      }

      StyleTransferType.SKETCH_CHARCOAL -> {
        val shader = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uIntensity;
uniform vec2 uTexelSize;
varying vec2 vTexSamplingCoord;

void main() {
  vec2 uv = clamp(vTexSamplingCoord, 0.0, 1.0);
  vec4 original = texture2D(uTexSampler, uv);
  vec2 step = uTexelSize * 1.5;

  float center = dot(original.rgb, vec3(0.299, 0.587, 0.114));
  float up = dot(texture2D(uTexSampler, uv + vec2(0.0, step.y)).rgb, vec3(0.299, 0.587, 0.114));
  float left = dot(texture2D(uTexSampler, uv - vec2(step.x, 0.0)).rgb, vec3(0.299, 0.587, 0.114));
  
  float diff = abs(center - up) + abs(center - left);
  float sketch = 1.0 - clamp(diff * 6.5, 0.0, 1.0);
  sketch = pow(sketch, 1.8);
  
  vec3 sketchColor = vec3(sketch * 0.95 + 0.05);
  gl_FragColor = vec4(mix(original.rgb, sketchColor, uIntensity), original.a);
}
"""
        CustomShaderGlEffect(
          name = "SketchCharcoalEffect",
          fragmentShader = shader,
          uniformBinder = { program: GlProgram, _, width: Int, height: Int ->
            program.setFloatUniform("uIntensity", intensity)
            val w = if (width > 0) width.toFloat() else 1920f
            val h = if (height > 0) height.toFloat() else 1080f
            program.setFloatsUniform("uTexelSize", floatArrayOf(1.0f / w, 1.0f / h))
          }
        )
      }

      StyleTransferType.CYBERPUNK_NEON -> {
        val shader = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uIntensity;
uniform vec2 uTexelSize;
varying vec2 vTexSamplingCoord;

void main() {
  vec2 uv = clamp(vTexSamplingCoord, 0.0, 1.0);
  vec4 original = texture2D(uTexSampler, uv);
  vec2 step = uTexelSize * 2.0;

  float t = dot(texture2D(uTexSampler, uv + vec2(0.0, step.y)).rgb, vec3(0.299, 0.587, 0.114));
  float b = dot(texture2D(uTexSampler, uv - vec2(0.0, step.y)).rgb, vec3(0.299, 0.587, 0.114));
  float l = dot(texture2D(uTexSampler, uv - vec2(step.x, 0.0)).rgb, vec3(0.299, 0.587, 0.114));
  float r = dot(texture2D(uTexSampler, uv + vec2(step.x, 0.0)).rgb, vec3(0.299, 0.587, 0.114));
  
  float edge = clamp(length(vec2(r - l, t - b)) * 5.0, 0.0, 1.0);
  
  // Neon Palette (Cyan & Magenta)
  vec3 darkBase = original.rgb * 0.45;
  vec3 neonCyan = vec3(0.0, 0.95, 1.0) * edge * 1.5;
  vec3 neonMagenta = vec3(1.0, 0.05, 0.75) * (1.0 - edge) * (original.r * 0.8);
  vec3 cyberColor = darkBase + neonCyan + neonMagenta;

  gl_FragColor = vec4(mix(original.rgb, cyberColor, uIntensity), original.a);
}
"""
        CustomShaderGlEffect(
          name = "CyberpunkNeonEffect",
          fragmentShader = shader,
          uniformBinder = { program: GlProgram, _, width: Int, height: Int ->
            program.setFloatUniform("uIntensity", intensity)
            val w = if (width > 0) width.toFloat() else 1920f
            val h = if (height > 0) height.toFloat() else 1080f
            program.setFloatsUniform("uTexelSize", floatArrayOf(1.0f / w, 1.0f / h))
          }
        )
      }

      StyleTransferType.DREAMY_BLOOM -> {
        val shader = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uIntensity;
uniform vec2 uTexelSize;
varying vec2 vTexSamplingCoord;

void main() {
  vec2 uv = clamp(vTexSamplingCoord, 0.0, 1.0);
  vec4 original = texture2D(uTexSampler, uv);
  vec2 step = uTexelSize * 3.5;

  vec4 blur = vec4(0.0);
  blur += texture2D(uTexSampler, uv + vec2(-step.x, -step.y)) * 0.15;
  blur += texture2D(uTexSampler, uv + vec2( step.x, -step.y)) * 0.15;
  blur += texture2D(uTexSampler, uv + vec2(-step.x,  step.y)) * 0.15;
  blur += texture2D(uTexSampler, uv + vec2( step.x,  step.y)) * 0.15;
  blur += texture2D(uTexSampler, uv) * 0.40;

  // Additive soft bloom highlight
  vec3 bloom = max(blur.rgb - 0.35, 0.0) * 1.6;
  vec3 result = original.rgb + bloom * uIntensity;
  result = mix(result, result * vec3(1.05, 0.98, 1.02), 0.5); // Pastel warmth

  gl_FragColor = vec4(clamp(result, 0.0, 1.0), original.a);
}
"""
        CustomShaderGlEffect(
          name = "DreamyBloomEffect",
          fragmentShader = shader,
          uniformBinder = { program: GlProgram, _, width: Int, height: Int ->
            program.setFloatUniform("uIntensity", intensity)
            val w = if (width > 0) width.toFloat() else 1920f
            val h = if (height > 0) height.toFloat() else 1080f
            program.setFloatsUniform("uTexelSize", floatArrayOf(1.0f / w, 1.0f / h))
          }
        )
      }

      StyleTransferType.HALFTONE_COMIC -> {
        val shader = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uIntensity;
varying vec2 vTexSamplingCoord;

void main() {
  vec2 uv = clamp(vTexSamplingCoord, 0.0, 1.0);
  vec4 color = texture2D(uTexSampler, uv);
  
  // Halftone dot pattern
  vec2 st = uv * vec2(120.0, 68.0);
  vec2 grid = fract(st) - 0.5;
  float dist = length(grid);
  
  float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));
  float radius = sqrt(1.0 - lum) * 0.55;
  float dotMask = step(dist, radius);
  
  vec3 halftone = mix(color.rgb * 1.25, vec3(0.08, 0.08, 0.12), dotMask * 0.85);
  gl_FragColor = vec4(mix(color.rgb, halftone, uIntensity), color.a);
}
"""
        CustomShaderGlEffect(
          name = "HalftoneComicEffect",
          fragmentShader = shader,
          uniformBinder = { program: GlProgram, _, _, _ ->
            program.setFloatUniform("uIntensity", intensity)
          }
        )
      }
    }
  }

  /**
   * Creates a custom RGB Split (chromatic aberration) shader effect using [CustomShaderGlEffect].
   */
  fun createRgbSplitEffect(intensity: Float = 0.5f): CustomShaderGlEffect {
    val fragmentShader = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uIntensity;
varying vec2 vTexSamplingCoord;

void main() {
  vec2 uv = clamp(vTexSamplingCoord, 0.0, 1.0);
  vec2 offset = vec2(uIntensity * 0.02, 0.0);
  float r = texture2D(uTexSampler, clamp(uv + offset, 0.0, 1.0)).r;
  float g = texture2D(uTexSampler, uv).g;
  float b = texture2D(uTexSampler, clamp(uv - offset, 0.0, 1.0)).b;
  float a = texture2D(uTexSampler, uv).a;
  gl_FragColor = vec4(r, g, b, a);
}
"""
    return CustomShaderGlEffect(
      name = "RgbSplitEffect",
      fragmentShader = fragmentShader,
      uniformBinder = { program: GlProgram, _, _, _ ->
        program.setFloatUniform("uIntensity", intensity)
      }
    )
  }

  /**
   * Creates a custom Glitch shader effect using [CustomShaderGlEffect].
   */
  fun createGlitchEffect(intensity: Float = 0.5f): CustomShaderGlEffect {
    val fragmentShader = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uIntensity;
uniform float uTime;
varying vec2 vTexSamplingCoord;

float rand(vec2 co) {
  return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
  vec2 uv = clamp(vTexSamplingCoord, 0.0, 1.0);
  float sliceY = floor(uv.y * 32.0);
  float sliceNoise = fract(sin(dot(vec2(sliceY, floor(uTime * 14.0)), vec2(12.9898, 78.233))) * 43758.5453);
  float glitchShift = 0.0;
  if (sliceNoise > 0.62) {
    glitchShift = (sliceNoise - 0.62) * 0.18 * uIntensity;
  }
  vec2 uvR = clamp(uv + vec2(glitchShift + 0.015 * uIntensity, 0.0), 0.0, 1.0);
  vec2 uvG = clamp(uv + vec2(glitchShift, 0.0), 0.0, 1.0);
  vec2 uvB = clamp(uv + vec2(glitchShift - 0.015 * uIntensity, 0.0), 0.0, 1.0);
  float r = texture2D(uTexSampler, uvR).r;
  float g = texture2D(uTexSampler, uvG).g;
  float b = texture2D(uTexSampler, uvB).b;
  float a = texture2D(uTexSampler, uvG).a;
  gl_FragColor = vec4(r, g, b, a);
}
"""
    return CustomShaderGlEffect(
      name = "GlitchEffect",
      fragmentShader = fragmentShader,
      uniformBinder = { program: GlProgram, presentationTimeUs: Long, _, _ ->
        program.setFloatUniform("uIntensity", intensity)
        program.setFloatUniform("uTime", (presentationTimeUs / 1_000_000.0).toFloat())
      }
    )
  }
}

