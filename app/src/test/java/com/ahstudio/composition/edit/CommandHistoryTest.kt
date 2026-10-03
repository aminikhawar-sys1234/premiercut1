package com.ahstudio.composition.edit

import com.ahstudio.composition.graph.*
import org.junit.Assert.*
import org.junit.Test

class CommandHistoryTest {
    @Test fun testAddAndRemoveLayerUndoRedo() {
        val g = CompositionGraph(CompositionId(1))
        val h = CommandHistory()
        val facade = CompositionEditorFacade(g, h)
        val l = CompositionLayer(LayerId(1), LayerType.SHAPE, "s", 0, 100, 0)

        facade.addLayer(l)
        assertEquals(1, g.allLayers.size)

        facade.undo()
        assertEquals(0, g.allLayers.size)

        facade.redo()
        assertEquals(1, g.allLayers.size)
    }

    @Test fun testTransformEditRestoresExactOldValueOnUndo() {
        val g = CompositionGraph(CompositionId(1))
        val h = CommandHistory()
        val facade = CompositionEditorFacade(g, h)
        val l = CompositionLayer(LayerId(1), LayerType.SHAPE, "s", 0, 100, 0, transform = Transform2D(positionX = PropertyTrack(10f)))
        g.addLayer(l)

        facade.setTransform(LayerId(1), Transform2D(positionX = PropertyTrack(50f)))
        assertEquals(50f, g.layer(LayerId(1))!!.transform.positionX.staticValue, 0.001f)

        facade.undo()
        assertEquals(10f, g.layer(LayerId(1))!!.transform.positionX.staticValue, 0.001f)
    }

    @Test fun testCircularParentRejectionThrows() {
        val g = CompositionGraph(CompositionId(1))
        val h = CommandHistory()
        val facade = CompositionEditorFacade(g, h)
        val l1 = CompositionLayer(LayerId(1), LayerType.NULL, "p1", 0, 100, 0)
        val l2 = CompositionLayer(LayerId(2), LayerType.NULL, "p2", 0, 100, 1, parentId = LayerId(1))
        g.addLayer(l1); g.addLayer(l2)

        try {
            facade.setParent(LayerId(1), LayerId(2))
            fail("Expected exception for circular parenting")
        } catch (e: IllegalArgumentException) {
            // Success
        }
    }
}
