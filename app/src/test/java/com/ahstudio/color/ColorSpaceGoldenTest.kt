package com.ahstudio.color

import com.ahstudio.color.math.ColorMath
import com.ahstudio.color.math.Mat3
import com.ahstudio.color.space.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ColorSpaceGoldenTest {
    private val tol = 3e-3f

    @Test fun rec709MatrixGolden() {
        val m = ColorSpaceRegistry.REC709.rgbToXyz
        val expected = floatArrayOf(
            0.4124f, 0.3576f, 0.1805f,
            0.2126f, 0.7152f, 0.0722f,
            0.0193f, 0.1192f, 0.9505f)
        assertArrayEquals(expected, m, tol)
    }

    @Test fun rec2020LumaRowGolden() {
        val y = ColorSpaceRegistry.REC2020.lumaCoefficients()
        assertArrayEquals(floatArrayOf(0.2627f, 0.6780f, 0.0593f), y, tol)
    }

    @Test fun rec709LumaRowGolden() {
        assertArrayEquals(floatArrayOf(0.2126f, 0.7152f, 0.0722f),
            ColorSpaceRegistry.REC709.lumaCoefficients(), tol)
    }

    @Test fun srgbXyzRoundTrip() {
        val rnd = Random(11); val s = ColorSpaceRegistry.SRGB
        repeat(300) {
            val c = floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat())
            val xyz = Mat3.mulVec(s.rgbToXyz, c)
            val back = Mat3.mulVec(s.xyzToRgb, xyz)
            assertArrayEquals(c, back, 1e-5f)
        }
    }

    @Test fun wideToNarrowToWideRoundTrip() {
        val a = ColorSpaceRegistry.REC2020; val b = ColorSpaceRegistry.REC709
        val c = floatArrayOf(1f, 0f, 0f) // 2020 red is out-of-gamut in 709 (negative channels OK)
        val narrow = ColorSpaceConverter.convert(c, a, b)
        val back = ColorSpaceConverter.convert(narrow, b, a)
        assertArrayEquals(c, back, 1e-4f)
        assertTrue(narrow.any { it < 0f })  // genuinely out-of-gamut — confirms real conversion
    }

    @Test fun bradfordAdaptationMapsWhite() {
        // D65 (0.3127,0.3290) -> D50 (0.3457,0.3585): adapted D65 white XYZ must equal D50 white XYZ
        val m = ChromaticAdaptation.adapt(0.3127f, 0.3290f, 0.3457f, 0.3585f)
        val d65 = ColorMath.xyToXyz(0.3127f, 0.3290f)
        val adapted = Mat3.mulVec(m, d65)
        val d50 = ColorMath.xyToXyz(0.3457f, 0.3585f)
        assertArrayEquals(d50, adapted, 2e-3f)
    }
}
