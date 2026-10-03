package com.example.engine.history

import com.example.domain.model.Timeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class TimelineActionType(val displayName: String) {
  MOVE_CLIP("Move Clip"),
  TRIM_LEFT("Trim Start"),
  TRIM_RIGHT("Trim End"),
  TRIM_TO_PLAYHEAD("Trim to Playhead"),
  DELETE_CLIP("Delete Clip"),
  RIPPLE_DELETE("Ripple Delete"),
  SPLIT_CLIP("Split Clip"),
  ADD_CLIP("Add Clip"),
  DUPLICATE_CLIP("Duplicate Clip"),
  CHANGE_SPEED("Change Speed"),
  REPLACE_MEDIA("Replace Media"),
  APPLY_FILTER("Filter"),
  APPLY_ADJUSTMENTS("Adjustment"),
  ADD_TRANSITION("Add Transition"),
  REMOVE_TRANSITION("Remove Transition"),
  KEYFRAME_EDIT("Keyframe Edit"),
  AUDIO_VOLUME("Volume Change"),
  ALIGN_TO_PLAYHEAD("Align to Playhead"),
  CLIP_RENAME("Rename Clip"),
  MASK_EDIT("Mask & Shape"),
  BLEND_MODE_CHANGE("Blend Mode"),
  SPEED_CURVE_EDIT("Speed Curve"),
  AUDIO_EFFECTS_EDIT("Audio Effects & EQ"),
  TRACK_SETTINGS_EDIT("Track Settings"),
  GENERIC_EDIT("Timeline Edit")
}

data class TimelineAction(
  val id: String = UUID.randomUUID().toString(),
  val type: TimelineActionType,
  val description: String,
  val timestampMs: Long = System.currentTimeMillis(),
  val beforeState: Timeline,
  val afterState: Timeline,
  val affectedClipIds: Set<String> = emptySet()
)

class TimelineActionManager(
  private val maxHistorySize: Int = 50
) {
  private val undoStack = ArrayDeque<TimelineAction>()
  private val redoStack = ArrayDeque<TimelineAction>()

  private val _canUndo = MutableStateFlow(false)
  val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

  private val _canRedo = MutableStateFlow(false)
  val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

  private val _lastAction = MutableStateFlow<TimelineAction?>(null)
  val lastAction: StateFlow<TimelineAction?> = _lastAction.asStateFlow()

  private val _undoActionTitle = MutableStateFlow<String?>(null)
  val undoActionTitle: StateFlow<String?> = _undoActionTitle.asStateFlow()

  private val _redoActionTitle = MutableStateFlow<String?>(null)
  val redoActionTitle: StateFlow<String?> = _redoActionTitle.asStateFlow()

  private val _actionHistory = MutableStateFlow<List<TimelineAction>>(emptyList())
  val actionHistory: StateFlow<List<TimelineAction>> = _actionHistory.asStateFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  // Transaction support for continuous gestures (move drag, trim handle drag)
  private var activeTransaction: Transaction? = null

  private data class Transaction(
    val type: TimelineActionType,
    val description: String,
    val initialTimeline: Timeline,
    val clipId: String? = null
  )

  val isTransactionActive: Boolean
    get() = activeTransaction != null

  fun beginTransaction(
    type: TimelineActionType,
    description: String,
    currentTimeline: Timeline,
    clipId: String? = null
  ) {
    if (activeTransaction == null) {
      activeTransaction = Transaction(type, description, currentTimeline, clipId)
    }
  }

  fun commitTransaction(finalTimeline: Timeline): Boolean {
    val tx = activeTransaction ?: return false
    activeTransaction = null
    if (tx.initialTimeline != finalTimeline) {
      val action = TimelineAction(
        type = tx.type,
        description = tx.description,
        beforeState = tx.initialTimeline,
        afterState = finalTimeline,
        affectedClipIds = if (tx.clipId != null) setOf(tx.clipId) else emptySet()
      )
      pushAction(action)
      return true
    }
    return false
  }

  fun cancelTransaction(): Timeline? {
    val tx = activeTransaction ?: return null
    activeTransaction = null
    return tx.initialTimeline
  }

  fun recordPreEditHistory(
    type: TimelineActionType = TimelineActionType.GENERIC_EDIT,
    description: String = type.displayName,
    currentTimeline: Timeline,
    clipIds: Set<String> = emptySet()
  ) {
    if (activeTransaction != null) return
    val action = TimelineAction(
      type = type,
      description = description,
      beforeState = currentTimeline,
      afterState = currentTimeline,
      affectedClipIds = clipIds
    )
    undoStack.addLast(action)
    if (undoStack.size > maxHistorySize) {
      undoStack.removeFirst()
    }
    redoStack.clear()
    updateState()
  }

  fun recordAction(
    type: TimelineActionType,
    description: String,
    beforeState: Timeline,
    afterState: Timeline,
    clipIds: Set<String> = emptySet()
  ) {
    if (activeTransaction != null) return
    if (beforeState == afterState) return
    val action = TimelineAction(
      type = type,
      description = description,
      beforeState = beforeState,
      afterState = afterState,
      affectedClipIds = clipIds
    )
    pushAction(action)
  }

  private fun pushAction(action: TimelineAction) {
    undoStack.addLast(action)
    if (undoStack.size > maxHistorySize) {
      undoStack.removeFirst()
    }
    redoStack.clear()
    updateState()
  }

  fun undo(currentTimeline: Timeline): Timeline? {
    if (undoStack.isEmpty()) return null
    val action = undoStack.removeLast()
    val actionWithAfter = if (action.afterState == action.beforeState) {
      action.copy(afterState = currentTimeline)
    } else action
    redoStack.addLast(actionWithAfter)
    _statusMessage.value = "Undid: ${action.description}"
    updateState()
    return action.beforeState
  }

  fun redo(currentTimeline: Timeline): Timeline? {
    if (redoStack.isEmpty()) return null
    val action = redoStack.removeLast()
    val actionWithBefore = if (action.beforeState == action.afterState) {
      action.copy(beforeState = currentTimeline)
    } else action
    undoStack.addLast(actionWithBefore)
    _statusMessage.value = "Redid: ${action.description}"
    updateState()
    return action.afterState
  }

  fun clear() {
    undoStack.clear()
    redoStack.clear()
    activeTransaction = null
    _statusMessage.value = null
    updateState()
  }

  fun dismissStatusMessage() {
    _statusMessage.value = null
  }

  private fun updateState() {
    _canUndo.value = undoStack.isNotEmpty()
    _canRedo.value = redoStack.isNotEmpty()
    _lastAction.value = undoStack.lastOrNull()
    _undoActionTitle.value = undoStack.lastOrNull()?.let { "Undo ${it.description}" }
    _redoActionTitle.value = redoStack.lastOrNull()?.let { "Redo ${it.description}" }
    _actionHistory.value = undoStack.toList()
  }
}
