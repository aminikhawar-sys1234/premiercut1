package com.example.engine.composition.gpu

/**
 * GLSL Shaders for GPU composition:
 * - Vertex transforms (scaling, rotation, translation, crop)
 * - Color adjustments (brightness, contrast, saturation, exposure, temp, tint, highlights, shadows, vignette, grain, sharpness)
 * - Chroma key in fragment shader
 * - GPU Filter presets via color matrix
 * - Multi-effect transitions
 */
object GpuShaders {

  const val VERTEX_SHADER = """
    uniform mat4 uMVPMatrix;
    uniform mat4 uTexMatrix;
    attribute vec4 aPosition;
    attribute vec4 aTextureCoord;
    varying vec2 vTextureCoord;
    
    void main() {
      gl_Position = uMVPMatrix * aPosition;
      vTextureCoord = (uTexMatrix * aTextureCoord).xy;
    }
  """

  fun buildFragmentShader(isOes: Boolean): String {
    val extensionHeader = if (isOes) {
      "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
    } else {
      "precision highp float;\n"
    }
    val samplerType = if (isOes) "samplerExternalOES" else "sampler2D"

    return """
      $extensionHeader
      varying vec2 vTextureCoord;
      uniform $samplerType uTexture;
      
      // Opacity
      uniform float uOpacity;
      
      // Keyframe Blur & Effect
      uniform float uBlur;
      uniform float uEffectParam;
      
      // Color Adjustments
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
      uniform float uSharpness;
      uniform vec2 uTexelSize;
      
      // Filter Color Matrix (4x4) + Offset (vec4)
      uniform mat4 uColorMatrix;
      uniform vec4 uColorOffset;
      uniform int uUseColorMatrix;
      
      // Chroma Key
      uniform int uChromaEnabled;
      uniform vec3 uChromaKeyColor;
      #define uKeyColor uChromaKeyColor
      uniform float uChromaSimilarity;
      uniform float uChromaSmoothness;
      uniform float uChromaSpill;
      uniform float uChromaEdge;
      uniform int uChromaBgType; // 0=Transparent, 1=SolidColor
      uniform vec4 uChromaBgColor;
      
      // Transitions
      uniform int uTransitionType;
      uniform float uTransitionProgress;
      
      // Blend Mode (0=Normal, 1=Multiply, 2=Screen, 3=Overlay, 4=Darken, 5=Lighten, 6=Color Dodge, 7=Soft Light, 8=Hard Light, 9=Difference, 10=Add)
      uniform int uBlendMode;
      
      // Masking
      uniform int uMaskEnabled;
      uniform int uMaskShape; // 0=None, 1=Rectangle, 2=Circle, 3=Linear, 4=Mirror, 5=Star, 6=Heart
      uniform vec2 uMaskPos;   // (-1f to 1f)
      uniform vec2 uMaskSize;  // (0f to 2f)
      uniform float uMaskRotation; // deg
      uniform float uMaskFeather;  // 0f to 1f
      uniform float uMaskOpacity;  // 0f to 1f
      uniform int uMaskInverted;   // 0 or 1
      
      // Pseudo random generator for grain
      float rand(vec2 co) {
        return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
      }
      
      void main() {
        vec2 clampedCoord = clamp(vTextureCoord, 0.0, 1.0);
        
        vec4 color;
        if (uBlur > 0.005) {
          float bRad = uBlur * 0.02;
          vec4 bc = vec4(0.0);
          bc += texture2D(uTexture, clamp(clampedCoord + vec2(-bRad, -bRad), 0.0, 1.0)) * 0.0625;
          bc += texture2D(uTexture, clamp(clampedCoord + vec2(0.0, -bRad), 0.0, 1.0)) * 0.125;
          bc += texture2D(uTexture, clamp(clampedCoord + vec2(bRad, -bRad), 0.0, 1.0)) * 0.0625;
          bc += texture2D(uTexture, clamp(clampedCoord + vec2(-bRad, 0.0), 0.0, 1.0)) * 0.125;
          bc += texture2D(uTexture, clampedCoord) * 0.25;
          bc += texture2D(uTexture, clamp(clampedCoord + vec2(bRad, 0.0), 0.0, 1.0)) * 0.125;
          bc += texture2D(uTexture, clamp(clampedCoord + vec2(-bRad, bRad), 0.0, 1.0)) * 0.0625;
          bc += texture2D(uTexture, clamp(clampedCoord + vec2(0.0, bRad), 0.0, 1.0)) * 0.125;
          bc += texture2D(uTexture, clamp(clampedCoord + vec2(bRad, bRad), 0.0, 1.0)) * 0.0625;
          color = bc;
        } else if (uSharpness > 0.01) {
          // 4-tap Laplacian sharpening
          vec4 c = texture2D(uTexture, clampedCoord);
          vec4 up = texture2D(uTexture, clamp(clampedCoord + vec2(0.0, uTexelSize.y), 0.0, 1.0));
          vec4 down = texture2D(uTexture, clamp(clampedCoord - vec2(0.0, uTexelSize.y), 0.0, 1.0));
          vec4 left = texture2D(uTexture, clamp(clampedCoord - vec2(uTexelSize.x, 0.0), 0.0, 1.0));
          vec4 right = texture2D(uTexture, clamp(clampedCoord + vec2(uTexelSize.x, 0.0), 0.0, 1.0));
          vec4 laplacian = (up + down + left + right) - 4.0 * c;
          color = c - uSharpness * laplacian;
        } else {
          color = texture2D(uTexture, clampedCoord);
        }
        
        if (uEffectParam > 0.005) {
          float splitDist = uEffectParam * 0.02;
          float r = texture2D(uTexture, clamp(clampedCoord + vec2(splitDist, 0.0), 0.0, 1.0)).r;
          float g = color.g;
          float b = texture2D(uTexture, clamp(clampedCoord - vec2(splitDist, 0.0), 0.0, 1.0)).b;
          color.rgb = vec3(r, g, b);
        }
        
        // GPU Mask Calculation
        if (uMaskEnabled == 1 && uMaskShape > 0) {
          vec2 centered = vTextureCoord - vec2(0.5) - uMaskPos * vec2(0.5, -0.5);
          float rad = uMaskRotation * 0.0174532925; // deg to rad
          float cosR = cos(rad);
          float sinR = sin(rad);
          vec2 mUv = vec2(cosR * centered.x - sinR * centered.y, sinR * centered.x + cosR * centered.y);
          
          float halfW = max(0.01, uMaskSize.x * 0.5);
          float halfH = max(0.01, uMaskSize.y * 0.5);
          float feather = max(0.001, uMaskFeather * 0.25);
          float maskAlpha = 1.0;
          
          if (uMaskShape == 1) { // RECTANGLE
            float edgeX = smoothstep(halfW + feather, halfW - feather, abs(mUv.x));
            float edgeY = smoothstep(halfH + feather, halfH - feather, abs(mUv.y));
            maskAlpha = edgeX * edgeY;
          } else if (uMaskShape == 2) { // CIRCLE / RADIAL
            float dist = length(mUv / vec2(halfW, halfH));
            maskAlpha = smoothstep(1.0 + feather, 1.0 - feather, dist);
          } else if (uMaskShape == 3) { // LINEAR
            maskAlpha = smoothstep(-feather, feather, mUv.x);
          } else if (uMaskShape == 4) { // MIRROR
            maskAlpha = smoothstep(-feather, feather, abs(mUv.x));
          } else {
            float dist = length(mUv / vec2(halfW, halfH));
            maskAlpha = smoothstep(1.0 + feather, 1.0 - feather, dist);
          }
          
          if (uMaskInverted == 1) {
            maskAlpha = 1.0 - maskAlpha;
          }
          color.a *= maskAlpha * uMaskOpacity;
        }
        
        // 1. Chroma Key removal (Similarity, Smoothness, Spill suppression, Edge control, Backgrounds)
        if (uChromaEnabled == 1) {
          float dist = distance(color.rgb, uKeyColor);
          float thresh = uChromaSimilarity + (uChromaEdge * 0.1);
          float feather = max(0.001, uChromaSmoothness);
          float alphaFactor = 1.0;
          if (dist < thresh) {
            alphaFactor = 0.0;
          } else if (dist < thresh + feather) {
            alphaFactor = (dist - thresh) / feather;
          }
          
          if (alphaFactor < 1.0 && uChromaSpill > 0.0) {
            float maxOther = max(color.r, color.b);
            if (color.g > maxOther && uKeyColor.g > uKeyColor.r) {
              color.g = mix(color.g, maxOther, uChromaSpill);
            } else {
              float keyInfluence = max(0.0, 1.0 - (dist / max(0.001, thresh + feather)));
              vec3 keyNorm = normalize(uKeyColor + vec3(0.0001));
              float proj = dot(color.rgb, keyNorm);
              if (proj > 0.0) {
                color.rgb = mix(color.rgb, color.rgb - proj * keyNorm * keyInfluence, uChromaSpill * 0.7);
              }
            }
          }
          
          if (alphaFactor <= 0.0) {
            if (uChromaBgType == 1) {
              color = uChromaBgColor;
            } else {
              discard;
            }
          } else if (alphaFactor < 1.0) {
            if (uChromaBgType == 1) {
              color = mix(uChromaBgColor, color, alphaFactor);
            } else {
              color.a *= alphaFactor;
            }
          }
        }
        
        // 2. Exposure & Brightness
        color.rgb = color.rgb * pow(2.0, uExposure) + vec3(uBrightness);
        
        // 3. Contrast
        color.rgb = (color.rgb - 0.5) * uContrast + 0.5;
        
        // 4. Saturation
        float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
        color.rgb = mix(vec3(luminance), color.rgb, uSaturation);
        
        // 5. Temperature & Tint
        color.r += uTemperature * 0.15;
        color.b -= uTemperature * 0.15;
        color.g -= uTint * 0.12;
        color.r += uTint * 0.08;
        color.b += uTint * 0.08;
        
        // 6. Highlights & Shadows
        if (uHighlights != 0.0 || uShadows != 0.0) {
          float l = dot(color.rgb, vec3(0.299, 0.587, 0.114));
          float shadowMask = 1.0 - smoothstep(0.0, 0.5, l);
          float highlightMask = smoothstep(0.5, 1.0, l);
          color.rgb += uShadows * shadowMask * 0.2;
          color.rgb += uHighlights * highlightMask * 0.2;
        }
        
        // 7. Color Matrix Filter
        if (uUseColorMatrix == 1) {
          color = (uColorMatrix * color) + uColorOffset;
        }
        
        // 8. Vignette
        if (uVignette > 0.01) {
          vec2 coord = (vTextureCoord - 0.5) * 2.0;
          float distFromCenter = length(coord);
          float vig = 1.0 - smoothstep(0.7, 1.4, distFromCenter * (0.8 + uVignette * 0.8));
          color.rgb *= vig;
        }
        
        // 9. Grain
        if (uGrain > 0.01) {
          float noise = (rand(vTextureCoord) - 0.5) * uGrain * 0.3;
          color.rgb += vec3(noise);
        }
        
        // 10. Opacity
        color.rgb = clamp(color.rgb, 0.0, 1.0);
        color.a *= uOpacity;
        
        gl_FragColor = color;
      }
    """
  }

