package com.example

import com.example.domain.model.ClipKeyframe
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.integration.AdvancedTimelineIndex
import com.example.engine.integration.CompoundTimelineTimeMapper
import com.example.engine.integration.KeyframeAnimationEngine
import com.example.engine.integration.MultiLayerCompositor
import com.example.engine.integration.TimelineCommandSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutocutSystemsIntegrationTest {
  private fun timeline() = Timeline(
    videoClips = listOf(
      VideoClip(id = "v1", name = "one", durationMs = 2_000, timelineStartMs = 0),
      VideoClip(id = "v2", name = "two", durationMs = 2_000, timelineStartMs = 1_000)
    ),
    overlayClips = listOf(
      VideoClip(id = "o1", name = "overlay", durationMs = 1_000, timelineStartMs = 1_000)
    )
  )

  @Test
  fun intervalTreeTrackIndexAndSnapIndexQuery() {
    val index = AdvancedTimelineIndex.build(timeline())
    assertEquals("v1", index.findClip("v1")?.id)
    assertEquals(setOf("v1", "v2", "o1"), index.getClipsAt(1_500).map { it.id }.toSet())
    assertEquals(1, index.findOverlaps("video").size)
    assertEquals(1_000L, index.snapIndex.findClosest(995, 10))
    assertNull(index.snapIndex.findClosest(950, 10))
  }

  @Test
  fun keyframeEngineInterpolatesAllCoreTransformProperties() {
    val frames = listOf(
      ClipKeyframe(timeMs = 0, scale = 1f, posX = 0f, opacity = 1f),
      ClipKeyframe(timeMs = 1_000, scale = 2f, posX = 10f, rotation = 90f, opacity = .5f)
    )
    val value = KeyframeAnimationEngine.evaluate(frames, 500)
    assertEquals(1.5f, value.scaleX, .001f)
    assertEquals(5f, value.positionX, .001f)
    assertEquals(45f, value.rotation, .001f)
    assertEquals(.75f, value.opacity, .001f)
  }

  @Test
  fun compoundMapperSupportsBidirectionalSpeedMapping() {
    val mapper = CompoundTimelineTimeMapper(100, 1_000, 0, 2_000, speed = 2.0)
    assertEquals(1_000L, mapper.parentToSource(600))
    assertEquals(600L, mapper.sourceToParent(1_000))
    assertNull(mapper.parentToSource(1_100))
  }

  @Test
  fun commandSystemUndoRedoIsNonDestructive() {
    val before = timeline()
    val after = before.copy(videoClips = before.videoClips.drop(1))
    val commands = TimelineCommandSystem()
    commands.execute("remove", before, after)
    assertEquals(before, commands.undo())
    assertEquals(after, commands.redo())
  }

  @Test
  fun multiLayerProjectionPreservesLayerOrderAndVisibility() {
    val before = timeline()
    assertEquals(listOf("v1", "v2", "o1"), MultiLayerCompositor.activeLayerIds(before, 1_500))
  }
}
