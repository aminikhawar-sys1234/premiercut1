package com.vfx.engine

import com.vfx.engine.core.color.ColorScience
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorScienceTest {

    @Test
    fun `srgb to linear and back roundtrip`() {
        for (i in 0..255) {
            val srgb = i / 255f
            val lin = ColorScience.srgbToLinear(srgb)
            val restored = ColorScience.linearToSrgb(lin)
            assertEquals("Mismatch for sRGB=$srgb", srgb, restored, 1e-4f)
        }
    }

    @Test
    fun `luma coefficients sum to unity`() {
        val sum709 = ColorScience.LUMA_R_709 + ColorScience.LUMA_G_709 + ColorScience.LUMA_B_709
        assertEquals(1.0f, sum709, 1e-5f)

        val whiteLuma = ColorScience.luma709(1f, 1f, 1f)
        assertEquals(1.0f, whiteLuma, 1e-5f)

        val blackLuma = ColorScience.luma709(0f, 0f, 0f)
        assertEquals(0.0f, blackLuma, 1e-5f)
    }

    @Test
    fun `rgb to hsv and back roundtrip`() {
        val testColors = listOf(
            floatArrayOf(1f, 0f, 0f),      // Red
            floatArrayOf(0f, 1f, 0f),      // Green
            floatArrayOf(0f, 0f, 1f),      // Blue
            floatArrayOf(1f, 1f, 0f),      // Yellow
            floatArrayOf(0f, 1f, 1f),      // Cyan
            floatArrayOf(1f, 0f, 1f),      // Magenta
            floatArrayOf(0.5f, 0.5f, 0.5f) // Gray
        )

        for (rgb in testColors) {
            val hsv = ColorScience.rgbToHsv(rgb[0], rgb[1], rgb[2])
            val back = ColorScience.hsvToRgb(hsv[0], hsv[1], hsv[2])
            assertEquals(rgb[0], back[0], 1e-3f)
            assertEquals(rgb[1], back[1], 1e-3f)
            assertEquals(rgb[2], back[2], 1e-3f)
        }
    }

    @Test
    fun `rgb to hsl and back roundtrip`() {
        val testColors = listOf(
            floatArrayOf(0.8f, 0.2f, 0.4f),
            floatArrayOf(0.1f, 0.9f, 0.3f),
            floatArrayOf(0.3f, 0.4f, 0.8f)
        )

        for (rgb in testColors) {
            val hsl = ColorScience.rgbToHsl(rgb[0], rgb[1], rgb[2])
            val back = ColorScience.hslToRgb(hsl[0], hsl[1], hsl[2])
            assertEquals(rgb[0], back[0], 1e-3f)
            assertEquals(rgb[1], back[1], 1e-3f)
            assertEquals(rgb[2], back[2], 1e-3f)
        }
    }

    @Test
    fun `exposure multiplier doubles per stop`() {
        assertEquals(1f, ColorScience.exposureMultiplier(0f), 1e-5f)
        assertEquals(2f, ColorScience.exposureMultiplier(1f), 1e-5f)
        assertEquals(4f, ColorScience.exposureMultiplier(2f), 1e-5f)
        assertEquals(0.5f, ColorScience.exposureMultiplier(-1f), 1e-5f)
    }

    @Test
    fun `kelvin multipliers normalize green to 1`() {
        val multWarm = ColorScience.kelvinToRgbMultipliers(3200f) // Warm / tungsten
        assertEquals(1.0f, multWarm[1], 1e-5f)
        assertTrue(multWarm[0] > multWarm[2]) // Red > Blue for warm

        val multCool = ColorScience.kelvinToRgbMultipliers(7500f) // Cool / daylight
        assertEquals(1.0f, multCool[1], 1e-5f)
        assertTrue(multCool[2] > multCool[0]) // Blue > Red for cool
    }
}
