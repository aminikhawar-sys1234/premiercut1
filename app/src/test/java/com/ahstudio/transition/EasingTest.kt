package com.ahstudio.transition

import com.ahstudio.transition.core.Easing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EasingTest {
    private val types = Easing.Type.values().filter { it != Easing.Type.CUBIC_BEZIER }

    @Test fun `endpoints exact for all types`() = types.forEach { t ->
        assertEquals("f(0) of $t", 0f, Easing.of(t).evaluate(0f), 1e-6f)
        assertEquals("f(1) of $t", 1f, Easing.of(t).evaluate(1f), 1e-6f)
    }

    @Test fun `input clamped outside 0-1`() {
        assertEquals(0f, Easing.of(Easing.Type.CUBIC_IN).evaluate(-0.7f), 1e-6f)
        assertEquals(1f, Easing.of(Easing.Type.CUBIC_IN).evaluate(1.7f), 1e-6f)
    }

    @Test fun `non overshoot types are monotonic`() = listOf(
        Easing.Type.LINEAR, Easing.Type.EASE_IN, Easing.Type.EASE_OUT, Easing.Type.EASE_IN_OUT,
        Easing.Type.CUBIC_IN_OUT, Easing.Type.EXPO_IN_OUT, Easing.Type.SINE_IN_OUT).forEach { t ->
        val e = Easing.of(t); var prev = -1f
        for (i in 0..100) {
            val v = e.evaluate(i / 100f)
            assertTrue("$t at $i", v >= prev - 1e-6f)
            prev = v
        }
    }

    @Test fun `overshoot types exceed 1 but return to 1`() {
        val back = Easing.of(Easing.Type.BACK_OUT)
        assertTrue((0..100).maxOf { back.evaluate(it / 100f) } > 1f)
        assertEquals(1f, back.evaluate(1f), 1e-6f)
    }

    @Test fun `symmetric bezier hits midpoint exactly`() {
        val e = Easing.bezier(0.42f, 0f, 0.58f, 1f)
        assertEquals(0.5f, e.evaluate(0.5f), 1e-3f)
        assertEquals(0f, e.evaluate(0f), 1e-6f)
        assertEquals(1f, e.evaluate(1f), 1e-6f)
    }

    @Test fun `bezier linear controls equal linear`() {
        val e = Easing.bezier(0f, 0f, 1f, 1f)
        for (i in 0..10) assertEquals(i / 10f, e.evaluate(i / 10f), 1e-3f)
    }

    @Test fun `determinism repeated evaluation`() {
        val e = Easing.of(Easing.Type.ELASTIC_OUT)
        val a = (0..50).map { e.evaluate(it / 50f) }
        val b = (0..50).map { e.evaluate(it / 50f) }
        assertEquals(a, b)
    }

    @Test fun `bezier rejects x controls outside 0-1`() {
        try { Easing.bezier(1.5f, 0f, 0.5f, 1f); fail("Expected IllegalArgumentException") }
        catch (_: IllegalArgumentException) { /* expected */ }
    }
}