  const val TRANSITION_FRAGMENT_SHADER = """
    precision highp float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTextureFrom;
    uniform sampler2D uTextureTo;
    uniform float uProgress;
    uniform int uType; // 0=FADE, 1=DISSOLVE, 2=WIPE, 3=SLIDE_LEFT, 4=SLIDE_RIGHT, 5=ZOOM_IN, 6=ZOOM_OUT, 7=SPIN, 8=BLUR, 9=FLASH, 10=GLITCH
    
    float rand(vec2 co) {
      return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
    }
    
    void main() {
      vec2 p = vTextureCoord;
      float pr = clamp(uProgress, 0.0, 1.0);
      
      if (uType == 0) { // FADE
        vec4 from = texture2D(uTextureFrom, p);
        vec4 to = texture2D(uTextureTo, p);
        gl_FragColor = mix(from, to, pr);
      } else if (uType == 1) { // DISSOLVE
        float noise = rand(p);
        if (noise < pr) {
          gl_FragColor = texture2D(uTextureTo, p);
        } else {
          gl_FragColor = texture2D(uTextureFrom, p);
        }
      } else if (uType == 2) { // WIPE
        if (p.x < pr) {
          gl_FragColor = texture2D(uTextureTo, p);
        } else {
          gl_FragColor = texture2D(uTextureFrom, p);
        }
      } else if (uType == 3) { // SLIDE LEFT
        vec2 pTo = p + vec2(1.0 - pr, 0.0);
        vec2 pFrom = p - vec2(pr, 0.0);
        if (p.x < 1.0 - pr) {
          gl_FragColor = texture2D(uTextureFrom, p + vec2(pr, 0.0));
        } else {
          gl_FragColor = texture2D(uTextureTo, p - vec2(1.0 - pr, 0.0));
        }
      } else if (uType == 4) { // SLIDE RIGHT
        if (p.x < pr) {
          gl_FragColor = texture2D(uTextureTo, p + vec2(1.0 - pr, 0.0));
        } else {
          gl_FragColor = texture2D(uTextureFrom, p - vec2(pr, 0.0));
        }
      } else if (uType == 5) { // ZOOM IN
        vec2 center = vec2(0.5, 0.5);
        vec2 pFromZoom = center + (p - center) / (1.0 + pr * 0.8);
        vec2 pToZoom = center + (p - center) * (2.0 - pr);
        vec4 from = texture2D(uTextureFrom, clamp(pFromZoom, 0.0, 1.0));
        vec4 to = texture2D(uTextureTo, clamp(pToZoom, 0.0, 1.0));
        gl_FragColor = mix(from, to, smoothstep(0.3, 0.7, pr));
      } else if (uType == 7) { // SPIN
        float angle = pr * 6.283185;
        float s = sin(angle);
        float c = cos(angle);
        vec2 centered = p - 0.5;
        vec2 rotated = vec2(c * centered.x - s * centered.y, s * centered.x + c * centered.y) + 0.5;
        vec4 from = texture2D(uTextureFrom, clamp(rotated, 0.0, 1.0));
        vec4 to = texture2D(uTextureTo, clamp(p, 0.0, 1.0));
        gl_FragColor = mix(from, to, smoothstep(0.4, 0.6, pr));
      } else if (uType == 8) { // BLUR
        float blurAmt = sin(pr * 3.14159) * 0.04;
        vec4 sumFrom = vec4(0.0);
        vec4 sumTo = vec4(0.0);
        for (int i = -3; i <= 3; i++) {
          vec2 off = vec2(float(i) * blurAmt, 0.0);
          sumFrom += texture2D(uTextureFrom, clamp(p + off, 0.0, 1.0));
          sumTo += texture2D(uTextureTo, clamp(p + off, 0.0, 1.0));
        }
        gl_FragColor = mix(sumFrom / 7.0, sumTo / 7.0, pr);
      } else if (uType == 9) { // FLASH
        vec4 from = texture2D(uTextureFrom, p);
        vec4 to = texture2D(uTextureTo, p);
        float flash = 1.0 - abs(pr - 0.5) * 2.0;
        vec4 blended = mix(from, to, pr);
        gl_FragColor = mix(blended, vec4(1.0), flash * 0.85);
      } else if (uType == 10) { // GLITCH
        float blockY = floor(p.y * 30.0);
        float r = rand(vec2(blockY, pr));
        vec2 offset = vec2(0.0);
        if (r > 0.75) {
          offset.x = (r - 0.75) * 0.15 * sin(pr * 3.14159);
        }
        vec4 from = texture2D(uTextureFrom, clamp(p + offset, 0.0, 1.0));
        vec4 to = texture2D(uTextureTo, clamp(p - offset, 0.0, 1.0));
        gl_FragColor = mix(from, to, pr);
      } else if (uType == 11) { // WHIP PAN
        float blurAmt = sin(pr * 3.14159) * 0.08;
        vec2 pFrom = p - vec2(pr * 1.5, 0.0);
        vec2 pTo = p + vec2((1.0 - pr) * 1.5, 0.0);
        vec4 from = texture2D(uTextureFrom, clamp(pFrom, 0.0, 1.0));
        vec4 to = texture2D(uTextureTo, clamp(pTo, 0.0, 1.0));
        gl_FragColor = mix(from, to, pr);
      } else if (uType == 12) { // ZOOM BLUR
        vec2 center = vec2(0.5);
        float scale = 1.0 + sin(pr * 3.14159) * 0.6;
        vec2 pZoom = center + (p - center) / scale;
        vec4 from = texture2D(uTextureFrom, clamp(pZoom, 0.0, 1.0));
        vec4 to = texture2D(uTextureTo, clamp(p, 0.0, 1.0));
        gl_FragColor = mix(from, to, pr);
      } else if (uType == 13) { // GLITCH WIPE
        float noise = rand(vec2(p.y * 20.0, pr));
        float threshold = pr + (noise - 0.5) * 0.2;
        gl_FragColor = p.x < threshold ? texture2D(uTextureTo, p) : texture2D(uTextureFrom, p);
      } else if (uType == 14) { // LIGHT LEAK
        vec4 from = texture2D(uTextureFrom, p);
        vec4 to = texture2D(uTextureTo, p);
        float leak = sin(pr * 3.14159) * 0.9;
        vec3 leakColor = vec3(1.0, 0.65, 0.3) * leak;
        gl_FragColor = vec4(mix(from.rgb, to.rgb, pr) + leakColor, 1.0);
      } else { // Default blend
        vec4 from = texture2D(uTextureFrom, p);
        vec4 to = texture2D(uTextureTo, p);
        gl_FragColor = mix(from, to, pr);
      }
    }
  """

