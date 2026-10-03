package com.ahstudio.color.gpu

import android.opengl.GLES30
import com.ahstudio.color.lut.Lut3D
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Uploads 3D LUTs to GPU textures ONCE per LUT (§34) and releases them deterministically.
 * Uses sampler3D on ES3; 2D tiled atlas fallback is in the shader (HAS_LUT2D).
 */
class LutTextureManager {

    private data class Entry(val texId: Int, val size: Int, val internalFormat: Int)
    private val cache = HashMap<String, Entry>()

    /** Returns GL texture id for the LUT, uploading only if not cached. */
    fun getOrCreate(lut: Lut3D, lutId: String, floatCapable: Boolean): Int {
        cache[lutId]?.let { return it.texId }
        val domainWithinUnit = lut.domainMin.all { it >= 0f } && lut.domainMax.all { it <= 1f }
        val internalFormat = if (domainWithinUnit || !floatCapable) GLES30.GL_RGBA8 else GLES30.GL_RGBA16F
        val type = if (internalFormat == GLES30.GL_RGBA16F) GLES30.GL_HALF_FLOAT else GLES30.GL_UNSIGNED_BYTE

        val n = lut.size
        val rgba = FloatArray(n * n * n * 4)
        for (i in 0 until n * n * n) {
            rgba[i * 4 + 0] = lut.data[i * 3 + 0]
            rgba[i * 4 + 1] = lut.data[i * 3 + 1]
            rgba[i * 4 + 2] = lut.data[i * 3 + 2]
            rgba[i * 4 + 3] = 1f
        }
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, tex[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        if (type == GLES30.GL_HALF_FLOAT) {
            GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, internalFormat, n, n, n, 0,
                GLES30.GL_RGBA, type, halfFloatByteBuffer(rgba))
        } else {
            val bytes = ByteBuffer.allocateDirect(rgba.size).order(ByteOrder.nativeOrder())
            for (v in rgba) bytes.put((v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255).toByte())
            bytes.position(0)
            GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, internalFormat, n, n, n, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, bytes)
        }
        cache[lutId] = Entry(tex[0], n, internalFormat)
        return tex[0]
    }

    fun lutParams(lut: Lut3D): FloatArray {
        val n = lut.size.toFloat()
        val scale = (n - 1f) / n
        val offset = 0.5f / n
        return floatArrayOf(scale, scale, scale, offset, offset, offset)
    }

    fun remove(lutId: String) {
        cache.remove(lutId)?.let { GLES30.glDeleteTextures(1, intArrayOf(it.texId), 0) }
    }

    /** Deterministic GPU resource release (§34, §67). */
    fun releaseAll() {
        cache.values.forEach { GLES30.glDeleteTextures(1, intArrayOf(it.texId), 0) }
        cache.clear()
    }

    private fun halfFloatByteBuffer(floats: FloatArray): ByteBuffer {
        val bb = ByteBuffer.allocateDirect(floats.size * 2).order(ByteOrder.nativeOrder())
        for (f in floats) bb.putShort(floatToHalf(f))
        bb.position(0)
        return bb
    }

    companion object {
        fun floatToHalf(f: Float): Short {
            val bits = java.lang.Float.floatToIntBits(f)
            val sign = (bits ushr 16) and 0x8000
            val exp = ((bits ushr 23) and 0xff) - 127 + 15
            val mant = bits and 0x7fffff
            if (exp >= 0x1f) return (sign or 0x7c00).toShort()
            if (exp <= 0) {
                if (exp < -10) return sign.toShort()
                val m = (mant or 0x800000) shr (1 - exp)
                return (sign or (m shr 13)).toShort()
            }
            return (sign or (exp shl 10) or (mant shr 13)).toShort()
        }
    }
}
