package com.ahstudio.color.gpu

import android.opengl.GLES30

/** Real device capability detection (§66) — never assume, always probe. */
object GpuCaps {
    @Volatile private var cached: Caps? = null

    data class Caps(
        val glVersionMajor: Int,
        val supportsFloatTextures: Boolean,
        val supportsCompute: Boolean,      // ES 3.1
        val maxTextureSize: Int,
        val maxColorAttachments: Int
    )

    fun detect(): Caps {
        cached?.let { return it }
        val version = GLES30.glGetString(GLES30.GL_VERSION) ?: ""
        val major = Regex("OpenGL ES ([0-9])").find(version)?.groupValues?.get(1)?.toIntOrNull() ?: 2
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        val caps = Caps(
            glVersionMajor = major,
            supportsFloatTextures = major >= 3 || ext.contains("EXT_color_buffer_half_float"),
            supportsCompute = major >= 3 && ext.contains("GL_ES_VERSION_3_1"),
            maxTextureSize = IntArray(1).also { GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, it, 0) }[0],
            maxColorAttachments = IntArray(1).also { GLES30.glGetIntegerv(GLES30.GL_MAX_COLOR_ATTACHMENTS, it, 0) }[0]
        )
        cached = caps
        return caps
    }

    fun resetCache() { cached = null }
}

/** GL-thread confinement (§58). All GPU color work runs here. */
class GlDispatcher(private val handler: android.os.Handler) {
    companion object {
        fun create(name: String = "AhColorGl"): GlDispatcher {
            val ht = object : android.os.HandlerThread(name) {
                override fun onLooperPrepared() { /* EGL context attach happens via host */ }
            }
            ht.start()
            return GlDispatcher(android.os.Handler(ht.looper))
        }
    }
    fun post(block: () -> Unit) { handler.post(block) }
    fun <T> postAndWait(block: () -> T): T {
        val latch = java.util.concurrent.CountDownLatch(1)
        var result: T? = null; var error: Throwable? = null
        handler.post { try { result = block() } catch (t: Throwable) { error = t } finally { latch.countDown() } }
        latch.await()
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST") return result as T
    }
}
