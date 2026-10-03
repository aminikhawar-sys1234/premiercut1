package com.vfx.engine.core.math

import kotlin.math.*

data class Vec2(var x: Float = 0f, var y: Float = 0f) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    fun length() = hypot(x.toDouble(), y.toDouble()).toFloat()
    companion object { val ZERO = Vec2(0f, 0f); val ONE = Vec2(1f, 1f) }
}

data class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    companion object { val ZERO = Vec3(0f, 0f, 0f); val ONE = Vec3(1f, 1f, 1f) }
}

data class Vec4(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var w: Float = 1f) {
    operator fun plus(o: Vec4) = Vec4(x + o.x, y + o.y, z + o.z, w + o.w)
    operator fun times(s: Float) = Vec4(x * s, y * s, z * s, w * s)
}

/** Column-major 3x3 (like GLSL mat3). Index = col*3+row. */
class Mat3(val m: FloatArray = floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f)) {
    val values: FloatArray get() = m
    companion object {
        fun identity() = Mat3(floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f))
        fun translation(x: Float, y: Float) = Mat3(floatArrayOf(1f,0f,0f, 0f,1f,0f, x,y,1f))
        fun scale(sx: Float, sy: Float) = Mat3(floatArrayOf(sx,0f,0f, 0f,sy,0f, 0f,0f,1f))
        fun rotation(rad: Float): Mat3 { val c=cos(rad); val s=sin(rad)
            return Mat3(floatArrayOf(c,s,0f, -s,c,0f, 0f,0f,1f)) }
        fun flipY() = Mat3(floatArrayOf(1f,0f,0f, 0f,-1f,1f, 0f,0f,1f))
    }
    operator fun times(o: Mat3): Mat3 {
        val r = FloatArray(9)
        for (col in 0..2) for (row in 0..2) {
            var s = 0f
            for (k in 0..2) s += m[k*3+row] * o.m[col*3+k]
            r[col*3+row] = s
        }
        return Mat3(r)
    }
    fun mapPoint(x: Float, y: Float): FloatArray {
        return floatArrayOf(m[0]*x + m[3]*y + m[6], m[1]*x + m[4]*y + m[7])
    }
    fun copy() = Mat3(m.copyOf())
}

/** Column-major 4x4 (SurfaceTexture matrices etc.) */
class Mat4(val m: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f }) {
    val values: FloatArray get() = m
    fun toMat3Uv(): Mat3 = Mat3(floatArrayOf(m[0],m[1],m[3], m[4],m[5],m[7], m[12],m[13],m[15]))
    companion object { fun fromColumnMajor(a: FloatArray) = Mat4(a.copyOf()) }
}

object MathUtils {
    const val PI = Math.PI.toFloat()
    const val DEG_TO_RAD = PI / 180.0f
    const val RAD_TO_DEG = 180.0f / PI

    fun clamp(v: Float, min: Float, max: Float) = v.coerceIn(min, max)
    fun clamp01(v: Float) = v.coerceIn(0f, 1f)
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = clamp01((x - e0) / (e1 - e0)); return t * t * (3f - 2f * t)
    }
    fun isFinite(v: Float) = !v.isNaN() && !v.isInfinite()
    fun sanitize(v: Float): Float = if (isFinite(v)) v else 0f
}

data class RectF(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f) {
    val width: Float get() = abs(right - left)
    val height: Float get() = abs(bottom - top)
}

data class Color(val r: Float = 0f, val g: Float = 0f, val b: Float = 0f, val a: Float = 1f) {
    fun toPremultiplied(): Color = Color(r * a, g * a, b * a, a)
    fun toUnpremultiplied(): Color = if (a > 0.0001f) Color(r / a, g / a, b / a, a) else Color(0f, 0f, 0f, 0f)
}
