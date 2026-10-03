package com.ahstudio.composition.graph

import org.junit.Assert.*
import org.junit.Test

class CompositionGraphTest {
    @Test fun `draw order respects z then insertion`() {
        val g = CompositionGraph(CompositionId(1))
        val l1 = CompositionLayer(LayerId(1), LayerType.SHAPE, "l1", 0, 100, 0, zOrder = 0)
        val l2 = CompositionLayer(LayerId(2), LayerType.SHAPE, "l2", 0, 100, 1, zOrder = 0)
        val l3 = CompositionLayer(LayerId(3), LayerType.SHAPE, "l3", 0, 100, 0, zOrder = 5)
        g.addLayer(l3); g.addLayer(l1); g.addLayer(l2)
        val order = g.drawOrderAt(10)
        assertEquals(listOf(1L, 2L, 3L), order.map { it.id.value })
    }

    @Test fun `cycle detection flags loop`() {
        val g = CompositionGraph(CompositionId(1))
        g.addLayer(CompositionLayer(LayerId(1), LayerType.NULL, "a", 0, 100, 0, parentId = LayerId(2)))
        g.addLayer(CompositionLayer(LayerId(2), LayerType.NULL, "b", 0, 100, 1, parentId = LayerId(1)))
        val errs = g.validate()
        assertTrue(errs.any { it is CompositionError.CircularParenting })
    }

    @Test fun `missing parent reported`() {
        val g = CompositionGraph(CompositionId(1))
        g.addLayer(CompositionLayer(LayerId(1), LayerType.SHAPE, "a", 0, 100, 0, parentId = LayerId(999)))
        val errs = g.validate()
        assertTrue(errs.any { it is CompositionError.MissingParent })
    }

    @Test fun `removing parent clears child reference`() {
        val g = CompositionGraph(CompositionId(1))
        g.addLayer(CompositionLayer(LayerId(1), LayerType.NULL, "p", 0, 100, 0))
        g.addLayer(CompositionLayer(LayerId(2), LayerType.SHAPE, "c", 0, 100, 1, parentId = LayerId(1)))
        g.removeLayer(LayerId(1))
        assertNull(g.layer(LayerId(2))!!.parentId)
    }

    @Test fun `wouldCreateParentCycle catches transitive cycle`() {
        val g = CompositionGraph(CompositionId(1))
        g.addLayer(CompositionLayer(LayerId(1), LayerType.NULL, "a", 0, 100, 0))
        g.addLayer(CompositionLayer(LayerId(2), LayerType.NULL, "b", 0, 100, 1, parentId = LayerId(1)))
        g.addLayer(CompositionLayer(LayerId(3), LayerType.NULL, "c", 0, 100, 2, parentId = LayerId(2)))
        assertTrue(GraphValidator.wouldCreateParentCycle(g, child = LayerId(1), parent = LayerId(3)))
        assertFalse(GraphValidator.wouldCreateParentCycle(g, child = LayerId(4), parent = LayerId(3)))
    }
}
