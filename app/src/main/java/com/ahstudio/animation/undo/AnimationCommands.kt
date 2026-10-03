package com.ahstudio.animation.undo

import com.ahstudio.animation.core.AnimationEngine
import com.ahstudio.animation.core.AnimationTrack
import com.ahstudio.animation.keyframes.*
import com.ahstudio.animation.properties.BindingKey
import com.ahstudio.animation.easing.EasingType

interface UndoableCommand {
    val description: String
    fun undo()
    fun redo()
}

fun interface UndoSink { fun push(cmd: UndoableCommand) }

/** Host plugs its existing CommandManager in: engine.undoSink = commandManager::push */
internal class TrackStatesCommand(
    private val engine: AnimationEngine,
    private val before: Map<BindingKey, AnimationTrack.State?>,
    private val after: Map<BindingKey, AnimationTrack.State?>,
    override val description: String
) : UndoableCommand {
    override fun undo() = engine.applyStates(before)
    override fun redo() = engine.applyStates(after)
}

/**
 * Batched edit session: mutate freely (drag, multi-select ops) -- commit produces ONE command
 * capturing before/after immutable states. Cheap, correct, integrates with any undo stack.
 */
class KeyframeEditSession internal constructor(
    private val engine: AnimationEngine,
    private val keys: List<BindingKey>
) {
    private val before = LinkedHashMap<BindingKey, AnimationTrack.State?>()
    private val mutated = LinkedHashSet<BindingKey>()
    private var committed = false

    init { for (k in keys) before[k] = engine.trackFor(k)?.get() }

    private fun mutate(key: BindingKey, f: (AnimationTrack.State) -> AnimationTrack.State) {
        check(!committed) { "Session already committed" }
        val tr = engine.trackFor(key) ?: return
        tr.forceState(f(tr.get()).let { st -> st.copy(data = st.data) })
        mutated.add(key)
        engine.bumpVersion()
    }

    // ---- live mutation API (no undo pushes during drag) ----
    fun upsertKeyframe(key: BindingKey, kf: Keyframe): KeyframeEditSession = apply {
        mutate(key) { st -> st.copy(data = KeyframeOps.upsert(st.data, kf)) }
    }
    fun deleteKeyframes(key: BindingKey, ids: Set<KeyframeId>): KeyframeEditSession = apply {
        mutate(key) { st -> st.copy(data = KeyframeOps.delete(st.data, ids)) }
    }
    fun moveKeyframes(key: BindingKey, ids: Set<KeyframeId>, deltaMs: Long): KeyframeEditSession = apply {
        mutate(key) { st -> st.copy(data = KeyframeOps.move(st.data, ids, deltaMs)) }
    }
    fun setKeyframeTime(key: BindingKey, id: KeyframeId, newTimeMs: Long): KeyframeEditSession = apply {
        mutate(key) { st -> st.copy(data = KeyframeOps.setTime(st.data, id, newTimeMs)) }
    }
    fun duplicateKeyframes(key: BindingKey, ids: Set<KeyframeId>, offsetMs: Long): KeyframeEditSession = apply {
        mutate(key) { st -> st.copy(data = KeyframeOps.duplicate(st.data, ids, offsetMs) { engine.nextId() }) }
    }
    fun scaleTiming(key: BindingKey, ids: Set<KeyframeId>, pivotMs: Long, factor: Double): KeyframeEditSession = apply {
        mutate(key) { st -> st.copy(data = KeyframeOps.scaleTimes(st.data, ids, pivotMs, factor)) }
    }
    fun reverseTiming(key: BindingKey, startMs: Long, endMs: Long): KeyframeEditSession = apply {
        mutate(key) { st -> st.copy(data = KeyframeOps.reverseRange(st.data, startMs, endMs)) }
    }
    fun mirrorTiming(key: BindingKey, pivotMs: Long): KeyframeEditSession = apply {
        mutate(key) { st -> st.copy(data = KeyframeOps.mirror(st.data, pivotMs)) }
    }
    fun quantize(key: BindingKey, ids: Set<KeyframeId>, gridMs: Long): KeyframeEditSession = apply {
        mutate(key) { st -> st.copy(data = KeyframeOps.quantize(st.data, ids, gridMs)) }
    }
    fun distribute(key: BindingKey, ids: Set<KeyframeId>): KeyframeEditSession = apply {
        mutate(key) { st -> st.copy(data = KeyframeOps.distributeEvenly(st.data, ids)) }
    }
    fun setInterpolation(key: BindingKey, ids: Set<KeyframeId>, type: InterpolationType): KeyframeEditSession = apply {
        mutate(key) { st -> st.copy(data = KeyframeOps.setInterpolation(st.data, ids, type)) }
    }
    fun setEasing(key: BindingKey, ids: Set<KeyframeId>, easing: EasingType): KeyframeEditSession = apply {
        mutate(key) { st -> st.copy(data = KeyframeOps.setEasing(st.data, ids, easing)) }
    }
    fun setTrackOptions(key: BindingKey, transform: (AnimationTrack.State) -> AnimationTrack.State): KeyframeEditSession = apply {
        mutate(key, transform)
    }

    /** Commits as ONE undoable command. Returns null if nothing changed. */
    fun commit(description: String): UndoableCommand? {
        check(!committed) { "Session already committed" }
        committed = true
        val after = LinkedHashMap<BindingKey, AnimationTrack.State?>()
        for (k in keys) after[k] = engine.trackFor(k)?.get()
        val changed = keys.any { before[it] != after[it] }
        if (!changed) return null
        val cmd = TrackStatesCommand(engine, before, after, description)
        engine.undoSink?.push(cmd)
        return cmd
    }

    fun cancel() { committed = true }
}
