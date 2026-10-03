package com.ahstudio.composition.transform

import com.ahstudio.composition.graph.*
import org.junit.Assert.*
import org.junit.Test

class TransformEvaluationTest {
    @Test fun `anchor maps to position`() {
        val t = Transform2D(
            positionX = PropertyTrack(100f), positionY = PropertyTrack(100f),
            anchorX = PropertyTrack(50f), anchorY = PropertyTrack(50f),
            rotationDeg = PropertyTrack(90f)
        )
        val m = TransformEvaluator.evaluate(t, 0L)
        assertEquals(150f, m.mapX(50f, 50f), 0.001f)
        assertEquals(150f, m.mapY(50f, 50f), 0.001f)
    }

    @Test fun `scale is around anchor`() {
        val t = Transform2D(
            scaleX = PropertyTrack(2f), scaleY = PropertyTrack(2f),
            anchorX = PropertyTrack(10f), anchorY = PropertyTrack(10f)
        )
        val m = TransformEvaluator.evaluate(t, 0L)
        assertEquals(30f, m.mapX(20f, 10f), 0.001f)
        assertEquals(10f, m.mapY(20f, 10f), 0.001f)
    }

    @Test fun `parent chain composes`() {
        val parent = CompositionLayer(
            LayerId(1), LayerType.NULL, "p", 0, 100_000, 0,
            transform = Transform2D(positionX = PropertyTrack(10f), positionY = PropertyTrack(0f))
        )
        val child = CompositionLayer(
            LayerId(2), LayerType.SHAPE, "c", 0, 100_000, 1,
            parentId = LayerId(1),
            transform = Transform2D(positionX = PropertyTrack(5f), positionY = PropertyTrack(0f))
        )
        val world = TransformEvaluator.worldOf(child, { id -> if (id == 1L) parent else null }, 0L)
        assertEquals(15f, world.mapX(0f, 0f), 0.001f)
    }

    @Test fun `flipH negates x around anchor`() {
        val t = Transform2D(anchorX = PropertyTrack(0f), anchorY = PropertyTrack(0f), flipH = true)
        val m = TransformEvaluator.evaluate(t, 0L)
        assertEquals(-1f, m.mapX(1f, 0f), 0.001f)
    }

    @Test fun `keyframed rotation interpolates linearly`() {
        val t = Transform2D(rotationDeg = PropertyTrack(0f, listOf(
            PropertyTrack.Kf(0, 0f), PropertyTrack.Kf(100, 90f)
        )))
        val m = TransformEvaluator.evaluate(t, 50L)
        assertEquals(kotlin.math.cos(Math.toRadians(45.0)).toFloat(), m.a, 0.001f)
    }
}
