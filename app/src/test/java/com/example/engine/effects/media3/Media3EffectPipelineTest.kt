package com.example.engine.effects.media3

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.FilterSettings
import com.example.domain.model.FilterType
import com.example.domain.model.Timeline
import com.example.domain.model.VideoAdjustments
import com.example.domain.model.VideoClip
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Media3EffectPipelineTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testColorGradingGlEffect_isNoOpForDefaultAdjustments() {
    val defaultEffect = ColorGradingGlEffect.fromTimeline(
      adjustments = VideoAdjustments(),
      filterSettings = FilterSettings(type = FilterType.NONE)
    )
    assertTrue("Default adjustments without filter should be no-op", defaultEffect.isNoOp(1920, 1080))
  }

  @Test
  fun testColorGradingGlEffect_detectsActiveAdjustments() {
    val brightEffect = ColorGradingGlEffect.fromTimeline(
      adjustments = VideoAdjustments(brightness = 0.2f),
      filterSettings = FilterSettings(type = FilterType.NONE)
    )
    assertFalse("Adjusted brightness should not be no-op", brightEffect.isNoOp(1920, 1080))
    assertEquals(0.2f, brightEffect.brightness, 0.001f)

    val contrastEffect = ColorGradingGlEffect.fromTimeline(
      adjustments = VideoAdjustments(contrast = 1.4f),
      filterSettings = FilterSettings(type = FilterType.NONE)
    )
    assertFalse("Adjusted contrast should not be no-op", contrastEffect.isNoOp(1920, 1080))

    val vignetteEffect = ColorGradingGlEffect.fromTimeline(
      adjustments = VideoAdjustments(vignette = 0.6f),
      filterSettings = FilterSettings(type = FilterType.NONE)
    )
    assertFalse("Adjusted vignette should not be no-op", vignetteEffect.isNoOp(1920, 1080))
  }

  @Test
  fun testColorGradingGlEffect_convertsColorMatrixToOpenGL() {
    val androidMatrix = ColorMatrix(floatArrayOf(
      1f, 0f, 0f, 0f, 25.5f,
      0f, 1f, 0f, 0f, 51.0f,
      0f, 0f, 1f, 0f, 76.5f,
      0f, 0f, 0f, 1f, 0f
    ))

    val (glMatrix4x4, offset) = ColorGradingGlEffect.convertColorMatrixToGl(androidMatrix)

    // Check 4x4 matrix dimensions and identity diagonals
    assertEquals(16, glMatrix4x4.size)
    assertEquals(1f, glMatrix4x4[0], 0.001f) // col 0, row 0
    assertEquals(1f, glMatrix4x4[5], 0.001f) // col 1, row 1
    assertEquals(1f, glMatrix4x4[10], 0.001f) // col 2, row 2
    assertEquals(1f, glMatrix4x4[15], 0.001f) // col 3, row 3

    // Check normalized offset (25.5 / 255.0 = 0.1, 51 / 255 = 0.2, 76.5 / 255 = 0.3)
    assertEquals(4, offset.size)
    assertEquals(0.1f, offset[0], 0.001f)
    assertEquals(0.2f, offset[1], 0.001f)
    assertEquals(0.3f, offset[2], 0.001f)
    assertEquals(0.0f, offset[3], 0.001f)
  }

  @Test
  fun testColorGradingGlEffect_withPresetFilter() {
    val filterEffect = ColorGradingGlEffect.fromTimeline(
      adjustments = VideoAdjustments(),
      filterSettings = FilterSettings(type = FilterType.CINEMATIC, intensity = 0.8f)
    )
    assertFalse("Preset cinematic filter should not be no-op", filterEffect.isNoOp(1920, 1080))
    assertNotNull("Cinematic filter should supply colorMatrix4x4", filterEffect.colorMatrix4x4)
    assertNotNull("Cinematic filter should supply colorOffset", filterEffect.colorOffset)
  }

  @Test
  fun testLutGlEffect_formatsAndLengths() {
    // 16 x 256 is a vertical strip LUT (N x N^2 where N = 16)
    val vertBitmap = Bitmap.createBitmap(16, 256, Bitmap.Config.ARGB_8888)
    assertEquals(LutGlEffect.LutFormat.VERTICAL_STRIP, LutGlEffect.detectLutFormat(vertBitmap))
    assertEquals(16, LutGlEffect.detectLutLength(vertBitmap))

    // 256 x 16 is a horizontal strip LUT (N^2 x N where N = 16)
    val horizBitmap = Bitmap.createBitmap(256, 16, Bitmap.Config.ARGB_8888)
    assertEquals(LutGlEffect.LutFormat.HORIZONTAL_STRIP, LutGlEffect.detectLutFormat(horizBitmap))
    assertEquals(16, LutGlEffect.detectLutLength(horizBitmap))
  }

  @Test
  fun testLutGlEffect_generators() {
    val identityLut = LutGlEffect.createIdentityLut(cubeLength = 16)
    assertEquals(16, identityLut.width)
    assertEquals(256, identityLut.height)

    val lutEffect = LutGlEffect(identityLut, intensity = 0.75f)
    assertEquals(0.75f, lutEffect.intensity, 0.001f)
    assertFalse("Active LUT intensity should not be no-op", lutEffect.isNoOp(1920, 1080))

    val zeroIntensity = LutGlEffect(identityLut, intensity = 0.0f)
    assertTrue("Zero intensity LUT should be no-op", zeroIntensity.isNoOp(1920, 1080))
  }

  @Test
  fun testLutGlEffect_cinematicTealOrange() {
    val tealOrangeLut = LutGlEffect.createCinematicTealOrangeLut(cubeLength = 16)
    assertNotNull(tealOrangeLut)
    assertEquals(16, tealOrangeLut.width)
    assertEquals(256, tealOrangeLut.height)
  }

  @Test
  fun testMedia3EffectPipeline_buildVideoEffects() {
    val timeline = Timeline(
      adjustments = VideoAdjustments(exposure = 0.5f, saturation = 1.2f),
      filter = FilterSettings(type = FilterType.VINTAGE, intensity = 1.0f)
    )

    val effects = Media3EffectPipeline.buildVideoEffects(
      context = context,
      timeline = timeline,
      exportWidth = 1920,
      exportHeight = 1080,
      targetFps = 30f
    )

    // Presentation (1) + FrameDrop (2) + ColorGrading (3)
    assertTrue("Effects should contain at least 3 stages", effects.size >= 3)
    assertTrue("Should include a ColorGradingGlEffect", effects.any { it is ColorGradingGlEffect })
  }

  @Test
  fun testMedia3EffectPipeline_buildClipEffects() {
    val clip = VideoClip(
      name = "Clip1",
      filter = FilterSettings(type = FilterType.BLACK_AND_WHITE, intensity = 1.0f)
    )
    val timeline = Timeline()

    val clipEffects = Media3EffectPipeline.buildClipEffects(
      context = context,
      clip = clip,
      timeline = timeline,
      exportWidth = 1280,
      exportHeight = 720,
      targetFps = 60f
    )

    assertTrue("Clip effects should contain at least 3 stages", clipEffects.size >= 3)
    assertTrue("Should include ColorGradingGlEffect for clip filter", clipEffects.any { it is ColorGradingGlEffect })
  }

  @Test
  fun testMedia3EffectPipeline_customShaders() {
    val glitchEffect = Media3EffectPipeline.createGlitchEffect(intensity = 0.8f)
    assertEquals("GlitchEffect", glitchEffect.name)
    assertFalse("Custom glitch shader is not no-op", glitchEffect.isNoOp(1920, 1080))

    val rgbSplit = Media3EffectPipeline.createRgbSplitEffect(intensity = 0.5f)
    assertEquals("RgbSplitEffect", rgbSplit.name)
    assertFalse("Custom RGB split shader is not no-op", rgbSplit.isNoOp(1920, 1080))
  }

  @Test
  fun testMedia3EffectPipeline_mlStyleTransfers() {
    val animeEffect = Media3EffectPipeline.createStyleTransferEffect(StyleTransferType.ANIME_CEL_SHADING, intensity = 0.9f)
    assertNotNull(animeEffect)
    assertEquals("AnimeCelShadingEffect", animeEffect?.name)

    val oilEffect = Media3EffectPipeline.createStyleTransferEffect(StyleTransferType.OIL_PAINTING_KUWAHARA, intensity = 0.85f)
    assertNotNull(oilEffect)
    assertEquals("OilPaintingKuwaharaEffect", oilEffect?.name)

    val sketchEffect = Media3EffectPipeline.createStyleTransferEffect(StyleTransferType.SKETCH_CHARCOAL, intensity = 1.0f)
    assertNotNull(sketchEffect)
    assertEquals("SketchCharcoalEffect", sketchEffect?.name)

    val cyberpunkEffect = Media3EffectPipeline.createStyleTransferEffect(StyleTransferType.CYBERPUNK_NEON, intensity = 0.75f)
    assertNotNull(cyberpunkEffect)
    assertEquals("CyberpunkNeonEffect", cyberpunkEffect?.name)

    val bloomEffect = Media3EffectPipeline.createStyleTransferEffect(StyleTransferType.DREAMY_BLOOM, intensity = 0.6f)
    assertNotNull(bloomEffect)
    assertEquals("DreamyBloomEffect", bloomEffect?.name)

    val halftoneEffect = Media3EffectPipeline.createStyleTransferEffect(StyleTransferType.HALFTONE_COMIC, intensity = 0.5f)
    assertNotNull(halftoneEffect)
    assertEquals("HalftoneComicEffect", halftoneEffect?.name)

    val noneEffect = Media3EffectPipeline.createStyleTransferEffect(StyleTransferType.NONE)
    assertNull(noneEffect)
  }

  @Test
  fun testMedia3EffectPipeline_buildRealtimePreviewEffects() {
    val adjustments = VideoAdjustments(brightness = 0.15f, contrast = 1.2f)
    val filter = FilterSettings(type = FilterType.CINEMATIC, intensity = 0.8f)

    val effects = Media3EffectPipeline.buildRealtimePreviewEffects(
      adjustments = adjustments,
      filterSettings = filter,
      styleTransfer = StyleTransferType.ANIME_CEL_SHADING,
      styleIntensity = 0.9f
    )

    assertTrue("Should generate at least 2 real-time effects (ColorGrading + Anime)", effects.size >= 2)
    assertTrue("Should include ColorGradingGlEffect", effects.any { it is ColorGradingGlEffect })
    assertTrue("Should include CustomShaderGlEffect for Anime", effects.any { it is CustomShaderGlEffect && it.name == "AnimeCelShadingEffect" })
  }
}
