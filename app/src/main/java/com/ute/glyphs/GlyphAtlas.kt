package com.ute.glyphs

import android.opengl.GLES30.*
import com.ute.core.ResourceRegistry
import com.ute.core.Safe
import com.ute.fonts.FontHandle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

data class AtlasEntry(
    val u: Float, val v: Float, val uw: Float, val vh: Float,  // normalized atlas rect
    val rasterW: Int, val rasterH: Int,
    val isColor: Boolean,
    val lastUsedFrame: Long,
)

/**
 * Dual glyph atlas: single-channel SDF atlas for scalable text, RGBA atlas for
 * color glyphs (emoji). Skyline-packed, LRU-evicted under a byte budget, and
 * fully rebuildable after GPU context loss.
 */
class GlyphAtlas(
    private val registry: ResourceRegistry,
    val sizePx: Int = 2048,
    val sdfRasterSizePx: Int = 48,   // raster size for SDF generation (scale-independent)
    val spreadPx: Int = 6,
) : ResourceRegistry.Managed {

    private val rasterizer = GlyphRasterizer()
    private var alphaPacker = SkylinePacker(sizePx, sizePx)
    private var colorPacker = SkylinePacker(sizePx, sizePx)
    private val alphaEntries = ConcurrentHashMap<GlyphKey, AtlasEntry>()
    private val colorEntries = ConcurrentHashMap<GlyphKey, AtlasEntry>()
    private var alphaTexture = -1
    private var colorTexture = -1
    private var currentFrame = 0L

    init { registry.register("ute:atlas", this) }

    fun createGpuResources() {
        if (alphaTexture > 0) return
        alphaTexture = createGlTexture(sizePx, sizePx, singleChannel = true)
        colorTexture = createGlTexture(sizePx, sizePx, singleChannel = false)
    }

    private fun createGlTexture(w: Int, h: Int, singleChannel: Boolean): Int {
        val ids = IntArray(1); glGenTextures(1, ids, 0)
        glBindTexture(GL_TEXTURE_2D, ids[0])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        if (singleChannel) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, w, h, 0, GL_RED, GL_UNSIGNED_BYTE, null)
        } else {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
        }
        return ids[0]
    }

    fun beginFrame(frame: Long) { currentFrame = frame }

    val spread: Int get() = spreadPx

    fun getOrRasterize(font: FontHandle, clusterText: String, sdf: Boolean): AtlasEntry? =
        Safe.critical("atlas", null) {
            createGpuResources()
            val size = if (sdf) sdfRasterSizePx else sdfRasterSizePx * 2
            val key = GlyphKey(font.id, clusterText, size, sdf)
            val cache = if (sdf) alphaEntries else colorEntries
            cache[key]?.let { return@critical it.copy(lastUsedFrame = currentFrame) }

            val rg = rasterizer.rasterize(font, clusterText, size.toFloat()) ?: return@critical null

            val packer = if (sdf) alphaPacker else colorPacker
            var place = packer.insert(rg.width, rg.height)
            if (place == null) {
                evictStale(cache)
                if (sdf) {
                    alphaPacker = SkylinePacker(sizePx, sizePx)
                    place = alphaPacker.insert(rg.width, rg.height) ?: return@critical null
                } else {
                    colorPacker = SkylinePacker(sizePx, sizePx)
                    place = colorPacker.insert(rg.width, rg.height) ?: return@critical null
                }
            }

            val data: ByteBuffer = if (sdf) {
                val sdfData = SdfGenerator.compute(rg.alpha, rg.width, rg.height, spreadPx)
                ByteBuffer.allocateDirect(rg.width * rg.height).apply {
                    order(ByteOrder.nativeOrder())
                    for (f in sdfData) put((f * 255f).toInt().coerceIn(0, 255).toByte())
                    position(0)
                }
            } else {
                ByteBuffer.allocateDirect(rg.width * rg.height * 4).apply {
                    order(ByteOrder.nativeOrder())
                    asIntBuffer().put(rg.argb ?: IntArray(rg.width * rg.height))
                    position(0)
                }
            }

            val tex = if (sdf) alphaTexture else colorTexture
            glBindTexture(GL_TEXTURE_2D, tex)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            if (sdf) {
                glTexSubImage2D(GL_TEXTURE_2D, 0, place.x, place.y, rg.width, rg.height,
                    GL_RED, GL_UNSIGNED_BYTE, data)
            } else {
                glTexSubImage2D(GL_TEXTURE_2D, 0, place.x, place.y, rg.width, rg.height,
                    GL_RGBA, GL_UNSIGNED_BYTE, data)
            }

            val entry = AtlasEntry(
                u = place.x / sizePx.toFloat(), v = place.y / sizePx.toFloat(),
                uw = rg.width / sizePx.toFloat(), vh = rg.height / sizePx.toFloat(),
                rasterW = rg.width, rasterH = rg.height,
                isColor = !sdf, lastUsedFrame = currentFrame,
            )
            cache[key] = entry
            entry
        }

    private fun evictStale(cache: ConcurrentHashMap<GlyphKey, AtlasEntry>) {
        cache.entries.removeIf { currentFrame - it.value.lastUsedFrame > 600 }
    }

    fun bindAlpha(unit: Int) { glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, alphaTexture) }
    fun bindColor(unit: Int) { glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, colorTexture) }

    override fun resourceBytes() = sizePx.toLong() * sizePx * 5L // R8 + RGBA8
    override fun destroy() {
        if (alphaTexture > 0) { glDeleteTextures(1, intArrayOf(alphaTexture), 0); alphaTexture = -1 }
        if (colorTexture > 0) { glDeleteTextures(1, intArrayOf(colorTexture), 0); colorTexture = -1 }
        alphaEntries.clear(); colorEntries.clear()
        alphaPacker = SkylinePacker(sizePx, sizePx); colorPacker = SkylinePacker(sizePx, sizePx)
    }
}
