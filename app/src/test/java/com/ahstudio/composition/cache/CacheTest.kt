package com.ahstudio.composition.cache

import com.ahstudio.composition.graph.CompositionGraph
import com.ahstudio.composition.graph.CompositionId
import com.ahstudio.composition.graph.CompositionLayer
import com.ahstudio.composition.graph.LayerId
import com.ahstudio.composition.graph.LayerType
import org.junit.Assert.*
import org.junit.Test

class CacheTest {
    @Test fun `lru eviction respects budget`() {
        val cache = FrameCache(maxBytes = 200L)
        var evicted = 0
        val k1 = FrameCache.Key(1L, 0L, 10, 10, 0L)
        val k2 = FrameCache.Key(1L, 1L, 10, 10, 0L)
        val k3 = FrameCache.Key(1L, 2L, 10, 10, 0L)

        cache.put(k1, FrameCache.Entry(101, 100L)) { evicted++ }
        cache.put(k2, FrameCache.Entry(102, 100L)) { evicted++ }
        assertEquals(0, evicted)

        // Adding 3rd entry exceeds 200 budget -> evicts k1
        cache.put(k3, FrameCache.Entry(103, 100L)) { evicted++ }
        assertEquals(1, evicted)
        assertNull(cache.get(k1))
        assertNotNull(cache.get(k2))
        assertNotNull(cache.get(k3))
    }

    @Test fun `state generation changes on property or structural bump`() {
        val g = CompositionGraph(CompositionId(1))
        val inv = CacheInvalidator(g)
        val g0 = inv.currentGeneration()

        val l = CompositionLayer(LayerId(1), LayerType.SHAPE, "s", 0, 100, 0)
        g.addLayer(l)
        val g1 = inv.currentGeneration()
        assertNotEquals(g0, g1)

        g.touchProperty(LayerId(1))
        val g2 = inv.currentGeneration()
        assertNotEquals(g1, g2)
    }
}