  const val EFFECT_BLUR = 0
  const val EFFECT_GLOW = 1
  const val EFFECT_MOTION_BLUR = 2
  const val EFFECT_SHAKE = 3
  const val EFFECT_ZOOM = 4
  const val EFFECT_SPIN = 5
  const val EFFECT_FLASH = 6
  const val EFFECT_GLITCH = 7
  const val EFFECT_RGB_SPLIT = 8
  const val EFFECT_DISTORTION = 9
  const val EFFECT_LENS_FLARE = 10
  const val EFFECT_LIGHT_LEAK = 11
  const val EFFECT_VIGNETTE = 12
  const val EFFECT_NOISE = 13
  const val EFFECT_CRT = 14
  const val EFFECT_VHS = 15
  const val EFFECT_THERMAL = 16
  const val EFFECT_BLUEPRINT = 17
  const val EFFECT_ACID_TRIP = 18
  const val EFFECT_POP_ART = 19
  const val EFFECT_SEPIA = 20
  const val EFFECT_POLAROID = 21
  const val EFFECT_OIL_PAINTING = 22
  const val EFFECT_HALFTONE = 23
  const val EFFECT_COMIC = 24
  const val EFFECT_SOLAR_FLARE = 25
  const val EFFECT_BOKEH = 26
  const val EFFECT_PRISM = 27
  const val EFFECT_MIRROR = 28
  const val EFFECT_RIPPLE = 29

