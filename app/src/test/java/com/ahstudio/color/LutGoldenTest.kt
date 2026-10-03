package com.ahstudio.color

import com.ahstudio.color.lut.*
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class LutGoldenTest {

    private val identity2 = """
        TITLE "identity"
        LUT_3D_SIZE 2
        0 0 0
        1 0 0
        0 1 0
        1 1 0
        0 0 1
        1 0 1
        0 1 1
        1 1 1
    """.trimIndent()

    @Test fun parsesIdentity_andSamplesIdentity() {
        val lut = CubeParser.parse(StringReader(identity2)) as Lut3D
        assertEquals(2, lut.size); assertEquals("identity", lut.title)
        val s = LutSampler.sampleTrilinear(lut, 0.3f, 0.6f, 0.9f)
        assertArrayEquals(floatArrayOf(0.3f, 0.6f, 0.9f), s, 1e-5f)
    }

    @Test fun knownChannelBoostLut() {
        // out.r = 0.1 + 0.8*r ; out.g = g ; out.b = b
        val cube = """
            LUT_3D_SIZE 2
            0.1 0 0
            0.9 0 0
            0.1 1 0
            0.9 1 0
            0.1 0 1
            0.9 0 1
            0.1 1 1
            0.9 1 1
        """.trimIndent()
        val lut = CubeParser.parse(StringReader(cube)) as Lut3D
        val s = LutSampler.sampleTrilinear(lut, 0.25f, 0.6f, 0.9f)
        assertArrayEquals(floatArrayOf(0.3f, 0.6f, 0.9f), s, 1e-5f)
    }

    @Test fun domainMappingRespected() {
        val cube = """
            LUT_3D_SIZE 2
            DOMAIN_MIN 0.1 0.1 0.1
            DOMAIN_MAX 0.9 0.9 0.9
            0 0 0
            1 0 0
            0 1 0
            1 1 0
            0 0 1
            1 0 1
            0 1 1
            1 1 1
        """.trimIndent()
        val lut = CubeParser.parse(StringReader(cube)) as Lut3D
        // input 0.1 -> domain 0.0 ; 0.5 -> 0.5 ; 0.9 -> 1.0 (identity on domain)
        val s = LutSampler.sampleTrilinear(lut, 0.1f, 0.5f, 0.9f)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f), s, 1e-5f)
    }

    @Test fun lut1dInterpolation() {
        val lut = Lut1D("boost", 2, floatArrayOf(0f, 0f, 0f, 0.8f, 1f, 0.6f))
        assertArrayEquals(floatArrayOf(0.8f, 1f, 0.6f), LutSampler.sample1d(lut, 1f, 1f, 1f), 1e-6f)
        assertArrayEquals(floatArrayOf(0.4f, 0.5f, 0.3f), LutSampler.sample1d(lut, 0.5f, 0.5f, 0.5f), 1e-6f)
    }

    @Test fun corruptInputsAreRejected_notCrashes() {
        assertThrows(LutParseException::class.java) { CubeParser.parse(StringReader("LUT_3D_SIZE 2\n0 0 0\n")) }
        assertThrows(LutParseException::class.java) { CubeParser.parse(StringReader("LUT_3D_SIZE 2\nfoo bar baz\n")) }
        assertThrows(LutParseException::class.java) { CubeParser.parse(StringReader(identity2 + "\nLUT_1D_SIZE 4\n")) }
        assertThrows(LutParseException::class.java) { CubeParser.parse(StringReader("TITLE only\n0 0 0\n")) }
        assertThrows(LutParseException::class.java) { CubeParser.parse(StringReader("LUT_3D_SIZE 2\n0 0 NaN\n")) }
    }

    @Test fun cacheEvictsOldest() {
        val c = LutCache(2)
        c.put("a", Lut1D("a", 2, FloatArray(6))); c.put("b", Lut1D("b", 2, FloatArray(6)))
        c.get("a")                       // touch a
        c.put("c", Lut1D("c", 2, FloatArray(6)))  // evicts b (LRU)
        assertNull(c.get("b")); assertNotNull(c.get("a")); assertNotNull(c.get("c"))
    }
}
