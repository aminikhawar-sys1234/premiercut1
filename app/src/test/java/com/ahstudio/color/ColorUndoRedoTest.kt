package com.ahstudio.color

import com.ahstudio.color.core.ColorState
import com.ahstudio.color.undo.*
import org.junit.Assert.*
import org.junit.Test

class ColorUndoRedoTest {
    private class Store : ColorStateStore {
        var cur = ColorState()
        override fun get() = cur
        override fun set(s: ColorState) { cur = s }
    }

    @Test fun sliderDragCoalescesToSingleUndoEntry() {
        val store = Store()
        val sink = DefaultCommandSink()
        var before = store.cur
        repeat(50) { i ->
            val after = before.copy(exposure = (i + 1) * 0.02f)
            sink.execute(SetPropertyCommand("exposure", 100L, before, after, store))
            before = after
        }
        assertEquals(0.9999f, store.cur.exposure, 0.01f)
        assertTrue(sink.undo())                       // ONE entry, not 50
        assertEquals(0f, store.cur.exposure, 1e-6f)   // restored to pre-drag
        assertTrue(sink.redo())
        assertEquals(1.0f, store.cur.exposure, 0.01f)
        assertFalse(sink.canRedo)
    }

    @Test fun differentSessionsCreateSeparateEntries() {
        val store = Store(); val sink = DefaultCommandSink()
        sink.execute(SetPropertyCommand("exposure", 1L, store.cur, store.cur.copy(exposure = 0.5f), store))
        sink.execute(SetPropertyCommand("exposure", 2L, store.cur, store.cur.copy(exposure = 1.0f), store))
        assertTrue(sink.undo()); assertEquals(0.5f, store.cur.exposure, 1e-6f)
        assertTrue(sink.undo()); assertEquals(0f, store.cur.exposure, 1e-6f)
    }

    @Test fun coalescingSinkReleasesOnSessionEnd() {
        val released = ArrayList<ColorCommand>()
        val sink = CoalescingSink(object : ColorCommandSink {
            override fun execute(command: ColorCommand) { released.add(command) }
        })
        val store = Store()
        var before = store.cur
        repeat(20) { i ->
            val after = before.copy(contrast = i * 0.01f)
            sink.execute(SetPropertyCommand("contrast", 9L, before, after, store)); before = after
        }
        assertEquals(0, released.size)
        sink.endSession(9L)
        assertEquals(1, released.size)
    }
}
