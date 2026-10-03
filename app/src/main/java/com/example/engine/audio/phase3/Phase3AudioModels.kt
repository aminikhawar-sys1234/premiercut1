package com.example.engine.audio.phase3

import com.example.domain.model.AudioEffectsSettings
import com.example.domain.model.ClipKeyframe
import com.example.engine.KeyframeInterpolator
import kotlin.math.pow

/** Immutable timeline audio description consumed by the centralized mixer. */
data class TimelineAudioClip(
  val id: String,
  val sourceMediaId: String,
  val sourceUri: String,
  val sourceStartMs: Long,
  val sourceEndMs: Long,
  val timelineStartMs: Long,
  val timelineEndMs: Long,
  val trackId: String,
  val volume: Float = 1f,
  val gainDb: Float = 0f,
  val pan: Float = 0f,
  val mute: Boolean = false,
  val fadeInMs: Long = 0L,
  val fadeOutMs: Long = 0L,
  val speed: Float = 1f,
  val isReversed: Boolean = false,
  val audioEffects: AudioEffectsSettings = AudioEffectsSettings(),
  val keyframes: List<ClipKeyframe> = emptyList(),
  val enabled: Boolean = true
) {
  val durationMs: Long get() = (timelineEndMs - timelineStartMs).coerceAtLeast(0L)

  fun contains(timelineMs: Long): Boolean = enabled && timelineMs >= timelineStartMs && timelineMs < timelineEndMs

  fun containsSample(samplePosition: Long, sampleRate: Int): Boolean {
    val startSample = timelineStartSample(sampleRate)
    val endSample = timelineEndSample(sampleRate)
    return enabled && samplePosition >= startSample && samplePosition < endSample
  }

  fun timelineStartSample(sampleRate: Int): Long = (timelineStartMs * sampleRate) / 1000L
  fun timelineEndSample(sampleRate: Int): Long = (timelineEndMs * sampleRate) / 1000L
  fun sourceStartSample(sampleRate: Int): Long = (sourceStartMs * sampleRate) / 1000L
  fun sourceEndSample(sampleRate: Int): Long = (sourceEndMs * sampleRate) / 1000L
  fun durationSamples(sampleRate: Int): Long = (durationMs * sampleRate) / 1000L

  fun timelineToSourceMs(timelineMs: Long): Long {
    val local = (timelineMs - timelineStartMs).coerceIn(0L, durationMs)
    val scaledOffsetMs = (local.toDouble() * speed.coerceIn(0.01f, 16f).toDouble()).toLong()
    val rawSourceMs = if (isReversed) {
      sourceEndMs - scaledOffsetMs
    } else {
      sourceStartMs + scaledOffsetMs
    }
    return rawSourceMs.coerceIn(sourceStartMs, sourceEndMs)
  }

  fun timelineToSourceSample(timelineSample: Long, sampleRate: Int): Long {
    val startSample = timelineStartSample(sampleRate)
    val endSample = timelineEndSample(sampleRate)
    val localSample = (timelineSample - startSample).coerceIn(0L, (endSample - startSample).coerceAtLeast(0L))
    val scaledSample = (localSample.toDouble() * speed.coerceIn(0.01f, 16f).toDouble()).toLong()
    val srcStart = sourceStartSample(sampleRate)
    val srcEnd = sourceEndSample(sampleRate)
    val rawSample = if (isReversed) {
      srcEnd - scaledSample
    } else {
      srcStart + scaledSample
    }
    return rawSample.coerceIn(srcStart, srcEnd)
  }

  fun gainAt(timelineMs: Long): Float {
    if (!contains(timelineMs) || mute) return 0f
    val local = timelineMs - timelineStartMs
    val remaining = timelineEndMs - timelineMs
    val fi = if (fadeInMs > 0) (local.toFloat() / fadeInMs).coerceIn(0f, 1f) else 1f
    val fo = if (fadeOutMs > 0) (remaining.toFloat() / fadeOutMs).coerceIn(0f, 1f) else 1f

    val kfVolume = if (keyframes.isNotEmpty()) {
      KeyframeInterpolator.interpolateVolume(keyframes, local, 1.0f)
    } else 1.0f

    val linearGain = dbToLinear(gainDb)
    return (volume * linearGain * kfVolume).coerceAtLeast(0f) * minOf(fi, fo)
  }
}

data class TimelineAudioTrack(
  val id: String,
  val order: Int,
  val volume: Float = 1f,
  val mute: Boolean = false,
  val solo: Boolean = false,
  val enabled: Boolean = true,
  val clips: List<TimelineAudioClip> = emptyList()
)

data class AudioMixFrame(
  val sampleRate: Int,
  val channelCount: Int,
  val samples: FloatArray
)

internal fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
