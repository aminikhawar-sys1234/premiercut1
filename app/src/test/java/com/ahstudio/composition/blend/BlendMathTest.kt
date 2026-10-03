package com.ahstudio.composition.blend

import com.ahstudio.composition.graph.BlendMode
import org.junit.Assert.*
import org.junit.Test

class BlendMathTest {
    @Test fun `normal returns source`() = assertEquals(0.4f, BlendMath.blend(BlendMode.NORMAL, 0.8f, 0.4f), 0.0001f)
    @Test fun `multiply matches product`() = assertEquals(0.32f, BlendMath.blend(BlendMode.MULTIPLY, 0.8f, 0.4f), 0.0001f)
    @Test fun `screen commutative`() {
        val a = BlendMath.blend(BlendMode.SCREEN, 0.3f, 0.7f)
        val b = BlendMath.blend(BlendMode.SCREEN, 0.7f, 0.3f)
        assertEquals(a, b, 0.0001f)
    }
    @Test fun `overlay matches W3C hardLight swap`() {
        val o = BlendMath.blend(BlendMode.OVERLAY, 0.3f, 0.8f)
        val h = BlendMath.hardLight(0.8f, 0.3f)
        assertEquals(h, o, 0.0001f)
    }
    @Test fun `composite pure black over white multiply`() {
        val r = BlendMath.composite(BlendMode.MULTIPLY, floatArrayOf(1f, 1f, 1f), 1f, floatArrayOf(0f, 0f, 0f), 1f)
        assertEquals(0f, r[0], 0.001f); assertEquals(0f, r[1], 0.001f); assertEquals(0f, r[2], 0.001f); assertEquals(1f, r[3], 0.001f)
    }
    @Test fun `porter-duff src-over opaque replaces`() {
        val r = BlendMath.porterDuff(0, floatArrayOf(1f, 0f, 0f), 1f, floatArrayOf(0f, 1f, 0f), 1f)
        assertEquals(1f, r[0], 0.001f); assertEquals(0f, r[1], 0.001f); assertEquals(1f, r[3], 0.001f)
    }
    @Test fun `porter-duff src-in transparent dst produces zero`() {
        val r = BlendMath.porterDuff(1, floatArrayOf(1f, 0f, 0f), 1f, floatArrayOf(0f, 0f, 0f), 0f)
        assertEquals(0f, r[3], 0.001f)
    }
}
