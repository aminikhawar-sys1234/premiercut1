package com.ahstudio.audio.master.commands

import com.ahstudio.audio.master.clips.AudioClipOperations
import com.ahstudio.audio.master.model.*

interface AudioEditCommand {
    val description: String
    fun apply(project: MasterAudioProject): MasterAudioProject
    fun affectedTrackId(): String?
}

private fun removeClip(p: MasterAudioProject, clipId: String): Pair<MasterAudioProject, AudioClipModel>? {
    val found = p.clipById(clipId) ?: return null
    val (track, clip) = found
    return p.withTrack(track.copy(clips = track.clips.filter { it.id != clipId })) to clip
}

class AddTrackCommand(private val track: AudioTrackModel) : AudioEditCommand {
    override val description = "add track ${track.id}"
    override fun apply(p: MasterAudioProject) = p.copy(tracks = p.tracks + track)
    override fun affectedTrackId() = track.id
}
class RemoveTrackCommand(private val trackId: String) : AudioEditCommand {
    override val description = "remove track $trackId"
    override fun apply(p: MasterAudioProject) = p.copy(tracks = p.tracks.filter { it.id != trackId })
    override fun affectedTrackId() = trackId
}
class AddSourceCommand(private val source: AudioSourceModel) : AudioEditCommand {
    override val description = "add source ${source.id}"
    override fun apply(p: MasterAudioProject) = p.copy(sources = p.sources + (source.id to source))
    override fun affectedTrackId(): String? = null
}
class AddClipCommand(private val clip: AudioClipModel) : AudioEditCommand {
    override val description = "add clip ${clip.id}"
    override fun apply(p: MasterAudioProject): MasterAudioProject {
        val t = p.trackById(clip.trackId) ?: throw IllegalArgumentException("track ${clip.trackId} not found")
        return p.withTrack(t.copy(clips = t.clips + clip))
    }
    override fun affectedTrackId() = clip.trackId
}
class MoveClipCommand(private val clipId: String, private val newTrackId: String, private val newStartSec: Double) : AudioEditCommand {
    override val description = "move clip $clipId"
    override fun apply(p: MasterAudioProject): MasterAudioProject {
        val (np, clip) = removeClip(p, clipId) ?: throw IllegalArgumentException("clip $clipId not found")
        val t = np.trackById(newTrackId) ?: throw IllegalArgumentException("track $newTrackId not found")
        return np.withTrack(t.copy(clips = t.clips + clip.copy(trackId = newTrackId, timelineStartSec = newStartSec)))
    }
    override fun affectedTrackId() = newTrackId
}
class TrimClipCommand(private val clipId: String, private val newStartSec: Double, private val newEndSec: Double) : AudioEditCommand {
    override val description = "trim clip $clipId"
    override fun apply(p: MasterAudioProject): MasterAudioProject {
        val (track, clip) = p.clipById(clipId) ?: throw IllegalArgumentException("clip $clipId not found")
        return p.withClip(track.id, AudioClipOperations.trim(clip, newStartSec, newEndSec))
    }
    override fun affectedTrackId(): String? = null
}
class SplitClipCommand(private val clipId: String, private val atSec: Double) : AudioEditCommand {
    override val description = "split clip $clipId"
    override fun apply(p: MasterAudioProject): MasterAudioProject {
        val (np, clip) = removeClip(p, clipId) ?: throw IllegalArgumentException("clip $clipId not found")
        val (left, right) = AudioClipOperations.split(clip, atSec) ?: throw IllegalArgumentException("split point outside clip")
        val t = np.trackById(clip.trackId)!!
        return np.withTrack(t.copy(clips = t.clips + left + right))
    }
    override fun affectedTrackId(): String? = null
}
class DeleteClipCommand(private val clipId: String) : AudioEditCommand {
    override val description = "delete clip $clipId"
    override fun apply(p: MasterAudioProject) = removeClip(p, clipId)?.first ?: throw IllegalArgumentException("clip $clipId not found")
    override fun affectedTrackId(): String? = null
}
class DuplicateClipCommand(private val clipId: String, private val newStartSec: Double? = null) : AudioEditCommand {
    override val description = "duplicate clip $clipId"
    override fun apply(p: MasterAudioProject): MasterAudioProject {
        val (track, clip) = p.clipById(clipId) ?: throw IllegalArgumentException("clip $clipId not found")
        val copy = AudioClipOperations.duplicate(clip, track.id, newStartSec ?: clip.timelineDurationSec)
        return p.withTrack(track.copy(clips = track.clips + copy))
    }
    override fun affectedTrackId(): String? = null
}
class ReplaceClipSourceCommand(private val clipId: String, private val newSourceId: String, private val newSourceDurationSec: Double) : AudioEditCommand {
    override val description = "replace source of $clipId"
    override fun apply(p: MasterAudioProject): MasterAudioProject {
        val (track, clip) = p.clipById(clipId) ?: throw IllegalArgumentException("clip $clipId not found")
        return p.withClip(track.id, AudioClipOperations.replaceSource(clip, newSourceId, newSourceDurationSec))
    }
    override fun affectedTrackId(): String? = null
}
class UpdateClipCommand(private val clipId: String, private val transform: (AudioClipModel) -> AudioClipModel) : AudioEditCommand {
    override val description = "update clip $clipId"
    override fun apply(p: MasterAudioProject): MasterAudioProject {
        val (track, clip) = p.clipById(clipId) ?: throw IllegalArgumentException("clip $clipId not found")
        return p.withClip(track.id, transform(clip))
    }
    override fun affectedTrackId(): String? = null
}
class UpdateTrackCommand(private val trackId: String, private val transform: (AudioTrackModel) -> AudioTrackModel) : AudioEditCommand {
    override val description = "update track $trackId"
    override fun apply(p: MasterAudioProject): MasterAudioProject {
        val t = p.trackById(trackId) ?: throw IllegalArgumentException("track $trackId not found")
        return p.withTrack(transform(t))
    }
    override fun affectedTrackId() = trackId
}
class UpdateMixCommand(private val transform: (AudioMixSettings) -> AudioMixSettings) : AudioEditCommand {
    override val description = "update mix"
    override fun apply(p: MasterAudioProject) = p.copy(mix = transform(p.mix))
    override fun affectedTrackId(): String? = null
}

