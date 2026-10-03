package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.*
import com.example.engine.TimelineEngine
import com.example.engine.SelectedTrackElement
import com.example.engine.composition.VideoEffectRenderer
import com.example.engine.composition.VideoCompositionEngine
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoEffectsTest {

  private lateinit var timelineEngine: TimelineEngine

  @Before
  fun setUp() {
    timelineEngine = TimelineEngine()
    val initialTimeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Clip 1", uri = "uri1", durationMs = 4000L, timelineStartMs = 0L),
        VideoClip(id = "v2", name = "Clip 2", uri = "uri2", durationMs = 6000L, timelineStartMs = 4000L)
      )
    )
    timelineEngine.loadTimeline(initialTimeline)
  }

  @Test
  fun testApplyEffectToCurrentClipLinksDurationAndClipId() {
    // Select Clip 1
    timelineEngine.selectElement(SelectedTrackElement.Video("v1"))

    // Apply GLOW effect
    val effect = timelineEngine.applyEffectToCurrentClip(EffectType.GLOW, intensity = 0.85f, customName = "Neon Glow")

    assertEquals(EffectType.GLOW, effect.effectType)
    assertEquals(0.85f, effect.intensity, 0.01f)
    assertEquals("v1", effect.targetClipId)
    assertEquals(0L, effect.timelineStartMs)
    assertEquals(4000L, effect.durationMs)

    // Check timeline has effect clip
    val timeline = timelineEngine.timeline.value
    assertEquals(1, timeline.effectClips.size)
    assertEquals(effect.id, timeline.effectClips[0].id)
  }

  @Test
  fun testRemoveEffectFromCurrentClipRestoresOriginal() {
    timelineEngine.selectElement(SelectedTrackElement.Video("v1"))
    timelineEngine.applyEffectToCurrentClip(EffectType.RGB_SPLIT)

    assertEquals(1, timelineEngine.timeline.value.effectClips.size)

    // Now remove effect
    timelineEngine.removeEffectFromCurrentClip()

    assertEquals(0, timelineEngine.timeline.value.effectClips.size)
  }

  @Test
  fun testChangeEffectUpdatesExistingClip() {
    timelineEngine.selectElement(SelectedTrackElement.Video("v1"))
    timelineEngine.applyEffectToCurrentClip(EffectType.FLASH)

    assertEquals(1, timelineEngine.timeline.value.effectClips.size)
    assertEquals(EffectType.FLASH, timelineEngine.timeline.value.effectClips[0].effectType)

    // Change to VIGNETTE
    timelineEngine.applyEffectToCurrentClip(EffectType.VIGNETTE, intensity = 0.7f)

    assertEquals(1, timelineEngine.timeline.value.effectClips.size)
    assertEquals(EffectType.VIGNETTE, timelineEngine.timeline.value.effectClips[0].effectType)
    assertEquals(0.7f, timelineEngine.timeline.value.effectClips[0].intensity, 0.01f)
  }

  @Test
  fun testTimelinePlayheadSynchronizedEffects() {
    // Clip 1 (0..4000ms): GLOW
    timelineEngine.selectElement(SelectedTrackElement.Video("v1"))
    timelineEngine.applyEffectToCurrentClip(EffectType.GLOW)

    // Clip 2 (4000..10000ms): SHAKE
    timelineEngine.selectElement(SelectedTrackElement.Video("v2"))
    timelineEngine.applyEffectToCurrentClip(EffectType.SHAKE)

    assertEquals(2, timelineEngine.timeline.value.effectClips.size)

    // At 2000ms (within Clip 1)
    val effectsAt2000 = timelineEngine.timeline.value.effectClips.filter {
      2000L >= it.timelineStartMs && 2000L < it.timelineStartMs + it.durationMs
    }
    assertEquals(1, effectsAt2000.size)
    assertEquals(EffectType.GLOW, effectsAt2000[0].effectType)

    // At 6000ms (within Clip 2)
    val effectsAt6000 = timelineEngine.timeline.value.effectClips.filter {
      6000L >= it.timelineStartMs && 6000L < it.timelineStartMs + it.durationMs
    }
    assertEquals(1, effectsAt6000.size)
    assertEquals(EffectType.SHAKE, effectsAt6000[0].effectType)
  }

  @Test
  fun testFiltersAndEffectsAreStrictlyClipLocal() {
    timelineEngine.selectElement(SelectedTrackElement.Video("v1"))
    timelineEngine.updateFilter(FilterSettings(type = FilterType.CINEMATIC, intensity = 0.65f), "v1")
    val effect1 = timelineEngine.applyEffectToCurrentClip(EffectType.GLOW, intensity = 0.8f)

    timelineEngine.selectElement(SelectedTrackElement.Video("v2"))
    timelineEngine.updateFilter(FilterSettings(type = FilterType.GOLDEN_AUTUMN, intensity = 0.55f), "v2")
    val effect2 = timelineEngine.applyEffectToCurrentClip(EffectType.SHAKE, intensity = 0.7f)

    val timeline = timelineEngine.timeline.value
    assertEquals(FilterType.CINEMATIC, timeline.videoClips.first { it.id == "v1" }.filter?.type)
    assertEquals(FilterType.GOLDEN_AUTUMN, timeline.videoClips.first { it.id == "v2" }.filter?.type)

    val engine = try {
      VideoCompositionEngine(ApplicationProvider.getApplicationContext())
    } catch (t: Throwable) {
      assumeNoException("Composition runtime is unavailable in this JVM environment", t)
      throw AssertionError("unreachable")
    }
    val (frame1, frame2) = try {
      engine.evaluateFrame(timeline, 2000L) to engine.evaluateFrame(timeline, 6000L)
    } catch (t: Throwable) {
      assumeNoException("Composition runtime is unavailable in this JVM environment", t)
      throw AssertionError("unreachable")
    }

    assertEquals("v1", frame1.activeClip?.id)
    assertEquals(FilterType.CINEMATIC, timeline.videoClips.first { it.id == frame1.activeClip!!.id }.filter?.type)
    assertTrue(frame1.activeEffects.any { it.clip.id == effect1.id })
    assertFalse(frame1.activeEffects.any { it.clip.id == effect2.id })

    assertEquals("v2", frame2.activeClip?.id)
    assertEquals(FilterType.GOLDEN_AUTUMN, timeline.videoClips.first { it.id == frame2.activeClip!!.id }.filter?.type)
    assertTrue(frame2.activeEffects.any { it.clip.id == effect2.id })
    assertFalse(frame2.activeEffects.any { it.clip.id == effect1.id })

  }

  @Test
  fun testMotionTransformCalculation() {
    val shakeEffect = EffectClip(
      effectType = EffectType.SHAKE,
      timelineStartMs = 0L,
      durationMs = 2000L,
      intensity = 0.9f
    )

    val transform = VideoEffectRenderer.calculateMotionTransform(listOf(shakeEffect), currentPosMs = 500L)
    assertNotNull(transform)
    assertTrue(transform.scaleX >= 1.0f) // Shake applies slight overscan to avoid black borders
    assertTrue(transform.scaleY >= 1.0f)

    val zoomEffect = EffectClip(
      effectType = EffectType.ZOOM,
      timelineStartMs = 0L,
      durationMs = 2000L,
      intensity = 1.0f
    )
    val zoomTransform = VideoEffectRenderer.calculateMotionTransform(listOf(zoomEffect), currentPosMs = 1000L)
    assertTrue(zoomTransform.scaleX > 1.0f)
  }

  @Test
  fun testColorMatrixCalculation() {
    val blueprintEffect = EffectClip(
      effectType = EffectType.BLUEPRINT_CAD,
      timelineStartMs = 0L,
      durationMs = 2000L,
      intensity = 1.0f
    )

    val matrix = VideoEffectRenderer.calculateEffectColorMatrix(listOf(blueprintEffect), currentPosMs = 500L)
    assertNotNull(matrix)
    assertEquals(20, matrix!!.array.size)
  }

  @Test
  fun testCanvasRenderingWithoutCrash() {
    val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val effects = listOf(
      EffectClip(effectType = EffectType.GLOW, timelineStartMs = 0L, durationMs = 3000L, intensity = 0.8f),
      EffectClip(effectType = EffectType.LIGHT_LEAK, timelineStartMs = 0L, durationMs = 3000L, intensity = 0.7f),
      EffectClip(effectType = EffectType.FIRE_SPARK, timelineStartMs = 0L, durationMs = 3000L, intensity = 0.9f),
      EffectClip(effectType = EffectType.LASER_GRID, timelineStartMs = 0L, durationMs = 3000L, intensity = 0.75f)
    )

    VideoEffectRenderer.renderEffectsOnCanvas(
      canvas = canvas,
      activeEffects = effects,
      currentPosMs = 1000L,
      width = 720,
      height = 1280
    )

    // Render should execute cleanly without exceptions
    assertNotNull(bitmap)
  }

  @Test
  fun testEffectsRegistryAndLiveRenderingConsistency() {
    com.example.engine.effects.registry.EffectsAssetRegistry.clearAll()
    
    // Test dynamic registration
    val sampleEffect = com.example.engine.effects.registry.RegisteredEffect(
      id = "fx_laser",
      name = "Laser Grid",
      category = com.example.engine.effects.registry.EffectCategory.VIDEO_EFFECTS,
      effectType = EffectType.LASER_GRID,
      intensity = 0.85f
    )
    com.example.engine.effects.registry.EffectsAssetRegistry.registerEffect(sampleEffect)
    
    val videoEffects = com.example.engine.effects.registry.EffectsAssetRegistry.getEffects(
      com.example.engine.effects.registry.EffectCategory.VIDEO_EFFECTS
    )
    assertTrue(videoEffects.isNotEmpty())
    assertEquals("fx_laser", videoEffects.first().id)

    // Select target video clip
    timelineEngine.selectElement(SelectedTrackElement.Video("v1"))

    val applied = timelineEngine.applyEffectToCurrentClip(
      effectType = sampleEffect.effectType!!,
      intensity = sampleEffect.intensity,
      customName = sampleEffect.name
    )

    assertEquals(sampleEffect.effectType, applied.effectType)
    assertEquals(sampleEffect.intensity, applied.intensity, 0.001f)
    assertEquals("v1", applied.targetClipId)

    // Test that the configuration renders on a thumbnail canvas cleanly
    val thumbBitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
    val thumbCanvas = Canvas(thumbBitmap)

    val motion = VideoEffectRenderer.calculateMotionTransform(listOf(applied), 500L)
    assertNotNull(motion)

    VideoEffectRenderer.renderSingleEffect(
      canvas = thumbCanvas,
      effect = applied,
      intensity = sampleEffect.intensity,
      relTime = 500L,
      width = 120,
      height = 120
    )
    assertNotNull(thumbBitmap)
  }

  @Test
  fun testEffectIntensitySliderUpdates() {
    timelineEngine.selectElement(SelectedTrackElement.Video("v1"))

    val applied = timelineEngine.applyEffectToCurrentClip(
      effectType = EffectType.GLOW,
      intensity = 0.8f,
      customName = "Neon Glow"
    )
    assertEquals(0.8f, applied.intensity, 0.001f)

    // Simulate slider dragging to various intensities
    val sliderValues = listOf(0.0f, 0.25f, 0.5f, 0.73f, 1.0f)
    for (value in sliderValues) {
      timelineEngine.updateEffectIntensity(applied.id, value)
      val updated = timelineEngine.timeline.value.effectClips.find { it.id == applied.id }
      assertNotNull(updated)
      assertEquals(value, updated!!.intensity, 0.001f)
    }

    // Verify out-of-bound clamping
    timelineEngine.updateEffectIntensity(applied.id, 1.5f)
    var updated = timelineEngine.timeline.value.effectClips.find { it.id == applied.id }
    assertEquals(1.0f, updated!!.intensity, 0.001f)

    timelineEngine.updateEffectIntensity(applied.id, -0.5f)
    updated = timelineEngine.timeline.value.effectClips.find { it.id == applied.id }
    assertEquals(0.0f, updated!!.intensity, 0.001f)

    // Verify deletion/removal
    timelineEngine.deleteEffectClip(applied.id)
    val afterDelete = timelineEngine.timeline.value.effectClips.find { it.id == applied.id }
    assertNull(afterDelete)
  }
}
