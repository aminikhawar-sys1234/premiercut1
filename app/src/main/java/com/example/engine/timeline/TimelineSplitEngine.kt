package com.example.engine.timeline

import android.util.Log
import com.example.domain.model.*
import com.example.engine.SelectedTrackElement
import com.example.engine.TimelineEngine
import java.util.UUID

/**
 * High-Precision Pinpoint Split Engine for Non-Linear Multi-Track Timelines.
 *
 * Implements sample-accurate and microsecond-accurate non-destructive splitting of media clips,
 * guaranteeing audio/video synchronization, keyframe distribution, and smooth playback continuity.
 */
object TimelineSplitEngine {
  private const val TAG = "TimelineSplitEngine"

  /** Minimum threshold in microseconds (50ms) to prevent creating zero-duration or corrupt sub-clips. */
  const val MIN_SPLIT_MARGIN_US = 50_000L
  /** Minimum threshold in milliseconds (50ms). */
  const val MIN_SPLIT_MARGIN_MS = 50L

  data class SplitResult<T>(
    val isSuccess: Boolean,
    val headClip: T? = null,
    val tailClip: T? = null,
    val message: String = ""
  )

  /**
   * Identifies the target clip intersecting the fixed playhead timestamp.
   * Priority order:
   * 1. Explicitly selected clip if intersecting playhead.
   * 2. Main video track clip under playhead.
   * 3. Topmost secondary track clip (Overlay, Audio, Text, Effect) intersecting playhead.
   */
  fun findTargetClipAtTimestamp(
    timeline: Timeline,
    playheadMs: Long,
    selectedElement: SelectedTrackElement = SelectedTrackElement.None
  ): Pair<SelectedTrackElement, String?> {
    // 1. Check if the currently selected element intersects the playhead
    when (selectedElement) {
      is SelectedTrackElement.Video -> {
        val clip = timeline.videoClips.find { it.id == selectedElement.clipId }
        if (clip != null && isTimeInside(playheadMs, clip.timelineStartMs, clip.durationMs)) {
          return Pair(selectedElement, clip.id)
        }
      }
      is SelectedTrackElement.Overlay -> {
        val clip = timeline.overlayClips.find { it.id == selectedElement.clipId }
        if (clip != null && isTimeInside(playheadMs, clip.timelineStartMs, clip.durationMs)) {
          return Pair(selectedElement, clip.id)
        }
      }
      is SelectedTrackElement.Audio -> {
        val clip = timeline.audioClips.find { it.id == selectedElement.clipId }
        if (clip != null && isTimeInside(playheadMs, clip.timelineStartMs, clip.durationMs)) {
          return Pair(selectedElement, clip.id)
        }
      }
      is SelectedTrackElement.Text -> {
        val clip = timeline.textClips.find { it.id == selectedElement.clipId }
        if (clip != null && isTimeInside(playheadMs, clip.timelineStartMs, clip.durationMs)) {
          return Pair(selectedElement, clip.id)
        }
      }
      is SelectedTrackElement.Sticker -> {
        val clip = timeline.stickerClips.find { it.id == selectedElement.clipId }
        if (clip != null && isTimeInside(playheadMs, clip.timelineStartMs, clip.durationMs)) {
          return Pair(selectedElement, clip.id)
        }
      }
      is SelectedTrackElement.Effect -> {
        val clip = timeline.effectClips.find { it.id == selectedElement.clipId }
        if (clip != null && isTimeInside(playheadMs, clip.timelineStartMs, clip.durationMs)) {
          return Pair(selectedElement, clip.id)
        }
      }
      else -> { /* Fallback to auto-detect */ }
    }

    // 2. Check Main Video Track
    val mainClip = timeline.videoClips.find { isTimeInside(playheadMs, it.timelineStartMs, it.durationMs) }
    if (mainClip != null) {
      return Pair(SelectedTrackElement.Video(mainClip.id), mainClip.id)
    }

    // 3. Check Overlay Tracks
    val overlayClip = timeline.overlayClips.find { isTimeInside(playheadMs, it.timelineStartMs, it.durationMs) }
    if (overlayClip != null) {
      return Pair(SelectedTrackElement.Overlay(overlayClip.id), overlayClip.id)
    }

    // 4. Check Audio Tracks
    val audioClip = timeline.audioClips.find { isTimeInside(playheadMs, it.timelineStartMs, it.durationMs) }
    if (audioClip != null) {
      return Pair(SelectedTrackElement.Audio(audioClip.id), audioClip.id)
    }

    // 5. Check Text Tracks
    val textClip = timeline.textClips.find { isTimeInside(playheadMs, it.timelineStartMs, it.durationMs) }
    if (textClip != null) {
      return Pair(SelectedTrackElement.Text(textClip.id), textClip.id)
    }

    // 6. Check Sticker Tracks
    val stickerClip = timeline.stickerClips.find { isTimeInside(playheadMs, it.timelineStartMs, it.durationMs) }
    if (stickerClip != null) {
      return Pair(SelectedTrackElement.Sticker(stickerClip.id), stickerClip.id)
    }

    // 7. Check Effect Tracks
    val effectClip = timeline.effectClips.find { isTimeInside(playheadMs, it.timelineStartMs, it.durationMs) }
    if (effectClip != null) {
      return Pair(SelectedTrackElement.Effect(effectClip.id), effectClip.id)
    }

    return Pair(SelectedTrackElement.None, null)
  }