object AudioEditCommandFactory {
    fun addTrack(name: String, index: Int = 0) = AddTrackCommand(
        AudioTrackModel(java.util.UUID.randomUUID().toString(), index, settings = AudioTrackSettings(name = name)))
    fun addClip(trackId: String, sourceId: String, timelineStartSec: Double, sourceStartSec: Double,
                durationSec: Double, speed: Float = 1f) = AddClipCommand(
        AudioClipModel(java.util.UUID.randomUUID().toString(), trackId, sourceId,
            timelineStartSec, durationSec / speed, sourceStartSec, durationSec, transform = AudioClipTransform(speed)))
    fun moveClip(clipId: String, trackId: String, startSec: Double) = MoveClipCommand(clipId, trackId, startSec)
    fun trimClip(clipId: String, startSec: Double, endSec: Double) = TrimClipCommand(clipId, startSec, endSec)
    fun splitClip(clipId: String, atSec: Double) = SplitClipCommand(clipId, atSec)
    fun deleteClip(clipId: String) = DeleteClipCommand(clipId)
    fun duplicateClip(clipId: String, newStartSec: Double? = null) = DuplicateClipCommand(clipId, newStartSec)
    fun replaceClipSource(clipId: String, sourceId: String, sourceDurSec: Double) = ReplaceClipSourceCommand(clipId, sourceId, sourceDurSec)
    fun setClipVolume(clipId: String, v: Float) = UpdateClipCommand(clipId) { it.copy(volume = v.coerceIn(0f, 4f)) }
    fun setClipGain(clipId: String, db: Float) = UpdateClipCommand(clipId) { it.copy(gainDb = db) }
    fun setClipPan(clipId: String, pan: Float) = UpdateClipCommand(clipId) { it.copy(pan = AudioPanSettings(pan)) }
    fun setClipFade(clipId: String, inSec: Double, outSec: Double) = UpdateClipCommand(clipId) { it.copy(fade = it.fade.copy(fadeInSec = inSec, fadeOutSec = outSec)) }
    fun setClipSpeed(clipId: String, speed: Float) = UpdateClipCommand(clipId) { AudioClipOperations.withSpeed(it, speed) }
    fun setClipDsp(clipId: String, dsp: AudioDspChainSpec) = UpdateClipCommand(clipId) { it.copy(clipDsp = dsp) }
    fun setTrackVolume(trackId: String, v: Float) = UpdateTrackCommand(trackId) { it.copy(settings = it.settings.copy(volume = v)) }
    fun setTrackMute(trackId: String, m: Boolean) = UpdateTrackCommand(trackId) { it.copy(settings = it.settings.copy(mute = m)) }
    fun setTrackSolo(trackId: String, s: Boolean) = UpdateTrackCommand(trackId) { it.copy(settings = it.settings.copy(solo = s)) }
    fun setTrackDsp(trackId: String, dsp: AudioDspChainSpec) = UpdateTrackCommand(trackId) { it.copy(trackDsp = dsp) }
    fun setMasterVolume(v: Float) = UpdateMixCommand { it.copy(masterVolume = v) }
    fun addClipKeyframe(clipId: String, parameter: AutomationParameter, timeSec: Double, value: Float) = UpdateClipCommand(clipId) { c ->
        val existing = c.automation.firstOrNull { it.parameter == parameter }
        val updated = if (existing == null) AudioAutomationModel(parameter, enabled = true, keyframes = listOf(AudioKeyframeModel(timeSec, value)))
            else existing.copy(enabled = true, keyframes = (existing.keyframes + AudioKeyframeModel(timeSec, value)).sortedBy { it.timeSec })
        c.copy(automation = c.automation.filter { it.parameter != parameter } + updated)
    }
}

/** Snapshot-based undo/redo. MasterAudioProject is immutable, so snapshots share structure — cheap. */
class AudioUndoRedoAdapter(private val depth: Int = 100) {
    private class Entry(val before: MasterAudioProject, val after: MasterAudioProject)
    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()

    @Synchronized fun push(command: AudioEditCommand, before: MasterAudioProject, after: MasterAudioProject) {
        undoStack.addLast(Entry(before, after))
        while (undoStack.size > depth) undoStack.removeFirst()
        redoStack.clear()
    }
    @Synchronized fun undo(current: MasterAudioProject): MasterAudioProject? {
        val e = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(Entry(current, e.after)); return e.before
    }
    @Synchronized fun redo(current: MasterAudioProject): MasterAudioProject? {
        val e = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(Entry(current, e.after)); return e.after
    }
    @Synchronized fun canUndo() = undoStack.isNotEmpty()
    @Synchronized fun canRedo() = redoStack.isNotEmpty()
}
