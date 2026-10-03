package com.ute.text3d

import com.ute.core.Vec2

/**
 * Ear-clipping triangulation with hole bridging (the "keyhole" method):
 * each hole contour is connected to the nearest outer vertex with two
 * coincident edges, collapsing to a single polygon before clipping.
 */
object Triangulator {

    /** Returns triangle index triples into the combined vertex list. */
    fun triangulate(outer: List<Vec2>, holes: List<List<Vec2>>): List<Int> {
        val poly = outer.toMutableList()
        val indexMap = (0 until outer.size).toMutableList()

        for (hole in holes) {
            if (hole.size < 3) continue
            val hp = hole.minByOrNull { it.x } ?: continue
            var bestIdx = 0; var bestD = Float.MAX_VALUE
            poly.forEachIndexed { i, p ->
                val d = (p.x - hp.x) * (p.x - hp.x) + (p.y - hp.y) * (p.y - hp.y)
                if (d < bestD) { bestD = d; bestIdx = i }
            }
            val insertAt = bestIdx + 1
            val newIndex = outer.size + holes.indexOf(hole) * hole.size
            val holeIndices = hole.indices.map { k -> newIndex + k }
            poly.addAll(insertAt, hole + listOf(hole.first()))
            val mapped = holeIndices + listOf(holeIndices.first())
            indexMap.addAll(insertAt, mapped)
        }

        val indices = poly.indices.toMutableList()
        val triangles = ArrayList<Int>()
        var guard = 0
        while (indices.size > 3 && guard++ < 10000) {
            var earFound = false
            for (i in indices.indices) {
                val a = indices[(i + indices.size - 1) % indices.size]
                val b = indices[i]
                val c = indices[(i + 1) % indices.size]
                val pa = poly[a]; val pb = poly[b]; val pc = poly[c]
                if (cross(pa, pb, pc) <= 0f) continue
                if (containsAny(poly, indices, a, b, c)) continue
                triangles.addAll(listOf(indexMap[a], indexMap[b], indexMap[c]))
                indices.removeAt(i); earFound = true; break
            }
            if (!earFound) break
        }
        if (indices.size == 3) {
            triangles.addAll(listOf(indexMap[indices[0]], indexMap[indices[1]], indexMap[indices[2]]))
        }
        return triangles
    }

    private fun cross(a: Vec2, b: Vec2, c: Vec2) =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun containsAny(poly: List<Vec2>, indices: List<Int>, a: Int, b: Int, c: Int): Boolean {
        val pa = poly[a]; val pb = poly[b]; val pc = poly[c]
        for (i in indices) {
            if (i == a || i == b || i == c) continue
            if (pointInTri(poly[i], pa, pb, pc)) return true
        }
        return false
    }

    private fun pointInTri(p: Vec2, a: Vec2, b: Vec2, c: Vec2): Boolean {
        val d1 = cross(a, b, p); val d2 = cross(b, c, p); val d3 = cross(c, a, p)
        val hasNeg = d1 < 0 || d2 < 0 || d3 < 0
        val hasPos = d1 > 0 || d2 > 0 || d3 > 0
        return !(hasNeg && hasPos)
    }
}