  private fun isTimeInside(timeMs: Long, startMs: Long, durationMs: Long): Boolean {
    return timeMs > startMs + MIN_SPLIT_MARGIN_MS && timeMs < (startMs + durationMs) - MIN_SPLIT_MARGIN_MS
  }

  /**
   * Splits a VideoClip cleanly at an exact timestamp without losing sync or corrupting source bounds.
   */
  fun splitVideoClip(
    clip: VideoClip,
    splitTimestampMs: Long
  ): SplitResult<VideoClip> {
    val startMs = clip.timelineStartMs
    val durationMs = clip.durationMs
    val endMs = startMs + durationMs

    if (splitTimestampMs <= startMs + MIN_SPLIT_MARGIN_MS || splitTimestampMs >= endMs - MIN_SPLIT_MARGIN_MS) {
      Log.w(TAG, "Cannot split VideoClip '${clip.name}': split time $splitTimestampMs ms is too close to clip boundary [$startMs..$endMs]")
      return SplitResult(false, message = "Split location is too close to clip start or end.")
    }

    val headDurationMs = splitTimestampMs - startMs
    val tailDurationMs = durationMs - headDurationMs

    // Calculate exact source media split position considering clip playback speed
    val splitSourcePosMs = clip.timelineToSourceMs(splitTimestampMs)

    // Distribute keyframes between head and tail clips
    val headKeyframes = clip.keyframes.filter { it.timeMs <= headDurationMs }
    val tailKeyframes = clip.keyframes.filter { it.timeMs > headDurationMs }.map {
      it.copy(id = UUID.randomUUID().toString(), timeMs = (it.timeMs - headDurationMs).coerceAtLeast(0L))
    }

    val headClip = clip.copy(
      durationMs = headDurationMs,
      sourceEndMs = splitSourcePosMs,
      keyframes = headKeyframes
    )

    val tailClip = clip.copy(
      id = UUID.randomUUID().toString(),
      name = "${clip.name} (Part 2)",
      timelineStartMs = splitTimestampMs,
      durationMs = tailDurationMs,
      sourceStartMs = splitSourcePosMs,
      sourceEndMs = clip.sourceEndMs,
      keyframes = tailKeyframes
    )

    Log.i(TAG, "Successfully split VideoClip '${clip.name}' at $splitTimestampMs ms -> Head: ${headClip.durationMs}ms, Tail: ${tailClip.durationMs}ms")
    return SplitResult(true, headClip, tailClip, "Split successfully.")
  }

