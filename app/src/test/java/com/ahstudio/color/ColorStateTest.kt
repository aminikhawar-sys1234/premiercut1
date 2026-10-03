package com.ahstudio.color

import com.ahstudio.color.core.*
import com.ahstudio.color.preset.ColorPresets
import com.ahstudio.color.serialize.ColorStateCodec
import org.junit.Assert.*
import org.junit.Test

class ColorStateTest {
    @Test fun serializationRoundTripExact() {
        val s = ColorState(
            exposure = 0.7f, contrast = -0.2f, temperature = 0.4f, tint = -0.1f,
            curves = CurveSet(master = listOf(CurvePoint(0f, 0.05f), CurvePoint(0.5f, 0.55f), CurvePoint(1f, 0.95f))),
            hsl = HslBands(oranges = HslAdjust(hueShift = 0.02f, sat = 0.2f, lum = 0.1f),
                           reds = HslAdjust(sat = -0.1f)),
            wheels = WheelState(lift = WheelVec3(0.2f, -0.1f, 0.05f, 0.5f)),
            lut = LutState("abc123", 0.8f, "creative", "before_grade"),
            secondaries = listOf(SecondaryCorrection(hueCenter = 0.08f, gainR = 1.2f)),
            hdr = HdrState(ToneMapMode.ACES, 0.3f, true))
        assertEquals(s, ColorStateCodec.decode(ColorStateCodec.encode(s)))
    }

    @Test fun decodeTolerantToUnknownKeys() {
        val raw = """{"version":2,"exposure":0.5,"someFutureField":42}"""
        assertEquals(0.5f, ColorStateCodec.decode(raw).exposure, 1e-6f)
    }

    @Test fun fingerprintChangesWithValue() {
        val a = ColorState()
        assertNotEquals(a.fingerprint(), a.copy(exposure = 0.1f).fingerprint())
        assertEquals(a.fingerprint(), a.copy().fingerprint())
    }

    @Test fun curvePointDomainEnforced() {
        assertThrows(IllegalArgumentException::class.java) { CurvePoint(2f, 0.5f) }
        assertThrows(IllegalArgumentException::class.java) { CurvePoint(0.5f, Float.NaN) }
    }

    @Test fun presetsAreDistinctParameterSets() {
        val all = ColorPresets.builtIn().values.map { it() }
        assertEquals(all.size, all.map { it.fingerprint() }.distinct().size)
        assertTrue(ColorPresets.blackAndWhite().bw.enabled)
    }
}
