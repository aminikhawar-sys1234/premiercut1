package com.ute.gpu

import android.opengl.GLES30.*
import com.ute.core.Mat4
import com.ute.core.Safe
import com.ute.device.DeviceCapabilities
import com.ute.glyphs.GlyphAtlas
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Instanced SDF glyph renderer. ONE draw call per layer renders every glyph
 * with fill/outline/shadow/glow/mask/deform. Per-instance attributes via
 * glVertexAttribDivisor (GLES3).
 */
class GlTextRenderer(private val atlas: GlyphAtlas, private val caps: DeviceCapabilities) {

    private val floatsPerInstance = 22
    private var program = -1
    private var quadVbo = -1
    private var quadVao = -1
    private var instanceVbo = -1

    fun ensure() {
        if (program > 0) return
        program = GlUtil.compileProgram(Shaders.TEXT_VERT, Shaders.TEXT_FRAG, "sdf_text")
        quadVao = glGenVertexArraysSafe()
        glBindVertexArray(quadVao)

        quadVbo = glGenBuffersSafe()
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo)
        val corners = floatArrayOf(
            -0.5f, -0.5f,
             0.5f, -0.5f,
            -0.5f,  0.5f,
             0.5f,  0.5f
        )
        glBufferData(GL_ARRAY_BUFFER, corners.size * 4, GlUtil.floatBuffer(corners), GL_STATIC_DRAW)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0)
        glEnableVertexAttribArray(0)
        glVertexAttribDivisor(0, 0)

        instanceVbo = glGenBuffersSafe()
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo)
        bindInstanceAttrib(1, 4, 0)
        bindInstanceAttrib(2, 4, 16)
        bindInstanceAttrib(3, 4, 32)
        bindInstanceAttrib(4, 4, 48)
        bindInstanceAttrib(5, 4, 64)
        bindInstanceAttrib(6, 2, 80)
        for (loc in 1..6) glVertexAttribDivisor(loc, 1)

        glBindVertexArray(0)
    }

    private fun bindInstanceAttrib(loc: Int, size: Int, byteOffset: Int) {
        glEnableVertexAttribArray(loc)
        glVertexAttribPointer(loc, size, GL_FLOAT, false, floatsPerInstance * 4, byteOffset)
    }

    fun drawLayer(
        instances: FloatArray, instanceCount: Int,
        viewProj: Mat4, layerMatrix: Mat4,
        styleParams: StyleUniforms,
    ): Boolean = Safe.critical("drawText", false) {
        if (instanceCount <= 0) return@critical false
        ensure()
        val fb = floatBufferFor(instances)

        glUseProgram(program)
        atlas.bindAlpha(0)
        glUniform1i(uLoc("uAtlas"), 0)
        glUniformMatrix4fv(uLoc("uViewProj"), 1, false, viewProj.m, 0)
        glUniformMatrix4fv(uLoc("uLayerMatrix"), 1, false, layerMatrix.m, 0)

        glUniform4f(uLoc("uOutlineColor"), r(styleParams.outlineColor), g(styleParams.outlineColor),
            b(styleParams.outlineColor), a(styleParams.outlineColor) * styleParams.outlineOpacity)
        glUniform1f(uLoc("uOutlineWidthSdf"), styleParams.outlineWidthSdf)
        glUniform4f(uLoc("uShadowColor"), r(styleParams.shadowColor), g(styleParams.shadowColor),
            b(styleParams.shadowColor), a(styleParams.shadowColor) * styleParams.shadowOpacity)
        glUniform2f(uLoc("uShadowOffsetUv"), styleParams.shadowOffsetUvX, styleParams.shadowOffsetUvY)
        glUniform4f(uLoc("uGlowParams"), styleParams.glowStrength, styleParams.glowFalloffPx,
            if (styleParams.glowEnabled) 1f else 0f, 0f)
        glUniform4f(uLoc("uGlowColor"), r(styleParams.glowColor), g(styleParams.glowColor),
            b(styleParams.glowColor), 1f)

        if (styleParams.gradientTex > 0) {
            glUniform1i(uLoc("uUseGradient"), 1)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, styleParams.gradientTex)
            glUniform1i(uLoc("uGradientLut"), 2)
        } else {
            glUniform1i(uLoc("uUseGradient"), 0)
        }

        glUniform1i(uLoc("uDeformMode"), styleParams.deformMode)
        glUniform1f(uLoc("uDeformAmount"), styleParams.deformAmount)
        glUniform1f(uLoc("uDeformRadius"), styleParams.deformRadius)
        glUniform1f(uLoc("uDeformFreq"), styleParams.deformFreq)
        glUniform1f(uLoc("uDeformPhase"), styleParams.deformPhase)

        glUniform1i(uLoc("uMaskMode"), styleParams.maskMode)
        glUniform4f(uLoc("uMaskRect"), styleParams.maskCenterX, styleParams.maskCenterY,
            styleParams.maskHalfW, styleParams.maskHalfH)
        glUniform1f(uLoc("uMaskFeather"), styleParams.maskFeather)
        glUniform1f(uLoc("uMaskInvert"), if (styleParams.maskInvert) 1f else 0f)
        glUniform2f(uLoc("uLayerBoxHalf"), styleParams.boxHalfW, styleParams.boxHalfH)

        glEnable(GL_BLEND)
        glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_DEPTH_TEST)

        glBindVertexArray(quadVao)
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo)
        GlUtil.uploadStream(instanceVbo, fb)

        bindInstanceAttrib(1, 4, 0)
        bindInstanceAttrib(2, 4, 16)
        bindInstanceAttrib(3, 4, 32)
        bindInstanceAttrib(4, 4, 48)
        bindInstanceAttrib(5, 4, 64)
        bindInstanceAttrib(6, 2, 80)

        glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, instanceCount)
        glBindVertexArray(0)
        true
    }

    private fun floatBufferFor(data: FloatArray): FloatBuffer {
        val fb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(data)
        fb.position(0)
        return fb
    }

    private fun uLoc(name: String) = glGetUniformLocation(program, name)
    private fun r(c: Int) = ((c shr 16) and 0xFF) / 255f
    private fun g(c: Int) = ((c shr 8) and 0xFF) / 255f
    private fun b(c: Int) = (c and 0xFF) / 255f
    private fun a(c: Int) = ((c ushr 24) and 0xFF) / 255f

    data class StyleUniforms(
        val outlineColor: Int, val outlineOpacity: Float, val outlineWidthSdf: Float,
        val shadowColor: Int, val shadowOpacity: Float, val shadowOffsetUvX: Float, val shadowOffsetUvY: Float,
        val glowEnabled: Boolean, val glowStrength: Float, val glowFalloffPx: Float, val glowColor: Int,
        val gradientTex: Int,
        val deformMode: Int, val deformAmount: Float, val deformRadius: Float,
        val deformFreq: Float, val deformPhase: Float,
        val maskMode: Int, val maskCenterX: Float, val maskCenterY: Float,
        val maskHalfW: Float, val maskHalfH: Float, val maskFeather: Float, val maskInvert: Boolean,
        val boxHalfW: Float, val boxHalfH: Float,
    )

    object Shaders {
        const val TEXT_VERT = """#version 300 es
layout(location=0) in vec2 aCorner;
layout(location=1) in vec4 aAtlasRect;
layout(location=2) in vec4 aGlyph;
layout(location=3) in vec4 aFill;
layout(location=4) in vec4 aMotion;
layout(location=5) in vec4 aMisc;

uniform mat4 uViewProj;
uniform mat4 uLayerMatrix;
uniform int   uDeformMode;
uniform float uDeformAmount;
uniform float uDeformRadius;
uniform float uDeformFreq;
uniform float uDeformPhase;

out vec2 vUv;
out vec4 vFill;
out vec4 vMisc;
out vec2 vLayerPos;

void main() {
    vec2 local = aCorner * aGlyph.zw * aMotion.zw;
    vec2 rotated = vec2(local.x * aMotion.y - local.y * aMotion.x,
                        local.x * aMotion.x + local.y * aMotion.y);
    vec2 p = rotated + aGlyph.xy;

    if (uDeformMode == 1) {
        float theta = p.x / max(uDeformRadius, 1.0) * uDeformAmount * 3.0;
        float r = max(uDeformRadius, 1.0);
        p = vec2(sin(theta) * r, p.y + (r - cos(theta) * r) * uDeformAmount);
    } else if (uDeformMode == 2) {
        p.y += sin(p.x * uDeformFreq * 0.02 + uDeformPhase) * uDeformAmount * 30.0;
    } else if (uDeformMode == 3) {
        float r = length(p);
        p *= mix(1.0, 1.0 + uDeformAmount * 40.0 / (r + 60.0), step(0.0001, r));
    } else if (uDeformMode == 4) {
        float r = length(p);
        float f = 1.0 + uDeformAmount * exp(-r / max(uDeformRadius, 1.0));
        p *= f;
    }

    vUv = aAtlasRect.xy + (aCorner + 0.5) * aAtlasRect.zw;
    vFill = aFill;
    vMisc = aMisc;
    vLayerPos = aGlyph.xy + rotated;
    gl_Position = uViewProj * uLayerMatrix * vec4(p, 0.0, 1.0);
}
"""

        const val TEXT_FRAG = """#version 300 es
precision highp float;

in vec2 vUv;
in vec4 vFill;
in vec4 vMisc;
in vec2 vLayerPos;

uniform sampler2D uAtlas;
uniform sampler2D uGradientLut;
uniform int   uUseGradient;
uniform vec4  uOutlineColor;
uniform float uOutlineWidthSdf;
uniform vec4  uShadowColor;
uniform vec2  uShadowOffsetUv;
uniform vec4  uGlowParams;
uniform vec4  uGlowColor;
uniform int   uMaskMode;
uniform vec4  uMaskRect;
uniform float uMaskFeather;
uniform float uMaskInvert;
uniform vec2  uLayerBoxHalf;

out vec4 fragColor;

vec4 over(vec4 src, vec4 dst) {
    float a = src.a + dst.a * (1.0 - src.a);
    vec3 c = (src.rgb * src.a + dst.rgb * dst.a * (1.0 - src.a)) / max(a, 1e-5);
    return vec4(c, a);
}

float rectSdf(vec2 p, vec2 halfSize, float r) {
    vec2 d = abs(p) - halfSize + r;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}
float ellipseSdf(vec2 p, vec2 ab) {
    float k1 = length(p / ab);
    float k2 = length(p / (ab * ab));
    return k1 * (k1 - 1.0) / max(k2, 1e-5);
}

void main() {
    float d = texture(uAtlas, vUv).r;

    float w = fwidth(d) + 1e-4;
    float alpha = vMisc.x;
    float fillA = smoothstep(0.5 - w, 0.5 + w, d) * alpha;

    float outlineA = smoothstep(0.5 - 2.0 * w, 0.5 + w, d + uOutlineWidthSdf)
                     * uOutlineColor.a * alpha;

    float sd = texture(uAtlas, vUv - uShadowOffsetUv).r;
    float shadowA = smoothstep(0.5 - 1.5 * w, 0.5 + w, sd) * uShadowColor.a * alpha;

    float distOutsidePx = (0.5 - d) * 2.0 * vMisc.z;
    float glow = uGlowParams.z * alpha *
                 exp(-max(distOutsidePx, 0.0) / max(uGlowParams.y, 1.0)) * uGlowParams.x;
    vec3 glowCol = uGlowColor.rgb;

    vec4 fillCol = vFill;
    if (uUseGradient == 1) {
        fillCol = vec4(texture(uGradientLut, vec2(vMisc.y, 0.5)).rgb, fillCol.a);
    }

    vec4 c = vec4(0.0);
    c = over(vec4(uShadowColor.rgb, shadowA), c);
    c = over(vec4(glowCol, glow), c);
    c = over(vec4(uOutlineColor.rgb, outlineA), c);
    c = over(vec4(fillCol.rgb, fillA), c);

    if (uMaskMode > 0) {
        vec2 mp = vLayerPos - uMaskRect.xy;
        float msd = (uMaskMode == 1)
            ? rectSdf(mp, uMaskRect.zw, 0.0)
            : ellipseSdf(mp, uMaskRect.zw);
        float feather = max(uMaskFeather, 1.0);
        float m = smoothstep(feather, -feather, msd);
        if (uMaskInvert > 0.5) m = 1.0 - m;
        c.a *= m;
    }

    if (c.a < 0.003) discard;
    fragColor = c;
}
"""

        const val POST_VERT = """#version 300 es
layout(location=0) in vec2 aPos;
out vec2 vUv;
void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }
"""

        const val POST_FRAG = """#version 300 es
precision mediump float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTex;
uniform vec2 uDir;
void main() {
    float w0 = 0.227027, w1 = 0.1945946, w2 = 0.1216216, w3 = 0.054054, w4 = 0.016216;
    vec4 c = texture(uTex, vUv) * w0;
    c += (texture(uTex, vUv + uDir * 1.3846) + texture(uTex, vUv - uDir * 1.3846)) * w1;
    c += (texture(uTex, vUv + uDir * 3.2308) + texture(uTex, vUv - uDir * 3.2308)) * w2;
    c += (texture(uTex, vUv + uDir * 5.0769) + texture(uTex, vUv - uDir * 5.0769)) * w3;
    c += (texture(uTex, vUv + uDir * 6.9231) + texture(uTex, vUv - uDir * 6.9231)) * w4;
    fragColor = c;
}
"""
    }

    private fun glGenVertexArraysSafe(): Int {
        val ids = IntArray(1); glGenVertexArrays(1, ids, 0); return ids[0]
    }
    private fun glGenBuffersSafe(): Int {
        val ids = IntArray(1); glGenBuffers(1, ids, 0); return ids[0]
    }
}
