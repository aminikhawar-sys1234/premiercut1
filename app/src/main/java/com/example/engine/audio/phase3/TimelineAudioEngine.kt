package com.example.engine.audio.phase3

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.engine.controller.EnginePlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Central timeline audio output. It has one AudioTrack and one render loop; individual clips
 * are decoded by providers and mixed into that output. The PlaybackController clock is the
 * only authoritative timeline position.
 */
class TimelineAudioEngine(
  private val clockMs: StateFlow<Long>,
  private val playbackState: StateFlow<EnginePlaybackState>,
  private val sampleRate: Int = 48_000
) {
  interface PcmProvider {
    /** Return stereo PCM for this clip beginning at sourceMs. Must not mutate returned data. */
    fun read(clip: TimelineAudioClip, sourceMs: Long, frames: Int): FloatArray
    fun flush() {}
    fun release() {}
  }

  private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  private val generation = AtomicLong(0L)
  private var renderJob: Job? = null
  private var output: AudioTrack? = null
  private var provider: PcmProvider? = null
  private var tracks: List<TimelineAudioTrack> = emptyList()
  private var index = AudioTimelineIndex()
  private val mixer = AudioMixer(sampleRate)
  @Volatile private var running = false
  @Volatile private var lastClockMs = 0L

  fun setTimeline(newTracks: List<TimelineAudioTrack>) {
    tracks = newTracks
    index.rebuild(newTracks)
  }

  fun setProvider(pcmProvider: PcmProvider?) {
    provider?.release()
    provider = pcmProvider
  }

  fun setMasterVolume(volume: Float) { mixer.masterVolume = volume }

  fun prepare() {
    if (output != null) return
    val min = AudioTrack.getMinBufferSize(
      sampleRate,
      AudioFormat.CHANNEL_OUT_STEREO,
      AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(sampleRate / 5 * 2 * 2)
    output = AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build()
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setSampleRate(sampleRate)
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
          .build()
      )
      .setBufferSizeInBytes(min)
      .setTransferMode(AudioTrack.MODE_STREAM)
      .build()
  }

  fun play() {
    prepare()
    if (running) return
    running = true
    output?.play()
    renderJob = scope.launch { renderLoop(generation.incrementAndGet()) }
  }

  fun pause() {
    running = false
    renderJob?.cancel()
    renderJob = null
    output?.pause()
    output?.flush()
    provider?.flush()
  }

  /** Latest seek wins. Obsolete decoder work is invalidated by generation. */
  fun seekTo(timelineMs: Long) {
    generation.incrementAndGet()
    lastClockMs = timelineMs.coerceAtLeast(0L)
    renderJob?.cancel()
    output?.pause()
    output?.flush()
    provider?.flush()
    if (running && playbackState.value == EnginePlaybackState.PLAYING) {
      output?.play()
      renderJob = scope.launch { renderLoop(generation.get()) }
    }
  }

  fun release() {
    running = false
    generation.incrementAndGet()
    renderJob?.cancel()
    renderJob = null
    provider?.release()
    provider = null
    output?.release()
    output = null
    scope.cancel()
  }

  private suspend fun renderLoop(myGeneration: Long) {
    val frames = 960 // 20 ms @ 48 kHz; small enough for responsive seeks.
    val bytes = ShortArray(frames * 2)
    while (scope.isActive && running && myGeneration == generation.get()) {
      if (playbackState.value != EnginePlaybackState.PLAYING) {
        delay(8)
        continue
      }
      val t = clockMs.value
      lastClockMs = t
      val active = index.activeAt(t)
      val decoded = HashMap<String, FloatArray>(active.size)
      for (clip in active) {
        val source = clip.timelineToSourceMs(t)
        decoded[clip.id] = provider?.read(clip, source, frames) ?: FloatArray(frames * 2)
      }
      val mixed = mixer.mix(t, tracks, decoded, frames)
      for (i in bytes.indices) {
        bytes[i] = (mixed.samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
      }
      val track = output ?: break
      track.write(bytes, 0, bytes.size, AudioTrack.WRITE_BLOCKING)
      // A clock correction is deliberately non-destructive: never advance timeline time here.
      // The next block is always addressed using PlaybackController's current clock.
      if (abs(clockMs.value - t) > 100L) {
        generation.incrementAndGet()
        provider?.flush()
        track.flush()
        if (running && playbackState.value == EnginePlaybackState.PLAYING) {
          renderJob = scope.launch { renderLoop(generation.get()) }
        }
        break
      }
    }
  }
}
