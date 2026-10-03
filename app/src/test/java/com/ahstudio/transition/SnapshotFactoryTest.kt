package com.ahstudio.transition

import com.ahstudio.transition.core.ParamValue
import com.ahstudio.transition.core.TransitionInstance
import com.ahstudio.transition.render.SnapshotResult
import com.ahstudio.transition.render.TransitionSnapshotFactory
import com.ahstudio.transition.transitions.BuiltinTransitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotFactoryTest {
    private val def = BuiltinTransitions.zoom()

    private fun instance(start: Long = 1000, end: Long = 1800) = TransitionInstance(
        "i1", def.id, "clipA", "clipB", start, end,
        parameters = mapOf("zoomAmount" to ParamValue.FloatValue(9f)))  // clamps to 3

    @Test fun `ready inside window with clamped parameters`() {
        val s = (TransitionSnapshotFactory.create(def, instance(), 1400, 1080, 1920, 1)
                as SnapshotResult.Ready).snapshot
        assertEquals(0.5f, s.rawProgress, 1e-6f)
        assertEquals(3f, (s.parameters["zoomAmount"]!!.value as ParamValue.FloatValue).value, 1e-6f)
        assertTrue(s.warnings.isEmpty())   // clamping is silent per contract
    }
    @Test fun `inactive before start at end and after`() {
        assertTrue(TransitionSnapshotFactory.create(def, instance(), 999, 1080, 1920, 1) is SnapshotResult.Inactive)
        assertTrue(TransitionSnapshotFactory.create(def, instance(), 1800, 1080, 1920, 1) is SnapshotResult.Inactive)
        assertTrue(TransitionSnapshotFactory.create(def, instance(), 5000, 1080, 1920, 1) is SnapshotResult.Inactive)
    }
    @Test fun `disabled instance is inactive`() {
        val r = TransitionSnapshotFactory.create(def, instance().copy(enabled = false), 1400, 1080, 1920, 1)
        assertTrue(r is SnapshotResult.Inactive)
    }
    @Test fun `invalid output size fails`() {
        assertTrue(TransitionSnapshotFactory.create(def, instance(), 1400, 0, 1920, 1) is SnapshotResult.Failed)
    }
    @Test fun `zero duration fails`() {
        assertTrue(TransitionSnapshotFactory.create(def, instance(1000, 1000), 1000, 1080, 1920, 1)
                is SnapshotResult.Failed)
    }
    @Test fun `determinism equal snapshots for equal inputs`() {
        val a = TransitionSnapshotFactory.create(def, instance(), 1400, 1080, 1920, 1)
        val b = TransitionSnapshotFactory.create(def, instance(), 1400, 1080, 1920, 1)
        assertEquals((a as SnapshotResult.Ready).snapshot, (b as SnapshotResult.Ready).snapshot)
    }
    @Test fun `seek determinism across sample points`() {
        listOf(1000L, 1250L, 1400L, 1700L, 1799L).forEach { t ->
            val a = (TransitionSnapshotFactory.create(def, instance(), t, 1080, 1920, 1)
                    as SnapshotResult.Ready).snapshot
            val b = (TransitionSnapshotFactory.create(def, instance(), t, 1080, 1920, -1)
                    as SnapshotResult.Ready).snapshot
            assertEquals(a.rawProgress, b.rawProgress, 0f)
            assertEquals(a.progress, b.progress, 0f)
        }
    }
    @Test fun `unknown parameter override produces warning not failure`() {
        val r = TransitionSnapshotFactory.create(def, instance().copy(
            parameters = mapOf("bogus" to ParamValue.FloatValue(1f))), 1400, 1080, 1920, 1)
        val s = (r as SnapshotResult.Ready).snapshot
        assertTrue(s.warnings.any { it.contains("bogus") })
    }
}
