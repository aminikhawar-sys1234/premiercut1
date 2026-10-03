package com.ahstudio.composition.transform

/** Immutable 2D affine: x' = a*x + b*y + tx ; y' = c*x + d*y + ty. (this * o) applies o first. */
data class Affine2(
    val a: Float = 1f, val b: Float = 0f, val tx: Float = 0f,
    val c: Float = 0f, val d: Float = 1f, val ty: Float = 0f,
) {
    operator fun times(o: Affine2) = Affine2(
        a = a * o.a + b * o.c, b = a * o.b + b * o.d,
        tx = a * o.tx + b * o.ty + tx,
        c = c * o.a + d * o.c, d = c * o.b + d * o.d,
        ty = c * o.tx + d * o.ty + ty
    )
    fun mapX(x: Float, y: Float) = a * x + b * y + tx
    fun mapY(x: Float, y: Float) = c * x + d * y + ty

    /** Column-major 4x4 for GLES (z passthrough). */
    fun toColumnMajor4(): FloatArray = floatArrayOf(
        a, c, 0f, 0f,
        b, d, 0f, 0f,
        0f, 0f, 1f, 0f,
        tx, ty, 0f, 1f
    )

    companion object {
        val Identity = Affine2()
        fun translation(x: Float, y: Float) = Affine2(tx = x, ty = y)
        fun scale(sx: Float, sy: Float) = Affine2(a = sx, d = sy)
        fun rotationDeg(deg: Float): Affine2 {
            val r = Math.toRadians(deg.toDouble()); val co = Math.cos(r).toFloat(); val si = Math.sin(r).toFloat()
            return Affine2(a = co, b = -si, c = si, d = co)
        }
        fun skewX(deg: Float) = Affine2(b = Math.tan(Math.toRadians(deg.toDouble())).toFloat())
        fun skewY(deg: Float) = Affine2(c = Math.tan(Math.toRadians(deg.toDouble())).toFloat())
    }
}
