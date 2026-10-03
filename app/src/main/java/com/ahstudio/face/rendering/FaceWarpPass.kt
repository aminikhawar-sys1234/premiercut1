package com.ahstudio.face.rendering

import android.opengl.GLES20.*
import com.ahstudio.face.deformation.WarpOp
import com.ahstudio.face.deformation.WarpType
import com.ahstudio.face.gl.FullscreenQuad
import com.ahstudio.face.gl.GlUtil

class FaceWarpPass {

    companion object { const val MAX_OPS = 8 }

    private var program = 0
    private var aPos = 0
    private var uTex = 0; private var uAspect = 0; private var uOpCount = 0
    private var uOpA = 0; private var uOpB = 0
    private val quad = FullscreenQuad()

    private val vs = """
        attribute vec2 aPos;
        varying vec2 vUv;
        void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }""".trimIndent()

    private val fs = """
        precision highp float;
        varying vec2 vUv;
        uniform sampler2D uTex;
        uniform float uAspect;
        uniform int uOpCount;
        uniform vec4 uOpA[8];
        uniform vec3 uOpB[8];
        void main() {
            vec2 p = vUv;
            for (int i = 0; i < 8; i++) {
                if (i >= uOpCount) break;
                vec2 c = uOpA[i].xy;
                float r = uOpA[i].z;
                float s = uOpA[i].w;
                vec2 d = vec2((p.x - c.x) * uAspect, p.y - c.y);
                float dist = length(d);
                float fall = exp(-(dist * dist) / (r * r));
                if (uOpB[i].x < 0.5) {
                    p = c + vec2(d.x / uAspect, d.y) / (1.0 + s * fall);
                } else if (uOpB[i].x < 1.5) {
                    p += vec2(uOpB[i].y / uAspect, uOpB[i].z) * s * fall;
                } else {
                    p -= vec2(uOpB[i].y / uAspect, uOpB[i].z) * s * fall;
                }
            }
            gl_FragColor = texture2D(uTex, p);
        }""".trimIndent()

    fun ensureGl() {
        if (program != 0) return
        program = GlUtil.createProgram(vs, fs)
        aPos = glGetAttribLocation(program, "aPos")
        uTex = glGetUniformLocation(program, "uTex")
        uAspect = glGetUniformLocation(program, "uAspect")
        uOpCount = glGetUniformLocation(program, "uOpCount")
        uOpA = glGetUniformLocation(program, "uOpA")
        uOpB = glGetUniformLocation(program, "uOpB")
    }

    fun render(inputTex: Int, outputFbo: Int, width: Int, height: Int, ops: List<WarpOp>) {
        ensureGl()
        if (ops.isEmpty()) return
        val aspect = width.toFloat() / height
        val arrA = FloatArray(MAX_OPS * 4)
        val arrB = FloatArray(MAX_OPS * 3)
        val count = ops.take(MAX_OPS).size
        ops.take(MAX_OPS).forEachIndexed { i, op ->
            arrA[i * 4 + 0] = op.center.x
            arrA[i * 4 + 1] = op.center.y
            arrA[i * 4 + 2] = (op.radius / aspect).coerceAtLeast(0.005f)
            arrA[i * 4 + 3] = op.strength
            arrB[i * 3 + 0] = when (op.type) {
                WarpType.MAGNIFY_EYE -> 0f
                WarpType.SLIM_LATERAL -> 1f
                WarpType.PUSH -> 2f
            }
            arrB[i * 3 + 1] = op.dir.x
            arrB[i * 3 + 2] = op.dir.y
        }

        val vp = IntArray(4); glGetIntegerv(GL_VIEWPORT, vp, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, outputFbo)
        glViewport(0, 0, width, height)
        glUseProgram(program)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, inputTex); glUniform1i(uTex, 0)
        glUniform1f(uAspect, aspect)
        glUniform1i(uOpCount, count)
        glUniform4fv(uOpA, MAX_OPS, arrA, 0)
        glUniform3fv(uOpB, MAX_OPS, arrB, 0)
        quad.draw(aPos)
        glBindTexture(GL_TEXTURE_2D, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glViewport(vp[0], vp[1], vp[2], vp[3])
    }
}
