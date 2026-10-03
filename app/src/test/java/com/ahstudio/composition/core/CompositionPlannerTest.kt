package com.ahstudio.composition.core

import com.ahstudio.composition.graph.*
import org.junit.Assert.*
import org.junit.Test

class CompositionPlannerTest {
    @Test fun `matte layer is hidden from main pass`() {
        val g = CompositionGraph(CompositionId(1))
        val matte = CompositionLayer(LayerId(1), LayerType.SHAPE, "matte", 0, 100, 0, zOrder = 0)
        val consumer = CompositionLayer(LayerId(2), LayerType.VIDEO, "video", 0, 100, 1, zOrder = 1,
            trackMatteLayer = LayerId(1), trackMatteMode = TrackMatteMode.ALPHA)
        g.addLayer(matte); g.addLayer(consumer)
        val p = CompositionPlanner.plan(g, 10)
        assertTrue(p.hiddenMatteLayerIds.contains(1L))
        assertEquals(1, p.ops.size)
        assertTrue((p.ops[0] as CompositionPlanner.Op.DrawLayer).hasMatte)
    }

    @Test fun `inactive matte lets consumer draw normally`() {
        val g = CompositionGraph(CompositionId(1))
        val matte = CompositionLayer(LayerId(1), LayerType.SHAPE, "matte", 50, 100, 0, zOrder = 0)
        val consumer = CompositionLayer(LayerId(2), LayerType.VIDEO, "video", 0, 100, 1, zOrder = 1,
            trackMatteLayer = LayerId(1), trackMatteMode = TrackMatteMode.ALPHA)
        g.addLayer(matte); g.addLayer(consumer)
        val p = CompositionPlanner.plan(g, 10) // matte not yet active
        assertFalse(p.hiddenMatteLayerIds.contains(1L))
        val draw = p.ops.filterIsInstance<CompositionPlanner.Op.DrawLayer>().first()
        assertFalse(draw.hasMatte)
    }

    @Test fun `adjustment layers emit Adjust op`() {
        val g = CompositionGraph(CompositionId(1))
        g.addLayer(CompositionLayer(LayerId(1), LayerType.ADJUSTMENT, "adj", 0, 100, 0))
        val p = CompositionPlanner.plan(g, 10)
        assertTrue(p.ops.first() is CompositionPlanner.Op.Adjust)
    }

    @Test fun `null layers are skipped`() {
        val g = CompositionGraph(CompositionId(1))
        g.addLayer(CompositionLayer(LayerId(1), LayerType.NULL, "n", 0, 100, 0))
        val p = CompositionPlanner.plan(g, 10)
        assertTrue(p.ops.first() is CompositionPlanner.Op.Skip)
    }
}
