package com.example.engine.integration

import com.example.domain.model.Timeline
import java.util.ArrayDeque

/**
 * Non-destructive command history boundary. Commands store immutable Timeline
 * snapshots; source media is never copied or mutated.
 *
 * This facade is intentionally independent of Ah Studio's existing UI history
 * manager so it can be used by integrations that already operate on Timeline values.
 */
class TimelineCommandSystem(private val maxHistorySize: Int = 100) {
  init { require(maxHistorySize > 0) }

  private data class Entry(
    val before: Timeline,
    val after: Timeline,
    val name: String
  )

  private val undoStack = ArrayDeque<Entry>()
  private val redoStack = ArrayDeque<Entry>()

  val canUndo: Boolean get() = undoStack.isNotEmpty()
  val canRedo: Boolean get() = redoStack.isNotEmpty()
  val undoCount: Int get() = undoStack.size
  val redoCount: Int get() = redoStack.size

  fun execute(name: String, before: Timeline, after: Timeline): Timeline {
    if (before == after) return after
    undoStack.addLast(Entry(before, after, name))
    while (undoStack.size > maxHistorySize) undoStack.removeFirst()
    redoStack.clear()
    return after
  }

  fun undo(): Timeline? =
    if (undoStack.isEmpty()) null
    else undoStack.removeLast().also { redoStack.addLast(it) }.before

  fun redo(): Timeline? =
    if (redoStack.isEmpty()) null
    else redoStack.removeLast().also { undoStack.addLast(it) }.after

  fun clear() {
    undoStack.clear()
    redoStack.clear()
  }
}
