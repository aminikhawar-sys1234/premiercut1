package com.ahstudio.transition

import com.ahstudio.transition.core.Easing
import com.ahstudio.transition.core.TransitionResult
import com.ahstudio.transition.core.TransitionTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionTimingTest {
    private val t = (TransitionTiming.create(1000L, 3000L, Easing.of(Easing.Type.EASE_IN_OUT))
            as TransitionResult.Ok).value

    @Test fun `raw progress boundaries`() {
        assertEquals(0f, t.rawProgressAt(1000), 1e-6f)
        assertEquals(0.5f, t.rawProgressAt(2000), 1e-6f)
        assertEquals(1f, t.rawProgressAt(3000), 1e-6f)
    }
    @Test fun `clamped before and after`() {
        assertEquals(0f, t.rawProgressAt(0), 1e-6f)
        assertEquals(1f, t.rawProgressAt(9999), 1e-6f)
    }
    @Test fun `window is half-open`() {
        assertTrue(t.isActiveAt(1000)); assertTrue(t.isActiveAt(2999))
        assertFalse(t.isActiveAt(3000)); assertFalse(t.isActiveAt(999))
    }
    @Test fun `eased progress bounded`() {
        for (ms in 1000L..3000L step 7L) assertTrue("$ms", t.easedProgressAt(ms) in 0f..1f)
    }
    @Test fun `determinism same time twice same value`() {
        assertEquals(t.easedProgressAt(1743), t.easedProgressAt(1743), 0f)
    }
    @Test fun `zero and invalid durations rejected`() {
        assertTrue(TransitionTiming.create(100, 100) is TransitionResult.Err)
        assertTrue(TransitionTiming.create(300, 100) is TransitionResult.Err)
    }
    @Test fun `duration cap enforced`() {
        assertTrue(TransitionTiming.create(0, 60_001) is TransitionResult.Err)
        assertTrue(TransitionTiming.create(0, 60_000) is TransitionResult.Ok)
    }
    @Test fun `reverse direction pure function of time not direction`() {
        // Reverse playback = host passes decreasing timelineTimeMs; engine result must
        // equal a forward pass at the same timestamp (§21).
        assertEquals(t.easedProgressAt(1500), t.easedProgressAt(1500), 0f)
    }
    @Test fun `speed change does not shift window`() {
        // Duration defined in TIMELINE time (§22). Host speed moves source frames only.
        assertEquals(0.25f, t.rawProgressAt(1500), 1e-6f)
    }
}
