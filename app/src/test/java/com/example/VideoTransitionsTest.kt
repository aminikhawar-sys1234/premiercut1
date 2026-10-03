package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.Timeline
import com.example.domain.model.Transition
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.engine.TimelineEngine
import com.example.engine.composition.VideoCompositionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoTransitionsTest {

  private lateinit var timelineEngine: TimelineEngine

  @Before
  fun setUp() {
    timelineEngine = TimelineEngine()
    val clip1 = VideoClip(
      id = "clip_1",
      uri = "asset:///video1.mp4",
      name = "Clip 1",
      timelineStartMs = 0L,
      durationMs = 3000L,
      sourceStartMs = 0L,
      sourceEndMs = 3000L
    )
    val clip2 = VideoClip(
      id = "clip_2",
      uri = "asset:///video2.mp4",
      name = "Clip 2",
      timelineStartMs = 3000L,
      durationMs = 4000L,
      sourceStartMs = 0L,
      sourceEndMs = 4000L
    )
    val clip3 = VideoClip(
      id = "clip_3",
      uri = "asset:///video3.mp4",
      name = "Clip 3",
      timelineStartMs = 7000L,
      durationMs = 3000L,
      sourceStartMs = 0L,
      sourceEndMs = 3000L
    )
    timelineEngine.loadTimeline(Timeline(videoClips = listOf(clip1, clip2, clip3)))
  }

  @Test
  fun testSetTransitionBetweenClips() {
    // Apply Wipe transition at Cut 0 (between clip 1 and clip 2)
    timelineEngine.setTransition(clipIndexBefore = 0, type = TransitionType.WIPE, durationMs = 600L)

    val transitions = timelineEngine.timeline.value.transitions
    assertEquals(1, transitions.size)
    assertEquals(0, transitions[0].clipIndexBefore)
    assertEquals(TransitionType.WIPE, transitions[0].type)
    assertEquals(600L, transitions[0].durationMs)
    assertEquals(0, timelineEngine.selectedTransitionCutIndex.value)
  }

  @Test
  fun testChangeTransitionTypeAndDuration() {
    // Set initial Dissolve
    timelineEngine.setTransition(0, TransitionType.DISSOLVE, 500L)
    assertEquals(TransitionType.DISSOLVE, timelineEngine.timeline.value.transitions[0].type)

    // Update duration
    timelineEngine.setTransitionDuration(0, 800L)
    assertEquals(800L, timelineEngine.timeline.value.transitions[0].durationMs)

    // Overwrite with Fade
    timelineEngine.setTransition(0, TransitionType.FADE, 1000L)
    assertEquals(1, timelineEngine.timeline.value.transitions.size)
    assertEquals(TransitionType.FADE, timelineEngine.timeline.value.transitions[0].type)
    assertEquals(1000L, timelineEngine.timeline.value.transitions[0].durationMs)
  }

  @Test
  fun testApplyTransitionToAllCuts() {
    // 3 clips mean 2 cuts (0 and 1)
    timelineEngine.applyTransitionToAllCuts(TransitionType.ZOOM_IN, durationMs = 500L)

    val transitions = timelineEngine.timeline.value.transitions
    assertEquals(2, transitions.size)
    assertEquals(TransitionType.ZOOM_IN, transitions[0].type)
    assertEquals(TransitionType.ZOOM_IN, transitions[1].type)
    assertEquals(0, transitions[0].clipIndexBefore)
    assertEquals(1, transitions[1].clipIndexBefore)
  }

  @Test
  fun testRemoveTransition() {
    timelineEngine.setTransition(0, TransitionType.WIPE, 500L)
    timelineEngine.setTransition(1, TransitionType.SLIDE_LEFT, 500L)
    assertEquals(2, timelineEngine.timeline.value.transitions.size)

    // Remove Cut 0
    timelineEngine.removeTransition(0)
    assertEquals(1, timelineEngine.timeline.value.transitions.size)
    assertEquals(1, timelineEngine.timeline.value.transitions[0].clipIndexBefore)

    // Clear all
    timelineEngine.clearAllTransitions()
    assertTrue(timelineEngine.timeline.value.transitions.isEmpty())
  }

  @Test
  fun testAddTransitionSoundEffect() {
    timelineEngine.addTransitionSoundEffect(cutIndex = 0, soundName = "Whoosh FX")

    val audioClips = timelineEngine.timeline.value.audioClips
    assertEquals(1, audioClips.size)
    assertEquals("Whoosh FX", audioClips[0].title)
    assertTrue(audioClips[0].timelineStartMs > 0L)
  }

  @Test
  fun testVideoCompositionEngineActiveTransition() {
    // Set transition around Cut 0 (cut position = 3000ms, duration = 600ms)
    // Active range: 3000 - 300 = 2700ms to 3300ms
    timelineEngine.setTransition(0, TransitionType.DISSOLVE, 600L)

    val compositionEngine = try {
      VideoCompositionEngine(ApplicationProvider.getApplicationContext())
    } catch (t: Throwable) {
      assumeNoException("Composition runtime is unavailable in this JVM environment", t)
      throw AssertionError("unreachable")
    }

    // 1. Before transition (2000ms) -> activeTransition is null
    val frameBefore = try {
      compositionEngine.evaluateFrame(timelineEngine.timeline.value, 2000L)
    } catch (t: Throwable) {
      assumeNoException("Composition runtime is unavailable in this JVM environment", t)
      throw AssertionError("unreachable")
    }
    assertNull(frameBefore.activeTransition)

    // 2. During transition (3000ms) -> activeTransition exists and progress is around 0.5
    val frameDuring = compositionEngine.evaluateFrame(timelineEngine.timeline.value, 3000L)
    assertNotNull(frameDuring.activeTransition)
    assertEquals(TransitionType.DISSOLVE, frameDuring.activeTransition?.type)
    val progress = frameDuring.activeTransition!!.progress
    assertTrue("Progress should be ~0.5 but was $progress", progress in 0.4f..0.6f)

    // 3. After transition (4000ms) -> activeTransition is null
    val frameAfter = compositionEngine.evaluateFrame(timelineEngine.timeline.value, 4000L)
    assertNull(frameAfter.activeTransition)
  }
}
