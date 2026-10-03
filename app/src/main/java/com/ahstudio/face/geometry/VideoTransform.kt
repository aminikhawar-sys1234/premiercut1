package com.ahstudio.face.geometry

import com.ahstudio.face.core.FaceBounds
import com.ahstudio.face.core.Vec2
import kotlin.math.max
import kotlin.math.min

enum class FitMode { CONTAIN, COVER }

/**
 * Canonical chain:
 *   crop (oriented source space) -> user-rotation -> mirror -> fit(viewport) -> viewport px -> GL clip
 */
data class VideoTransform(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotationDegrees: Int = 0,
    val mirror: Boolean = false,
    val crop: FaceBounds? = null,
    val viewportWidth: Int = sourceWidth,
    val viewportHeight: Int = sourceHeight,
    val fitMode: FitMode = FitMode.CONTAIN,
) {
    val orientedWidth: Int = if (rotationDegrees % 180 == 90) sourceHeight else sourceWidth
    val orientedHeight: Int = if (rotationDegrees % 180 == 90) sourceWidth else sourceHeight
    private val cw = crop?.width ?: 1f
    private val ch = crop?.height ?: 1f
    private val frameW = orientedWidth * cw
    private val frameH = orientedHeight * ch
    private val fitScale = if (fitMode == FitMode.CONTAIN)
        min(viewportWidth / frameW, viewportHeight / frameH)
    else max(viewportWidth / frameW, viewportHeight / frameH)
    private val offX = (viewportWidth - frameW * fitScale) / 2f
    private val offY = (viewportHeight - frameH * fitScale) / 2f

    val visibleFrameWidthPx: Float get() = frameW * fitScale
    val visibleFrameHeightPx: Float get() = frameH * fitScale

    fun rotatePoint(p: Vec2): Vec2 = when (rotationDegrees) {
        90 -> Vec2(1f - p.y, p.x)
        180 -> Vec2(1f - p.x, 1f - p.y)
        270 -> Vec2(p.y, 1f - p.x)
        else -> p
    }

    fun unrotatePoint(p: Vec2): Vec2 = when (rotationDegrees) {
        90 -> Vec2(p.y, 1f - p.x)
        180 -> Vec2(1f - p.x, 1f - p.y)
        270 -> Vec2(1f - p.y, p.x)
        else -> p
    }

    /** normalized oriented frame -> viewport pixels */
    fun toViewport(p: Vec2): Vec2 {
        val c = crop
        var q = if (c != null) Vec2((p.x - c.left) / cw, (p.y - c.top) / ch) else p
        q = rotatePoint(q)
        if (mirror) q = Vec2(1f - q.x, q.y)
        return Vec2(q.x * frameW * fitScale + offX, q.y * frameH * fitScale + offY)
    }

    /** viewport pixels -> normalized oriented frame (touch input path) */
    fun fromViewport(p: Vec2): Vec2 {
        var q = Vec2((p.x - offX) / (frameW * fitScale), (p.y - offY) / (frameH * fitScale))
        if (mirror) q = Vec2(1f - q.x, q.y)
        q = unrotatePoint(q)
        val c = crop
        return if (c != null) Vec2(q.x * cw + c.left, q.y * ch + c.top) else q
    }

    fun mapRectToViewport(r: FaceBounds): FaceBounds {
        val a = toViewport(Vec2(r.left, r.top)); val b = toViewport(Vec2(r.right, r.bottom))
        return FaceBounds(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }

    /** GL clip-space position of a viewport pixel (y flipped). */
    fun viewportToClip(p: Vec2): Vec2 =
        Vec2(p.x / viewportWidth * 2f - 1f, 1f - p.y / viewportHeight * 2f)
}

/** Minimal 2D affine matrix. */
class Mat3(val m: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)) {
    operator fun times(o: Mat3) = Mat3(FloatArray(9) { i ->
        val r = i / 3; val c = i % 3
        var s = 0f
        for (k in 0..2) s += m[r * 3 + k] * o.m[k * 3 + c]
        s
    })
    fun apply(p: Vec2): Vec2 = Vec2(
        m[0] * p.x + m[1] * p.y + m[2],
        m[3] * p.x + m[4] * p.y + m[5],
    )
    companion object {
        fun translation(x: Float, y: Float) = Mat3().also { it.m[2] = x; it.m[5] = y }
        fun rotationDeg(deg: Float): Mat3 {
            val r = Math.toRadians(deg.toDouble())
            val c = kotlin.math.cos(r).toFloat(); val s = kotlin.math.sin(r).toFloat()
            return Mat3(floatArrayOf(c, -s, 0f, s, c, 0f, 0f, 0f, 1f))
        }
        fun scale(sx: Float, sy: Float) = Mat3().also { it.m[0] = sx; it.m[4] = sy }
    }
}
