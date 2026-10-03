package com.vfx.engine

import com.vfx.engine.core.Microseconds
import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.effect.EffectInstance
import com.vfx.engine.core.stack.EffectStack
import com.vfx.engine.core.stack.StackEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackEvaluatorTest {

    private fun createDef(id: String, name: String = id): EffectDefinition {
        return EffectDefinition(
            id = id,
            name = name,
            category = EffectCategory.COLOR
        )
    }

    @Test
    fun `empty stack evaluates to empty snapshots`() {
        val stack = EffectStack()
        val snapshots = StackEvaluator.evaluateStackAtTime(stack, Microseconds(0L))
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun `disabled effect instances are excluded from evaluation`() {
        val stack = EffectStack()

        val e1 = EffectInstance(createDef("vfx.color.brightness"))
        e1.enabled = true
        val e2 = EffectInstance(createDef("vfx.blur.kawase"))
        e2.enabled = false

        stack.addInstance(e1)
        stack.addInstance(e2)

        val snapshots = StackEvaluator.evaluateStackAtTime(stack, Microseconds(100_000L))
        assertEquals(1, snapshots.size)
        assertEquals("vfx.color.brightness", snapshots[0].first.id)
    }

    @Test
    fun `stack evaluation preserves authoring order`() {
        val stack = EffectStack()
        val e1 = EffectInstance(createDef("vfx.color.contrast"))
        val e2 = EffectInstance(createDef("vfx.light.bloom"))
        val e3 = EffectInstance(createDef("vfx.light.vignette"))

        stack.addInstance(e1)
        stack.addInstance(e2)
        stack.addInstance(e3)

        val snapshots = StackEvaluator.evaluateStackAtTime(stack, Microseconds(500_000L))
        assertEquals(3, snapshots.size)
        assertEquals("vfx.color.contrast", snapshots[0].first.id)
        assertEquals("vfx.light.bloom", snapshots[1].first.id)
        assertEquals("vfx.light.vignette", snapshots[2].first.id)
    }
}

