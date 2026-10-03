package com.example

import com.example.domain.model.AudioClip
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.integration.UnifiedAdvancedTimelineAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedAdvancedTimelineTest {
  @Test
  fun exposes_one_timeline_and_one_master_playhead() {
    val video = VideoClip(name = "v", timelineStartMs = 0L, durationMs = 1000L)
    val audio = AudioClip(uri = "a", title = "a", timelineStartMs = 1000L, durationMs = 500L)
    val timeline = Timeline(videoClips = listOf(video), audioClips = listOf(audio))
    val unified = UnifiedAdvancedTimelineAdapter(timeline, playheadMs = 1000L, fps = 30)

    assertEquals(timeline, unified.timelineSnapshot)
    assertEquals(1000L, unified.playheadMs)
    assertEquals(listOf(0L, 1000L, 1500L), unified.snapPoints())
    assertEquals(30L, unified.timeToFrame(1000L))
    assertEquals(1000L, unified.frameToTime(30L))
    assertTrue(unified.totalDurationMs() >= 1500L)
  }
}
