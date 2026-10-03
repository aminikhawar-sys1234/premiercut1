package com.example

import com.example.domain.model.AudioClip
import com.example.domain.model.Timeline
import com.example.domain.model.TextClip
import com.example.domain.model.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedTimelineInteractionTest {

  @Test
  fun masterTimelineDurationIncludesAllIndependentTracks() {
    val timeline = Timeline(
      videoClips = listOf(VideoClip(id = "v1", name = "Video", durationMs = 5_000)),
      overlayClips = listOf(
        VideoClip(id = "o1", name = "Overlay 1", timelineStartMs = 1_000, durationMs = 2_000, trackIndex = 1),
        VideoClip(id = "o2", name = "Overlay 2", timelineStartMs = 1_000, durationMs = 4_000, trackIndex = 2)
      ),
      textClips = listOf(
        TextClip(id = "t1", text = "SALE", timelineStartMs = 1_000, durationMs = 4_000, trackIndex = 1),
        TextClip(id = "t2", text = "50% OFF", timelineStartMs = 1_000, durationMs = 4_000, trackIndex = 2),
        TextClip(id = "t3", text = "LIMITED TIME", timelineStartMs = 1_000, durationMs = 4_000, trackIndex = 3)
      ),
      audioClips = listOf(
        AudioClip(id = "a1", uri = "internal://a1", title = "A1", timelineStartMs = 0, durationMs = 3_000, trackIndex = 1),
        AudioClip(id = "a2", uri = "internal://a2", title = "A2", timelineStartMs = 2_000, durationMs = 5_000, trackIndex = 2)
      )
    )

    assertEquals(7_000L, timeline.totalDurationMs)
    assertEquals(listOf(1, 2), timeline.overlayClips.map { it.trackIndex })
    assertEquals(listOf(1, 2, 3), timeline.textClips.map { it.trackIndex })
    assertEquals(listOf(1, 2), timeline.audioClips.map { it.trackIndex })
  }

  @Test
  fun sameTimeTextItemsRemainIndependent() {
    val clips = listOf(
      TextClip(id = "1", text = "SALE", timelineStartMs = 5_000, trackIndex = 1),
      TextClip(id = "2", text = "50% OFF", timelineStartMs = 5_000, trackIndex = 2),
      TextClip(id = "3", text = "LIMITED TIME", timelineStartMs = 5_000, trackIndex = 3)
    )

    assertEquals(3, clips.distinctBy { it.id }.size)
    assertEquals(setOf(1, 2, 3), clips.map { it.trackIndex }.toSet())
    assertTrue(clips.all { it.timelineStartMs == 5_000L })
  }

  @Test
  fun clipSeekMappingIsDeterministic() {
    val clip = VideoClip(
      id = "v",
      name = "Video",
      timelineStartMs = 5_000,
      durationMs = 9_000,
      sourceStartMs = 2_000,
      sourceEndMs = 11_000
    )

    assertEquals(2_000L, clip.timelineToSourceMs(5_000))
    assertEquals(5_000L, clip.timelineToSourceMs(8_000))
    assertEquals(11_000L, clip.timelineToSourceMs(14_000))
  }
}
