package com.ahstudio.face.rendering

import android.opengl.GLES20.*
import com.ahstudio.face.geometry.VideoTransform
import com.ahstudio.face.gl.GlUtil
import com.ahstudio.face.overlay.BlendMode
import com.ahstudio.face.overlay.OverlayPlacement
import java.nio.FloatBuffer

data class OverlayDrawItem(val placement: OverlayPlacement, val textureId: Int, val aspect: Float)

class FaceOverlayRenderPass {

    private var program = 0
    private var aPos = 0; private var aUv = 0
    private var uCenter = 0; private var uHalf = 0; private var uRot = 0
    private var uOpacity = 0; private var uMirror = 0; private var uTex = 0

    private val quad: FloatBuffer = GlUtil.direct(floatArrayOf(
        -1f, -1f, 0f, 1f,
         1f, -1f, 1f, 1f,
        -1f,  1f, 0f, 0f,
         1f,  1f, 1f, 0f))

    private val vs = """
        attribute vec2 aPos;
        attribute vec2 aUv;
        uniform vec2 uCenter;
        uniform vec2 uHalf;
        uniform float uRot;
        varying vec2 vUv;
        void main() {
            float c = cos(uRot), s = sin(uRot);
            vec2 p = vec2(aPos.x * c - aPos.y * s, aPos.x * s + aPos.y * c);
            gl_Position = vec4(uCenter + p * uHalf, 0.0, 1.0);
            vUv = aUv;
        }""".trimIndent()

    private val fs = """
        precision mediump float;
        uniform sampler2D uTex;
        uniform float uOpacity;
        uniform float uMirror;
        varying vec2 vUv;
        void main() {
            vec2 uv = vec2(mix(vUv.x, 1.0 - vUv.x, uMirror), vUv.y);
            vec4 c = texture2D(uTex, uv);
            gl_FragColor = vec4(c.rgb * c.a, c.a) * uOpacity;
        }""".trimIndent()

    fun ensureGl() {
        if (program != 0) return
        program = GlUtil.createProgram(vs, fs)
        aPos = glGetAttribLocation(program, "aPos")
        aUv = glGetAttribLocation(program, "aUv")
        uCenter = glGetUniformLocation(program, "uCenter")
        uHalf = glGetUniformLocation(program, "uHalf")
        uRot = glGetUniformLocation(program, "uRot")
        uOpacity = glGetUniformLocation(program, "uOpacity")
        uMirror = glGetUniformLocation(program, "uMirror")
        uTex = glGetUniformLocation(program, "uTex")
    }

    fun render(transform: VideoTransform, items: List<OverlayDrawItem>) {
        ensureGl()
        glUseProgram(program)
        glEnable(GL_BLEND)
        glDisable(GL_DEPTH_TEST)
        val vw = transform.viewportWidth.toFloat()
        val vh = transform.viewportHeight.toFloat()
        items.sortedBy { it.placement.zOrder }.forEach { item ->
            val pl = item.placement
            val centerClip = transform.viewportToClip(transform.toViewport(pl.centerFrame))
            val widthPx = pl.widthFrame * transform.visibleFrameWidthPx
            val halfWClip = widthPx / vw
            val halfHClip = (widthPx / item.aspect) / vh
            when (pl.blendMode) {
                BlendMode.NORMAL -> glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
                BlendMode.ADDITIVE -> glBlendFunc(GL_ONE, GL_ONE)
                else -> glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
            }
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, item.textureId)
            glUniform1i(uTex, 0)
            glUniform2f(uCenter, centerClip.x, centerClip.y)
            glUniform2f(uHalf, halfWClip, halfHClip)
            glUniform1f(uRot, Math.toRadians(pl.rotationDeg.toDouble()).toFloat())
            glUniform1f(uOpacity, pl.opacity)
            glUniform1f(uMirror, if (pl.mirrored) 1f else 0f)
            quad.position(0)
            glVertexAttribPointer(aPos, 2, GL_FLOAT, false, 16, quad)
            glEnableVertexAttribArray(aPos)
            quad.position(2)
            glVertexAttribPointer(aUv, 2, GL_FLOAT, false, 16, quad)
            glEnableVertexAttribArray(aUv)
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
            glDisableVertexAttribArray(aPos)
            glDisableVertexAttribArray(aUv)
        }
        glDisable(GL_BLEND)
        glBindTexture(GL_TEXTURE_2D, 0)
    }
}
