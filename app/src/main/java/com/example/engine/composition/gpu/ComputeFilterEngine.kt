package com.example.engine.composition.gpu

import android.opengl.GLES20
import android.util.Log

/**
 * High-Performance Spatial Filter Engine for Noise Reduction, Edge Detection, and Sharpening.
 *
 * Provides optimized GPU multi-tap spatial kernels for 4K / 60 FPS processing.
 */
class ComputeFilterEngine {
    companion object {
        private const val TAG = "ComputeFilterEngine"

        val VERTEX_SHADER = """
attribute vec4 a_Position;
attribute vec2 a_TexCoord;
varying vec2 v_TexCoord;
void main() {
    gl_Position = a_Position;
    v_TexCoord = a_TexCoord;
}
""".trimIndent()

        // Sobel Edge Detection Kernel
        val SOBEL_FRAG = """
precision mediump float;
varying vec2 v_TexCoord;
uniform sampler2D u_Texture;
uniform vec2 u_TexelSize;
uniform float u_Intensity;

void main() {
    float gx = 0.0;
    float gy = 0.0;

    vec3 c00 = texture2D(u_Texture, v_TexCoord + vec2(-u_TexelSize.x, -u_TexelSize.y)).rgb;
    vec3 c01 = texture2D(u_Texture, v_TexCoord + vec2( 0.0,           -u_TexelSize.y)).rgb;
    vec3 c02 = texture2D(u_Texture, v_TexCoord + vec2( u_TexelSize.x, -u_TexelSize.y)).rgb;

    vec3 c10 = texture2D(u_Texture, v_TexCoord + vec2(-u_TexelSize.x,  0.0)).rgb;
    vec3 c12 = texture2D(u_Texture, v_TexCoord + vec2( u_TexelSize.x,  0.0)).rgb;

    vec3 c20 = texture2D(u_Texture, v_TexCoord + vec2(-u_TexelSize.x,  u_TexelSize.y)).rgb;
    vec3 c21 = texture2D(u_Texture, v_TexCoord + vec2( 0.0,            u_TexelSize.y)).rgb;
    vec3 c22 = texture2D(u_Texture, v_TexCoord + vec2( u_TexelSize.x,  u_TexelSize.y)).rgb;

    float l00 = dot(c00, vec3(0.299, 0.587, 0.114));
    float l01 = dot(c01, vec3(0.299, 0.587, 0.114));
    float l02 = dot(c02, vec3(0.299, 0.587, 0.114));
    float l10 = dot(c10, vec3(0.299, 0.587, 0.114));
    float l12 = dot(c12, vec3(0.299, 0.587, 0.114));
    float l20 = dot(c20, vec3(0.299, 0.587, 0.114));
    float l21 = dot(c21, vec3(0.299, 0.587, 0.114));
    float l22 = dot(c22, vec3(0.299, 0.587, 0.114));

    gx = -l00 - 2.0 * l10 - l20 + l02 + 2.0 * l12 + l22;
    gy = -l00 - 2.0 * l01 - l02 + l20 + 2.0 * l21 + l22;

    float edge = sqrt(gx * gx + gy * gy) * u_Intensity;
    gl_FragColor = vec4(vec3(edge), 1.0);
}
""".trimIndent()

        // Bilateral Filter Noise Reduction (Edge-Preserving Denoising)
        val BILATERAL_DENOISE_FRAG = """
precision mediump float;
varying vec2 v_TexCoord;
uniform sampler2D u_Texture;
uniform vec2 u_TexelSize;
uniform float u_SpatialSigma;
uniform float u_RangeSigma;

void main() {
    vec4 centerColor = texture2D(u_Texture, v_TexCoord);
    vec4 sum = vec4(0.0);
    float totalWeight = 0.0;

    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            vec2 offset = vec2(float(x), float(y)) * u_TexelSize;
            vec4 sampleColor = texture2D(u_Texture, v_TexCoord + offset);

            float spatialDist = length(vec2(float(x), float(y)));
            float spatialWeight = exp(-(spatialDist * spatialDist) / (2.0 * u_SpatialSigma * u_SpatialSigma));

            float colorDist = length(sampleColor.rgb - centerColor.rgb);
            float rangeWeight = exp(-(colorDist * colorDist) / (2.0 * u_RangeSigma * u_RangeSigma));

            float weight = spatialWeight * rangeWeight;
            sum += sampleColor * weight;
            totalWeight += weight;
        }
    }

    gl_FragColor = sum / max(totalWeight, 0.0001);
}
""".trimIndent()

        // Unsharp Mask Sharpening
        val UNSHARP_MASK_FRAG = """
precision mediump float;
varying vec2 v_TexCoord;
uniform sampler2D u_Texture;
uniform vec2 u_TexelSize;
uniform float u_Amount;

void main() {
    vec4 center = texture2D(u_Texture, v_TexCoord);
    vec4 blur = (
        texture2D(u_Texture, v_TexCoord + vec2(-u_TexelSize.x, 0.0)) +
        texture2D(u_Texture, v_TexCoord + vec2( u_TexelSize.x, 0.0)) +
        texture2D(u_Texture, v_TexCoord + vec2(0.0, -u_TexelSize.y)) +
        texture2D(u_Texture, v_TexCoord + vec2(0.0,  u_TexelSize.y))
    ) * 0.25;

    vec4 sharp = center + (center - blur) * u_Amount;
    gl_FragColor = clamp(sharp, 0.0, 1.0);
}
""".trimIndent()
    }

