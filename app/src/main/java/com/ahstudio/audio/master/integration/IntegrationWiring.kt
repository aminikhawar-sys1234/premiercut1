package com.ahstudio.audio.master.integration

import android.media.MediaMuxer
import com.ahstudio.audio.master.AhStudioAudioMasterEngine
import com.ahstudio.audio.master.commands.AudioEditCommand
import com.ahstudio.audio.master.core.AudioClockAdapter
import com.ahstudio.audio.master.export.AudioMuxerAdapter
import com.ahstudio.audio.master.export.AudioExportResult
import com.ahstudio.audio.master.export.MediaMuxerAudioSinkAdapter

/**
 * [WIRE] PlaybackAudioIntegration — connect to existing PlaybackController + MasterTimelineClock:
 *  1. PlaybackController.onPlay  → onHostPlay()      2. onPause → onHostPause()
 *  3. MasterTimelineClock seek observer → onCtiMoved()
 *  4. While playing, host clock should poll engine.audioPositionSource for A/V sync (LEAD mode),
 *     OR call bindHostClock() (FOLLOW mode). Only ONE mode must be active.
 */
class PlaybackAudioIntegration(
    private val engine: AhStudioAudioMasterEngine,
    private val clockAdapter: AudioClockAdapter,
) {
    fun onHostPlay() = engine.onHostPlay(clockAdapter.playbackPositionSec())
    fun onHostPause() = engine.onHostPause()
    fun onCtiMoved(newPosSec: Double) = engine.onHostSeek(newPosSec)
    fun bindAudioPositionToHostClock() { clockAdapter.audioPositionSource = engine.audioPositionSource }
}

/** [WIRE] TimelineAudioIntegration — forward all host audio-edit UI actions through here. */
class TimelineAudioIntegration(private val engine: AhStudioAudioMasterEngine) {
    fun execute(command: AudioEditCommand) = engine.submit(command)
    fun undo() = engine.undo(); fun redo() = engine.redo()
    fun canUndo() = engine.canUndo(); fun canRedo() = engine.canRedo()
}

/** [WIRE] ExportAudioIntegration — inside the existing video export pipeline:
 *  val fmt = AudioMuxerAdapter.createAudioMediaFormat(48000, 2)
 *  val trackIndex = muxer.addTrack(fmt)   // same MediaMuxer as the video track
 *  val sink = MediaMuxerAudioSinkAdapter(muxer, trackIndex, 48000)
 *  engine.renderToPcmSink(sink, videoStartSec, videoEndSec)
 */
class ExportAudioIntegration(private val engine: AhStudioAudioMasterEngine) {
    fun renderAudioForVideoExport(muxer: MediaMuxer, trackIndex: Int, startSec: Double, endSec: Double,
                                  sampleRate: Int, progress: ((Float) -> Unit)? = null): AudioExportResult =
        engine.renderToPcmSink(MediaMuxerAudioSinkAdapter(muxer, trackIndex, sampleRate), startSec, endSec, progress)
}

/** [WIRE] ProjectAudioIntegration — hook into existing project save/load. */
class ProjectAudioIntegration(private val engine: AhStudioAudioMasterEngine) {
    fun onProjectSave(): String = engine.toJson()
    fun onProjectLoad(json: String) = engine.loadFromJson(json)
}

/** [WIRE] Undo/redo adapter for the existing host command stack. */
class AudioCommandAdapter(private val engine: AhStudioAudioMasterEngine) {
    fun execute(cmd: AudioEditCommand) = engine.submit(cmd)
    fun undo() = engine.undo(); fun redo() = engine.redo()
}

object AudioEngineDependencyGraph {
    fun description(): String = """
        MasterTimelineClock (host, authoritative)
          └─ PlaybackAudioIntegration ─ AhStudioAudioMasterEngine
               ├─ AudioTimelineController (edits, undo/redo) → MasterAudioProject (immutable)
               ├─ DecodedAudioCache ← MediaCodecAudioDecoder / WavPcmReader
               ├─ MasterAudioMixer ─ AudioTrackMixer (clips: gain/fade/automation/pan/DSP)
               │       └─ AudioTrackProcessor (track gain/pan/DSP) → master bus → limiter
               ├─ AudioOutputEngine → AndroidAudioTrackOutput  (one playback path)
               ├─ WaveformEngine ← DecodedAudioCache (real PCM min/max)
               ├─ AndroidAudioRecorder → RecordingClipCreator → timeline commands
               └─ AudioExportEngine / renderToPcmSink → host MediaMuxer (one export path)
    """.trimIndent()
}
