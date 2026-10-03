package com.vfx.engine.gpu

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class TextureSpec(
    val internalFormat: Int = GLES30.GL_RGBA8,
    val format: Int = GLES30.GL_RGBA,
    val type: Int = GLES30.GL_UNSIGNED_BYTE,
    val minFilter: Int = GLES30.GL_LINEAR,
    val magFilter: Int = GLES30.GL_LINEAR,
    val wrapS: Int = GLES30.GL_CLAMP_TO_EDGE,
    val wrapT: Int = GLES30.GL_CLAMP_TO_EDGE,
    val target: Int = GLES30.GL_TEXTURE_2D
) {
    companion object {
        val LINEAR_CLAMP = TextureSpec(
            minFilter = GLES30.GL_LINEAR,
            magFilter = GLES30.GL_LINEAR,
            wrapS = GLES30.GL_CLAMP_TO_EDGE,
            wrapT = GLES30.GL_CLAMP_TO_EDGE
        )
    }
}

/**
 * A GPU texture owned by the engine resource system — never created/destroyed per frame.
 * Adopted textures (decoder output, host masks, Media3 inputs) are wrapped, never deleted.
 */
class GpuTexture(
    val spec: TextureSpec = TextureSpec(),
    val width: Int,
    val height: Int,
    val persistent: Boolean = false,
    private val adoptedHandle: Int = -1
) {
    var handle: Int = 0; private set
    val textureId: Int get() = handle
    var released = false; private set
    var depth = 1
    val isAdopted: Boolean = adoptedHandle >= 0

    init {
        if (isAdopted) {
            handle = adoptedHandle
            released = true   // never deleted by the engine
        } else {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            handle = ids[0]
            GLES30.glBindTexture(spec.target, handle)
            GLES30.glTexParameteri(spec.target, GLES30.GL_TEXTURE_MIN_FILTER, spec.minFilter)
            GLES30.glTexParameteri(spec.target, GLES30.GL_TEXTURE_MAG_FILTER, spec.magFilter)
            GLES30.glTexParameteri(spec.target, GLES30.GL_TEXTURE_WRAP_S, spec.wrapS)
            GLES30.glTexParameteri(spec.target, GLES30.GL_TEXTURE_WRAP_T, spec.wrapT)
        }
    }

    fun allocate() {
        GLES30.glBindTexture(spec.target, handle)
        GLES30.glTexImage2D(spec.target, 0, spec.internalFormat, width, height, 0, spec.format, spec.type, null)
    }

    fun allocate3d(d: Int) {
        depth = d
        GLES30.glBindTexture(spec.target, handle)
        GLES30.glTexImage3D(spec.target, 0, spec.internalFormat, width, height, depth, 0, spec.format, spec.type, null)
    }

    fun bind(unit: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(spec.target, handle)
    }

    fun release() {
        if (released) return
        val ids = IntArray(1); ids[0] = handle
        GLES30.glDeleteTextures(1, ids, 0)
        released = true
    }

    /** Post-context-loss: handles are dead — drop references WITHOUT GL calls. */
    fun abandon() { handle = 0; released = true }

    companion object {
        object Target {
            const val TEXTURE_2D = GLES30.GL_TEXTURE_2D
            const val TEXTURE_3D = GLES30.GL_TEXTURE_3D
        }

        /** Wraps an externally-owned GL texture id. Never deleted by the engine. */
        fun adoptExternal(textureId: Int, width: Int, height: Int, spec: TextureSpec = TextureSpec()): GpuTexture =
            GpuTexture(spec, width, height, persistent = true, adoptedHandle = textureId)

        fun wrapExternal(textureId: Int, width: Int, height: Int, spec: TextureSpec = TextureSpec()): GpuTexture =
            adoptExternal(textureId, width, height, spec)
    }
}

/** CPU readback fallback (thumbnails / tests only — never the normal video path). Top-down RGBA. */
class PixelReadback {
    private var buffer: ByteBuffer = ByteBuffer.allocateDirect(0)

    fun read(width: Int, height: Int): ByteBuffer {
        val size = width * height * 4
        if (buffer.capacity() != size)
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        buffer.position(0)
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        return flipVertical(buffer, width, height)
    }

    private fun flipVertical(src: ByteBuffer, w: Int, h: Int): ByteBuffer {
        val out = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        val row = ByteArray(w * 4)
        for (y in h - 1 downTo 0) { src.position(y * w * 4); src.get(row); out.put(row) }
        out.position(0)
        return out
    }
}
