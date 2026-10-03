package com.ahstudio.color.math

object Mat3 {
    // Row-major 3x3
    fun mul(a: FloatArray, b: FloatArray): FloatArray = FloatArray(9).also { o ->
        for (r in 0..2) for (c in 0..2) {
            o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
        }
    }

    fun mulVec(m: FloatArray, v: FloatArray): FloatArray = floatArrayOf(
        m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
        m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
        m[6] * v[0] + m[7] * v[1] + m[8] * v[2]
    )

    fun inverse(m: FloatArray): FloatArray {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]
        val A = e * i - f * h; val B = -(d * i - f * g); val C = d * h - e * g
        val det = a * A + b * B + c * C
        require(kotlin.math.abs(det) > 1e-12f) { "Singular matrix" }
        val id = 1f / det
        return floatArrayOf(
            A * id, -(b * i - c * h) * id, (b * f - c * e) * id,
            B * id, (a * i - c * g) * id, -(a * f - c * d) * id,
            C * id, -(a * h - b * g) * id, (a * e - b * d) * id
        )
    }

    fun identity(): FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
}
