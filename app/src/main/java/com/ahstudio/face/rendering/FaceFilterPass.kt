package com.ahstudio.face.rendering

import android.opengl.GLES20.*
import com.ahstudio.face.gl.FullscreenQuad
import com.ahstudio.face.gl.GlUtil

class FaceFilterPass {

    private var program = 0
    private var aPos = 0
    private var uTex = 0; private var uMask = 0
    private var uMode = 0; private var uStrength = 0
    private var uBrightness = 0; private var uContrast = 0
    private var uTexel = 0; private var uAspect = 0
    private val quad = FullscreenQuad()

    private val vs = """
        attribute vec2 aPos;
        varying vec2 vUv;
        void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }""".trimIndent()

    private val fs = """
        precision mediump float;
        varying vec2 vUv;
        uniform sampler2D uTex;
        uniform sampler2D uMask;
        uniform int uMode;
        uniform float uStrength;
        uniform float uBrightness;
        uniform float uContrast;
        uniform vec2 uTexel;
        uniform float uAspect;
        float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
        vec3 blur9(vec2 uv) {
            vec3 s = vec3(0.0);
            for (int y = -1; y <= 1; y++)
                for (int x = -1; x <= 1; x++)
                    s += texture2D(uTex, uv + vec2(float(x), float(y)) * uTexel * 1.5).rgb;
            return s / 9.0;
        }
        void main() {
            float m = texture2D(uMask, vUv).r * uStrength;
            vec3 c = texture2D(uTex, vUv).rgb;
            vec3 e = c;
            if (uMode == 0) { e = blur9(vUv); }
            else if (uMode == 1) {
                float n = 48.0;
                float rows = n / uAspect;
                vec2 q = vec2(floor(vUv.x * n) / n, floor(vUv.y * rows) / rows);
                e = texture2D(uTex, q).rgb;
            }
            else if (uMode == 2) {
                vec3 b = blur9(vUv);
                float w = smoothstep(0.22, 0.04, abs(luma(b) - luma(c)));
                e = mix(c, b, w);
            }
            else if (uMode == 3) { e = (c - 0.5) * (1.0 + uContrast) + 0.5 + uBrightness; }
            else if (uMode == 4) { e = c + blur9(vUv) * 0.8; }
            gl_FragColor = vec4(mix(c, e, clamp(m, 0.0, 1.0)), 1.0);
        }""".trimIndent()

    fun ensureGl() {
        if (program != 0) return
        program = GlUtil.createProgram(vs, fs)
        aPos = glGetAttribLocation(program, "aPos")
        uTex = glGetUniformLocation(program, "uTex")
        uMask = glGetUniformLocation(program, "uMask")
        uMode = glGetUniformLocation(program, "uMode")
        uStrength = glGetUniformLocation(program, "uStrength")
        uBrightness = glGetUniformLocation(program, "uBrightness")
        uContrast = glGetUniformLocation(program, "uContrast")
        uTexel = glGetUniformLocation(program, "uTexel")
        uAspect = glGetUniformLocation(program, "uAspect")
    }

    fun render(inputTex: Int, maskTex: Int, outputFbo: Int, width: Int, height: Int,
               mode: Int, strength: Float, brightness: Float = 0f, contrast: Float = 0f) {
        ensureGl()
        if (maskTex == 0 || strength <= 0f) return
        val vp = IntArray(4); glGetIntegerv(GL_VIEWPORT, vp, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, outputFbo)
        glViewport(0, 0, width, height)
        glUseProgram(program)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, inputTex); glUniform1i(uTex, 0)
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, maskTex); glUniform1i(uMask, 1)
        glUniform1i(uMode, mode)
        glUniform1f(uStrength, strength)
        glUniform1f(uBrightness, brightness)
        glUniform1f(uContrast, contrast)
        glUniform2f(uTexel, 1f / width, 1f / height)
        glUniform1f(uAspect, width.toFloat() / height)
        quad.draw(aPos)
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, 0)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glViewport(vp[0], vp[1], vp[2], vp[3])
    }
}
