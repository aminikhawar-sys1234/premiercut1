package com.ute.color

import android.opengl.GLES30.*
import com.ute.model.GradientSpec
import com.ute.model.GradientStop
import com.ute.model.GradientType
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Gradient baking, color-space-aware interpolation, and gradient LUT textures.
 * Gradients are baked to a 256×1 RGBA texture once per (spec, time-bucket) and
 * sampled per-glyph in the text shader — supports unlimited stops on any GPU.
 */
class ColorEngine {

    private val lutCache = LinkedHashMap<Int, Int>(16, 0.75f, true)

    fun gradientColorAt(spec: GradientSpec, t: Float, timeSec: Double): Int {
        var color = sampleStops(spec.stops, t.coerceIn(0f, 1f))
        if (spec.hueRotationPerSec != 0f) {
            val hsv = ColorMath.hsvFromArgb(color)
            color = ColorMath.argbFromHsv(hsv[0] + (spec.hueRotationPerSec * timeSec).toFloat() % 360f, hsv[1], hsv[2], color ushr 24)
        }
        return color
    }

    private fun sampleStops(stops: List<GradientStop>, t: Float): Int {
        if (stops.isEmpty()) return 0xFFFFFFFF.toInt()
        val sorted = stops.sortedBy { it.position }
        if (t <= sorted.first().position) return sorted.first().color
        if (t >= sorted.last().position) return sorted.last().color
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]; val b = sorted[i + 1]
            if (t in a.position..b.position) {
                val f = (t - a.position) / (b.position - a.position).coerceAtLeast(1e-6f)
                return ColorMath.mixOklab(a.color, b.color, f)
            }
        }
        return sorted.last().color
    }

    fun gradientLut(spec: GradientSpec, timeSec: Double): Int {
        val key = spec.hashCode() * 31 + (timeSec * 10).toInt()
        lutCache[key]?.let { return it }
        val tex = GlTexture.create1x256()
        val px = ByteArray(256 * 4)
        for (i in 0 until 256) {
            val c = gradientColorAt(spec, i / 255f, timeSec)
            px[i * 4] = ((c shr 16) and 0xFF).toByte()
            px[i * 4 + 1] = ((c shr 8) and 0xFF).toByte()
            px[i * 4 + 2] = (c and 0xFF).toByte()
            px[i * 4 + 3] = ((c ushr 24) and 0xFF).toByte()
        }
        glBindTexture(GL_TEXTURE_2D, tex)
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 256, 1, GL_RGBA, GL_UNSIGNED_BYTE,
            ByteBuffer.allocateDirect(px.size).apply { order(ByteOrder.nativeOrder()); put(px); position(0) })
        lutCache[key] = tex
        if (lutCache.size > 32) {
            val eldest = lutCache.keys.first()
            glDeleteTextures(1, intArrayOf(eldest), 0)
            lutCache.remove(eldest)
        }
        return tex
    }

    fun gradientCoord(spec: GradientSpec, x: Float, y: Float, boxW: Float, boxH: Float): Float {
        val ax = spec.endX - spec.startX; val ay = spec.endY - spec.startY
        val len2 = (ax * ax + ay * ay).coerceAtLeast(1e-6f)
        val px = x / boxW.coerceAtLeast(1f); val py = y / boxH.coerceAtLeast(1f)
        return (((px - spec.startX) * ax + (py - spec.startY) * ay) / len2).coerceIn(0f, 1f)
    }

    fun radialCoord(spec: GradientSpec, x: Float, y: Float, boxW: Float, boxH: Float): Float =
        kotlin.math.hypot(
            (x / boxW.coerceAtLeast(1f) - (spec.startX + spec.endX) / 2).toDouble(),
            (y / boxH.coerceAtLeast(1f) - (spec.startY + spec.endY) / 2).toDouble()
        ).toFloat().coerceIn(0f, 1f)

    object GlTexture {
        fun create1x256(): Int {
            val ids = IntArray(1); glGenTextures(1, ids, 0)
            glBindTexture(GL_TEXTURE_2D, ids[0])
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 256, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
            return ids[0]
        }
    }
}
