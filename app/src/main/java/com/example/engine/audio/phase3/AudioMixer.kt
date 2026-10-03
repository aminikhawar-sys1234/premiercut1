package com.example.engine.audio.phase3

import com.example.engine.audio.AdvancedAudioProcessor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Thread-confined, allocation-light PCM mixer. One output is produced for all tracks;
 * clips never own playback clocks or output devices.
 */
class AudioMixer(
  val sampleRate: Int = 48_000,
  val channels: Int = 2
) {
  @Volatile var masterVolume: Float = 1f
    set(value) { field = value.coerceIn(0f, 4f) }

  private var scratch = FloatArray(0)

  fun mix(
    timelineMs: Long,
    tracks: List<TimelineAudioTrack>,
    decoded: Map<String, FloatArray>,
    frames: Int
  ): AudioMixFrame {
    require(channels == 2) { "AudioMixer output expects stereo" }
    val count = frames * channels
    if (scratch.size < count) scratch = FloatArray(count)
    java.util.Arrays.fill(scratch, 0, count, 0f)

    val soloActive = tracks.any { it.enabled && it.solo && !it.mute }
    for (track in tracks.sortedBy { it.order }) {
      if (!track.enabled || track.mute || (soloActive && !track.solo)) continue
      val trackGain = track.volume.coerceIn(0f, 4f)
      for (clip in track.clips) {
        if (!clip.contains(timelineMs) || clip.mute) continue
        var pcm = decoded[clip.id] ?: continue
        if (pcm.isEmpty()) continue

        val hasEffects = clip.audioEffects.hasActiveEffects()
        if (hasEffects) {
          pcm = AdvancedAudioProcessor.processFloatBuffer(
            pcm = pcm,
            sampleRate = sampleRate,
            channels = channels,
            effects = clip.audioEffects
          )
        }

        val gain = clip.gainAt(timelineMs) * trackGain * masterVolume
        val pan = clip.pan.coerceIn(-1f, 1f)
        // Constant-power stereo pan preserves perceived loudness better than linear pan.
        val angle = (pan + 1f) * (Math.PI.toFloat() / 4f)
        val leftGain = cos(angle) * gain
        val rightGain = sin(angle) * gain

        val isInputStereo = pcm.size >= frames * 2
        val availableFrames = minOf(frames, if (isInputStereo) pcm.size / 2 else pcm.size)

        if (isInputStereo) {
          for (i in 0 until availableFrames) {
            scratch[i * 2] += pcm[i * 2] * leftGain
            scratch[i * 2 + 1] += pcm[i * 2 + 1] * rightGain
          }
        } else {
          // Mono input duplicated to stereo with pan
          for (i in 0 until availableFrames) {
            val s = pcm[i]
            scratch[i * 2] += s * leftGain
            scratch[i * 2 + 1] += s * rightGain
          }
        }
      }
    }

    // Soft-knee safety limiter to prevent digital clipping when mixing multiple tracks
    for (i in 0 until count) {
      val x = scratch[i]
      val absX = abs(x)
      scratch[i] = when {
        absX <= 0.85f -> x
        absX <= 1.5f -> {
          val sign = if (x < 0) -1f else 1f
          sign * (0.85f + (absX - 0.85f) / (1f + (absX - 0.85f)))
        }
        else -> {
          val sign = if (x < 0) -1f else 1f
          sign * (1.175f + (absX - 1.5f) * 0.05f).coerceAtMost(1.3f)
        }
      }
    }
    return AudioMixFrame(sampleRate, channels, scratch.copyOf(count))
  }

  fun reset() { java.util.Arrays.fill(scratch, 0f) }
}
