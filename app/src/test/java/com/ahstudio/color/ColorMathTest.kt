package com.ahstudio.color

import com.ahstudio.color.math.ColorMath
import com.ahstudio.color.math.Mat3
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ColorMathTest {
    private val tol = 1e-4f

    @Test fun hslRoundTrip() {
        val rnd = Random(42)
        repeat(500) {
            val r = rnd.nextFloat(); val g = rnd.nextFloat(); val b = rnd.nextFloat()
            val hsl = ColorMath.rgbToHsl(r, g, b)
            val back = ColorMath.hslToRgb(hsl[0], hsl[1], hsl[2])
            assertArrayEquals(floatArrayOf(r, g, b), back, tol)
        }
    }

    @Test fun pureRedHsl() {
        val hsl = ColorMath.rgbToHsl(1f, 0f, 0f)
        assertEquals(0f, hsl[0], 1e-5f); assertEquals(1f, hsl[1], 1e-5f); assertEquals(0.5f, hsl[2], 1e-5f)
    }

    @Test fun hueDistanceWraps() {
        assertEquals(0.04f, ColorMath.hueDistance(0.98f, 0.02f), 1e-5f)
        assertEquals(0.0f,  ColorMath.hueDistance(0.25f, 0.25f), 1e-6f)
        assertEquals(0.5f,  ColorMath.hueDistance(0f, 0.5f), 1e-6f)
    }

    @Test fun oklabWhiteIsL1ab0() {
        val l = ColorMath.linearSrgbToOklab(1f, 1f, 1f)
        assertEquals(1f, l[0], 1e-3f); assertEquals(0f, l[1], 1e-4f); assertEquals(0f, l[2], 1e-4f)
    }

    @Test fun oklabRedGolden() {
        // Ottosson reference: linear sRGB red -> OKLab (0.628, 0.225, 0.126)
        val l = ColorMath.linearSrgbToOklab(1f, 0f, 0f)
        assertEquals(0.628f, l[0], 5e-3f); assertEquals(0.225f, l[1], 5e-3f); assertEquals(0.126f, l[2], 5e-3f)
    }

    @Test fun oklabRoundTrip() {
        val rnd = Random(7)
        repeat(300) {
            val c = floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat())
            val lab = ColorMath.linearSrgbToOklab(c[0], c[1], c[2])
            val back = ColorMath.oklabToLinearSrgb(lab[0], lab[1], lab[2])
            assertArrayEquals(c, back, 1e-4f)
        }
    }

    @Test fun mat3InverseRoundTrip() {
        val m = floatArrayOf(0.4124f, 0.3576f, 0.1805f, 0.2126f, 0.7152f, 0.0722f, 0.0193f, 0.1192f, 0.9505f)
        val id = Mat3.mul(m, Mat3.inverse(m))
        assertArrayEquals(Mat3.identity(), id, 1e-5f)
    }

    @Test fun labRoundTripAndWhitePoint() {
        val lab = ColorMath.xyzToLab(0.95047f, 1f, 1.08883f)
        assertEquals(100f, lab[0], 1e-3f); assertEquals(0f, lab[1], 1e-3f); assertEquals(0f, lab[2], 1e-3f)
        val xyz = ColorMath.labToXyz(lab[0], lab[1], lab[2])
        assertArrayEquals(floatArrayOf(0.95047f, 1f, 1.08883f), xyz, 1e-4f)
    }
}
