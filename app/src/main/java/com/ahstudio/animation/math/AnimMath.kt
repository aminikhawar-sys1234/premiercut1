package com.ahstudio.animation.math

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    operator fun div(s: Double) = Vec2(x / s, y / s)
    fun length() = sqrt(x * x + y * y)
    fun normalized(): Vec2 { val l = length(); return if (l < 1e-12) ZERO else this / l }
    fun dot(o: Vec2) = x * o.x + y * o.y
    fun lerp(o: Vec2, t: Double) = Vec2(x + (o.x - x) * t, y + (o.y - y) * t)
    companion object { val ZERO = Vec2(0.0, 0.0); val ONE = Vec2(1.0, 1.0) }
}

data class Vec3(val x: Double, val y: Double, val z: Double) {
    fun lerp(o: Vec3, t: Double) =
        Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
    companion object { val ZERO = Vec3(0.0, 0.0, 0.0); val ONE = Vec3(1.0, 1.0, 1.0) }
}

data class Color4(val r: Double, val g: Double, val b: Double, val a: Double) {
    fun lerp(o: Color4, t: Double) = Color4(
        r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t, a + (o.a - a) * t
    )
}

/** 2D affine matrix. Row-major: [m00 m01 m02 ; m10 m11 m12 ; 0 0 1]. */
data class Mat3(
    val m00: Double, val m01: Double, val m02: Double,
    val m10: Double, val m11: Double, val m12: Double
) {
    operator fun times(o: Mat3) = Mat3(
        m00 * o.m00 + m01 * o.m10, m00 * o.m01 + m01 * o.m11, m00 * o.m02 + m01 * o.m12 + m02,
        m10 * o.m00 + m11 * o.m10, m10 * o.m01 + m11 * o.m11, m10 * o.m02 + m11 * o.m12 + m12
    )
    fun transform(x: Double, y: Double) =
        Vec2(m00 * x + m01 * y + m02, m10 * x + m11 * y + m12)
    fun transform(p: Vec2) = transform(p.x, p.y)
    fun inverse(): Mat3 {
        val det = m00 * m11 - m01 * m10
        if (abs(det) < 1e-12) return IDENTITY // degenerate -> graceful fallback
        val a = m11 / det; val b = -m01 / det; val c = -m10 / det; val d = m00 / det
        return Mat3(a, b, -(a * m02 + b * m12), c, d, -(c * m02 + d * m12))
    }
    companion object {
        val IDENTITY = Mat3(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        fun translation(x: Double, y: Double) = Mat3(1.0, 0.0, x, 0.0, 1.0, y)
        fun rotationRad(rad: Double) =
            Mat3(cos(rad), -sin(rad), 0.0, sin(rad), cos(rad), 0.0)
        fun rotationDeg(deg: Double) = rotationRad(deg * Math.PI / 180.0)
        fun scaling(sx: Double, sy: Double) = Mat3(sx, 0.0, 0.0, 0.0, sy, 0.0)
        fun skew(kx: Double, ky: Double) = Mat3(1.0, kx, 0.0, ky, 1.0, 0.0)
    }
}

fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
fun isFinite(v: Double) = !v.isNaN() && !v.isInfinite()
fun isFinite(v: Vec2) = isFinite(v.x) && isFinite(v.y)
fun near(a: Double, b: Double, eps: Double = 1e-6) = abs(a - b) <= eps
fun smoothstep(t: Double) = t * t * (3.0 - 2.0 * t)

fun normalizeAngleDeg(deg: Double): Double {
    var d = deg % 360.0
    if (d < 0) d += 360.0
    return d
}