  val EFFECT_FRAGMENT_SHADER = """
    precision mediump float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    uniform int uEffectType;
    uniform float uIntensity; // 0.0 to 1.0
    uniform float uTime;      // elapsed time in seconds
    uniform vec2 uTexelSize;  // 1.0 / (width, height)

    void main() {
      vec2 uv = vTextureCoord;
      float intensity = clamp(uIntensity, 0.0, 1.0);
      
      if (uEffectType == $EFFECT_BLUR) {
        float rad = intensity * 14.0 * uTexelSize.x;
        vec4 sum = vec4(0.0);
        sum += texture2D(uTexture, clamp(uv + vec2(-rad * 1.5, -rad * 1.5), 0.0, 1.0)) * 0.05;
        sum += texture2D(uTexture, clamp(uv + vec2(0.0, -rad), 0.0, 1.0)) * 0.12;
        sum += texture2D(uTexture, clamp(uv + vec2(rad * 1.5, -rad * 1.5), 0.0, 1.0)) * 0.05;
        sum += texture2D(uTexture, clamp(uv + vec2(-rad, 0.0), 0.0, 1.0)) * 0.12;
        sum += texture2D(uTexture, uv) * 0.32;
        sum += texture2D(uTexture, clamp(uv + vec2(rad, 0.0), 0.0, 1.0)) * 0.12;
        sum += texture2D(uTexture, clamp(uv + vec2(-rad * 1.5, rad * 1.5), 0.0, 1.0)) * 0.05;
        sum += texture2D(uTexture, clamp(uv + vec2(0.0, rad), 0.0, 1.0)) * 0.12;
        sum += texture2D(uTexture, clamp(uv + vec2(rad * 1.5, rad * 1.5), 0.0, 1.0)) * 0.05;
        gl_FragColor = vec4(clamp(sum.rgb, 0.0, 1.0), sum.a);
        return;
      }
      
      if (uEffectType == $EFFECT_GLOW) {
        vec4 baseColor = texture2D(uTexture, uv);
        float rad = intensity * 12.0 * uTexelSize.x;
        vec4 bloom = vec4(0.0);
        bloom += max(texture2D(uTexture, clamp(uv + vec2(-rad, -rad), 0.0, 1.0)) - 0.45, vec4(0.0)) * 0.125;
        bloom += max(texture2D(uTexture, clamp(uv + vec2(rad, -rad), 0.0, 1.0)) - 0.45, vec4(0.0)) * 0.125;
        bloom += max(texture2D(uTexture, clamp(uv + vec2(-rad, rad), 0.0, 1.0)) - 0.45, vec4(0.0)) * 0.125;
        bloom += max(texture2D(uTexture, clamp(uv + vec2(rad, rad), 0.0, 1.0)) - 0.45, vec4(0.0)) * 0.125;
        bloom += max(texture2D(uTexture, clamp(uv + vec2(0.0, -rad * 1.5), 0.0, 1.0)) - 0.45, vec4(0.0)) * 0.125;
        bloom += max(texture2D(uTexture, clamp(uv + vec2(0.0, rad * 1.5), 0.0, 1.0)) - 0.45, vec4(0.0)) * 0.125;
        bloom += max(texture2D(uTexture, clamp(uv + vec2(-rad * 1.5, 0.0), 0.0, 1.0)) - 0.45, vec4(0.0)) * 0.125;
        bloom += max(texture2D(uTexture, clamp(uv + vec2(rad * 1.5, 0.0), 0.0, 1.0)) - 0.45, vec4(0.0)) * 0.125;
        vec3 glowColor = bloom.rgb * vec3(1.2, 1.1, 1.3) * (intensity * 2.8);
        gl_FragColor = vec4(clamp(baseColor.rgb + glowColor, 0.0, 1.0), baseColor.a);
        return;
      }
      
      if (uEffectType == $EFFECT_MOTION_BLUR) {
        vec2 dir = vec2(cos(uTime * 2.0), sin(uTime * 2.0) * 0.6) * (intensity * 0.035);
        vec4 sum = vec4(0.0);
        for (int i = -4; i <= 4; i++) {
          vec2 offset = dir * (float(i) / 4.0);
          sum += texture2D(uTexture, clamp(uv + offset, 0.0, 1.0));
        }
        vec4 res = sum / 9.0;
        gl_FragColor = vec4(clamp(res.rgb, 0.0, 1.0), res.a);
        return;
      }
      
      if (uEffectType == $EFFECT_SHAKE) {
        vec2 shakeOffset = vec2(
          sin(uTime * 42.0) * 0.6 + sin(uTime * 85.0) * 0.4,
          cos(uTime * 50.0) * 0.6 + cos(uTime * 95.0) * 0.4
        ) * (intensity * 0.04);
        vec4 col = texture2D(uTexture, clamp(uv + shakeOffset, 0.0, 1.0));
        gl_FragColor = vec4(clamp(col.rgb, 0.0, 1.0), col.a);
        return;
      }
      
      if (uEffectType == $EFFECT_ZOOM) {
        float zoom = 1.0 + (sin(uTime * 7.0) * 0.5 + 0.5) * (intensity * 0.35);
        vec2 centered = (uv - 0.5) / zoom + 0.5;
        vec4 col = texture2D(uTexture, clamp(centered, 0.0, 1.0));
        gl_FragColor = vec4(clamp(col.rgb, 0.0, 1.0), col.a);
        return;
      }
      
      if (uEffectType == $EFFECT_SPIN) {
        float angle = sin(uTime * 3.5) * (intensity * 0.4);
        float s = sin(angle);
        float c = cos(angle);
        vec2 centered = uv - 0.5;
        vec2 rotated = vec2(c * centered.x - s * centered.y, s * centered.x + c * centered.y) + 0.5;
        vec4 col = texture2D(uTexture, clamp(rotated, 0.0, 1.0));
        gl_FragColor = vec4(clamp(col.rgb, 0.0, 1.0), col.a);
        return;
      }
      
      if (uEffectType == $EFFECT_FLASH) {
        float flashPhase = fract(uTime * 3.0);
        float flashStrength = pow(1.0 - flashPhase, 3.0) * intensity;
        vec4 col = texture2D(uTexture, uv);
        gl_FragColor = vec4(clamp(mix(col.rgb, vec3(1.0), flashStrength), 0.0, 1.0), col.a);
        return;
      }
      
      if (uEffectType == $EFFECT_GLITCH) {
        float sliceY = floor(uv.y * 32.0);
        float sliceNoise = fract(sin(dot(vec2(sliceY, floor(uTime * 14.0)), vec2(12.9898, 78.233))) * 43758.5453);
        float glitchShift = 0.0;
        if (sliceNoise > 0.62) {
          glitchShift = (sliceNoise - 0.62) * 0.18 * intensity;
        }
        vec2 uvR = clamp(uv + vec2(glitchShift + 0.015 * intensity, 0.0), 0.0, 1.0);
        vec2 uvG = clamp(uv + vec2(glitchShift, 0.0), 0.0, 1.0);
        vec2 uvB = clamp(uv + vec2(glitchShift - 0.015 * intensity, 0.0), 0.0, 1.0);
        float r = texture2D(uTexture, uvR).r;
        float g = texture2D(uTexture, uvG).g;
        float b = texture2D(uTexture, uvB).b;
        float a = texture2D(uTexture, uvG).a;
        gl_FragColor = vec4(clamp(vec3(r, g, b), 0.0, 1.0), a);
        return;
      }
      
      if (uEffectType == $EFFECT_RGB_SPLIT) {
        vec2 offset = vec2(intensity * 0.032, 0.0);
        float r = texture2D(uTexture, clamp(uv + offset, 0.0, 1.0)).r;
        float g = texture2D(uTexture, uv).g;
        float b = texture2D(uTexture, clamp(uv - offset, 0.0, 1.0)).b;
        float a = texture2D(uTexture, uv).a;
        gl_FragColor = vec4(clamp(vec3(r, g, b), 0.0, 1.0), a);
        return;
      }
      
      if (uEffectType == $EFFECT_DISTORTION) {
        vec2 wave = vec2(
          sin(uv.y * 28.0 + uTime * 6.0),
          cos(uv.x * 28.0 + uTime * 6.0)
        ) * (0.028 * intensity);
        vec4 col = texture2D(uTexture, clamp(uv + wave, 0.0, 1.0));
        gl_FragColor = vec4(clamp(col.rgb, 0.0, 1.0), col.a);
        return;
      }

      if (uEffectType == $EFFECT_RIPPLE) {
        vec2 c = uv - vec2(0.5);
        float dist = length(c);
        float rip = sin(dist * 35.0 - uTime * 8.0) * (0.02 * intensity);
        vec2 ripUv = uv + (dist > 0.001 ? (c / dist) * rip : vec2(0.0));
        vec4 col = texture2D(uTexture, clamp(ripUv, 0.0, 1.0));
        gl_FragColor = vec4(clamp(col.rgb, 0.0, 1.0), col.a);
        return;
      }
      
      if (uEffectType == $EFFECT_LENS_FLARE) {
        vec4 base = texture2D(uTexture, uv);
        vec2 flareCenter = vec2(0.42 + sin(uTime * 0.9) * 0.18, 0.36 + cos(uTime * 0.7) * 0.12);
        float dist = distance(uv, flareCenter);
        float star = pow(max(0.0, 1.0 - dist * 3.2), 2.0);
        float streak = pow(max(0.0, 1.0 - abs(uv.y - flareCenter.y) * 28.0), 4.0) *
                       pow(max(0.0, 1.0 - abs(uv.x - flareCenter.x) * 1.6), 1.5);
        float halo = smoothstep(0.32, 0.36, dist) * smoothstep(0.40, 0.36, dist) * 0.7;
        vec3 flareCol = (vec3(1.0, 0.88, 0.55) * (star + streak * 1.6) + vec3(0.45, 0.75, 1.0) * halo) * (intensity * 1.4);
        gl_FragColor = vec4(clamp(base.rgb + flareCol, 0.0, 1.0), base.a);
        return;
      }
      
      if (uEffectType == $EFFECT_LIGHT_LEAK) {
        vec4 base = texture2D(uTexture, uv);
        float leak1 = smoothstep(0.75, 0.05, distance(uv, vec2(0.12 + sin(uTime * 0.75) * 0.08, 0.12)));
        float leak2 = smoothstep(0.85, 0.15, distance(uv, vec2(0.88, 0.82 + cos(uTime * 0.85) * 0.08)));
        vec3 leakColor1 = vec3(1.0, 0.62, 0.25) * leak1 * 1.3;
        vec3 leakColor2 = vec3(1.0, 0.25, 0.55) * leak2 * 1.0;
        vec3 totalLeak = (leakColor1 + leakColor2) * intensity;
        gl_FragColor = vec4(clamp(base.rgb + totalLeak, 0.0, 1.0), base.a);
        return;
      }

      if (uEffectType == $EFFECT_VIGNETTE) {
        vec4 col = texture2D(uTexture, uv);
        vec2 d = uv - vec2(0.5);
        float vig = smoothstep(0.8, 0.25, length(d) * (1.2 + intensity * 0.8));
        gl_FragColor = vec4(col.rgb * vig, col.a);
        return;
      }

      if (uEffectType == $EFFECT_NOISE) {
        vec4 col = texture2D(uTexture, uv);
        float n = fract(sin(dot(uv + vec2(fract(uTime * 17.0), fract(uTime * 23.0)), vec2(12.9898, 78.233))) * 43758.5453);
        vec3 noisy = mix(col.rgb, col.rgb + (vec3(n) - 0.5) * 0.35, intensity);
        gl_FragColor = vec4(clamp(noisy, 0.0, 1.0), col.a);
        return;
      }

      if (uEffectType == $EFFECT_CRT) {
        float scanline = sin(uv.y * 360.0 + uTime * 10.0) * 0.12 * intensity;
        vec4 col = texture2D(uTexture, uv);
        vec3 crt = (col.rgb - scanline) * vec3(0.95, 1.05, 0.95);
        gl_FragColor = vec4(clamp(crt, 0.0, 1.0), col.a);
        return;
      }

      if (uEffectType == $EFFECT_VHS) {
        float scanY = floor(uv.y * 120.0);
        float tapeNoise = fract(sin(dot(vec2(scanY, floor(uTime * 8.0)), vec2(12.9898, 78.233))) * 43758.5453);
        float shift = (tapeNoise > 0.85) ? (tapeNoise - 0.85) * 0.05 * intensity : 0.0;
        vec2 vUv = clamp(uv + vec2(shift, 0.0), 0.0, 1.0);
        float r = texture2D(uTexture, clamp(vUv + vec2(0.008 * intensity, 0.0), 0.0, 1.0)).r;
        float g = texture2D(uTexture, vUv).g;
        float b = texture2D(uTexture, clamp(vUv - vec2(0.008 * intensity, 0.0), 0.0, 1.0)).b;
        vec3 vhs = mix(vec3(r, g, b), vec3(r * 1.1, g * 0.95, b * 0.8), 0.4);
        gl_FragColor = vec4(clamp(vhs, 0.0, 1.0), 1.0);
        return;
      }

      if (uEffectType == $EFFECT_THERMAL) {
        vec4 col = texture2D(uTexture, uv);
        float luma = dot(col.rgb, vec3(0.299, 0.587, 0.114));
        vec3 thermal;
        if (luma < 0.33) {
          thermal = mix(vec3(0.0, 0.0, 0.8), vec3(0.0, 0.8, 0.8), luma * 3.0);
        } else if (luma < 0.66) {
          thermal = mix(vec3(0.0, 0.8, 0.8), vec3(1.0, 0.9, 0.0), (luma - 0.33) * 3.0);
        } else {
          thermal = mix(vec3(1.0, 0.9, 0.0), vec3(1.0, 0.1, 0.0), (luma - 0.66) * 3.0);
        }
        gl_FragColor = vec4(mix(col.rgb, thermal, intensity), col.a);
        return;
      }

      if (uEffectType == $EFFECT_BLUEPRINT) {
        vec4 col = texture2D(uTexture, uv);
        float luma = dot(col.rgb, vec3(0.299, 0.587, 0.114));
        vec3 bp = vec3(0.05, 0.25, 0.75) + vec3(luma * 0.8);
        gl_FragColor = vec4(mix(col.rgb, bp, intensity), col.a);
        return;
      }

      if (uEffectType == $EFFECT_ACID_TRIP) {
        vec4 col = texture2D(uTexture, uv);
        float angle = uTime * 3.0 * intensity;
        vec3 rotated = vec3(
          col.r * cos(angle) - col.g * sin(angle),
          col.r * sin(angle) + col.g * cos(angle),
          col.b
        );
        gl_FragColor = vec4(clamp(abs(rotated), 0.0, 1.0), col.a);
        return;
      }

      if (uEffectType == $EFFECT_POP_ART) {
        vec4 col = texture2D(uTexture, uv);
        vec3 pop = vec3(
          step(0.4, col.r),
          step(0.4, col.g),
          step(0.4, col.b)
        );
        gl_FragColor = vec4(mix(col.rgb, pop, intensity), col.a);
        return;
      }

      if (uEffectType == $EFFECT_SEPIA) {
        vec4 col = texture2D(uTexture, uv);
        vec3 sepia = vec3(
          dot(col.rgb, vec3(0.393, 0.769, 0.189)),
          dot(col.rgb, vec3(0.349, 0.686, 0.168)),
          dot(col.rgb, vec3(0.272, 0.534, 0.131))
        );
        gl_FragColor = vec4(mix(col.rgb, sepia, intensity), col.a);
        return;
      }

      if (uEffectType == $EFFECT_POLAROID) {
        vec4 col = texture2D(uTexture, uv);
        vec3 pol = col.rgb * vec3(1.15, 1.05, 0.88) + vec3(0.05, 0.04, 0.01);
        gl_FragColor = vec4(mix(col.rgb, clamp(pol, 0.0, 1.0), intensity), col.a);
        return;
      }

      if (uEffectType == $EFFECT_OIL_PAINTING) {
        vec2 p = floor(uv / (uTexelSize * 3.0)) * (uTexelSize * 3.0);
        vec4 col = texture2D(uTexture, p);
        gl_FragColor = vec4(mix(texture2D(uTexture, uv).rgb, col.rgb, intensity), col.a);
        return;
      }

      if (uEffectType == $EFFECT_HALFTONE) {
        vec4 col = texture2D(uTexture, uv);
        float luma = dot(col.rgb, vec3(0.299, 0.587, 0.114));
        vec2 dotPos = fract(uv / (uTexelSize * 6.0)) - vec2(0.5);
        float circle = length(dotPos);
        float radius = (1.0 - luma) * 0.65;
        float dotCol = step(radius, circle);
        gl_FragColor = vec4(mix(col.rgb, vec3(dotCol), intensity), col.a);
        return;
      }

      if (uEffectType == $EFFECT_COMIC) {
        vec4 col = texture2D(uTexture, uv);
        vec3 q = floor(col.rgb * 4.0) / 4.0;
        gl_FragColor = vec4(mix(col.rgb, q, intensity), col.a);
        return;
      }

      if (uEffectType == $EFFECT_SOLAR_FLARE) {
        vec4 col = texture2D(uTexture, uv);
        vec2 center = vec2(0.5 + sin(uTime * 0.8) * 0.2, 0.5 + cos(uTime * 0.8) * 0.2);
        float d = distance(uv, center);
        vec3 flare = vec3(1.0, 0.5, 0.1) * (1.0 - smoothstep(0.0, 0.6, d)) * intensity * 1.5;
        gl_FragColor = vec4(clamp(col.rgb + flare, 0.0, 1.0), col.a);
        return;
      }

      if (uEffectType == $EFFECT_BOKEH) {
        vec4 col = texture2D(uTexture, uv);
        float d1 = distance(uv, vec2(0.2 + sin(uTime * 0.5) * 0.1, 0.3 + cos(uTime * 0.5) * 0.1));
        float d2 = distance(uv, vec2(0.8 - sin(uTime * 0.4) * 0.1, 0.7 - cos(uTime * 0.4) * 0.1));
        float b1 = smoothstep(0.18, 0.02, d1);
        float b2 = smoothstep(0.22, 0.03, d2);
        vec3 bokeh = (vec3(1.0, 0.8, 0.4) * b1 + vec3(0.4, 0.8, 1.0) * b2) * (intensity * 0.8);
        gl_FragColor = vec4(clamp(col.rgb + bokeh, 0.0, 1.0), col.a);
        return;
      }

      if (uEffectType == $EFFECT_PRISM) {
        vec4 col = texture2D(uTexture, uv);
        float r = texture2D(uTexture, clamp(uv + vec2(0.012 * intensity, 0.008 * intensity), 0.0, 1.0)).r;
        float g = texture2D(uTexture, uv).g;
        float b = texture2D(uTexture, clamp(uv - vec2(0.012 * intensity, 0.008 * intensity), 0.0, 1.0)).b;
        gl_FragColor = vec4(vec3(r, g, b), col.a);
        return;
      }

      if (uEffectType == $EFFECT_MIRROR) {
        vec2 mUv = uv;
        if (mUv.x > 0.5) mUv.x = 1.0 - mUv.x;
        vec4 col = texture2D(uTexture, mUv);
        gl_FragColor = mix(texture2D(uTexture, uv), col, intensity);
        return;
      }
      
      // Clean default passthrough
      vec4 def = texture2D(uTexture, uv);
      gl_FragColor = vec4(clamp(def.rgb, 0.0, 1.0), def.a);
    }
  """

  /**
   * Dedicated Blit Vertex Shader feeding the MediaCodec export encoder surface.
   * Flips Y (vec2(coords.x, 1.0 - coords.y)) when rendering directly from an FBO
   * to MediaCodec input surfaces to prevent inverted Y-axis upside-down video output.
   */
  const val ENCODER_BLIT_VERTEX_SHADER = """
    uniform mat4 uMVPMatrix;
    uniform mat4 uTexMatrix;
    uniform int uFlipY;
    uniform int uFlipX;
    attribute vec4 aPosition;
    attribute vec4 aTextureCoord;
    varying vec2 vTextureCoord;
    
    void main() {
      gl_Position = uMVPMatrix * aPosition;
      vec2 coords = (uTexMatrix * aTextureCoord).xy;
      float x = (uFlipX != 0) ? 1.0 - coords.x : coords.x;
      float y = (uFlipY != 0) ? 1.0 - coords.y : coords.y;
      vTextureCoord = vec2(x, y);
    }
  """

  const val BLIT_FRAGMENT_SHADER = """
    precision mediump float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    
    void main() {
      gl_FragColor = texture2D(uTexture, vTextureCoord);
    }
  """
}
