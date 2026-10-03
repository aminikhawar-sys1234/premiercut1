package com.example.engine.integration

import com.example.domain.model.Timeline

/**
 * Testable projection of Ah Studio's multi-layer composition order.
 * Rendering remains owned by Ah Studio's existing compositor.
 */
object MultiLayerCompositor {
  fun activeLayerIds(timeline: Timeline, timeMs: Long): List<String> = buildList {
    timeline.videoClips
      .filter { timeMs in it.timelineStartMs until (it.timelineStartMs + it.durationMs) && !it.isHidden && it.opacity > 0f }
      .forEach { add(it.id) }
    timeline.overlayClips
      .filter { timeMs in it.timelineStartMs until (it.timelineStartMs + it.durationMs) && !it.isHidden && it.opacity > 0f }
      .forEach { add(it.id) }
    timeline.stickerClips
      .filter { timeMs in it.timelineStartMs until (it.timelineStartMs + it.durationMs) && !it.isHidden && it.opacity > 0f }
      .forEach { add(it.id) }
    timeline.textClips
      .filter { timeMs in it.timelineStartMs until (it.timelineStartMs + it.durationMs) && !it.isHidden && it.opacity > 0f }
      .forEach { add(it.id) }
  }
}