    private var sobelProgram = 0
    private var denoiseProgram = 0
    private var unsharpProgram = 0
    private var isInitialized = false

    fun init() {
        if (isInitialized) return
        sobelProgram = GlShaderUtil.createProgram(VERTEX_SHADER, SOBEL_FRAG)
        denoiseProgram = GlShaderUtil.createProgram(VERTEX_SHADER, BILATERAL_DENOISE_FRAG)
        unsharpProgram = GlShaderUtil.createProgram(VERTEX_SHADER, UNSHARP_MASK_FRAG)
        isInitialized = true
    }

    fun applySobel(textureId: Int, targetFbo: GlFramebuffer, intensity: Float = 1.0f): Int {
        if (!isInitialized) init()
        targetFbo.bind()
        GLES20.glUseProgram(sobelProgram)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(sobelProgram, "u_TexelSize"), 1f / targetFbo.width, 1f / targetFbo.height)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(sobelProgram, "u_Intensity"), intensity)

        bindTexture(textureId)
        GlShaderUtil.drawFullscreenQuad(sobelProgram)
        targetFbo.unbind()
        return targetFbo.getTextureId()
    }

    fun applyDenoise(textureId: Int, targetFbo: GlFramebuffer, spatialSigma: Float = 1.5f, rangeSigma: Float = 0.15f): Int {
        if (!isInitialized) init()
        targetFbo.bind()
        GLES20.glUseProgram(denoiseProgram)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(denoiseProgram, "u_TexelSize"), 1f / targetFbo.width, 1f / targetFbo.height)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(denoiseProgram, "u_SpatialSigma"), spatialSigma)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(denoiseProgram, "u_RangeSigma"), rangeSigma)

        bindTexture(textureId)
        GlShaderUtil.drawFullscreenQuad(denoiseProgram)
        targetFbo.unbind()
        return targetFbo.getTextureId()
    }

    fun applySharpen(textureId: Int, targetFbo: GlFramebuffer, amount: Float = 1.2f): Int {
        if (!isInitialized) init()
        targetFbo.bind()
        GLES20.glUseProgram(unsharpProgram)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(unsharpProgram, "u_TexelSize"), 1f / targetFbo.width, 1f / targetFbo.height)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(unsharpProgram, "u_Amount"), amount)

        bindTexture(textureId)
        GlShaderUtil.drawFullscreenQuad(unsharpProgram)
        targetFbo.unbind()
        return targetFbo.getTextureId()
    }

    private fun bindTexture(textureId: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    }

    fun release() {
        if (sobelProgram != 0) {
            GLES20.glDeleteProgram(sobelProgram)
            GLES20.glDeleteProgram(denoiseProgram)
            GLES20.glDeleteProgram(unsharpProgram)
            sobelProgram = 0
            isInitialized = false
        }
    }
}
