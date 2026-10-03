package com.vfx.engine.gpu

import android.opengl.GLES30
import com.vfx.engine.core.EffectEngineException

/** FBO + attached color texture. Acquired from [FramebufferPool], returned after use. */
class FramebufferObject(val texture: GpuTexture) {

    var handle: Int = 0; private set
    var checkedOut = false; internal set
    internal var pooled = true

    val width: Int get() = texture.width
    val height: Int get() = texture.height

    constructor(width: Int, height: Int, texture: GpuTexture = GpuTexture(TextureSpec(), width, height)) : this(texture)

    init {
        val ids = IntArray(1)
        GLES30.glGenFramebuffers(1, ids, 0)
        handle = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, handle)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            texture.spec.target, texture.handle, 0)
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            val h = handle
            deleteFbo()
            throw EffectEngineException.InvalidFramebuffer(h, status)
        }
    }

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, handle)
        GLES30.glViewport(0, 0, texture.width, texture.height)
    }

    private fun deleteFbo() {
        if (handle != 0) {
            val ids = IntArray(1); ids[0] = handle
            GLES30.glDeleteFramebuffers(1, ids, 0); handle = 0
        }
    }

    fun release() { deleteFbo(); texture.release() }
    fun abandon() { handle = 0; texture.abandon() }
}

/**
 * Pools FBOs by (width, height, internalFormat, target).
 * Reuses compatible resources, enforces a memory budget, trims idle entries,
 * and drops everything safely on context loss. Suitable for real-time preview AND export.
 */
class FramebufferPool(private val budgetBytes: Long = DEFAULT_BUDGET) {

    data class Key(val width: Int, val height: Int, val internalFormat: Int, val target: Int)

    private val free = HashMap<Key, ArrayDeque<FramebufferObject>>()
    private var liveBytes = 0L

    fun acquire(width: Int, height: Int, spec: TextureSpec = TextureSpec()): FramebufferObject {
        val key = Key(width, height, spec.internalFormat, spec.target)
        free[key]?.removeFirstOrNull()?.let {
            it.checkedOut = true
            return it
        }
        val tex = GpuTexture(spec, width, height).also { it.allocate() }
        liveBytes += bytesOf(width, height, spec.internalFormat)
        if (liveBytes > budgetBytes) trimOldest()
        return FramebufferObject(tex).also { it.checkedOut = true }
    }

    fun release(fbo: FramebufferObject) = releaseToPool(fbo)

    fun releaseToPool(fbo: FramebufferObject) {
        if (!fbo.checkedOut || !fbo.pooled || fbo.texture.persistent || fbo.texture.isAdopted) return
        fbo.checkedOut = false
        free.getOrPut(Key(fbo.texture.width, fbo.texture.height,
            fbo.texture.spec.internalFormat, fbo.texture.spec.target)) { ArrayDeque() }.addLast(fbo)
    }

    private fun bytesOf(w: Int, h: Int, fmt: Int) =
        w.toLong() * h * (if (fmt == GLES30.GL_RGBA16F || fmt == GLES30.GL_RGBA32F) 8L else 4L)

    private fun trimOldest() {
        val oldest = free.values.firstNotNullOfOrNull { it.removeFirstOrNull() } ?: return
        liveBytes -= bytesOf(oldest.texture.width, oldest.texture.height, oldest.texture.spec.internalFormat)
        oldest.release()
    }

    /** Call after export / on memory pressure. */
    fun trim(maxIdlePerKey: Int = 3) {
        free.values.forEach { q -> while (q.size > maxIdlePerKey) trimOldest() }
    }

    fun clear() = releaseAll()

    /** Context lost: GL already reclaimed everything — drop references WITHOUT GL calls. */
    fun onContextLost() { free.values.forEach { q -> q.forEach { it.abandon() } }; free.clear(); liveBytes = 0 }

    fun releaseAll() { free.values.forEach { q -> q.forEach { it.release() } }; free.clear(); liveBytes = 0 }

    companion object { const val DEFAULT_BUDGET = 256L * 1024 * 1024 }
}
