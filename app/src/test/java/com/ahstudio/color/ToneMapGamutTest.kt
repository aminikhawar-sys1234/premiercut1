package com.ahstudio.color

import com.ahstudio.color.core.ToneMapMode
import com.ahstudio.color.hdr.ToneMappers
import com.ahstudio.color.space.ColorSpaceRegistry
import com.ahstudio.color.space.GamutMapping
import org.junit.Assert.*
import org.junit.Test

class ToneMapGamutTest {
    @Test fun acesBlackZero_whiteClampsOne() {
        assertEquals(0f, ToneMappers.apply(ToneMapMode.ACES, floatArrayOf(0f, 0f, 0f))[0], 1e-6f)
        assertEquals(1f, ToneMappers.apply(ToneMapMode.ACES, floatArrayOf(8f, 8f, 8f))[0], 1e-4f)
    }

    @Test fun reinhardWhitePoint4() {
        assertEquals(1.0f, ToneMappers.apply(ToneMapMode.REINHARD, floatArrayOf(4f, 4f, 4f))[0], 5e-3f)
    }

    @Test fun hableBlackZero_whiteAt5_6() {
        assertEquals(0f, ToneMappers.apply(ToneMapMode.HABLE, floatArrayOf(0f, 0f, 0f))[0], 1e-5f)
        assertEquals(1f, ToneMappers.apply(ToneMapMode.HABLE, floatArrayOf(5.6f, 5.6f, 5.6f))[0], 5e-3f)
    }

    @Test fun toneMappersMonotonic() {
        var prev = -1f
        var x = 0f
        repeat(50) {
            val y = ToneMappers.apply(ToneMapMode.ACES, floatArrayOf(x, x, x))[0]
            assertTrue(y >= prev); prev = y; x += 0.2f
        }
    }

    @Test fun gamutCompressionPreservesLuma_andContainsResult() {
        val lum = ColorSpaceRegistry.REC709.lumaCoefficients()
        fun luma(c: FloatArray) = lum[0]*c[0] + lum[1]*c[1] + lum[2]*c[2]
        val oog = floatArrayOf(1.5f, -0.2f, 0.3f)
        val y0 = luma(oog)
        val out = GamutMapping.compress(oog, lum)
        assertEquals(y0, luma(out), 1e-4f)                       // luma preserved exactly
        assertTrue(out.all { it >= -1e-4f && it <= 1f + 1e-4f }) // now inside gamut
        val ingamut = floatArrayOf(0.2f, 0.5f, 0.8f)
        assertArrayEquals(ingamut, GamutMapping.compress(ingamut, lum), 1e-6f) // untouched
    }
}
