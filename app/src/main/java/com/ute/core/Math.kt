package com.ute.core

import kotlin.math.*

data class Vec2(val x: Float = 0f, val y: Float = 0f) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    fun length() = hypot(x.toDouble(), y.toDouble()).toFloat()
}

object MathUtil {
    /** Crash-protection: replaces NaN/Infinity with a safe fallback. Used at every render boundary. */
    fun sanitize(v: Float, fallback: Float = 0f): Float =
        if (v.isNaN() || v.isInfinite()) fallback else v

    fun clamp(v: Float, lo: Float, hi: Float) = v.coerceIn(lo, hi)
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

/** Column-major 4x4 matrix, OpenGL convention. All math real, no library dependency. */
class Mat4 private constructor(val m: FloatArray = FloatArray(16)) {

    companion object {
        fun identity() = Mat4().apply {
            m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
        }

        fun translation(x: Float, y: Float, z: Float) = identity().apply {
            m[12] = x; m[13] = y; m[14] = z
        }

        fun scale(x: Float, y: Float, z: Float) = identity().apply {
            m[0] = x; m[5] = y; m[10] = z
        }

        fun rotationX(deg: Float) = identity().apply {
            val r = Math.toRadians(deg.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
            m[5] = c; m[6] = s; m[9] = -s; m[10] = c
        }

        fun rotationY(deg: Float) = identity().apply {
            val r = Math.toRadians(deg.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
            m[0] = c; m[2] = -s; m[8] = s; m[10] = c
        }

        fun rotationZ(deg: Float) = identity().apply {
            val r = Math.toRadians(deg.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
            m[0] = c; m[1] = s; m[4] = -s; m[5] = c
        }

        fun perspective(fovyDeg: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val f = 1f / tan(Math.toRadians(fovyDeg.toDouble()) / 2.0).toFloat()
            return Mat4().apply {
                m[0] = f / aspect; m[5] = f
                m[10] = (far + near) / (near - far); m[11] = -1f
                m[14] = 2f * far * near / (near - far)
            }
        }

        fun ortho(l: Float, r: Float, b: Float, t: Float, n: Float, f: Float) = Mat4().apply {
            m[0] = 2f / (r - l); m[5] = 2f / (t - b); m[10] = -2f / (f - n); m[15] = 1f
            m[12] = -(r + l) / (r - l); m[13] = -(t + b) / (t - b); m[14] = -(f + n) / (f - n)
        }

        fun multiply(a: Mat4, b: Mat4): Mat4 {
            val out = FloatArray(16)
            for (c in 0 until 4) for (r in 0 until 4) {
                var s = 0f
                for (k in 0 until 4) s += a.m[k * 4 + r] * b.m[c * 4 + k]
                out[c * 4 + r] = s
            }
            return Mat4(out)
        }

        /** 2D affine for the instanced text path: translate → rotate → scale, +Y up. */
        fun trs2d(tx: Float, ty: Float, rotDeg: Float, sx: Float, sy: Float): Mat4 =
            multiply(
                multiply(translation(tx, ty, 0f), rotationZ(rotDeg)),
                scale(sx, sy, 1f)
            )
    }
}
