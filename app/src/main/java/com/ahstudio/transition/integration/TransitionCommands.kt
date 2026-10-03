package com.ahstudio.transition.integration

import com.ahstudio.transition.core.ParamValue
import com.ahstudio.transition.core.TransitionAlignment
import com.ahstudio.transition.core.TransitionInstance

/** Minimal command contract — the HOST's existing undo manager executes these (§30). */
interface UndoableCommand { val name: String; fun execute(); fun undo() }

interface TransitionStore {
    fun upsert(instance: TransitionInstance)
    fun remove(instanceId: String)
    fun get(instanceId: String): TransitionInstance?
}

class InMemoryTransitionStore : TransitionStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, TransitionInstance>()
    override fun upsert(instance: TransitionInstance) { map[instance.instanceId] = instance }
    override fun remove(instanceId: String) { map.remove(instanceId) }
    override fun get(instanceId: String) = map[instanceId]
}

/** Base for commands that mutate an existing instance (loud failure if missing). */
abstract class StoreCommand(
    protected val store: TransitionStore,
    protected val instanceId: String,
) : UndoableCommand {
    private var before: TransitionInstance? = null
    protected abstract fun apply(current: TransitionInstance): TransitionInstance
    final override fun execute() {
        val current = store.get(instanceId) ?: error("Transition '$instanceId' not found")
        before = current
        store.upsert(apply(current))
    }
    final override fun undo() { before?.let { store.upsert(it) } }
}

class AddTransitionCommand(
    private val store: TransitionStore,
    private val instance: TransitionInstance,
) : UndoableCommand {
    override val name = "AddTransition"
    private var added = false
    override fun execute() { added = true; store.upsert(instance) }
    override fun undo() { if (added) store.remove(instance.instanceId) }
}

class RemoveTransitionCommand(
    private val store: TransitionStore,
    private val instanceId: String,
) : UndoableCommand {
    override val name = "RemoveTransition"
    private var before: TransitionInstance? = null
    override fun execute() {
        before = store.get(instanceId) ?: error("Transition '$instanceId' not found")
        store.remove(instanceId)
    }
    override fun undo() { before?.let { store.upsert(it) } }
}

class ChangeTransitionDurationCommand(
    store: TransitionStore, instanceId: String,
    private val newStartMs: Long, private val newEndMs: Long,
) : StoreCommand(store, instanceId) {
    override val name = "ChangeTransitionDuration"
    override fun apply(current: TransitionInstance) =
        current.copy(startMs = newStartMs, endMs = newEndMs)
}

class ChangeTransitionAlignmentCommand(
    store: TransitionStore, instanceId: String,
    private val alignment: TransitionAlignment, private val customAnchor: Float = 0.5f,
) : StoreCommand(store, instanceId) {
    override val name = "ChangeTransitionAlignment"
    override fun apply(current: TransitionInstance) =
        current.copy(alignment = alignment, customAnchor = customAnchor)
}

class ChangeTransitionParameterCommand(
    store: TransitionStore, instanceId: String,
    private val paramId: String, private val value: ParamValue,
) : StoreCommand(store, instanceId) {
    override val name = "ChangeTransitionParameter"
    override fun apply(current: TransitionInstance) =
        current.copy(parameters = current.parameters + (paramId to value))
}

class SetTransitionEnabledCommand(
    store: TransitionStore, instanceId: String, private val enabled: Boolean,
) : StoreCommand(store, instanceId) {
    override val name = "SetTransitionEnabled"
    override fun apply(current: TransitionInstance) = current.copy(enabled = enabled)
}

class ReplaceTransitionCommand(
    private val store: TransitionStore,
    private val instanceId: String,
    private val replacement: TransitionInstance,
) : UndoableCommand {
    override val name = "ReplaceTransition"
    private var before: TransitionInstance? = null
    override fun execute() {
        before = store.get(instanceId) ?: error("Transition '$instanceId' not found")
        store.upsert(replacement.copy(instanceId = instanceId))
    }
    override fun undo() { before?.let { store.upsert(it) } }
}
