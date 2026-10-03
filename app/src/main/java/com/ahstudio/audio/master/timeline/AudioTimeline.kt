package com.ahstudio.audio.master.timeline

import com.ahstudio.audio.master.AudioEngineError
import com.ahstudio.audio.master.AudioEngineResult
import com.ahstudio.audio.master.commands.AudioEditCommand
import com.ahstudio.audio.master.commands.AudioUndoRedoAdapter
import com.ahstudio.audio.master.model.AudioClipModel
import com.ahstudio.audio.master.model.AudioTrackModel
import com.ahstudio.audio.master.model.MasterAudioProject

enum class OverlapStrategy {
    REJECT, TRIM_INCOMING, TRIM_EXISTING, AUTO_CROSSFADE
}

object AudioOverlapResolver {
    fun resolve(project: MasterAudioProject, trackId: String, strategy: OverlapStrategy): MasterAudioProject? {
        val track = project.trackById(trackId) ?: return project
        val sortedClips = track.clips.sortedBy { it.timelineStartSec }.toMutableList()
        if (sortedClips.size <= 1) return project

        for (i in 0 until sortedClips.size - 1) {
            val c1 = sortedClips[i]
            val c2 = sortedClips[i + 1]
            if (c1.timelineEndSec > c2.timelineStartSec) {
                when (strategy) {
                    OverlapStrategy.REJECT -> return null
                    OverlapStrategy.TRIM_INCOMING -> {
                        val shift = c1.timelineEndSec - c2.timelineStartSec
                        val newDur = (c2.timelineDurationSec - shift).coerceAtLeast(0.0)
                        val trimmedC2 = c2.copy(
                            timelineStartSec = c1.timelineEndSec,
                            timelineDurationSec = newDur,
                            sourceStartSec = c2.sourceStartSec + shift * c2.speed,
                            sourceDurationSec = (c2.sourceDurationSec - shift * c2.speed).coerceAtLeast(0.0)
                        )
                        sortedClips[i + 1] = trimmedC2
                    }
                    OverlapStrategy.TRIM_EXISTING -> {
                        val newDur = (c2.timelineStartSec - c1.timelineStartSec).coerceAtLeast(0.0)
                        val trimmedC1 = c1.copy(
                            timelineDurationSec = newDur,
                            sourceDurationSec = (newDur * c1.speed).coerceAtLeast(0.0)
                        )
                        sortedClips[i] = trimmedC1
                    }
                    OverlapStrategy.AUTO_CROSSFADE -> {
                        // Retain both clips for crossfade
                    }
                }
            }
        }
        return project.withTrack(track.copy(clips = sortedClips))
    }
}

object AudioTimelineValidator {
    fun validate(project: MasterAudioProject): Pair<List<String>, List<String>> {
        val fatal = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        for (track in project.tracks) {
            for (clip in track.clips) {
                if (clip.timelineStartSec < 0.0) {
                    fatal.add("Clip ${clip.id} starts at negative time: ${clip.timelineStartSec}")
                }
                if (clip.timelineDurationSec <= 0.0) {
                    fatal.add("Clip ${clip.id} has invalid duration: ${clip.timelineDurationSec}")
                }
                if (!project.sources.containsKey(clip.sourceId)) {
                    warnings.add("Clip ${clip.id} references missing source: ${clip.sourceId}")
                }
            }
        }
        return Pair(fatal, warnings)
    }
}

class AudioTimelineController(
    val undoRedo: AudioUndoRedoAdapter = AudioUndoRedoAdapter()
) {
    @Volatile
    var current: MasterAudioProject = MasterAudioProject()
        private set

    private val listeners = mutableListOf<(MasterAudioProject) -> Unit>()

    fun addListener(listener: (MasterAudioProject) -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: (MasterAudioProject) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners() {
        val p = current
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it(p) }
    }

    fun setProject(project: MasterAudioProject) {
        current = project
        notifyListeners()
    }

    fun submit(command: AudioEditCommand): AudioEngineResult<MasterAudioProject> {
        return try {
            val before = current
            val after = command.apply(before)
            undoRedo.push(command, before, after)
            current = after
            notifyListeners()
            AudioEngineResult.Success(after)
        } catch (e: Exception) {
            AudioEngineResult.Failure(AudioEngineError.INVALID_PROJECT, e.message ?: "Command failed", e)
        }
    }

    fun undo(): Boolean {
        val prev = undoRedo.undo(current) ?: return false
        current = prev
        notifyListeners()
        return true
    }

    fun redo(): Boolean {
        val next = undoRedo.redo(current) ?: return false
        current = next
        notifyListeners()
        return true
    }

    fun canUndo(): Boolean = undoRedo.canUndo()
    fun canRedo(): Boolean = undoRedo.canRedo()
}