  /**
   * Splits an AudioClip cleanly with subtle crossfading to eliminate audio clicks/pops.
   */
  fun splitAudioClip(
    clip: AudioClip,
    splitTimestampMs: Long
  ): SplitResult<AudioClip> {
    val startMs = clip.timelineStartMs
    val durationMs = clip.durationMs
    val endMs = startMs + durationMs

    if (splitTimestampMs <= startMs + MIN_SPLIT_MARGIN_MS || splitTimestampMs >= endMs - MIN_SPLIT_MARGIN_MS) {
      return SplitResult(false, message = "Split location is too close to audio clip start or end.")
    }

    val headDurationMs = splitTimestampMs - startMs
    val tailDurationMs = durationMs - headDurationMs
    val splitSourcePosMs = clip.sourceStartMs + (headDurationMs * clip.speed).toLong()

    val headFadeOut = if (clip.fadeOutMs > 0L) minOf(clip.fadeOutMs, headDurationMs / 2) else 50L.coerceAtMost(headDurationMs / 2)
    val tailFadeIn = if (clip.fadeInMs > 0L) minOf(clip.fadeInMs, tailDurationMs / 2) else 50L.coerceAtMost(tailDurationMs / 2)

    val headKeyframes = clip.keyframes.filter { it.timeMs <= headDurationMs }
    val tailKeyframes = clip.keyframes.filter { it.timeMs > headDurationMs }.map {
      it.copy(id = UUID.randomUUID().toString(), timeMs = (it.timeMs - headDurationMs).coerceAtLeast(0L))
    }

    val headClip = clip.copy(
      durationMs = headDurationMs,
      sourceEndMs = splitSourcePosMs,
      fadeOutMs = headFadeOut,
      keyframes = headKeyframes
    )

    val tailClip = clip.copy(
      id = UUID.randomUUID().toString(),
      title = "${clip.title} (Part 2)",
      timelineStartMs = splitTimestampMs,
      durationMs = tailDurationMs,
      sourceStartMs = splitSourcePosMs,
      sourceEndMs = clip.sourceEndMs,
      fadeInMs = tailFadeIn,
      keyframes = tailKeyframes
    )

    return SplitResult(true, headClip, tailClip, "Audio split successfully.")
  }

  /**
   * Splits a TextClip at the specified timestamp.
   */
  fun splitTextClip(
    clip: TextClip,
    splitTimestampMs: Long
  ): SplitResult<TextClip> {
    val startMs = clip.timelineStartMs
    val durationMs = clip.durationMs
    val endMs = startMs + durationMs

    if (splitTimestampMs <= startMs + MIN_SPLIT_MARGIN_MS || splitTimestampMs >= endMs - MIN_SPLIT_MARGIN_MS) {
      return SplitResult(false, message = "Split location is too close to text clip boundary.")
    }

    val headDurationMs = splitTimestampMs - startMs
    val tailDurationMs = durationMs - headDurationMs

    val headKeyframes = clip.keyframes.filter { it.timeMs <= headDurationMs }
    val tailKeyframes = clip.keyframes.filter { it.timeMs > headDurationMs }.map {
      it.copy(id = UUID.randomUUID().toString(), timeMs = (it.timeMs - headDurationMs).coerceAtLeast(0L))
    }

    val headClip = clip.copy(
      durationMs = headDurationMs,
      keyframes = headKeyframes
    )

    val tailClip = clip.copy(
      id = UUID.randomUUID().toString(),
      timelineStartMs = splitTimestampMs,
      durationMs = tailDurationMs,
      keyframes = tailKeyframes
    )

    return SplitResult(true, headClip, tailClip, "Text split successfully.")
  }

  /**
   * Splits an EffectClip at the specified timestamp.
   */
  fun splitEffectClip(
    clip: EffectClip,
    splitTimestampMs: Long
  ): SplitResult<EffectClip> {
    val startMs = clip.timelineStartMs
    val durationMs = clip.durationMs
    val endMs = startMs + durationMs

    if (splitTimestampMs <= startMs + MIN_SPLIT_MARGIN_MS || splitTimestampMs >= endMs - MIN_SPLIT_MARGIN_MS) {
      return SplitResult(false, message = "Split location is too close to effect boundary.")
    }

    val headDurationMs = splitTimestampMs - startMs
    val tailDurationMs = durationMs - headDurationMs

    val headClip = clip.copy(durationMs = headDurationMs)
    val tailClip = clip.copy(
      id = UUID.randomUUID().toString(),
      timelineStartMs = splitTimestampMs,
      durationMs = tailDurationMs
    )

    return SplitResult(true, headClip, tailClip, "Effect split successfully.")
  }

