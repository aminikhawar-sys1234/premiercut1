package com.ute.glyphs

import kotlin.math.sqrt

/**
 * Real 8-points Signed Sequential Euclidean Distance Transform (8SSEDT).
 * Input: antialiased coverage. Output: signed distance field normalized to
 * 0..1 where 0.5 is the glyph edge and ±1 maps to ±spread pixels.
 * This is what makes text resolution-independent from a small atlas.
 */
object SdfGenerator {

    fun compute(coverage: FloatArray, w: Int, h: Int, spreadPx: Int): FloatArray {
        val size = w * h
        val gxIn = FloatArray(size); val gyIn = FloatArray(size)
        val gxOut = FloatArray(size); val gyOut = FloatArray(size)
        val INF = 1e9f

        for (i in 0 until size) {
            val solid = coverage[i] >= 0.5f
            if (solid) { gxIn[i] = 0f; gyIn[i] = 0f; gxOut[i] = INF; gyOut[i] = INF }
            else       { gxIn[i] = INF; gyIn[i] = INF; gxOut[i] = 0f; gyOut[i] = 0f }
        }

        for (pass in 0 until 2) {
            if (pass == 0) {
                for (y in 0 until h) for (x in 0 until w) {
                    val i = y * w + x
                    compare(gxIn, gyIn, i, x, y, w, h, -1, 0); compare(gxIn, gyIn, i, x, y, w, h, 0, -1)
                    compare(gxIn, gyIn, i, x, y, w, h, -1, -1); compare(gxIn, gyIn, i, x, y, w, h, 1, -1)
                    compare(gxOut, gyOut, i, x, y, w, h, -1, 0); compare(gxOut, gyOut, i, x, y, w, h, 0, -1)
                    compare(gxOut, gyOut, i, x, y, w, h, -1, -1); compare(gxOut, gyOut, i, x, y, w, h, 1, -1)
                }
            } else {
                for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
                    val i = y * w + x
                    compare(gxIn, gyIn, i, x, y, w, h, 1, 0); compare(gxIn, gyIn, i, x, y, w, h, 0, 1)
                    compare(gxIn, gyIn, i, x, y, w, h, 1, 1); compare(gxIn, gyIn, i, x, y, w, h, -1, 1)
                    compare(gxOut, gyOut, i, x, y, w, h, 1, 0); compare(gxOut, gyOut, i, x, y, w, h, 0, 1)
                    compare(gxOut, gyOut, i, x, y, w, h, 1, 1); compare(gxOut, gyOut, i, x, y, w, h, -1, 1)
                }
            }
        }

        val out = FloatArray(size)
        val inv = 0.5f / spreadPx
        for (i in 0 until size) {
            val dIn = sqrt((gxIn[i] * gxIn[i] + gyIn[i] * gyIn[i]).toDouble()).toFloat()
            val dOut = sqrt((gxOut[i] * gxOut[i] + gyOut[i] * gyOut[i]).toDouble()).toFloat()
            val sd = (dOut - dIn) * inv
            out[i] = (0.5f + sd).coerceIn(0f, 1f)
        }
        return out
    }

    private fun compare(gx: FloatArray, gy: FloatArray, i: Int, x: Int, y: Int,
                        w: Int, h: Int, dx: Int, dy: Int) {
        val nx = x + dx; val ny = y + dy
        if (nx < 0 || ny < 0 || nx >= w || ny >= h) return
        val ni = ny * w + nx
        val cx = gx[ni] + dx; val cy = gy[ni] + dy
        val cur = gx[i] * gx[i] + gy[i] * gy[i]
        val cand = cx * cx + cy * cy
        if (cand < cur) { gx[i] = cx; gy[i] = cy }
    }
}
