package com.ahstudio.face.rendering

import android.opengl.GLES20.*
import com.ahstudio.face.core.TrackedFace
import com.ahstudio.face.gl.FullscreenQuad
import com.ahstudio.face.gl.GlUtil

class FaceMaskBaker(private val size: Int = 256) {

    private var program = 0
    private var aPos = 0
    private var uCount = 0; private var uAspect = 0; private var uFeather = 0
    private var uRect = 0; private var uRot = 0
    private var tex = 0; private var fbo = 0
    private val quad = FullscreenQuad()

    private val vs = """
        attribute vec2 aPos;
        varying vec2 vUv;
        void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }""".trimIndent()

    private val fs = """
        precision mediump float;
        varying vec2 vUv;
        uniform int uCount;
        uniform float uAspect;
        uniform float uFeather;
        uniform vec4 uRect[8];
        uniform vec3 uRot[8];
        float sdBox(vec2 p, vec2 b) {
            vec2 d = abs(p) - b;
            return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
        }
        void main() {
            float cover = 0.0;
            vec2 p = vec2(vUv.x * uAspect, vUv.y);
            for (int i = 0; i < 8; i++) {
                if (i >= uCount) break;
                vec2 d = p - uRect[i].xy;
                vec2 rp = vec2(uRot[i].x * d.x + uRot[i].y * d.y, -uRot[i].y * d.x + uRot[i].x * d.y);
                float cr = uRot[i].z;
                float sd = sdBox(rp, max(uRect[i].zw - cr, vec2(0.001))) - cr;
                cover = max(cover, 1.0 - smoothstep(0.0, uFeather, sd));
            }
            gl_FragColor = vec4(cover, 0.0, 0.0, 1.0);
        }""".trimIndent()

    fun ensureGl() {
        if (program != 0) return
        program = GlUtil.createProgram(vs, fs)
        aPos = glGetAttribLocation(program, "aPos")
        uCount = glGetUniformLocation(program, "uCount")
        uAspect = glGetUniformLocation(program, "uAspect")
        uFeather = glGetUniformLocation(program, "uFeather")
        uRect = glGetUniformLocation(program, "uRect")
        uRot = glGetUniformLocation(program, "uRot")
        tex = glCreateTexture()
        glBindTexture(GL_TEXTURE_2D, tex)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        fbo = glCreateFramebuffer()
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun bake(faces: List<TrackedFace>, aspect: Float, softness: Float): Int {
        ensureGl()
        if (faces.isEmpty() || aspect <= 0f) return 0
        val feather = (0.02f + 0.12f * softness.coerceIn(0f, 1f)).coerceAtLeast(0.001f)
        val rectA = FloatArray(32)
        val rotA = FloatArray(24)
        val count = faces.take(8).size
        faces.take(8).forEachIndexed { i, f ->
            val b = f.bounds
            rectA[i * 4 + 0] = b.centerX * aspect
            rectA[i * 4 + 1] = b.centerY
            rectA[i * 4 + 2] = (b.width / 2f) * aspect
            rectA[i * 4 + 3] = b.height / 2f
            val rad = Math.toRadians(-f.rotation.eulerZ.toDouble())
            rotA[i * 3 + 0] = kotlin.math.cos(rad).toFloat()
            rotA[i * 3 + 1] = kotlin.math.sin(rad).toFloat()
            rotA[i * 3 + 2] = minOf(rectA[i * 4 + 2], rectA[i * 4 + 3]) * 0.35f
        }

        val vp = IntArray(4).also { glGetIntegerv(GL_VIEWPORT, it, 0) }
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        glViewport(0, 0, size, size)
        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)
        glUseProgram(program)
        glUniform1i(uCount, count)
        glUniform1f(uAspect, aspect)
        glUniform1f(uFeather, feather)
        glUniform4fv(uRect, 8, rectA, 0)
        glUniform3fv(uRot, 8, rotA, 0)
        quad.draw(aPos)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glViewport(vp[0], vp[1], vp[2], vp[3])
        return tex
    }

    private fun glCreateTexture(): Int {
        val ids = IntArray(1); glGenTextures(1, ids, 0); return ids[0]
    }
    private fun glCreateFramebuffer(): Int {
        val ids = IntArray(1); glGenFramebuffers(1, ids, 0); return ids[0]
    }
}
