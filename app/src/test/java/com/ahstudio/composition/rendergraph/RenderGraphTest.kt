package com.ahstudio.composition.rendergraph

import org.junit.Assert.*
import org.junit.Test

class RenderGraphTest {
    @Test fun `topo order respects dependencies`() {
        val rg = RenderGraph()
        val src = RenderGraph.Node.Source(1L, RenderGraph.Node.Source.Kind.IMAGE)
        val xf = RenderGraph.Node.TransformPass(1L)
        val blend = RenderGraph.Node.BlendNode(1L, xf.id, 0, 1f, 0)
        val out = RenderGraph.Node.Output(blend.id)
        rg.add(src); rg.add(xf); rg.add(blend); rg.add(out)

        val order = rg.topoOrder()
        assertEquals(4, order.size)
        assertEquals(src.id, order[0].id)
        assertEquals(xf.id, order[1].id)
        assertEquals(blend.id, order[2].id)
        assertEquals(out.id, order[3].id)
    }

    @Test(expected = IllegalStateException::class)
    fun `cycle detection throws`() {
        val rg = RenderGraph()
        val n1 = object : RenderGraph.Node("n1", listOf("n2")) {}
        val n2 = object : RenderGraph.Node("n2", listOf("n1")) {}
        rg.add(n1); rg.add(n2)
        rg.topoOrder()
    }
}
