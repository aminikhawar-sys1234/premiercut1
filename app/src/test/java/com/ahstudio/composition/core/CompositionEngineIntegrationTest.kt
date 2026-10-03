package com.ahstudio.composition.core

import com.ahstudio.composition.graph.*
import com.ahstudio.composition.integration.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompositionEngineIntegrationTest {

    private class StubGpuEnvironment : com.ahstudio.composition.gpu.GpuEnvironment {
        override fun <T> runOnGlThread(await: Boolean, block: () -> T): T = block()
        override fun release() {}
    }

    @Test fun `engine manages compositions and evaluates draw order`() {
        val gpu = StubGpuEnvironment()
        val timeSource = object : CompositionTimeSource { override val currentTimeUs: Long = 500_000L }
        val engine = CompositionEngine(
            gpuEnv = gpu,
            timeSource = timeSource,
            mediaBridge = NoopBridges.media,
            textBridge = NoopBridges.text,
            imageBridge = NoopBridges.image,
            effectsBridge = NoopBridges.effects
        )

        val comp = engine.createComposition(CompositionId(1))
        val l1 = CompositionLayer(LayerId(1), LayerType.SHAPE, "Layer1", 0, 1000_000, 0, zOrder = 0)
        val l2 = CompositionLayer(LayerId(2), LayerType.SHAPE, "Layer2", 0, 1000_000, 1, zOrder = 1)
        comp.addLayer(l1)
        comp.addLayer(l2)

        assertEquals(2, comp.allLayers.size)
        val drawOrder = comp.drawOrderAt(500_000L)
        assertEquals(listOf(1L, 2L), drawOrder.map { it.id.value })
    }

    @Test fun `planner accurately maps op types`() {
        val comp = CompositionGraph(CompositionId(1))
        val shapeLayer = CompositionLayer(LayerId(1), LayerType.SHAPE, "shape", 0, 1000_000, 0)
        val adjLayer = CompositionLayer(LayerId(2), LayerType.ADJUSTMENT, "adj", 0, 1000_000, 1)
        val nullLayer = CompositionLayer(LayerId(3), LayerType.NULL, "null", 0, 1000_000, 2)
        comp.addLayer(shapeLayer)
        comp.addLayer(adjLayer)
        comp.addLayer(nullLayer)

        val plan = CompositionPlanner.plan(comp, 500_000L)
        assertEquals(3, plan.ops.size)
        assertTrue(plan.ops[0] is CompositionPlanner.Op.DrawLayer)
        assertTrue(plan.ops[1] is CompositionPlanner.Op.Adjust)
        assertTrue(plan.ops[2] is CompositionPlanner.Op.Skip)
    }
}
