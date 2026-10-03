package com.ahstudio.transition.gl

import android.opengl.GLES30
import com.ahstudio.transition.core.TransitionError
import com.ahstudio.transition.core.TransitionResult
import com.ahstudio.transition.diag.TransitionDiagnostics

/**
 * Pooled RGBA8 framebuffer + color texture pairs (§12). Exact-size reuse; idle budget
 * with oldest eviction; full teardown on context loss. Never allocate-per-frame.
 */
class FramebufferPool(private val diagnostics: TransitionDiagnostics,
                      private val maxIdleTotal: Int = 12) {

    class PooledFramebuffer internal constructor(
        val framebuffer: Int, val texture: Int, val width: Int, val height: Int
    ) {
        fun bindAndViewport() {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
            GLES30.glViewport(0, 0, width, height)
        }
    }

    private val idle = ArrayDeque<PooledFramebuffer>()

    var liveCount = 0
        private set
    var poolHits = 0L
        private set
    var poolMisses = 0L
        private set
    val idleCount: Int get() = idle.size

    fun acquire(width: Int, height: Int): TransitionResult<PooledFramebuffer> {
        val match = idle.firstOrNull { it.width == width && it.height == height }
        if (match != null) {
            idle.remove(match)
            poolHits++
            return TransitionResult.Ok(match)
        }
        poolMisses++
        return create(width, height)
    }

    private fun create(width: Int, height: Int): TransitionResult<PooledFramebuffer> {
        val tex = IntArray(1); GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val fbo = IntArray(1); GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, tex[0], 0)
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glDeleteFramebuffers(1, fbo, 0)
            GLES30.glDeleteTextures(1, tex, 0)
            val e = TransitionError.ResourceAllocation(
                "Incomplete FBO ${width}x$height (status 0x${Integer.toHexString(status)})")
            diagnostics.onError(e)
            return TransitionResult.Err(e)
        }
        liveCount++
        diagnostics.log("FBO created ${width}x$height (live=$liveCount)")
        return TransitionResult.Ok(PooledFramebuffer(fbo[0], tex[0], width, height))
    }

    fun release(fb: PooledFramebuffer) {
        if (idle.size >= maxIdleTotal) destroy(fb) else idle.addLast(fb)
    }

    fun destroyAll() {
        val toDestroy = idle.toList()
        idle.clear()
        toDestroy.forEach { destroy(it) }
    }

    private fun destroy(fb: PooledFramebuffer) {
        GLES30.glDeleteFramebuffers(1, intArrayOf(fb.framebuffer), 0)
        GLES30.glDeleteTextures(1, intArrayOf(fb.texture), 0)
        liveCount--
    }
}
