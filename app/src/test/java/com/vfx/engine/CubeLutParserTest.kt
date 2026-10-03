package com.vfx.engine

import com.vfx.engine.core.EffectEngineException
import com.vfx.engine.core.lut.CubeLut
import com.vfx.engine.core.lut.CubeLutParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CubeLutParserTest {

    @Test
    fun `parse 3d cube text correctly`() {
        val cubeContent = """
            # Adobe Cube LUT
            TITLE "Test 3D LUT"
            LUT_3D_SIZE 2
            DOMAIN_MIN 0.0 0.0 0.0
            DOMAIN_MAX 1.0 1.0 1.0
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val lut = CubeLutParser.parse(cubeContent)
        assertEquals(2, lut.size3d)
        assertEquals("Test 3D LUT", lut.title)
        assertTrue(lut.is3D)
        assertNotNull(lut.data3d)
        assertEquals(24, lut.data3d!!.size) // 2^3 * 3 = 24
    }

    @Test
    fun `parse 1d cube text correctly`() {
        val cubeContent = """
            TITLE "Test 1D LUT"
            LUT_1D_SIZE 2
            0.0 0.0 0.0
            1.0 1.0 1.0
        """.trimIndent()

        val lut = CubeLutParser.parse(cubeContent)
        assertEquals(2, lut.size1d)
        assertTrue(lut.is1D)
        assertNotNull(lut.data1d)
        assertEquals(6, lut.data1d!!.size)
    }

    @Test
    fun `identity lut trilinear sampling returns original color`() {
        val identityLut = CubeLut.identity3d(17)
        val out = FloatArray(3)

        identityLut.sample3dTrilinear(0.25f, 0.5f, 0.75f, out)
        assertEquals(0.25f, out[0], 0.05f)
        assertEquals(0.5f, out[1], 0.05f)
        assertEquals(0.75f, out[2], 0.05f)
    }

    @Test
    fun `rejects invalid or missing lut size`() {
        val invalidContent = """
            TITLE "Invalid"
            0.0 0.0 0.0
        """.trimIndent()

        try {
            CubeLutParser.parse(invalidContent)
            fail("Expected EffectEngineException for missing LUT size")
        } catch (e: EffectEngineException) {
            // Expected
        }
    }
}
