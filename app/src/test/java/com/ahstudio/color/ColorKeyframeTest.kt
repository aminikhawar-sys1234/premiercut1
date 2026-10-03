package com.ahstudio.color

import com.ahstudio.color.core.ColorState
import com.ahstudio.color.keyframe.KeyframeTrackReader
import com.ahstudio.color.keyframe.ColorKeyframeBridge
import org.junit.Assert.*
import org.junit.Test

class ColorKeyframeTest {
    @Test fun animatedPathsOverrideBase_othersFallBack() {
        val reader = object : KeyframeTrackReader {
            override fun floatValue(trackId: String, timeMs: Long): Float? =
                if (trackId == "color/c1/exposure" && timeMs >= 500L) 0.75f else null
        }
        val base = ColorState(exposure = 0f, temperature = 0.2f)
        val out = ColorKeyframeBridge(reader).evaluate(base, "c1", 1000L)
        assertEquals(0.75f, out.exposure, 1e-6f)
        assertEquals(0.2f, out.temperature, 1e-6f)          // no track -> base value
        assertEquals(0f, ColorKeyframeBridge(reader).evaluate(base, "c1", 100L).exposure, 1e-6f)
    }
}