  /**
   * Splits a StickerClip at the specified timestamp with keyframe distribution.
   */
  fun splitStickerClip(
    clip: StickerClip,
    splitTimestampMs: Long
  ): SplitResult<StickerClip> {
    val startMs = clip.timelineStartMs
    val durationMs = clip.durationMs
    val endMs = startMs + durationMs

    if (splitTimestampMs <= startMs + MIN_SPLIT_MARGIN_MS || splitTimestampMs >= endMs - MIN_SPLIT_MARGIN_MS) {
      return SplitResult(false, message = "Split location is too close to sticker boundary.")
    }

    val headDurationMs = splitTimestampMs - startMs
    val tailDurationMs = durationMs - headDurationMs

    val headKeyframes = clip.keyframes.filter { it.timeMs <= headDurationMs }
    val tailKeyframes = clip.keyframes.filter { it.timeMs > headDurationMs }.map {
      it.copy(id = UUID.randomUUID().toString(), timeMs = (it.timeMs - headDurationMs).coerceAtLeast(0L))
    }

    val headClip = clip.copy(durationMs = headDurationMs, keyframes = headKeyframes)
    val tailClip = clip.copy(
      id = UUID.randomUUID().toString(),
      timelineStartMs = splitTimestampMs,
      durationMs = tailDurationMs,
      keyframes = tailKeyframes
    )

    return SplitResult(true, headClip, tailClip, "Sticker split successfully.")
  }

  /**
   * Splits a ShapeClip at the specified timestamp with keyframe distribution.
   */
  fun splitShapeClip(
    clip: ShapeClip,
    splitTimestampMs: Long
  ): SplitResult<ShapeClip> {
    val startMs = clip.timelineStartMs
    val durationMs = clip.durationMs
    val endMs = startMs + durationMs

    if (splitTimestampMs <= startMs + MIN_SPLIT_MARGIN_MS || splitTimestampMs >= endMs - MIN_SPLIT_MARGIN_MS) {
      return SplitResult(false, message = "Split location is too close to shape boundary.")
    }

    val headDurationMs = splitTimestampMs - startMs
    val tailDurationMs = durationMs - headDurationMs

    val headKeyframes = clip.keyframes.filter { it.timeMs <= headDurationMs }
    val tailKeyframes = clip.keyframes.filter { it.timeMs > headDurationMs }.map {
      it.copy(id = UUID.randomUUID().toString(), timeMs = (it.timeMs - headDurationMs).coerceAtLeast(0L))
    }

    val headClip = clip.copy(durationMs = headDurationMs, keyframes = headKeyframes)
    val tailClip = clip.copy(
      id = UUID.randomUUID().toString(),
      timelineStartMs = splitTimestampMs,
      durationMs = tailDurationMs,
      keyframes = tailKeyframes
    )

    return SplitResult(true, headClip, tailClip, "Shape split successfully.")
  }

  /**
   * High-level split dispatcher that performs a split on the current timeline via TimelineEngine.
   */
  fun performSplitAtPlayhead(
    timelineEngine: TimelineEngine,
    playheadMs: Long,
    selectedElement: SelectedTrackElement = SelectedTrackElement.None
  ): Boolean {
    val currentTimeline = timelineEngine.timeline.value
    val (targetElement, clipId) = findTargetClipAtTimestamp(currentTimeline, playheadMs, selectedElement)

    if (clipId == null) {
      Log.w(TAG, "No clip found under playhead at $playheadMs ms to split.")
      return false
    }

    val result = timelineEngine.splitClipAtTime(clipId, playheadMs)
    return result != null
  }
}
