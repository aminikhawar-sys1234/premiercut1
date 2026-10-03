package com.ahstudio.composition.core

import org.junit.Assert.*
import org.junit.Test

class TimeMapperTest {
    @Test fun `identity preserves time`() {
        assertEquals(500L, TimeMapper.Identity.parentToChild(500L))
    }

    @Test fun `reverse maps between in and out`() {
        val rev = TimeMapper.Reverse(inUs = 100L, outUs = 500L)
        assertEquals(500L, rev.parentToChild(100L))
        assertEquals(100L, rev.parentToChild(500L))
        assertEquals(300L, rev.parentToChild(300L))
    }

    @Test fun `speed scales time delta`() {
        val spd = TimeMapper.Speed(inUs = 100L, factor = 2.0f)
        assertEquals(100L, spd.parentToChild(100L))
        assertEquals(300L, spd.parentToChild(200L))
    }

    @Test fun `segmented handles piecewise mapping`() {
        val seg = TimeMapper.Segmented(listOf(
            TimeMapper.Segmented.Segment(0L, 0L, 1.0f),
            TimeMapper.Segmented.Segment(1000L, 1000L, 2.0f)
        ))
        assertEquals(500L, seg.parentToChild(500L))
        assertEquals(1200L, seg.parentToChild(1100L))
    }
}
