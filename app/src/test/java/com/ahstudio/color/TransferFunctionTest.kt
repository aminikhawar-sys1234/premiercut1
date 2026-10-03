package com.ahstudio.color

import com.ahstudio.color.space.TransferFunctions
import org.junit.Assert.*
import org.junit.Test

class TransferFunctionTest {
    @Test fun srgbDecodeGolden() {
        assertEquals(0.2140f, TransferFunctions.SRGB.decode(0.5f), 1e-3f)
    }

    @Test fun srgbRoundTrip() {
        var x = 0f
        repeat(101) {
            assertEquals(x, TransferFunctions.SRGB.encode(TransferFunctions.SRGB.decode(x)), 1e-5f)
            assertEquals(x, TransferFunctions.SRGB.decode(TransferFunctions.SRGB.encode(x)), 1e-5f)
            x += 0.01f
        }
    }

    @Test fun rec709RoundTrip() {
        var x = 0.001f
        repeat(100) {
            assertEquals(x, TransferFunctions.REC709.encode(TransferFunctions.REC709.decode(x)), 1e-4f)
            x += 0.01f
        }
    }

    @Test fun pqGolden100Nits() {
        // PQ 0.50808 ≙ 100 nits -> normalized linear Y = 100/10000 = 0.01
        assertEquals(0.01f, TransferFunctions.PQ.decode(0.50808f), 5e-4f)
    }

    @Test fun pqRoundTrip() {
        var x = 0.0005f
        while (x <= 1.0f) {
            assertEquals(x, TransferFunctions.PQ.encode(TransferFunctions.PQ.decode(x)), 1e-4f)
            x *= 1.25f
        }
    }

    @Test fun hlgGolden75Percent() {
        assertEquals(0.265f, TransferFunctions.HLG.decode(0.75f), 2e-3f)
    }

    @Test fun hlgRoundTrip() {
        var x = 0.0001f
        while (x <= 1.0f) {
            assertEquals(x, TransferFunctions.HLG.encode(TransferFunctions.HLG.decode(x)), 1e-4f)
            x *= 1.3f
        }
    }

    @Test fun byIdRestoresFunctions() {
        // IDs are the stable wire format used by shaders/serialization
        listOf(0, 1, 2, 3, 4, 10, 11).forEach { id ->
            val f = TransferFunctions.byId(id)
            assertEquals(id, f.id)
        }
    }
}
