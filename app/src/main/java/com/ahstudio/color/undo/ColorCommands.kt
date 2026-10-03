package com.ahstudio.color.undo

import com.ahstudio.color.core.ColorState

interface ColorStateStore {
    fun get(): ColorState
    fun set(s: ColorState)   // MUST apply quietly (no new undo entries)
}

interface ColorCommand {
    val label: String
    val dragSessionId: Long
    fun undo()
    fun redo()
    /** Merge follower into this (same slider drag). Returns true if merged. */
    fun coalesce(follower: ColorCommand): Boolean
}

/** One command per completed drag gesture — dragging never floods the stack (§53). */
class SetPropertyCommand(
    val path: String,
    override val dragSessionId: Long,
    private val before: ColorState,
    var after: ColorState,
    private val store: ColorStateStore,
    private var lastTouchAt: Long = System.currentTimeMillis()
) : ColorCommand {
    override val label = "Set $path"
    override fun undo() = store.set(before)
    override fun redo() = store.set(after)
    override fun coalesce(follower: ColorCommand): Boolean {
        val o = follower as? SetPropertyCommand ?: return false
        val withinWindow = System.currentTimeMillis() - lastTouchAt < COALESCE_WINDOW_MS
        if (o.path != path || o.dragSessionId != dragSessionId || !withinWindow) return false
        after = o.after; lastTouchAt = o.lastTouchAt
        return true
    }
    companion object { const val COALESCE_WINDOW_MS = 1500L }
}

interface ColorCommandSink { fun execute(command: ColorCommand) }

class DefaultCommandSink(private val limit: Int = 200) : ColorCommandSink {
    private val undoStack = ArrayDeque<ColorCommand>()
    private val redoStack = ArrayDeque<ColorCommand>()
    override fun execute(command: ColorCommand) {
        val last = undoStack.lastOrNull()
        if (last == null || !last.coalesce(command)) {
            redoStack.clear()
            undoStack.addLast(command)
            if (undoStack.size > limit) undoStack.removeFirst()
        }
        command.redo()
    }
    fun undo(): Boolean { val c = undoStack.removeLastOrNull() ?: return false; c.undo(); redoStack.addLast(c); return true }
    fun redo(): Boolean { val c = redoStack.removeLastOrNull() ?: return false; c.redo(); undoStack.addLast(c); return true }
    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    fun clear() { undoStack.clear(); redoStack.clear() }
}

/**
 * For hosts with their OWN command manager: commands are batched per drag session
 * and released once, on session end — one host entry per gesture.
 */
class CoalescingSink(private val downstream: ColorCommandSink) : ColorCommandSink {
    private val open = LinkedHashMap<Long, ColorCommand>()
    override fun execute(command: ColorCommand) {
        val existing = open[command.dragSessionId]
        when {
            existing == null -> open[command.dragSessionId] = command
            existing.coalesce(command) -> Unit
            else -> { downstream.execute(existing); open[command.dragSessionId] = command }
        }
    }
    fun endSession(sessionId: Long) { open.remove(sessionId)?.let(downstream::execute) }
    fun endAll() { open.keys.toList().forEach(::endSession) }
}

/** Adapter onto existing AH Studio undo manager (pass your CommandManager's push). */
class HostCommandSink(
    private val push: (label: String, undo: () -> Unit, redo: () -> Unit) -> Unit
) : ColorCommandSink {
    override fun execute(command: ColorCommand) = push(command.label, command::undo, command::redo)
}
