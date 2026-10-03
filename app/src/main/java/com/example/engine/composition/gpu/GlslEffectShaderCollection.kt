package com.example.engine.composition.gpu

/**
 * Production-grade collection of GLSL Fragment Shaders for video effect processing.
 *
 * Every fragment shader in this collection enforces:
 *  1. Strict color clamping via `clamp(rgb, 0.0, 1.0)` to eliminate blown-out highlights,
 *     underflow artifacts, negative colors, and muddy inversions.
 *  2. Alpha channel transparency preservation (`color.a`).
 *  3. UV coordinate clamping `clamp(uv, 0.0, 1.0)` to prevent out-of-bounds sampling.
 *  4. Dual sampler support: standard `sampler2D` and Android hardware `samplerExternalOES`.
 */
object GlslEffectShaderCollection {

  /**
   * Generates standard precision & extension header for fragment shaders.
   */
  fun getHeader(isOes: Boolean): String {
    return if (isOes) {
      "#extension GL_OES_EGL_image_external : require\nprecision mediump float;\n"
    } else {
      "precision mediump float;\n"
    }
  }

  fun getSamplerType(isOes: Boolean): String {
    return if (isOes) "samplerExternalOES" else "sampler2D"
  }

  // =========================================================================
  // 1. COLOR GRADING SHADER
  // =========================================================================
  fun buildColorGradingShader(isOes: Boolean = false): String {
    val header = getHeader(isOes)
    val sampler = getSamplerType(isOes)

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;     // [0.0, 1.0] Master blend intensity
      uniform float uBrightness;    // [-1.0, 1.0] Brightness offset
      uniform float uContrast;      // [0.0, 2.0] Contrast multiplier (1.0 = neutral)
      uniform float uSaturation;    // [0.0, 2.0] Saturation multiplier (1.0 = neutral)
      uniform float uExposure;      // [-2.0, 2.0] Exposure EV stops
      uniform float uTemperature;   // [-1.0, 1.0] Warm (+R/-B) vs Cool (-R/+B)
      uniform float uTint;          // [-1.0, 1.0] Green (-G) vs Magenta (+R/+B)
      uniform float uHighlights;    // [-1.0, 1.0] Highlight lift/compress
      uniform float uShadows;       // [-1.0, 1.0] Shadow lift/compress
      uniform float uGamma;         // [0.2, 3.0] Gamma exponent (1.0 = neutral)

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec4 original = texture2D(uTexture, uv);
        vec3 color = original.rgb;

        // 1. Exposure (2^EV) & Brightness
        color = color * pow(2.0, uExposure * uIntensity) + vec3(uBrightness * uIntensity);

        // 2. Contrast around mid-gray (0.5)
        float effContrast = mix(1.0, uContrast, uIntensity);
        color = (color - 0.5) * effContrast + 0.5;

        // 3. Saturation using Rec.709 Luma coefficients
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        float effSat = mix(1.0, uSaturation, uIntensity);
        color = mix(vec3(luma), color, effSat);

        // 4. Color Temperature and Tint (White Balance)
        color.r += uTemperature * 0.15 * uIntensity;
        color.b -= uTemperature * 0.15 * uIntensity;
        color.g -= uTint * 0.12 * uIntensity;
        color.r += uTint * 0.08 * uIntensity;
        color.b += uTint * 0.08 * uIntensity;

        // 5. Highlights & Shadows adjustment
        float l = dot(color, vec3(0.299, 0.587, 0.114));
        float shadowMask = 1.0 - smoothstep(0.0, 0.5, l);
        float highlightMask = smoothstep(0.5, 1.0, l);
        color += uShadows * shadowMask * 0.25 * uIntensity;
        color += uHighlights * highlightMask * 0.25 * uIntensity;

        // 6. Gamma correction
        if (abs(uGamma - 1.0) > 0.01) {
          float effGamma = mix(1.0, max(0.1, uGamma), uIntensity);
          color = pow(max(color, vec3(0.0)), vec3(1.0 / effGamma));
        }

        // Strict color safety clamping to [0.0, 1.0] range and preserving alpha
        vec3 finalRgb = clamp(mix(original.rgb, color, uIntensity), 0.0, 1.0);
        gl_FragColor = vec4(finalRgb, original.a);
      }
    """.trimIndent()
  }

  // =========================================================================
  // 2. VIGNETTE SHADER (Smoothstep Radial & Oval falloff)
  // =========================================================================
  fun buildVignetteShader(isOes: Boolean = false): String {
    val header = getHeader(isOes)
    val sampler = getSamplerType(isOes)

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;     // [0.0, 1.0] Vignette darkness / opacity
      uniform float uRadius;        // [0.1, 1.5] Outer radius start
      uniform float uSoftness;      // [0.01, 1.0] Feather transition band
      uniform float uRoundness;     // [0.5, 2.0] Aspect-ratio stretch
      uniform vec2 uCenter;         // [0.0, 1.0] Center coordinate (default 0.5, 0.5)

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec4 color = texture2D(uTexture, uv);

        vec2 centered = (uv - uCenter) * 2.0;
        centered.x *= uRoundness;
        float dist = length(centered);

        float vig = smoothstep(uRadius, uRadius - max(0.01, uSoftness), dist);
        vec3 dimmed = color.rgb * vig;
        vec3 finalRgb = mix(color.rgb, dimmed, uIntensity);

        // Strict clamp & preserve alpha
        gl_FragColor = vec4(clamp(finalRgb, 0.0, 1.0), color.a);
      }
    """.trimIndent()
  }

  // =========================================================================
  // 3. GAUSSIAN BLUR SHADER (9-Tap High-Quality Separable Kernel)
  // =========================================================================
  fun buildGaussianBlurShader(isOes: Boolean = false): String {
    val header = getHeader(isOes)
    val sampler = getSamplerType(isOes)

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;     // [0.0, 1.0] Blur strength
      uniform float uRadius;        // Blur kernel radius multiplier
      uniform vec2 uTexelSize;      // 1.0 / vec2(width, height)

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        float rad = max(0.0, uRadius * uIntensity * 4.0);
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

        // Strict clamp & preserve alpha
        gl_FragColor = vec4(clamp(sum.rgb, 0.0, 1.0), sum.a);
      }
    """.trimIndent()
  }

  // =========================================================================
  // 4. DIRECTIONAL / MOTION BLUR SHADER
  // =========================================================================
  fun buildDirectionalBlurShader(isOes: Boolean = false): String {
    val header = getHeader(isOes)
    val sampler = getSamplerType(isOes)

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;     // [0.0, 1.0] Blur amount
      uniform vec2 uDirection;      // Normalized direction vector (e.g. vec2(1.0, 0.0))
      uniform vec2 uTexelSize;      // 1.0 / vec2(width, height)

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec2 step = normalize(uDirection) * uTexelSize * (uIntensity * 12.0);

        vec4 sum = vec4(0.0);
        sum += texture2D(uTexture, clamp(uv - step * 3.0, 0.0, 1.0)) * 0.09;
        sum += texture2D(uTexture, clamp(uv - step * 2.0, 0.0, 1.0)) * 0.12;
        sum += texture2D(uTexture, clamp(uv - step * 1.0, 0.0, 1.0)) * 0.15;
        sum += texture2D(uTexture, uv)                               * 0.28;
        sum += texture2D(uTexture, clamp(uv + step * 1.0, 0.0, 1.0)) * 0.15;
        sum += texture2D(uTexture, clamp(uv + step * 2.0, 0.0, 1.0)) * 0.12;
        sum += texture2D(uTexture, clamp(uv + step * 3.0, 0.0, 1.0)) * 0.09;

        gl_FragColor = vec4(clamp(sum.rgb, 0.0, 1.0), sum.a);
      }
    """.trimIndent()
  }

  // =========================================================================
  // 5. SHARPEN / UNSHARP MASK SHADER (Laplacian 4-tap High-Pass)
  // =========================================================================
  fun buildSharpenShader(isOes: Boolean = false): String {
    val header = getHeader(isOes)
    val sampler = getSamplerType(isOes)

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;     // [0.0, 2.0] Sharpness intensity
      uniform vec2 uTexelSize;      // 1.0 / vec2(width, height)

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec4 center = texture2D(uTexture, uv);

        vec4 top = texture2D(uTexture, clamp(uv + vec2(0.0, uTexelSize.y), 0.0, 1.0));
        vec4 bottom = texture2D(uTexture, clamp(uv - vec2(0.0, uTexelSize.y), 0.0, 1.0));
        vec4 left = texture2D(uTexture, clamp(uv - vec2(uTexelSize.x, 0.0), 0.0, 1.0));
        vec4 right = texture2D(uTexture, clamp(uv + vec2(uTexelSize.x, 0.0), 0.0, 1.0));

        // Laplacian 4-tap edge calculation
        vec4 laplacian = (top + bottom + left + right) - 4.0 * center;
        vec3 sharpened = center.rgb - (uIntensity * 1.5) * laplacian.rgb;

        // Strict clamp to prevent high-frequency edge ringing / dark inversion
        gl_FragColor = vec4(clamp(sharpened, 0.0, 1.0), center.a);
      }
    """.trimIndent()
  }

  // =========================================================================
  // 6. BLOOM & LUMINOUS GLOW SHADER
  // =========================================================================
  fun buildBloomGlowShader(isOes: Boolean = false): String {
    val header = getHeader(isOes)
    val sampler = getSamplerType(isOes)

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;     // [0.0, 1.0] Bloom intensity
      uniform float uThreshold;     // [0.0, 1.0] Highlight extraction cutoff
      uniform float uRadius;        // Glow spread radius
      uniform vec2 uTexelSize;

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec4 base = texture2D(uTexture, uv);

        float rad = uRadius * 8.0 * uTexelSize.x;
        vec4 bloom = vec4(0.0);
        bloom += max(texture2D(uTexture, clamp(uv + vec2(-rad, -rad), 0.0, 1.0)) - uThreshold, vec4(0.0)) * 0.15;
        bloom += max(texture2D(uTexture, clamp(uv + vec2( rad, -rad), 0.0, 1.0)) - uThreshold, vec4(0.0)) * 0.15;
        bloom += max(texture2D(uTexture, clamp(uv + vec2(-rad,  rad), 0.0, 1.0)) - uThreshold, vec4(0.0)) * 0.15;
        bloom += max(texture2D(uTexture, clamp(uv + vec2( rad,  rad), 0.0, 1.0)) - uThreshold, vec4(0.0)) * 0.15;
        bloom += max(texture2D(uTexture, uv) - uThreshold, vec4(0.0)) * 0.40;

        vec3 glow = bloom.rgb * vec3(1.15, 1.05, 1.25) * (uIntensity * 2.5);
        vec3 finalColor = base.rgb + glow;

        gl_FragColor = vec4(clamp(finalColor, 0.0, 1.0), base.a);
      }
    """.trimIndent()
  }

  // =========================================================================
  // 7. CHROMATIC ABERRATION (RGB Dispersion) SHADER
  // =========================================================================
  fun buildChromaticAberrationShader(isOes: Boolean = false): String {
    val header = getHeader(isOes)
    val sampler = getSamplerType(isOes)

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;     // Master blend factor
      uniform float uOffset;        // Dispersal distance [0.0, 0.05]

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec2 shift = vec2(uOffset * uIntensity, 0.0);

        float r = texture2D(uTexture, clamp(uv + shift, 0.0, 1.0)).r;
        vec4 gColor = texture2D(uTexture, uv);
        float b = texture2D(uTexture, clamp(uv - shift, 0.0, 1.0)).b;

        vec3 split = vec3(r, gColor.g, b);
        gl_FragColor = vec4(clamp(split, 0.0, 1.0), gColor.a);
      }
    """.trimIndent()
  }

  // =========================================================================
  // 8. TILT-SHIFT / DEPTH-OF-FIELD SELECTIVE FOCUS SHADER
  // =========================================================================
  fun buildTiltShiftShader(isOes: Boolean = false): String {
    val header = getHeader(isOes)
    val sampler = getSamplerType(isOes)

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;     // [0.0, 1.0] Maximum blur strength
      uniform float uFocusCenter;   // [0.0, 1.0] Vertical center of focused band (default 0.5)
      uniform float uFocusWidth;    // [0.05, 0.5] Sharp focal band width
      uniform vec2 uTexelSize;

      void main() {
        vec2 uv = clamp(vTextureCoord, 0.0, 1.0);
        vec4 base = texture2D(uTexture, uv);

        // Calculate distance from horizontal focus line
        float distFromFocus = abs(uv.y - uFocusCenter);
        float blurFactor = smoothstep(uFocusWidth * 0.5, uFocusWidth * 1.5, distFromFocus) * uIntensity;

        if (blurFactor < 0.01) {
          gl_FragColor = vec4(clamp(base.rgb, 0.0, 1.0), base.a);
          return;
        }

        vec2 step = uTexelSize * (blurFactor * 8.0);
        vec4 sum = vec4(0.0);
        sum += texture2D(uTexture, clamp(uv + vec2(-step.x, -step.y), 0.0, 1.0)) * 0.15;
        sum += texture2D(uTexture, clamp(uv + vec2( step.x, -step.y), 0.0, 1.0)) * 0.15;
        sum += texture2D(uTexture, clamp(uv + vec2(-step.x,  step.y), 0.0, 1.0)) * 0.15;
        sum += texture2D(uTexture, clamp(uv + vec2( step.x,  step.y), 0.0, 1.0)) * 0.15;
        sum += base * 0.40;

        vec3 result = mix(base.rgb, sum.rgb, blurFactor);
        gl_FragColor = vec4(clamp(result, 0.0, 1.0), base.a);
      }
    """.trimIndent()
  }

  // =========================================================================
  // 9. FILM GRAIN SHADER
  // =========================================================================
  fun buildFilmGrainShader(isOes: Boolean = false): String {
    val header = getHeader(isOes)
    val sampler = getSamplerType(isOes)

    return """
      $header
      varying vec2 vTextureCoord;
      uniform $sampler uTexture;
      uniform float uIntensity;     // [0.0, 1.0] Grain intensity
      uniform float uTime;          // Animation seed

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
}
