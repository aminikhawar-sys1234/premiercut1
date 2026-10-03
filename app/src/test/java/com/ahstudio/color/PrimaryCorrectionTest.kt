package com.ahstudio.color

import com.ahstudio.color.core.*
import com.ahstudio.color.curves.MonotoneCubicSpline
import com.ahstudio.color.hsl.HslEngine
import com.ahstudio.color.primary.WhiteBalance
import com.ahstudio.color.space.*
import org.junit.Assert.*
import org.junit.Test

class PrimaryCorrectionTest {

    // Linear-light harness: all transfers Linear -> pipeline reduces to pure math stages.
    private fun linearCfg() = ColorConfig(
        workingSpace = ColorSpaceRegistry.REC709,
        outputSpace = ColorSpaceRegistry.REC709,
        workingTransfer = TransferFunctions.Linear,
        outputTransfer = TransferFunctions.Linear,
        inputMetadata = InputColorMetadata(
            ColorSpaceRegistry.REC709, TransferFunctions.Linear, ColorRange.FULL, true)
    )

    private fun run(state: ColorState, r: Float, g: Float, b: Float,
                    cfg: ColorConfig = linearCfg()): FloatArray {
        val px = floatArrayOf(r, g, b, 1f)
        ColorPipelineCpu(cfg).process(state, px)
        return px
    }

    @Test fun exposureZeroIsIdentity() {
        val out = run(ColorState(), 0.25f, 0.5f, 0.75f)
        assertArrayEquals(floatArrayOf(0.25f, 0.5f, 0.75f, 1f), out, 2e-3f)
    }

    @Test fun exposurePlusOneDoublesLinear() {
        val out = run(ColorState(exposure = 1f), 0.25f, 0.5f, 0.75f)
        assertArrayEquals(floatArrayOf(0.5f, 1.0f, 1.5f, 1f), out, 5e-3f)
    }

    @Test fun contrastZeroIdentity_andPivotInvariant() {
        assertEquals(0.4f, run(ColorState(contrast = 0.3f), 0.4f, 0.4f, 0.4f)[0], 1e-4f)
        assertTrue(run(ColorState(contrast = 0.5f), 0.2f, 0.2f, 0.2f)[0] < 0.2f)
        assertTrue(run(ColorState(contrast = 0.5f), 0.6f, 0.6f, 0.6f)[0] > 0.6f)
    }

    @Test fun highlightsLiftBright_notDark() {
        val bright = run(ColorState(highlights = 1f), 0.9f, 0.9f, 0.9f)[0]
        val dark   = run(ColorState(highlights = 1f), 0.1f, 0.1f, 0.1f)[0]
        assertTrue(bright > 0.94f && bright < 1.0f)
        assertEquals(0.1f, dark, 2e-3f)
    }

    @Test fun shadowsDropDark_notBright() {
        val dark  = run(ColorState(shadows = -0.5f), 0.05f, 0.05f, 0.05f)[0]
        val bright= run(ColorState(shadows = -0.5f), 0.9f, 0.9f, 0.9f)[0]
        assertTrue(dark < 0.04f)
        assertEquals(0.9f, bright, 2e-3f)
    }

    @Test fun saturationMinusOneGivesLuma() {
        val y = 0.2126f * 0.3f + 0.7152f * 0.6f + 0.0722f * 0.9f
        val out = run(ColorState(saturation = -1f), 0.3f, 0.6f, 0.9f)
        assertEquals(y, out[0], 2e-3f); assertEquals(y, out[1], 2e-3f); assertEquals(y, out[2], 2e-3f)
    }

    @Test fun whiteBalanceZeroIsUnityGains() {
        assertArrayEquals(floatArrayOf(1f, 1f, 1f),
            WhiteBalance.computeGains(0f, 0f, ColorSpaceRegistry.REC709), 1e-6f)
    }

    @Test fun warmWhiteBalanceRaisesRedOverBlue() {
        val g = WhiteBalance.computeGains(0.5f, 0f, ColorSpaceRegistry.REC709)
        assertTrue(g[0] > 1f); assertTrue(g[2] < 1f); assertTrue(g[0] > g[2])
        assertEquals(1f, g[1], 0.08f)
    }

    @Test fun monotoneSplineNoOvershoot_andInterpolatesControls() {
        val lut = MonotoneCubicSpline.bakeLut(listOf(
            com.ahstudio.color.core.CurvePoint(0f, 0f),
            com.ahstudio.color.core.CurvePoint(0.5f, 0.1f),
            com.ahstudio.color.core.CurvePoint(1f, 1f)))
        for (i in 1 until lut.size) assertTrue(lut[i] >= lut[i - 1] - 1e-6f)   // monotone
        assertTrue(lut.min() >= 0f - 1e-4f && lut.max() <= 1f + 1e-4f)          // no overshoot
        assertEquals(0.1f, lut[(0.5f * 255).toInt()], 0.02f)                    // near control point
    }

    @Test fun hslWeightsNormalized_andRedBandShiftsRed() {
        val w = HslEngine.bandWeights(0f)
        assertEquals(1f, w.sum(), 1e-4f)
        assertTrue(w[0] > w[1]); assertEquals(0f, w[4], 1e-4f)

        val bands = com.ahstudio.color.core.HslBands(
            reds = com.ahstudio.color.core.HslAdjust(hueShift = 0.1f))
        val adj = HslEngine.applyHsl(0f, 1f, 0.5f, bands)
        assertEquals(0.1f * w[0], adj[0], 1e-3f)
    }

    @Test fun qualifierMaskCenterOne_farZero_invertFlips() {
        val m = HslEngine.qualifierMask(0.3f, 0.5f, 0.5f, 0.3f, 0.1f, 0.05f, 0.5f, 0.4f, 0.2f, 0.5f, 0.4f, 0.2f, invert = false)
        assertEquals(1f, m, 1e-5f)
        val far = HslEngine.qualifierMask(0.3f, 0.99f, 0.5f, 0.3f, 0.1f, 0.05f, 0.2f, 0.4f, 0.2f, 0.5f, 0.4f, 0.2f, invert = false)
        assertEquals(0f, far, 1e-5f)
        val inv = HslEngine.qualifierMask(0.3f, 0.5f, 0.5f, 0.3f, 0.1f, 0.05f, 0.5f, 0.4f, 0.2f, 0.5f, 0.4f, 0.2f, invert = true)
        assertEquals(0f, inv, 1e-5f)
    }

    @Test fun liftWheelAffectsShadowsMoreThanHighlights() {
        val dark  = run(ColorState(wheels = com.ahstudio.color.core.WheelState(
            lift = com.ahstudio.color.core.WheelVec3(0.5f, 0f, 0f, 1f))), 0.1f, 0.1f, 0.1f)
        val bright= run(ColorState(wheels = com.ahstudio.color.core.WheelState(
            lift = com.ahstudio.color.core.WheelVec3(0.5f, 0f, 0f, 1f))), 0.9f, 0.9f, 0.9f)
        assertTrue(dark[0] > 0.12f)            // red raised in shadows
        assertEquals(0.1f, dark[1], 2e-3f)     // only red channel moved
        assertEquals(0.9f, bright[0], 2e-3f)   // untouched in highlights
    }
}
