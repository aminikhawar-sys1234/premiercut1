package com.ahstudio.composition.io

import com.ahstudio.composition.graph.*
import org.junit.Assert.*
import org.junit.Test

class CompositionJsonTest {
    @Test fun testFullCompositionRoundTripSerialization() {
        val g = CompositionGraph(CompositionId(42))
        val l = CompositionLayer(
            LayerId(1), LayerType.SHAPE, "Square", 0, 1000_000, 0,
            transform = Transform2D(
                positionX = PropertyTrack(100f, listOf(PropertyTrack.Kf(0, 0f), PropertyTrack.Kf(100, 100f))),
                scaleX = PropertyTrack(2f)
            ),
            opacity = PropertyTrack(0.8f),
            blendMode = BlendMode.MULTIPLY,
            compositeOp = CompositeOp.SOURCE_OVER,
            masks = listOf(
                MaskInstance(
                    101,
                    PathData(listOf(PathData.Cmd.M(0f, 0f), PathData.Cmd.L(10f, 0f), PathData.Cmd.Z)),
                    MaskMode.INTERSECT, 0.9f, 2f, 1f, true
                )
            ),
            payload = LayerPayload.Shape(PathData(listOf(PathData.Cmd.M(0f, 0f), PathData.Cmd.Z)), 0xFF00FF00L)
        )
        g.addLayer(l)

        val json = CompositionJson.graphToJson(g).toString()
        val restored = CompositionJson.graphFromJson(json)

        assertEquals(g.compositionId.value, restored.compositionId.value)
        assertEquals(1, restored.allLayers.size)
        val rl = restored.layer(LayerId(1))!!
        assertEquals("Square", rl.name)
        assertEquals(BlendMode.MULTIPLY, rl.blendMode)
        assertEquals(0.8f, rl.opacity.staticValue, 0.001f)
        assertEquals(1, rl.masks.size)
        assertEquals(MaskMode.INTERSECT, rl.masks[0].mode)
        assertTrue(rl.masks[0].inverted)
    }
}
