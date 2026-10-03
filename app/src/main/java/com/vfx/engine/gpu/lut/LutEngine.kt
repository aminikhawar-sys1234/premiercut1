package com.vfx.engine.gpu.lut

import android.opengl.GLES30
import com.vfx.engine.core.EffectEngineException
import com.vfx.engine.core.cache.LruCache
import com.vfx.engine.core.curve.Curve
import com.vfx.engine.core.lut.CubeLut
import com.vfx.engine.gpu.GpuContext
import com.vfx.engine.gpu.GpuTexture
import com.vfx.engine.gpu.TextureSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.IdentityHashMap

/**
 * Builds + caches GPU textures for 3D LUTs (TEXTURE_3D on ES3, tiled 2D atlas on ES2)
 * and curve LUTs (256x1 RGBA). Caches keyed deterministically; invalidated on context loss.
 */
class LutEngine(private val gpu: GpuContext) {

    private val lut3dCache = IdentityHashMap<CubeLut, GpuTexture>()
    private val curveCache = LruCache<String, GpuTexture>(32)

    fun get3dTexture(lut: CubeLut): GpuTexture {
        lut3dCache[lut]?.let { return it }
        val n = lut.size3d ?: throw EffectEngineException.InvalidLut("Not a 3D LUT")
        val tex = if (gpu.capabilities.supports3dTextures) upload3d(lut, n) else uploadAtlas(lut, n)
        if (lut3dCache.size >= MAX_LUTS) { lut3dCache.values.forEach { it.release() }; lut3dCache.clear() }
        lut3dCache[lut] = tex
        return tex
    }

    fun getCubeLut(lut: CubeLut): GpuTexture = get3dTexture(lut)

    private val assetCache = HashMap<String, CubeLut>()

    fun loadAssetCube(path: String, context: android.content.Context? = null): CubeLut {
        assetCache[path]?.let { return it }
        val lut = try {
            if (context != null) {
                val text = context.assets.open(path).bufferedReader().use { it.readText() }
                com.vfx.engine.core.lut.CubeLutParser.parse(text)
            } else {
                CubeLut.identity3d(33)
            }
        } catch (_: Throwable) {
            CubeLut.identity3d(33)
        }
        assetCache[path] = lut
        return lut
    }

    private fun upload3d(lut: CubeLut, n: Int): GpuTexture {
        val spec = TextureSpec(
            internalFormat = GLES30.GL_RGBA16F, format = GLES30.GL_RGBA,
            type = GLES30.GL_HALF_FLOAT, target = GLES30.GL_TEXTURE_3D)
        val tex = GpuTexture(spec, n, n)
        tex.allocate3d(n)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, tex.handle)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA16F, n, n, n, 0,
            GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, packRgba(lut.data3d!!, n * n * n, half = true))
        for (p in intArrayOf(GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_TEXTURE_MAG_FILTER))
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, p, GLES30.GL_LINEAR)
        for (p in intArrayOf(GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_TEXTURE_WRAP_R))
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, p, GLES30.GL_CLAMP_TO_EDGE)
        return tex
    }

    /** Tiled atlas for ES2: x = b*n + r, y = g — matches sample3DLut2D in GLSL. */
    private fun uploadAtlas(lut: CubeLut, n: Int): GpuTexture {
        val src = lut.data3d!!
        val reordered = FloatArray(src.size)
        for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) {
            val si = (r + g * n + b * n * n) * 3
            val di = (b * n + r + g * n * n) * 3
            reordered[di] = src[si]; reordered[di + 1] = src[si + 1]; reordered[di + 2] = src[si + 2]
        }
        val tex = GpuTexture(TextureSpec(), n * n, n)
        tex.allocate()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex.handle)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, n * n, n, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, packRgba(reordered, n * n * n, half = false))
        return tex
    }

    private fun packRgba(rgb: FloatArray, count: Int, half: Boolean): ByteBuffer {
        val out = if (half) {
            val buf = ByteBuffer.allocateDirect(count * 8).order(ByteOrder.nativeOrder())
            var i = 0
            while (i < count * 3) {
                buf.putShort(floatToHalf(rgb[i]))
                buf.putShort(floatToHalf(rgb[i + 1]))
                buf.putShort(floatToHalf(rgb[i + 2]))
                buf.putShort(0x3C00) // 1.0
                i += 3
            }
            buf
        } else {
            val buf = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
            var i = 0
            while (i < count * 3) {
                buf.put(toByte(rgb[i])); buf.put(toByte(rgb[i + 1])); buf.put(toByte(rgb[i + 2])); buf.put(255.toByte())
                i += 3
            }
            buf
        }
        (out as java.nio.Buffer).position(0)
        return out
    }

    private fun toByte(v: Float): Byte = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()

    private fun floatToHalf(value: Float): Short {
        val bits = java.lang.Float.floatToIntBits(value)
        val sign = (bits ushr 16) and 0x8000
        var exp = ((bits ushr 23) and 0xFF) - 112
        val mant = bits and 0x007FFFFF
        if (exp <= 0) return sign.toShort()
        if (exp >= 0x1F) return (sign or 0x7C00).toShort()
        return (sign or (exp shl 10) or (mant ushr 13)).toShort()
    }

    /** Cached 256x1 RGBA LUT: combined = channelCurve(master(channel)). Key = content hash. */
    fun getCurveLut(master: Curve, r: Curve, g: Curve, b: Curve): GpuTexture {
        val m = master.sample256(); val rr = r.sample256(); val gg = g.sample256(); val bb = b.sample256()
        val cr = FloatArray(256) { i -> rr[i] * m[i] + rr[i] * (1f - m[i]) * 0f + (rr[i] - 0.5f) * m[i] + 0.5f * m[i] }
        // Combined mapping: apply master first, then channel curve.
        val cr2 = FloatArray(256) { i -> rr[(m[i] * 255f).toInt().coerceIn(0, 255)] }
        val cg2 = FloatArray(256) { i -> gg[(m[i] * 255f).toInt().coerceIn(0, 255)] }
        val cb2 = FloatArray(256) { i -> bb[(m[i] * 255f).toInt().coerceIn(0, 255)] }
        val key = "${cr2.contentHashCode()}_${cg2.contentHashCode()}_${cb2.contentHashCode()}"
        curveCache.get(key)?.let { return it }
        val tex = GpuTexture(TextureSpec(), 256, 1)
        tex.allocate()
        val buf = ByteBuffer.allocateDirect(256 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until 256) {
            buf.put(toByte(cr2[i])); buf.put(toByte(cg2[i])); buf.put(toByte(cb2[i])); buf.put(255.toByte())
        }
        buf.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex.handle)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 256, 1, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        curveCache.put(key, tex)
        return tex
    }

    fun onContextLost() { lut3dCache.clear(); curveCache.clear() }   // handles dead — no GL calls
    fun release() { lut3dCache.values.forEach { it.release() }; lut3dCache.clear(); curveCache.clear { it.release() } }

    companion object { const val MAX_LUTS = 32 }
}
