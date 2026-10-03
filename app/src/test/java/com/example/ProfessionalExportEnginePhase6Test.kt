package com.example

import com.example.domain.model.FrameRate
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.export.ExportConfig
import com.example.engine.export.ExportRenderPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalExportEnginePhase6Test {
  private fun timeline(durationMs: Long) = Timeline(
    videoClips = listOf(VideoClip(name = "test", timelineStartMs = 0L, durationMs = durationMs, sourceEndMs = durationMs))
  )

  @Test
  fun `planner produces monotonic frame timestamps`() {
    val plan = ExportRenderPlanner.build(timeline(2000L), ExportConfig(frameRate = FrameRate.FPS_30))
    val frames = plan.frames.toList()
    assertEquals(60L, plan.totalFrames)
    assertEquals(60, frames.size)
    assertEquals(0L, frames.first().presentationTimeUs)
    assertTrue(frames.zipWithNext().all { (a, b) -> b.presentationTimeUs > a.presentationTimeUs })
    assertTrue(frames.all { it.timelinePositionMs in 0L..1999L })
  }

  @Test
  fun `planner uses requested frame rate`() {
    val plan = ExportRenderPlanner.build(timeline(1000L), ExportConfig(frameRate = FrameRate.FPS_60))
    val frames = plan.frames.toList()
    assertEquals(60L, plan.totalFrames)
    assertEquals(60, frames.size)
    assertEquals(16_666L, frames[1].presentationTimeUs)
  }

  @Test
  fun `planner produces zero frames for empty timeline`() {
    val plan = ExportRenderPlanner.build(Timeline(), ExportConfig())
    assertEquals(0L, plan.totalFrames)
    assertTrue(plan.frames.none())
  }
}
