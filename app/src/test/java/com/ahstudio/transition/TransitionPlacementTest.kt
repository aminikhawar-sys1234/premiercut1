package com.ahstudio.transition

import com.ahstudio.transition.core.TransitionAlignment
import com.ahstudio.transition.core.TransitionPlacement
import com.ahstudio.transition.core.TransitionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionPlacementTest {
    private val clipA = 0L..2000L
    private val clipB = 2000L..5000L

    @Test fun `centered math`() {
        val (s, e) = (TransitionPlacement.resolve(TransitionAlignment.CENTERED, 800, 2000)
                as TransitionResult.Ok).value
        assertEquals(1600L, s); assertEquals(2400L, e)
    }
    @Test fun `a biased ends at cut`() {
        val (s, e) = (TransitionPlacement.resolve(TransitionAlignment.A_BIASED, 500, 2000)
                as TransitionResult.Ok).value
        assertEquals(1500L, s); assertEquals(2000L, e)
    }
    @Test fun `b biased starts at cut`() {
        val (s, e) = (TransitionPlacement.resolve(TransitionAlignment.B_BIASED, 500, 2000)
                as TransitionResult.Ok).value
        assertEquals(2000L, s); assertEquals(2500L, e)
    }
    @Test fun `custom anchor`() {
        val (s, _) = (TransitionPlacement.resolve(TransitionAlignment.CUSTOM, 1000, 2000, 0.25f)
                as TransitionResult.Ok).value
        assertEquals(1750L, s)
    }
    @Test fun `invalid anchor rejected`() {
        assertTrue(TransitionPlacement.resolve(TransitionAlignment.CUSTOM, 1000, 2000, 1.5f)
                is TransitionResult.Err)
    }
    @Test fun `valid straddling placement passes clip validation`() {
        // Abutting clips: transition correctly straddles the cut (head in A, tail in B).
        assertTrue(TransitionPlacement.validateAgainstClips(1600, 2400, clipA, clipB).isValid)
    }
    @Test fun `duration exceeding clip A length fails loudly`() {
        val v = TransitionPlacement.validateAgainstClips(0, 2600, clipA, clipB)
        assertFalse(v.isValid)
        assertTrue(v.errors.any { it.contains("Clip A") })
    }
    @Test fun `placement before clip A start fails`() {
        assertFalse(TransitionPlacement.validateAgainstClips(-100, 300, clipA, clipB).isValid)
    }
    @Test fun `placement after clip B end fails`() {
        assertFalse(TransitionPlacement.validateAgainstClips(4500, 5500, clipA, clipB).isValid)
    }
}
