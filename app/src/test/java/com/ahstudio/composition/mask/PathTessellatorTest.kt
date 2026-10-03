package com.ahstudio.composition.mask

import com.ahstudio.composition.graph.PathData
import org.junit.Assert.*
import org.junit.Test

class PathTessellatorTest {
    @Test fun `square triangulates to two triangles`() {
        val path = PathData(listOf(
            PathData.Cmd.M(0f, 0f),
            PathData.Cmd.L(100f, 0f),
            PathData.Cmd.L(100f, 100f),
            PathData.Cmd.L(0f, 100f),
            PathData.Cmd.Z
        ))
        val pts = PathTessellator.flatten(path)
        val tris = PathTessellator.triangulate(pts)
        assertEquals(2, tris.size)
    }

    @Test fun `signed area positive for CCW`() {
        val ccw = listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(10f, 10f), Vec2(0f, 10f))
        val area = PathTessellator.signedArea(ccw)
        assertTrue(area > 0)
    }

    @Test fun `offset polygon preserves vertex count for non-degenerate input`() {
        val pts = listOf(Vec2(0f, 0f), Vec2(100f, 0f), Vec2(100f, 100f), Vec2(0f, 100f))
        val expanded = PathTessellator.offsetPolygon(pts, 5f)
        assertEquals(4, expanded.size)
    }
}
