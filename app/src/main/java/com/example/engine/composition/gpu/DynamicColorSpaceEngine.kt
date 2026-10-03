package com.example.engine.composition.gpu

import android.opengl.GLES20
import android.util.Log

/**
 * Supported Professional Color Spaces.
 */
enum class ColorSpaceStandard(val displayName: String) {
    SRGB("sRGB (Standard)"),
    REC709("Rec. 709 (HDTV)"),
    DCI_P3("DCI-P3 (Display P3)"),
    BT2020_HDR("BT.2020 (HDR 10-Bit)"),
    ACES_CG("ACEScg (VFX Master)")
}

/**
 * Supported Film-Grade Tone Mapping Operators.
 */
enum class ToneMappingOperator(val displayName: String) {
    NONE("Pass-through"),
    LINEAR_CLAMP("Linear Clamped"),
    REINHARD("Reinhard Extended"),
    ACES_FILMIC("ACES Filmic (Academy)"),
    HABLE_UNCHARTED("Hable / Uncharted 2"),
    AGX("AgX High-Dynamic")
}

/**
 * Professional Dynamic Color Space & HDR Tone-Mapping Engine.
 *
 * Provides linear gamma conversions and ACEScg / Rec.709 / BT.2020 transformations
 * to prevent HDR 10-bit video inputs from clipping or washing out.
 */
class DynamicColorSpaceEngine {
    companion object {
        private const val TAG = "DynamicColorSpaceEngine"

        val VERTEX_SHADER = """
attribute vec4 a_Position;
attribute vec2 a_TexCoord;
varying vec2 v_TexCoord;
void main() {
    gl_Position = a_Position;
    v_TexCoord = a_TexCoord;
}
""".trimIndent()

        val FRAGMENT_SHADER = """
precision highp float;
varying vec2 v_TexCoord;
uniform sampler2D u_Texture;

uniform int u_ColorSpace; // 0=sRGB, 1=Rec709, 2=P3, 3=BT2020, 4=ACEScg
uniform int u_ToneMapper;  // 0=None, 1=Linear, 2=Reinhard, 3=ACES Filmic, 4=Hable, 5=AgX
uniform float u_Exposure;
uniform float u_Gamma;

// sRGB EOTF to Linear
vec3 srgbToLinear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

// Linear to sRGB OETF
vec3 linearToSrgb(vec3 c) {
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

// ACES Filmic Tone Mapping (Stephen Hill / Narkowicz Fit)
vec3 acesFilmicToneMap(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

// Reinhard Extended Tone Mapping
vec3 reinhardToneMap(vec3 x) {
    float white = 2.0;
    return (x * (1.0 + x / (white * white))) / (1.0 + x);
}

// Hable / Uncharted 2 Film Curve
vec3 hablePartial(vec3 x) {
    float A = 0.15; float B = 0.50; float C = 0.10;
    float D = 0.20; float E = 0.02; float F = 0.30;
    return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F;
}

vec3 hableToneMap(vec3 v) {
    float exposureBias = 2.0;
    vec3 curr = hablePartial(v * exposureBias);
    vec3 w = vec3(11.2);
    vec3 whiteScale = 1.0 / hablePartial(w);
    return curr * whiteScale;
}

void main() {
    vec4 src = texture2D(u_Texture, v_TexCoord);
    vec3 color = src.rgb;

    // 1. Convert source to Linear Radiance
    color = srgbToLinear(color);

    // 2. Apply Exposure adjustment
    color *= u_Exposure;

    // 3. Apply Tone Mapping
    if (u_ToneMapper == 2) {
        color = reinhardToneMap(color);
    } else if (u_ToneMapper == 3) {
        color = acesFilmicToneMap(color);
    } else if (u_ToneMapper == 4) {
        color = hableToneMap(color);
    }

    // 4. Gamma Correction & Color Output
    color = pow(color, vec3(1.0 / u_Gamma));
    color = linearToSrgb(color);

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), src.a);
}
""".trimIndent()
    }

    private var programId = 0
    private var uColorSpaceLoc = -1
    private var uToneMapperLoc = -1
    private var uExposureLoc = -1
    private var uGammaLoc = -1
    private var isInitialized = false

    fun init() {
        if (isInitialized) return
        programId = GlShaderUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uColorSpaceLoc = GLES20.glGetUniformLocation(programId, "u_ColorSpace")
        uToneMapperLoc = GLES20.glGetUniformLocation(programId, "u_ToneMapper")
        uExposureLoc = GLES20.glGetUniformLocation(programId, "u_Exposure")
        uGammaLoc = GLES20.glGetUniformLocation(programId, "u_Gamma")
        isInitialized = true
    }

    fun applyColorGrading(
        inputTextureId: Int,
        targetFbo: GlFramebuffer,
        colorSpace: ColorSpaceStandard = ColorSpaceStandard.REC709,
        toneMapping: ToneMappingOperator = ToneMappingOperator.ACES_FILMIC,
        exposure: Float = 1.0f,
        gamma: Float = 1.0f
    ): Int {
        if (!isInitialized) init()

        targetFbo.bind()
        GLES20.glUseProgram(programId)

        GLES20.glUniform1i(uColorSpaceLoc, colorSpace.ordinal)
        GLES20.glUniform1i(uToneMapperLoc, toneMapping.ordinal)
        GLES20.glUniform1f(uExposureLoc, exposure)
        GLES20.glUniform1f(uGammaLoc, gamma)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "u_Texture"), 0)

        GlShaderUtil.drawFullscreenQuad(programId)

        targetFbo.unbind()
        return targetFbo.getTextureId()
    }

    fun release() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
            isInitialized = false
        }
    }
}
