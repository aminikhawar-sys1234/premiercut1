package com.ute.text3d

import android.graphics.Path
import android.graphics.PathMeasure
import com.ute.core.Vec2
import kotlin.math.*

/**
 * Glyph outline → extruded, beveled 3D mesh.
 * Outlines come from Paint.getTextPath (platform stack, same engine that shaped).
 * Front/back faces are ear-clipped; sides are swept with a multi-step bevel profile.
 * Front face normals +Z, back −Z, sides face outward. All in glyph-space px.
 */
class ExtrusionBuilder {

    data class Mesh(val positions: FloatArray, val normals: FloatArray, val triangleCount: Int)

    fun flattenPath(path: Path, tolerancePx: Float = 0.5f): List<List<Vec2>> {
        val contours = ArrayList<List<Vec2>>()
        val measure = PathMeasure(path, false)
        do {
            val len = measure.length
            if (len < tolerancePx) continue
            val steps = (len / tolerancePx).toInt().coerceIn(8, 512)
            val pts = ArrayList<Vec2>(steps + 1)
            val pos = FloatArray(2)
            for (i in 0..steps) {
                measure.getPosTan(i.toFloat() / steps * len, pos, null)
                pts.add(Vec2(pos[0], -pos[1]))   // flip Y: font space is y-down, GL y-up
            }
            if (pts.size >= 3) contours.add(pts)
        } while (measure.nextContour())
        return contours
    }

    fun build(path: Path, depthPx: Float, bevelWidthPx: Float, bevelSteps: Int): Mesh {
        val contours = flattenPath(path)
        if (contours.isEmpty()) return Mesh(FloatArray(0), FloatArray(0), 0)

        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()

        fun push(p: Vec2, z: Float, n: Vec3) {
            positions.add(p.x); positions.add(p.y); positions.add(z)
            normals.add(n.x); normals.add(n.y); normals.add(n.z)
        }

        contours.forEach { contour ->
            val idx = Triangulator.triangulate(contour, emptyList())
            val zF = depthPx / 2f
            val zB = -depthPx / 2f
            idx.forEach { vi ->
                val p = contour[vi % contour.size]
                push(p, zF, Vec3(0f, 0f, 1f))
            }
            idx.reversed().forEach { vi ->
                val p = contour[vi % contour.size]
                push(p, zB, Vec3(0f, 0f, -1f))
            }
        }

        val steps = bevelSteps.coerceIn(0, 4)
        val profile = ArrayList<Pair<Float, Float>>()
        if (bevelWidthPx > 0f) {
            for (s in steps downTo 1) {
                val f = s.toFloat() / (steps + 1)
                profile.add(1f - f to depthPx / 2f + bevelWidthPx * f * 0.7f)
            }
        }
        profile.add(1f to depthPx / 2f)
        profile.add(1f to -depthPx / 2f)
        if (bevelWidthPx > 0f) {
            for (s in 1..steps) {
                val f = s.toFloat() / (steps + 1)
                profile.add(1f - f to -depthPx / 2f - bevelWidthPx * f * 0.7f)
            }
        }

        contours.forEach { contour ->
            for (i in contour.indices) {
                val a = contour[i]; val b = contour[(i + 1) % contour.size]
                val ex = b.x - a.x; val ey = b.y - a.y
                val el = hypot(ex.toDouble(), ey.toDouble()).toFloat().coerceAtLeast(1e-5f)
                var nx = -ey / el; var ny = ex / el
                val cx = contour.map { it.x }.average().toFloat()
                val cy = contour.map { it.y }.average().toFloat()
                val toMidX = (a.x + b.x) / 2f - cx; val toMidY = (a.y + b.y) / 2f - cy
                if (nx * toMidX + ny * toMidY < 0f) { nx = -nx; ny = -ny }

                for (k in 0 until profile.size - 1) {
                    val (o0, z0) = profile[k]
                    val (o1, z1) = profile[k + 1]
                    val a0 = Vec2(a.x + nx * bevelWidthPx * (1f - o0), a.y + ny * bevelWidthPx * (1f - o0))
                    val b0 = Vec2(b.x + nx * bevelWidthPx * (1f - o0), b.y + ny * bevelWidthPx * (1f - o0))
                    val a1 = Vec2(a.x + nx * bevelWidthPx * (1f - o1), a.y + ny * bevelWidthPx * (1f - o1))
                    val b1 = Vec2(b.x + nx * bevelWidthPx * (1f - o1), b.y + ny * bevelWidthPx * (1f - o1))
                    listOf(a0 to z0, b0 to z0, a1 to z1, a1 to z1, b0 to z0, b1 to z1).forEach { (p, z) ->
                        push(p, z, Vec3(nx, ny, 0f))
                    }
                }
            }
        }

        return Mesh(positions.toFloatArray(), normals.toFloatArray(), positions.size / 9)
    }

    data class Vec3(val x: Float, val y: Float, val z: Float)
}
