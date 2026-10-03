package com.ahstudio.audio.master

import android.content.Context
import com.ahstudio.audio.master.cache.DecodedAudioCache
import com.ahstudio.audio.master.clips.AudioClipReader
import com.ahstudio.audio.master.commands.*
import com.ahstudio.audio.master.core.*
import com.ahstudio.audio.master.diagnostics.AudioGlitchDetector
import com.ahstudio.audio.master.diagnostics.AudioPerformanceMonitor
import com.ahstudio.audio.master.export.*
import com.ahstudio.audio.master.mixer.MasterAudioMixer
import com.ahstudio.audio.master.model.AudioSourceModel
import com.ahstudio.audio.master.model.MasterAudioProject
import com.ahstudio.audio.master.persistence.AudioProjectDeserializer
import com.ahstudio.audio.master.persistence.AudioProjectSerializer
import com.ahstudio.audio.master.playback.AudioOutputEngine
import com.ahstudio.audio.master.playback.AudioPlaybackState
import com.ahstudio.audio.master.recording.*
import com.ahstudio.audio.master.timeline.AudioTimelineController
import com.ahstudio.audio.master.waveform.WaveformEngine
import java.io.File
import kotlinx.coroutines.launch

class AhStudioAudioMasterEngine(
    private val context: Context,
    val config: AudioMasterEngineConfig = AudioMasterEngineConfig(),
) {
    val format = AudioFormat(config.sampleRate, config.channels, PcmEncoding.FLOAT)
    private val threads = AudioThreadController()
    val performance = AudioPerformanceMonitor(config.sampleRate)
    val glitches = AudioGlitchDetector()
    val cache = DecodedAudioCache(context, format, config.memoryCacheBytes)
    val mixer = MasterAudioMixer(config.maxBlockFrames)
    val outputEngine = AudioOutputEngine(format, mixer, performance, glitches)
    val timeline = AudioTimelineController(AudioUndoRedoAdapter(config.undoDepth))
    val waveform = WaveformEngine(cache, context.cacheDir, threads.bgScope)
    val recorder = AndroidAudioRecorder(context, threads.bgScope)
    val capabilities: AudioEngineCapabilities = AudioEngineCapabilities.of()

    @Volatile var state: AudioEngineState = AudioEngineState.UNINITIALIZED; private set
    @Volatile private var project = MasterAudioProject()
    private val clipReader = AudioClipReader(cache)
    private var recordingStartSec = 0.0
    private var pendingRecordingTrackId: String? = null

    init {
        cache.sourceProvider = { id -> project.sourceById(id) }
        timeline.addListener { p -> project = p; mixer.setProject(p, format) }
        AudioEngineRegistry.install(this)
        state = AudioEngineState.READY
    }

    // ---- project ----
    fun setProject(p: MasterAudioProject) {
        threads.bgScope.launch { cache.preload(p.sources.values) }
        p.sources.values.forEach { waveform.requestAsync(it.id, config.waveformBucketsPerSecond) }
        timeline.setProject(p)
    }
    fun currentProject(): MasterAudioProject = timeline.current
    fun toJson(): String = AudioProjectSerializer().serialize(timeline.current)
    fun loadFromJson(json: String): AudioEngineResult<MasterAudioProject> = try {
        val p = AudioProjectDeserializer().deserialize(json)
        setProject(p); AudioEngineResult.Success(p)
    } catch (e: Exception) {
        AudioEngineResult.Failure(AudioEngineError.IO_FAILED, "project load failed: ${e.message}", e)
    }

    // ---- preview transport (host drives; engine never owns a second clock) ----
    fun onHostPlay(fromSec: Double) {
        threads.bgScope.launch { cache.preload(timeline.current.sources.values) }
        outputEngine.play(fromSec, clipReader)
        state = AudioEngineState.PLAYING
    }
    fun onHostPause() { outputEngine.pause(); if (state == AudioEngineState.PLAYING) state = AudioEngineState.PAUSED }
    fun onHostSeek(sec: Double) = outputEngine.seekTo(sec, clipReader)
    /** LEAD mode: host MasterTimelineClock polls this for sample-accurate A/V sync while playing. */
    val audioPositionSource: () -> Double get() = outputEngine::currentPositionSec
    /** FOLLOW mode: bind host clock; drift > 80 ms triggers audio resync. */
    fun bindHostClock(positionSec: () -> Double, isPlaying: () -> Boolean) {
        outputEngine.hostClock = object : AudioOutputEngine.HostClock {
            override fun positionSec() = positionSec()
            override fun isPlaying() = isPlaying()
        }
    }
    val isPlaying: Boolean get() = outputEngine.state == AudioPlaybackState.PLAYING

    // ---- recording ----
    fun startRecording(atTimelineSec: Double, trackId: String): AudioEngineResult<RecordingSession> {
        recordingStartSec = atTimelineSec
        pendingRecordingTrackId = trackId
        val r = recorder.start(RecordingConfig(sampleRate = config.sampleRate, channels = 1,
            outputDir = File(context.filesDir, "recordings")))
        if (r is AudioEngineResult.Success) state = AudioEngineState.RECORDING
        return r
    }
    fun pauseRecording() = recorder.pause()
    fun resumeRecording() = recorder.resume()
    fun cancelRecording() { threads.ioScope.launch { recorder.cancel() }; state = AudioEngineState.READY }
    suspend fun finishRecording(): AudioEngineResult<AudioSourceModel> {
        val trackId = pendingRecordingTrackId
            ?: return AudioEngineResult.Failure(AudioEngineError.RECORD_FAILED, "no pending recording")
        val r = recorder.finish()
        if (r !is AudioEngineResult.Success) { state = AudioEngineState.READY; return AudioEngineResult.Failure(AudioEngineError.RECORD_FAILED, "recording failed") }
        val (source, clip) = RecordingClipCreator.create(r.value, trackId, recordingStartSec)
        timeline.submit(AddSourceCommand(source))
        timeline.submit(AddClipCommand(clip))
        threads.bgScope.launch { cache.preload(listOf(source)); waveform.requestAsync(source.id, config.waveformBucketsPerSecond) }
        state = AudioEngineState.READY
        return AudioEngineResult.Success(source)
    }

    // ---- export ----
    suspend fun exportAudio(config: AudioExportConfig, progress: ((AudioExportProgress) -> Unit)? = null): AudioExportResult {
        state = AudioEngineState.EXPORTING
        try { return AudioExportEngine(cache, format).export(timeline.current, config, progress) }
        finally { state = AudioEngineState.READY }
    }
    /** [WIRE] Render audio into the host video exporter's MediaMuxer track (preview/export parity: same mixer graph). */
    fun renderToPcmSink(sink: PcmSink, startSec: Double, endSec: Double, progress: ((Float) -> Unit)? = null): AudioExportResult {
        sink.prepare()
        val total = (endSec - startSec).coerceAtLeast(1e-6)
        val r = AudioPcmRenderer(cache, format, 4096).render(timeline.current, startSec, endSec) { inter, frames, t ->
            sink.write(inter, frames)
            progress?.invoke(((t - startSec) / total).toFloat().coerceIn(0f, 1f))
            true
        }
        sink.finish()
        return r
    }

    // ---- edits / undo ----
    fun submit(command: AudioEditCommand) = timeline.submit(command)
    fun undo() = timeline.undo()
    fun redo() = timeline.redo()
    fun canUndo() = timeline.canUndo()
    fun canRedo() = timeline.canRedo()
    fun invalidateWaveform(sourceId: String) = waveform.invalidate(sourceId)

    fun release() {
        outputEngine.stopAndRelease()
        cache.release()
        mixer.reset()
        threads.release()
        state = AudioEngineState.RELEASED
        AudioEngineRegistry.clear()
    }
}

typealias AudioEngineFacade = AhStudioAudioMasterEngine
