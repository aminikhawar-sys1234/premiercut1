package com.example.engine.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase8StabilityGuardsTest {
    @Test fun invalidatedPlaybackWorkIsRejected() {
        val guard = Phase8PlaybackGuard()
        val first = guard.invalidate()
        val second = guard.invalidate()
        assertFalse(guard.isCurrent(first))
        assertTrue(guard.isCurrent(second))
        guard.release()
        assertFalse(guard.isCurrent(second))
    }

    @Test fun timelineMathIsDeterministic() {
        assertEquals(1000L, Phase8TimelineMath.sourceToTimeline(2000L, 1000L, 500L, 2f))
        assertEquals(0L, Phase8TimelineMath.clamp(-1L, 1000L))
        assertEquals(1000L, Phase8TimelineMath.clamp(2000L, 1000L))
    }
}
