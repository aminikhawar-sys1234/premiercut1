package com.ahstudio.color

import com.ahstudio.color.core.*
import com.ahstudio.color.space.*
import org.junit.Assert.*
import org.junit.Test

class ColorPipelineParityTest {
    private fun roundTripCfg() = ColorConfig(
        workingSpace = ColorSpaceRegistry.REC709, outputSpace = ColorSpaceRegistry.REC709,
        workingTransfer = TransferFunctions.REC709, outputTransfer = TransferFunctions.REC709,
        inputMetadata = InputColorMetadata(
            ColorSpaceRegistry.REC709, TransferFunctions.REC709, ColorRange.FULL, true))

    @Test fun identityStateRoundTrips() {
        val cfg = roundTripCfg()
        for (v in listOf(0.05f, 0.2f, 0.5f, 0.9f)) {
            val px = floatArrayOf(v, v, v, 1f)
            ColorPipelineCpu(cfg).process(ColorState(), px)
            assertEquals(v, px[0], 2e-3f); assertEquals(v, px[1], 2e-3f); assertEquals(v, px[2], 2e-3f)
        }
    }

    @Test fun gradeChangesPixels_nonIdentitySanity() {
        val cfg = roundTripCfg()
        val px = floatArrayOf(0.5f, 0.5f, 0.5f, 1f)
        ColorPipelineCpu(cfg).process(ColorState(exposure = 2f, saturation = 0.3f), px)
        assertTrue(px[0] > 0.5f)
    }
}
