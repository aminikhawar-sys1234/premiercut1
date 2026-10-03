package com.ahstudio.composition.mask

import com.ahstudio.composition.graph.PathData

data class Vec2(val x: Float, val y: Float)

/** Flattens PathData into polygons and ear-clip triangulates for GL mask/shape rasterization. */
object PathTessellator {

    fun flatten(path: PathData, tolerancePx: Float = 0.5f): List<Vec2> {
        val out = ArrayList<Vec2>(64)
        var cur = Vec2(0f, 0f); var start = cur
        var lastCtrl: Vec2? = null
        fun emit(p: Vec2) { if (out.isEmpty() || dist2(out.last(), p) > tolerancePx * tolerancePx * 0.01f) out.add(p) }
        for (c in path.commands) when (c) {
            is PathData.Cmd.M -> { cur = Vec2(c.x, c.y); start = cur; lastCtrl = null; emit(cur) }
            is PathData.Cmd.L -> { cur = Vec2(c.x, c.y); lastCtrl = null; emit(cur) }
            is PathData.Cmd.Q -> {
                val p0 = cur; val pc = Vec2(c.cx, c.cy); val p1 = Vec2(c.x, c.y)
                val steps = stepsFor(p0, pc, p1, tolerancePx)
                for (i in 1..steps) { val u = i.toFloat() / steps; emit(quad(p0, pc, p1, u)) }
                lastCtrl = pc; cur = p1
            }
            is PathData.Cmd.C -> {
                val p0 = cur; val c1 = Vec2(c.c1x, c.c1y); val c2 = Vec2(c.c2x, c.c2y); val p1 = Vec2(c.x, c.y)
                val steps = stepsForCubic(p0, c1, c2, p1, tolerancePx)
                for (i in 1..steps) { val u = i.toFloat() / steps; emit(cubic(p0, c1, c2, p1, u)) }
                lastCtrl = c2; cur = p1
            }
            PathData.Cmd.Z -> { cur = start; lastCtrl = null; emit(start) }
        }
        return out
    }

    private fun dist2(a: Vec2, b: Vec2) = (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
    private fun stepsFor(p0: Vec2, pc: Vec2, p1: Vec2, tol: Float): Int {
        val chord = kotlin.math.sqrt(dist2(p0, p1)); val ctrl = kotlin.math.sqrt(dist2(p0, pc)) + kotlin.math.sqrt(dist2(pc, p1))
        return ((ctrl / tol.coerceAtLeast(0.05f)).toInt() / 4).coerceIn(8, 64)
    }
    private fun stepsForCubic(p0: Vec2, c1: Vec2, c2: Vec2, p1: Vec2, tol: Float): Int {
        val approx = kotlin.math.sqrt(dist2(p0, c1)) + kotlin.math.sqrt(dist2(c1, c2)) + kotlin.math.sqrt(dist2(c2, p1))
        return ((approx / tol.coerceAtLeast(0.05f)).toInt() / 4).coerceIn(8, 96)
    }
    private fun quad(p0: Vec2, pc: Vec2, p1: Vec2, u: Float): Vec2 {
        val v = 1 - u
        return Vec2(v * v * p0.x + 2 * v * u * pc.x + u * u * p1.x, v * v * p0.y + 2 * v * u * pc.y + u * u * p1.y)
    }
    private fun cubic(p0: Vec2, c1: Vec2, c2: Vec2, p1: Vec2, u: Float): Vec2 {
        val v = 1 - u
        return Vec2(
            v * v * v * p0.x + 3 * v * v * u * c1.x + 3 * v * u * u * c2.x + u * u * u * p1.x,
            v * v * v * p0.y + 3 * v * v * u * c1.y + 3 * v * u * u * c2.y + u * u * u * p1.y
        )
    }

    /** Ear-clip triangulation. Handles CW and CCW input; outputs triangles with input winding. */
    fun triangulate(rawPoints: List<Vec2>): List<Tri> {
        val points = if (rawPoints.size > 2 && dist2(rawPoints.first(), rawPoints.last()) < 1e-6f) {
            rawPoints.dropLast(1)
        } else rawPoints
        val n = points.size
        if (n < 3) return emptyList()
        if (n == 3) return listOf(Tri(points[0], points[1], points[2]))
        val signed = signedArea(points)
        if (kotlin.math.abs(signed) < 1e-9f) return emptyList()
        val idx = (0 until n).toMutableList()
        val tris = ArrayList<Tri>(n - 2)
        var guard = 0
        while (idx.size > 3 && guard++ < 4 * n) {
            var clipped = false
            for (i in idx.indices) {
                val a = points[idx[(i + idx.size - 1) % idx.size]]
                val b = points[idx[i]]
                val c = points[idx[(i + 1) % idx.size]]
                val isConvex = cross(b - a, c - b) * signed > 0f
                if (!isConvex) continue
                val containsOther = idx.any { j ->
                    val p = points[j]
                    p != a && p != b && p != c && pointInTri(p, a, b, c)
                }
                if (containsOther) continue
                tris.add(Tri(a, b, c)); idx.removeAt(i); clipped = true; break
            }
            if (!clipped) break // degenerate/self-intersecting input: emit what we have, never hang
        }
        if (idx.size == 3) tris.add(Tri(points[idx[0]], points[idx[1]], points[idx[2]]))
        return tris
    }

    fun signedArea(p: List<Vec2>): Float {
        var s = 0f
        for (i in p.indices) { val a = p[i]; val b = p[(i + 1) % p.size]; s += a.x * b.y - b.x * a.y }
        return s * 0.5f
    }
    private operator fun Vec2.minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    private fun cross(a: Vec2, b: Vec2) = a.x * b.y - a.y * b.x
    private fun pointInTri(p: Vec2, a: Vec2, b: Vec2, c: Vec2): Boolean {
        val d1 = cross(b - a, p - a); val d2 = cross(c - b, p - b); val d3 = cross(a - c, p - c)
        val hasNeg = d1 < 0 || d2 < 0 || d3 < 0; val hasPos = d1 > 0 || d2 > 0 || d3 > 0
        return !(hasNeg && hasPos)
    }

    /** Polygon outline offset for mask expansion. Positive expands, negative erodes (miter join). */
    fun offsetPolygon(points: List<Vec2>, amountPx: Float): List<Vec2> {
        if (amountPx == 0f || points.size < 3) return points
        val n = points.size; val out = ArrayList<Vec2>(n)
        for (i in 0 until n) {
            val prev = points[(i + n - 1) % n]; val cur = points[i]; val next = points[(i + 1) % n]
            val e1 = normalize(cur - prev); val e2 = normalize(next - cur)
            val n1 = Vec2(e1.y, -e1.x); val n2 = Vec2(e2.y, -e2.x)
            val bis = normalize(Vec2(n1.x + n2.x, n1.y + n2.y))
            val cosHalf = (bis.x * n1.x + bis.y * n1.y).coerceAtLeast(0.2f) // clamp miter length
            out.add(Vec2(cur.x + bis.x * amountPx / cosHalf, cur.y + bis.y * amountPx / cosHalf))
        }
        return out
    }
    private fun normalize(v: Vec2): Vec2 {
        val l = kotlin.math.sqrt(v.x * v.x + v.y * v.y)
        return if (l < 1e-6f) Vec2(0f, 0f) else Vec2(v.x / l, v.y / l)
    }

    data class Tri(val a: Vec2, val b: Vec2, val c: Vec2)
}
