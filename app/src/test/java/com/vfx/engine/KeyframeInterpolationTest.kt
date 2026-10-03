package com.vfx.engine

import com.vfx.engine.core.Microseconds
import com.vfx.engine.core.keyframe.EasingPreset
import com.vfx.engine.core.keyframe.Keyframe
import com.vfx.engine.core.keyframe.KeyframeTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeInterpolationTest {

    @Test
    fun `empty track returns null`() {
        val track = KeyframeTrack<Float>("opacity", emptyList()) { a, b, t -> a + (b - a) * t }
        assertNull(track.evaluateAt(Microseconds(500_000L)))
    }

    @Test
    fun `single keyframe returns constant value everywhere`() {
        val kf = Keyframe(Microseconds(1_000_000L), 10f)
        val track = KeyframeTrack("scale", listOf(kf)) { a, b, t -> a + (b - a) * t }

        assertEquals(10f, track.evaluateAt(Microseconds(0L)))
        assertEquals(10f, track.evaluateAt(Microseconds(1_000_000L)))
        assertEquals(10f, track.evaluateAt(Microseconds(2_000_000L)))
    }

    @Test
    fun `linear interpolation between two keyframes`() {
        val k0 = Keyframe(Microseconds(0L), 0f, EasingPreset.LINEAR)
        val k1 = Keyframe(Microseconds(1_000_000L), 100f, EasingPreset.LINEAR)
        val track = KeyframeTrack("progress", listOf(k0, k1)) { a, b, t -> a + (b - a) * t }

        assertEquals(0f, track.evaluateAt(Microseconds(0L))!!, 1e-4f)
        assertEquals(50f, track.evaluateAt(Microseconds(500_000L))!!, 1e-4f)
        assertEquals(25f, track.evaluateAt(Microseconds(250_000L))!!, 1e-4f)
        assertEquals(75f, track.evaluateAt(Microseconds(750_000L))!!, 1e-4f)
        assertEquals(100f, track.evaluateAt(Microseconds(1_000_000L))!!, 1e-4f)
    }

    @Test
    fun `clamps before first and after last keyframe`() {
        val k0 = Keyframe(Microseconds(1_000_000L), 20f)
        val k1 = Keyframe(Microseconds(3_000_000L), 80f)
        val track = KeyframeTrack("value", listOf(k0, k1)) { a, b, t -> a + (b - a) * t }

        assertEquals(20f, track.evaluateAt(Microseconds(500_000L))!!, 1e-4f)
        assertEquals(80f, track.evaluateAt(Microseconds(4_000_000L))!!, 1e-4f)
    }

    @Test
    fun `ease in and ease out curves behave monotonically`() {
        val kEaseIn = Keyframe(Microseconds(0L), 0f, EasingPreset.EASE_IN)
        val kEnd = Keyframe(Microseconds(1_000_000L), 100f)
        val trackIn = KeyframeTrack("easeIn", listOf(kEaseIn, kEnd)) { a, b, t -> a + (b - a) * t }

        val midValIn = trackIn.evaluateAt(Microseconds(500_000L))!!
        // Ease-in should be below linear (0.5 * 0.5 * 100 = 25)
        assertEquals(25f, midValIn, 1e-3f)

        val kEaseOut = Keyframe(Microseconds(0L), 0f, EasingPreset.EASE_OUT)
        val trackOut = KeyframeTrack("easeOut", listOf(kEaseOut, kEnd)) { a, b, t -> a + (b - a) * t }
        val midValOut = trackOut.evaluateAt(Microseconds(500_000L))!!
        // Ease-out should be above linear ( (1 - (1-0.5)^2) * 100 = 75)
        assertEquals(75f, midValOut, 1e-3f)
    }

    @Test
    fun `multiple keyframe multi-segment evaluation`() {
        val keys = listOf(
            Keyframe(Microseconds(0L), 0f),
            Keyframe(Microseconds(1_000_000L), 10f),
            Keyframe(Microseconds(2_000_000L), 50f),
            Keyframe(Microseconds(3_000_000L), 30f)
        )
        val track = KeyframeTrack("multi", keys) { a, b, t -> a + (b - a) * t }

        assertEquals(5f, track.evaluateAt(Microseconds(500_000L))!!, 1e-4f)
        assertEquals(30f, track.evaluateAt(Microseconds(1_500_000L))!!, 1e-4f)
        assertEquals(40f, track.evaluateAt(Microseconds(2_500_000L))!!, 1e-4f)
    }
}
