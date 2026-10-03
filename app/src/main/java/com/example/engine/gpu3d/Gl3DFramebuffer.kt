package com.example.engine.gpu3d

import android.opengl.GLES30
import android.util.Log

/**
 * High-performance 3D Framebuffer with full Color Attachment and GL_DEPTH24_STENCIL8 Renderbuffer.
 * Enables true hardware 3D depth-sorting, stencil masking, and offscreen layer compositing.
 */
class Gl3DFramebuffer {
    companion object {
        private const val TAG = "Gl3DFramebuffer"
    }

    private var fboId = 0
    private var colorTextureId = 0
    private var depthStencilRboId = 0

    var width: Int = 0
        private set
    var height: Int = 0
        private set

    fun setup(w: Int, h: Int) {
        if (w == width && h == height && fboId != 0) return
        release()

        width = w
        height = h

        // 1. Color Texture Attachment
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        colorTextureId = textures[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
            width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )

        // 2. Depth + Stencil Renderbuffer Attachment (GL_DEPTH24_STENCIL8 with GL_DEPTH_COMPONENT16 fallback)
        val rbos = IntArray(1)
        GLES30.glGenRenderbuffers(1, rbos, 0)
        depthStencilRboId = rbos[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthStencilRboId)

        // 3. Create & Bind FBO
        val fbos = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        fboId = fbos[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)

        // Attach Color Texture
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            colorTextureId,
            0
        )

        // Try packed depth24 stencil8 first
        var depthAttached = false
        try {
            GLES30.glRenderbufferStorage(
                GLES30.GL_RENDERBUFFER,
                GLES30.GL_DEPTH24_STENCIL8,
                width, height
            )
            GLES30.glFramebufferRenderbuffer(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_DEPTH_STENCIL_ATTACHMENT,
                GLES30.GL_RENDERBUFFER,
                depthStencilRboId
            )
            if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
                depthAttached = true
            }
        } catch (_: Throwable) {}

        // Fallback to standard 16-bit depth buffer if packed depth-stencil is unsupported
        if (!depthAttached) {
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthStencilRboId)
            GLES30.glRenderbufferStorage(
                GLES30.GL_RENDERBUFFER,
                GLES30.GL_DEPTH_COMPONENT16,
                width, height
            )
            GLES30.glFramebufferRenderbuffer(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_DEPTH_ATTACHMENT,
                GLES30.GL_RENDERBUFFER,
                depthStencilRboId
            )
        }

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "Gl3DFramebuffer setup incomplete: status=$status")
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    /**
     * Binds this FBO as active render target and enables hardware Depth Testing.
     */
    fun bind(clearColor: Boolean = true, clearDepth: Boolean = true) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glViewport(0, 0, width, height)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

        var clearMask = 0
        if (clearColor) clearMask = clearMask or GLES30.GL_COLOR_BUFFER_BIT
        if (clearDepth) clearMask = clearMask or GLES30.GL_DEPTH_BUFFER_BIT or GLES30.GL_STENCIL_BUFFER_BIT

        if (clearMask != 0) {
            GLES30.glClear(clearMask)
        }
    }

    fun unbind() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun getColorTextureId(): Int = colorTextureId

    fun release() {
        if (fboId != 0) {
            val fbos = intArrayOf(fboId)
            val textures = intArrayOf(colorTextureId)
            val rbos = intArrayOf(depthStencilRboId)

            GLES30.glDeleteFramebuffers(1, fbos, 0)
            GLES30.glDeleteTextures(1, textures, 0)
            GLES30.glDeleteRenderbuffers(1, rbos, 0)

            fboId = 0
            colorTextureId = 0
            depthStencilRboId = 0
            width = 0
            height = 0
        }
    }
}
