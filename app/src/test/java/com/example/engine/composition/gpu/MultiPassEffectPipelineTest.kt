package com.example.engine.composition.gpu

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MultiPassEffectPipelineTest {

  private lateinit var pipeline: MultiPassEffectPipeline

  @Before
  fun setUp() {
    pipeline = MultiPassEffectPipeline()
  }

  @Test
  fun testGlPingPongFbo_lifecycleAndSwapping() {
    val pingPong = GlPingPongFbo()

    assertEquals(0, pingPong.width)
    assertEquals(0, pingPong.height)

    pingPong.setup(1920, 1080)
    assertEquals(1920, pingPong.width)
    assertEquals(1080, pingPong.height)

    val initialWriteFbo = pingPong.getWriteFbo()
    assertSame(pingPong.fboA, initialWriteFbo)

    pingPong.swap()
    val swappedWriteFbo = pingPong.getWriteFbo()
    assertSame(pingPong.fboB, swappedWriteFbo)

    pingPong.reset()
    assertSame(pingPong.fboA, pingPong.getWriteFbo())

    pingPong.release()
    assertEquals(0, pingPong.width)
    assertEquals(0, pingPong.height)
  }

  @Test
  fun testPipeline_passManagementAndDynamicReordering() {
    val colorGrading = ColorGradingPass()
    val vignette = VignettePass()
    val sharpen = SharpenPass()
    val blur = GaussianBlurPass()

    pipeline.addPass(colorGrading)
    pipeline.addPass(vignette)
    pipeline.addPass(sharpen)
    pipeline.addPass(blur)

    val passes = pipeline.getActivePasses()
    assertEquals(4, passes.size)
    assertEquals("color_grading", passes[0].id)
    assertEquals("vignette", passes[1].id)
    assertEquals("sharpen", passes[2].id)
    assertEquals("gaussian_blur", passes[3].id)

    // Dynamic disabling
    pipeline.setPassEnabled("vignette", false)
    assertFalse(pipeline.getActivePasses().find { it.id == "vignette" }!!.isEnabled)

    // Dynamic intensity adjustment
    pipeline.setPassIntensity("sharpen", 0.75f)
    assertEquals(0.75f, pipeline.getActivePasses().find { it.id == "sharpen" }!!.intensity, 0.001f)

    // Dynamic reordering (Blur -> ColorGrading -> Sharpen -> Vignette)
    pipeline.reorderPasses(listOf("gaussian_blur", "color_grading", "sharpen", "vignette"))
    val reordered = pipeline.getActivePasses()
    assertEquals("gaussian_blur", reordered[0].id)
    assertEquals("color_grading", reordered[1].id)
    assertEquals("sharpen", reordered[2].id)
    assertEquals("vignette", reordered[3].id)

    // Removal
    pipeline.removePass("gaussian_blur")
    assertEquals(3, pipeline.getActivePasses().size)
    assertNull(pipeline.getActivePasses().find { it.id == "gaussian_blur" })
  }

  @Test
  fun testColorGradingPass_shaderSafetyAndParameters() {
    val pass = ColorGradingPass()
    pass.brightness = 0.2f
    pass.contrast = 1.3f
    pass.saturation = 1.2f
    pass.exposure = 0.5f
    pass.temperature = 0.1f
    pass.tint = -0.05f
    pass.highlights = 0.1f
    pass.shadows = -0.1f

    val fs2D = pass.getFragmentShader(isOes = false)
    assertTrue("Fragment shader must enforce strict color clamping", fs2D.contains("clamp(color, 0.0, 1.0)"))
    assertTrue("Fragment shader must preserve alpha", fs2D.contains("original.a"))

    val fsOES = pass.getFragmentShader(isOes = true)
    assertTrue("OES fragment shader must include OES extension header", fsOES.contains("GL_OES_EGL_image_external"))
    assertTrue("OES fragment shader must use samplerExternalOES", fsOES.contains("samplerExternalOES"))
  }

  @Test
  fun testSharpenAndBlurPasses_shaderSafety() {
    val sharpen = SharpenPass()
    val fsSharpen = sharpen.getFragmentShader(isOes = false)
    assertTrue(fsSharpen.contains("clamp(sharpened, 0.0, 1.0)"))
    assertTrue(fsSharpen.contains("center.a"))

    val blur = GaussianBlurPass()
    val fsBlur = blur.getFragmentShader(isOes = false)
    assertTrue(fsBlur.contains("clamp(sum.rgb, 0.0, 1.0)"))
    assertTrue(fsBlur.contains("sum.a"))
  }

  @Test
  fun testCreativeEffectPasses_initializationAndShaders() {
    val chromatic = ChromaticAberrationPass()
    val bloom = BloomGlowPass()
    val grain = FilmGrainPass()
    val glitch = GlitchPass()

    assertEquals("chromatic_aberration", chromatic.id)
    assertEquals("bloom_glow", bloom.id)
    assertEquals("film_grain", grain.id)
    assertEquals("glitch", glitch.id)

    assertTrue(chromatic.getFragmentShader(isOes = false).contains("clamp(splitColor, 0.0, 1.0)"))
    assertTrue(bloom.getFragmentShader(isOes = false).contains("clamp(finalColor, 0.0, 1.0)"))
    assertTrue(grain.getFragmentShader(isOes = false).contains("clamp(finalColor, 0.0, 1.0)"))
    assertTrue(glitch.getFragmentShader(isOes = false).contains("clamp(vec3(r, g, b), 0.0, 1.0)"))
  }

  @Test
  fun testCustomGlEffectPass_customUniforms() {
    var uniformBound = false
    val customPass = CustomGlEffectPass(
      id = "custom_invert",
      name = "Invert Effect",
      fragmentShaderBody2D = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D uTexture;
        void main() {
          vec4 c = texture2D(uTexture, clamp(vTextureCoord, 0.0, 1.0));
          gl_FragColor = vec4(clamp(1.0 - c.rgb, 0.0, 1.0), c.a);
        }
      """.trimIndent(),
      uniformBinder = { _, _, _, _, _ ->
        uniformBound = true
      }
    )

    assertEquals("custom_invert", customPass.id)
    assertTrue(customPass.getFragmentShader(isOes = false).contains("1.0 - c.rgb"))

    pipeline.addPass(customPass)
    assertEquals(1, pipeline.getActivePasses().size)

    pipeline.release()
    assertEquals(0, pipeline.getActivePasses().size)
  }
}
