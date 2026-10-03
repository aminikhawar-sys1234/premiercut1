package com.example.engine.composition.gpu

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GlslEffectShaderCollectionTest {

  @Test
  fun testColorGradingShader_enforcesClampingAndAlpha() {
    val shader2D = GlslEffectShaderCollection.buildColorGradingShader(isOes = false)
    assertTrue("Must contain clamp for color output", shader2D.contains("clamp("))
    assertTrue("Must clamp color to 0.0 to 1.0", shader2D.contains("0.0, 1.0"))
    assertTrue("Must preserve original alpha", shader2D.contains("original.a"))
    assertTrue("Must use sampler2D", shader2D.contains("sampler2D uTexture;"))

    val shaderOES = GlslEffectShaderCollection.buildColorGradingShader(isOes = true)
    assertTrue("Must include OES extension header", shaderOES.contains("#extension GL_OES_EGL_image_external : require"))
    assertTrue("Must use samplerExternalOES", shaderOES.contains("samplerExternalOES uTexture;"))
  }

  @Test
  fun testVignetteShader_enforcesClampingAndAlpha() {
    val shader2D = GlslEffectShaderCollection.buildVignetteShader(isOes = false)
    assertTrue("Must clamp vignette final RGB", shader2D.contains("clamp(finalRgb, 0.0, 1.0)"))
    assertTrue("Must preserve alpha", shader2D.contains("color.a"))
    assertTrue("Must use smoothstep for soft radius falloff", shader2D.contains("smoothstep"))

    val shaderOES = GlslEffectShaderCollection.buildVignetteShader(isOes = true)
    assertTrue("Must use samplerExternalOES", shaderOES.contains("samplerExternalOES"))
  }

  @Test
  fun testGaussianBlurShader_enforcesClampingAndAlpha() {
    val shader2D = GlslEffectShaderCollection.buildGaussianBlurShader(isOes = false)
    assertTrue("Must clamp blur sum RGB", shader2D.contains("clamp(sum.rgb, 0.0, 1.0)"))
    assertTrue("Must preserve alpha", shader2D.contains("sum.a"))
    assertTrue("Must include 9-tap distribution weights", shader2D.contains("0.32") && shader2D.contains("0.12") && shader2D.contains("0.05"))
  }

  @Test
  fun testDirectionalBlurShader_enforcesClampingAndAlpha() {
    val shader2D = GlslEffectShaderCollection.buildDirectionalBlurShader(isOes = false)
    assertTrue("Must clamp directional blur sum RGB", shader2D.contains("clamp(sum.rgb, 0.0, 1.0)"))
    assertTrue("Must preserve alpha", shader2D.contains("sum.a"))
    assertTrue("Must use normalized direction step", shader2D.contains("normalize(uDirection)"))
  }

  @Test
  fun testSharpenShader_enforcesClampingAndAlpha() {
    val shader2D = GlslEffectShaderCollection.buildSharpenShader(isOes = false)
    assertTrue("Must clamp sharpened output to 0.0, 1.0", shader2D.contains("clamp(sharpened, 0.0, 1.0)"))
    assertTrue("Must preserve alpha", shader2D.contains("center.a"))
    assertTrue("Must use Laplacian 4-tap kernel", shader2D.contains("- 4.0 * center"))
  }

  @Test
  fun testBloomGlowShader_enforcesClampingAndAlpha() {
    val shader2D = GlslEffectShaderCollection.buildBloomGlowShader(isOes = false)
    assertTrue("Must clamp bloom final color to 0.0, 1.0", shader2D.contains("clamp(finalColor, 0.0, 1.0)"))
    assertTrue("Must preserve alpha", shader2D.contains("base.a"))
    assertTrue("Must calculate thresholded highlights", shader2D.contains("uThreshold"))
  }

  @Test
  fun testChromaticAberrationShader_enforcesClampingAndAlpha() {
    val shader2D = GlslEffectShaderCollection.buildChromaticAberrationShader(isOes = false)
    assertTrue("Must clamp split RGB to 0.0, 1.0", shader2D.contains("clamp(split, 0.0, 1.0)"))
    assertTrue("Must preserve alpha", shader2D.contains("gColor.a"))
  }

  @Test
  fun testTiltShiftShader_enforcesClampingAndAlpha() {
    val shader2D = GlslEffectShaderCollection.buildTiltShiftShader(isOes = false)
    assertTrue("Must clamp tilt shift result", shader2D.contains("clamp(result, 0.0, 1.0)"))
    assertTrue("Must preserve alpha", shader2D.contains("base.a"))
  }

  @Test
  fun testFilmGrainShader_enforcesClampingAndAlpha() {
    val shader2D = GlslEffectShaderCollection.buildFilmGrainShader(isOes = false)
    assertTrue("Must clamp film grain result", shader2D.contains("clamp(finalColor, 0.0, 1.0)"))
    assertTrue("Must preserve alpha", shader2D.contains("color.a"))
  }
}
