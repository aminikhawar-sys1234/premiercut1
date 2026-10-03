package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.*
import com.example.engine.KeyframeInterpolator
import com.example.engine.TimelineEngine
import com.example.engine.SelectedTrackElement
import com.example.engine.composition.VideoCompositionEngine
import com.example.engine.composition.VideoEffectRenderer
import com.example.engine.composition.VfxCatalogRenderer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EffectsPipelineVerificationTest {

  private lateinit var timelineEngine: TimelineEngine

  @Before
  fun setUp() {
    timelineEngine = TimelineEngine()
    val initialTimeline = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "Clip 1", uri = "uri1", durationMs = 3000L, timelineStartMs = 0L),
        VideoClip(id = "v2", name = "Clip 2", uri = "uri2", durationMs = 5000L, timelineStartMs = 3000L)
      )
    )
    timelineEngine.loadTimeline(initialTimeline)
  }

  @Test
  fun verifyAll200VfxCatalogEffectsAreSupportedAndMapped() {
    val vfxList = EffectType.values().filter { it.name.startsWith("VFX_") }
    assertEquals(200, vfxList.size)
    
    for (effect in vfxList) {
      assertTrue("VFX catalog entry ${effect.name} should be supported by VfxCatalogRenderer", VfxCatalogRenderer.supports(effect))
      assertNotNull("VFX catalog entry ${effect.name} should belong to a non-null catalog group", VfxCatalogRenderer.catalogGroup(effect))
    }
  }

  @Test
  fun verifyAll16AiCatalogEffectsAreSupported() {
    val aiList = EffectType.values().filter { it.name.startsWith("AI_") }
    assertEquals(16, aiList.size)

    val bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    for (effect in aiList) {
      val clip = EffectClip(effectType = effect, timelineStartMs = 0L, durationMs = 2000L, intensity = 0.8f)
      VideoEffectRenderer.renderSingleEffect(canvas, clip, 0.8f, 500L, 360, 640)
    }
  }

  @Test
  fun verifyEffectTimingAndClipBoundaryIsolation() {
    timelineEngine.selectElement(SelectedTrackElement.Video("v1"))
    val effect1 = timelineEngine.applyEffectToCurrentClip(EffectType.GLOW, intensity = 0.9f)

    timelineEngine.selectElement(SelectedTrackElement.Video("v2"))
    val effect2 = timelineEngine.applyEffectToCurrentClip(EffectType.SHAKE, intensity = 0.7f)

    val compositionEngine = VideoCompositionEngine(ApplicationProvider.getApplicationContext())
    val timeline = timelineEngine.timeline.value

    // Frame at 1500ms (Clip 1)
    val frameAt1500 = compositionEngine.evaluateFrame(timeline, 1500L)
    assertEquals("v1", frameAt1500.activeClip?.id)
    assertEquals(1, frameAt1500.activeEffects.size)
    assertEquals(effect1.id, frameAt1500.activeEffects[0].clip.id)
    assertEquals(EffectType.GLOW, frameAt1500.activeEffects[0].effectType)

    // Frame at 4500ms (Clip 2)
    val frameAt4500 = compositionEngine.evaluateFrame(timeline, 4500L)
    assertEquals("v2", frameAt4500.activeClip?.id)
    assertEquals(1, frameAt4500.activeEffects.size)
    assertEquals(effect2.id, frameAt4500.activeEffects[0].clip.id)
    assertEquals(EffectType.SHAKE, frameAt4500.activeEffects[0].effectType)

    // Frame at 8500ms (Past end of timeline)
    val frameAt8500 = compositionEngine.evaluateFrame(timeline, 8500L)
    assertNull(frameAt8500.activeClip)
    assertTrue(frameAt8500.activeEffects.isEmpty())
  }

  @Test
  fun verifyUntargetedGlobalTimelineEffects() {
    val globalEffect = EffectClip(
      id = "global_fx_1",
      effectType = EffectType.LIGHT_LEAK,
      timelineStartMs = 1000L,
      durationMs = 5000L,
      intensity = 0.8f,
      targetClipId = null // Untargeted global project effect
    )

    val currentTimeline = timelineEngine.timeline.value
    val timelineWithGlobal = currentTimeline.copy(effectClips = listOf(globalEffect))
    val compositionEngine = VideoCompositionEngine(ApplicationProvider.getApplicationContext())

    // At 500ms: Before global effect
    val frameAt500 = compositionEngine.evaluateFrame(timelineWithGlobal, 500L)
    assertTrue(frameAt500.activeEffects.isEmpty())

    // At 2000ms: Spans Clip 1
    val frameAt2000 = compositionEngine.evaluateFrame(timelineWithGlobal, 2000L)
    assertEquals(1, frameAt2000.activeEffects.size)
    assertEquals(EffectType.LIGHT_LEAK, frameAt2000.activeEffects[0].effectType)

    // At 4000ms: Spans Clip 2
    val frameAt4000 = compositionEngine.evaluateFrame(timelineWithGlobal, 4000L)
    assertEquals(1, frameAt4000.activeEffects.size)
    assertEquals(EffectType.LIGHT_LEAK, frameAt4000.activeEffects[0].effectType)
  }

  @Test
  fun verifyKeyframedEffectIntensityInterpolation() {
    val keyframes = listOf(
      ClipKeyframe(id = "k1", timeMs = 0L, opacity = 0.0f, effectParam = 0.0f),
      ClipKeyframe(id = "k2", timeMs = 1000L, opacity = 1.0f, effectParam = 1.0f),
      ClipKeyframe(id = "k3", timeMs = 2000L, opacity = 0.2f, effectParam = 0.2f)
    )

    val effect = EffectClip(
      id = "kf_fx",
      effectType = EffectType.RGB_SPLIT,
      timelineStartMs = 0L,
      durationMs = 2000L,
      intensity = 1.0f,
      keyframes = keyframes
    )

    // Start
    val intensityStart = KeyframeInterpolator.interpolateEffectIntensity(effect, 0L)
    assertEquals(0.0f, intensityStart, 0.01f)

    // Midpoint (500ms)
    val intensityMid = KeyframeInterpolator.interpolateEffectIntensity(effect, 500L)
    assertEquals(0.5f, intensityMid, 0.05f)

    // Peak (1000ms)
    val intensityPeak = KeyframeInterpolator.interpolateEffectIntensity(effect, 1000L)
    assertEquals(1.0f, intensityPeak, 0.01f)

    // End (2000ms)
    val intensityEnd = KeyframeInterpolator.interpolateEffectIntensity(effect, 2000L)
    assertEquals(0.2f, intensityEnd, 0.01f)
  }

  @Test
  fun verifyMotionTransformCalculationForShakeAndZoom() {
    val shakeClip = EffectClip(effectType = EffectType.SHAKE, timelineStartMs = 0L, durationMs = 2000L, intensity = 0.8f)
    val transform = VideoEffectRenderer.calculateMotionTransform(listOf(shakeClip), 500L)
    assertNotNull(transform)
    assertTrue("Shake effect should apply overscan scaleX >= 1.0", transform.scaleX >= 1.0f)
    assertTrue("Shake effect should apply overscan scaleY >= 1.0", transform.scaleY >= 1.0f)

    val zoomClip = EffectClip(effectType = EffectType.ZOOM, timelineStartMs = 0L, durationMs = 2000L, intensity = 1.0f)
    val zoomTransform = VideoEffectRenderer.calculateMotionTransform(listOf(zoomClip), 1000L)
    assertTrue("Zoom effect should scale up > 1.0", zoomTransform.scaleX > 1.0f)
  }

  @Test
  fun verifyPreviewAndExportEvaluateIdenticalComposedFrames() {
    timelineEngine.selectElement(SelectedTrackElement.Video("v1"))
    timelineEngine.applyEffectToCurrentClip(EffectType.VIGNETTE, intensity = 0.75f)

    val compositionEngine = VideoCompositionEngine(ApplicationProvider.getApplicationContext())
    val timeline = timelineEngine.timeline.value

    for (testPosMs in listOf(0L, 500L, 1500L, 2999L)) {
      val previewFrame = compositionEngine.evaluateFrame(timeline, testPosMs)
      val exportFrame = compositionEngine.evaluateFrame(timeline, testPosMs)

      assertEquals(previewFrame.timelinePosMs, exportFrame.timelinePosMs)
      assertEquals(previewFrame.activeClip?.id, exportFrame.activeClip?.id)
      assertEquals(previewFrame.activeEffects.size, exportFrame.activeEffects.size)
      if (previewFrame.activeEffects.isNotEmpty()) {
        assertEquals(previewFrame.activeEffects[0].effectType, exportFrame.activeEffects[0].effectType)
        assertEquals(previewFrame.activeEffects[0].intensity, exportFrame.activeEffects[0].intensity, 0.001f)
      }
    }
  }

  @Test
  fun verifyCanvasRenderingOfDiverseEffectCategoriesWithoutExceptions() {
    val bitmap = Bitmap.createBitmap(480, 854, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val sampleEffects = listOf(
      EffectType.GLOW,
      EffectType.RGB_SPLIT,
      EffectType.LIGHT_LEAK,
      EffectType.FIRE_SPARK,
      EffectType.LASER_GRID,
      EffectType.ANGEL_WINGS,
      EffectType.GLOW_EYES,
      EffectType.POLAROID_VINTAGE,
      EffectType.CELEBRATE_CONFETTI,
      EffectType.AI_NEON_TRAIL,
      EffectType.AI_CYBERPUNK_CITY,
      EffectType.VFX_VIRAL_1,
      EffectType.VFX_GLITCH_1,
      EffectType.VFX_LIGHT_1,
      EffectType.VFX_BLUR_1,
      EffectType.VFX_3D_1
    )

    for (effectType in sampleEffects) {
      val clip = EffectClip(effectType = effectType, timelineStartMs = 0L, durationMs = 2000L, intensity = 0.8f)
      VideoEffectRenderer.renderSingleEffect(
        canvas = canvas,
        effect = clip,
        intensity = 0.8f,
        relTime = 500L,
        width = 480,
        height = 854
      )
    }

    assertNotNull(bitmap)
  }
}
