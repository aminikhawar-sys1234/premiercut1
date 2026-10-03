package com.ahstudio.captions.commands

import com.ahstudio.captions.core.model.CaptionProject

interface CaptionCommand {
    fun execute(project: CaptionProject): CaptionProject
    fun undo(project: CaptionProject): CaptionProject
}

class CaptionCommandBus(private val maxHistory: Int = 30) {
    private val undoStack = java.util.ArrayDeque<CaptionCommand>()
    private val redoStack = java.util.ArrayDeque<CaptionCommand>()

    fun commit(command: CaptionCommand, current: CaptionProject): CaptionProject {
        redoStack.clear()
        undoStack.push(command)
        if (undoStack.size > maxHistory) {
            val list = undoStack.toList().take(maxHistory)
            undoStack.clear(); list.reversed().forEach { undoStack.push(it) }
        }
        return command.execute(current)
    }

    fun undo(current: CaptionProject): Pair<CaptionProject, Boolean> {
        val cmd = undoStack.poll() ?: return current to false
        redoStack.push(cmd)
        return cmd.undo(current) to true
    }

    fun redo(current: CaptionProject): Pair<CaptionProject, Boolean> {
        val cmd = redoStack.poll() ?: return current to false
        undoStack.push(cmd)
        return cmd.execute(current) to true
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}

class UpdateClipTextCommand(private val clipId: String, private val oldText: String, private val newText: String) : CaptionCommand {
    private val engine = com.ahstudio.captions.timeline.CaptionTimelineEngine()
    override fun execute(project: CaptionProject): CaptionProject =
        project.updateClip(clipId) { engine.withEditedText(it, newText) }
    override fun undo(project: CaptionProject): CaptionProject =
        project.updateClip(clipId) { engine.withEditedText(it, oldText) }
}
