package com.example.engine.integration

import com.example.domain.model.Timeline

/**
 * Single authoritative NLE timeline contract for AH Studio.
 *
 * TimelineEngine is the runtime owner. This contract deliberately exposes a
 * snapshot rather than a second mutable model, so playback, UI, rendering and
 * export can share the same timeline state.
 */
interface UnifiedAdvancedTimeline {
  val timelineSnapshot: Timeline
  val playheadMs: Long
  val fps: Int

  fun totalDurationMs(): Long = timelineSnapshot.totalDurationMs

  fun timeToFrame(timeMs: Long): Long =
    Math.round(timeMs.toDouble() * fps.toDouble() / 1000.0).coerceAtLeast(0L)

  fun frameToTime(frameIndex: Long): Long =
    Math.round(frameIndex.toDouble() * 1000.0 / fps.toDouble()).coerceAtLeast(0L)

  fun snapPoints(): List<Long> {
    val points = buildList {
      add(0L)
      timelineSnapshot.videoClips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.durationMs) }
      timelineSnapshot.overlayClips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.durationMs) }
      timelineSnapshot.audioClips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.durationMs) }
      timelineSnapshot.textClips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.durationMs) }
      timelineSnapshot.stickerClips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.durationMs) }
      timelineSnapshot.effectClips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.durationMs) }
      add(timelineSnapshot.totalDurationMs)
    }
    return points.distinct().sorted()
  }
}

/**
 * Read-only integration adapter. It owns no timeline state and no clock.
 */
class UnifiedAdvancedTimelineAdapter(
  override val timelineSnapshot: Timeline,
  override val playheadMs: Long,
  override val fps: Int
) : UnifiedAdvancedTimeline
