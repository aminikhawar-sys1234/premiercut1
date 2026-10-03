package com.example

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.*
import com.example.engine.composition.ComposedEffect
import com.example.engine.composition.ComposedFrame
import com.example.engine.composition.gpu.GpuCompositionRenderer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProceduralTextureLifecycleTest {

  private lateinit var gpuRenderer: GpuCompositionRenderer

  @Before
  fun setUp() {
    gpuRenderer = GpuCompositionRenderer(ApplicationProvider.getApplicationContext())
  }

  @Test
  fun testProceduralEffectTextureCreationAndCachingLifecycle() {
    val effectClip = EffectClip(
      id = "procedural_fx_1",
      effectType = EffectType.FIRE_SPARK,
      timelineStartMs = 0L,
      durationMs = 3000L,
      intensity = 0.8f
    )

    val composedEffect = ComposedEffect(
      clip = effectClip,
      effectType = EffectType.FIRE_SPARK,
      intensity = 0.8f,
      timeInEffectMs = 500L,
      progress = 0.16f
    )

    val colorMat = ColorMatrix()
    val frame1 = ComposedFrame(
      timelinePosMs = 500L,
      activeClip = VideoClip(id = "v1", name = "Clip 1", uri = "uri1", durationMs = 3000L),
      clipSourcePosMs = 500L,
      activeOverlays = emptyList(),
      activeTexts = emptyList(),
      activeStickers = emptyList(),
      activeTransition = null,
      activeEffects = listOf(composedEffect),
      colorMatrix = colorMat,
      colorFilter = ColorMatrixColorFilter(colorMat)
    )

    // 1. First evaluation: Texture created and cached
    val cached1 = gpuRenderer.getOrCreateProceduralEffectTexture(frame1, 480, 854)
    assertNotNull("Procedural effect texture should be created for active procedural effect", cached1)
    assertTrue("Texture ID should be valid (> 0)", cached1!!.texId > 0)
    assertEquals(480, cached1.width)
    assertEquals(854, cached1.height)

    // 2. Second evaluation with identical state within same time frame: Cache Hit
    val cached2 = gpuRenderer.getOrCreateProceduralEffectTexture(frame1, 480, 854)
    assertNotNull(cached2)
    assertEquals("Should reuse the exact same texture ID from cache", cached1.texId, cached2!!.texId)
    assertEquals("Cache hash should match", cached1.hash, cached2.hash)

    // 3. Evaluation at a different timestamp (time step change): Cache update with texture ID reuse
    val frame2 = frame1.copy(
      timelinePosMs = 1500L,
      activeEffects = listOf(composedEffect.copy(timeInEffectMs = 1500L, progress = 0.5f))
    )
    val cached3 = gpuRenderer.getOrCreateProceduralEffectTexture(frame2, 480, 854)
    assertNotNull(cached3)
    assertNotEquals("Hash should differ due to timestamp change", cached1.hash, cached3!!.hash)

    // 4. Lifecycle cleanup via release
    gpuRenderer.release()
  }

  @Test
  fun testProceduralTextureContextLossAndRelease() {
    val effectClip = EffectClip(
      id = "procedural_fx_2",
      effectType = EffectType.CELEBRATE_CONFETTI,
      timelineStartMs = 0L,
      durationMs = 3000L,
      intensity = 0.9f
    )

    val composedEffect = ComposedEffect(
      clip = effectClip,
      effectType = EffectType.CELEBRATE_CONFETTI,
      intensity = 0.9f,
      timeInEffectMs = 200L,
      progress = 0.06f
    )

    val colorMat = ColorMatrix()
    val frame = ComposedFrame(
      timelinePosMs = 200L,
      activeClip = VideoClip(id = "v1", name = "Clip 1", uri = "uri1", durationMs = 3000L),
      clipSourcePosMs = 200L,
      activeOverlays = emptyList(),
      activeTexts = emptyList(),
      activeStickers = emptyList(),
      activeTransition = null,
      activeEffects = listOf(composedEffect),
      colorMatrix = colorMat,
      colorFilter = ColorMatrixColorFilter(colorMat)
    )

    val cached = gpuRenderer.getOrCreateProceduralEffectTexture(frame, 360, 640)
    assertNotNull(cached)

    // Test onContextLost clears caches safely
    gpuRenderer.onContextLost()

    // Test release completes without throwing exceptions
    gpuRenderer.release()
  }
}
