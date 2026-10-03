package com.ute.gpu

import android.opengl.GLES30.*

/**
 * Multi-pass GPU post effects used by glow bloom and blur: renders to a
 * half-resolution FBO and applies two separable Gaussian passes.
 * Only allocated when a layer actually needs it (lazy, pooled, evicted).
 */
class GlPostChain(private val viewportW: Int, private val viewportH: Int) {

    private val fboA = GlUtil.Framebuffer(maxOf(1, viewportW / 2), maxOf(1, viewportH / 2))
    private val fboB = GlUtil.Framebuffer(maxOf(1, viewportW / 2), maxOf(1, viewportH / 2))
    private var blurProgram = -1
    private var quadVao = -1

    fun ensure() {
        if (blurProgram > 0) return
        blurProgram = GlUtil.compileProgram(GlTextRenderer.Shaders.POST_VERT,
            GlTextRenderer.Shaders.POST_FRAG, "post_blur")
        val vaoIds = IntArray(1); glGenVertexArrays(1, vaoIds, 0); quadVao = vaoIds[0]
        glBindVertexArray(quadVao)
        val vboIds = IntArray(1); glGenBuffers(1, vboIds, 0); val vbo = vboIds[0]
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, 8 * 4, GlUtil.floatBuffer(
            floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)), GL_STATIC_DRAW)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0)
        glBindVertexArray(0)
    }

    /** blur [srcTex] with [sigmaScale] passes; returns blurred texture id (B). */
    fun blur(srcTex: Int, sigmaScale: Float): Int {
        ensure()
        val fw = 1f / fboA.width; val fh = 1f / fboA.height
        glUseProgram(blurProgram)
        glUniform1i(glGetUniformLocation(blurProgram, "uTex"), 0)
        glActiveTexture(GL_TEXTURE0)

        // H pass → A
        fboA.bind()
        glBindTexture(GL_TEXTURE_2D, srcTex)
        glUniform2f(glGetUniformLocation(blurProgram, "uDir"), fw * sigmaScale, 0f)
        drawQuad()

        // V pass → B
        fboB.bind()
        glBindTexture(GL_TEXTURE_2D, fboA.texture())
        glUniform2f(glGetUniformLocation(blurProgram, "uDir"), 0f, fh * sigmaScale)
        drawQuad()

        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        return fboB.texture()
    }

    private fun drawQuad() {
        glBindVertexArray(quadVao)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glBindVertexArray(0)
    }
}
