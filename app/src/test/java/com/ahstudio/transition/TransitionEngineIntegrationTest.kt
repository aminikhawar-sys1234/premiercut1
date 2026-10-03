package com.ahstudio.transition

import com.ahstudio.transition.core.Easing
import com.ahstudio.transition.core.ParamValue
import com.ahstudio.transition.core.TransitionInstance
import com.ahstudio.transition.integration.AddTransitionCommand
import com.ahstudio.transition.integration.ChangeTransitionDurationCommand
import com.ahstudio.transition.integration.ChangeTransitionParameterCommand
import com.ahstudio.transition.integration.InMemoryTransitionStore
import com.ahstudio.transition.integration.RemoveTransitionCommand
import com.ahstudio.transition.integration.TransitionAppBridge
import com.ahstudio.transition.render.SnapshotResult
import com.ahstudio.transition.transitions.BuiltinTransitions
import com.example.domain.model.Transition
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionEngineIntegrationTest {

    @Test
    fun `registry contains all built-in transition types`() {
        val defs = TransitionAppBridge.registry.all()
        assertTrue("Expected at least 10 transitions, found ${defs.size}", defs.size >= 10)
        assertNotNull(TransitionAppBridge.registry.definition(BuiltinTransitions.CROSS_DISSOLVE_ID))
        assertNotNull(TransitionAppBridge.registry.definition(BuiltinTransitions.ZOOM_ID))
        assertNotNull(TransitionAppBridge.registry.definition(BuiltinTransitions.SLIDE_LEFT_ID))
        assertNotNull(TransitionAppBridge.registry.definition(BuiltinTransitions.WIPE_ID))
    }

    @Test
    fun `domain model conversion to transition instance creates valid timing`() {
        val clips = listOf(
            VideoClip(id = "c1", name = "Clip 1", uri = "file:///c1.mp4", timelineStartMs = 0L, durationMs = 2000L),
            VideoClip(id = "c2", name = "Clip 2", uri = "file:///c2.mp4", timelineStartMs = 2000L, durationMs = 3000L)
        )
        val tr = Transition(clipIndexBefore = 0, type = TransitionType.ZOOM_IN, durationMs = 600L)
        val instance = TransitionAppBridge.toTransitionInstance(tr, clips)

        assertNotNull(instance)
        assertEquals("c1", instance?.outgoingClipId)
        assertEquals("c2", instance?.incomingClipId)
        assertEquals(1700L, instance?.startMs)
        assertEquals(2300L, instance?.endMs)
    }

    @Test
    fun `snapshot creation during transition window evaluates easing correctly`() {
        val engine = TransitionAppBridge.createEngine()
        val instance = TransitionInstance(
            instanceId = "t1",
            definitionId = BuiltinTransitions.CROSS_DISSOLVE_ID,
            outgoingClipId = "c1",
            incomingClipId = "c2",
            startMs = 1000L,
            endMs = 2000L,
            easing = Easing.of(Easing.Type.LINEAR)
        )

        val beforeResult = engine.snapshot(instance.definitionId, instance, 500L, 1920, 1080, 1)
        assertTrue(beforeResult is SnapshotResult.Inactive)

        val midResult = engine.snapshot(instance.definitionId, instance, 1500L, 1920, 1080, 1)
        assertTrue(midResult is SnapshotResult.Ready)
        val snapshot = (midResult as SnapshotResult.Ready).snapshot
        assertEquals(0.5f, snapshot.progress, 0.001f)
        assertEquals(0.5f, snapshot.rawProgress, 0.001f)

        val afterResult = engine.snapshot(instance.definitionId, instance, 2000L, 1920, 1080, 1)
        assertTrue(afterResult is SnapshotResult.Inactive)
    }

    @Test
    fun `undo redo commands mutate store deterministically`() {
        val store = InMemoryTransitionStore()
        val instance = TransitionInstance(
            instanceId = "t_test",
            definitionId = BuiltinTransitions.CROSS_DISSOLVE_ID,
            outgoingClipId = "c1",
            incomingClipId = "c2",
            startMs = 1000L,
            endMs = 2000L
        )

        // 1. Add
        val addCmd = AddTransitionCommand(store, instance)
        addCmd.execute()
        assertEquals(instance, store.get("t_test"))

        // 2. Change Duration
        val changeDurCmd = ChangeTransitionDurationCommand(store, "t_test", 800L, 2200L)
        changeDurCmd.execute()
        assertEquals(800L, store.get("t_test")?.startMs)
        assertEquals(2200L, store.get("t_test")?.endMs)

        // 3. Undo Duration change
        changeDurCmd.undo()
        assertEquals(1000L, store.get("t_test")?.startMs)
        assertEquals(2000L, store.get("t_test")?.endMs)

        // 4. Change Parameter
        val changeParamCmd = ChangeTransitionParameterCommand(
            store, "t_test", "softness", ParamValue.NormalizedValue(0.35f)
        )
        changeParamCmd.execute()
        assertEquals(ParamValue.NormalizedValue(0.35f), store.get("t_test")?.parameters?.get("softness"))

        // 5. Undo Add
        addCmd.undo()
        assertNull(store.get("t_test"))
    }
}
