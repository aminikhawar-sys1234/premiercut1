package com.example.engine

import android.media.MediaCodec
import androidx.media3.common.PlaybackException
import com.example.domain.model.VideoClip
import com.example.engine.composition.gpu.HardwareVideoTextureSource
import com.example.engine.controller.DecoderManager
import com.example.engine.controller.DecoderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoDecoderEngineTest {

  @Test
  fun testDecoderManagerInitialState() {
    val manager = DecoderManager()
    // Initial state after detectCapabilities
    assertTrue(manager.decoderState == DecoderState.HARDWARE_ACCELERATED || manager.decoderState == DecoderState.SOFTWARE_FALLBACK)
    assertNotNull(manager.decoderState)
  }

  @Test
  fun testDecoderManagerFailureTriggersSoftwareFallback() {
    val manager = DecoderManager()

    val ex = PlaybackException("Codec init error", null, PlaybackException.ERROR_CODE_DECODING_FAILED)
    val handled = manager.handleCodecError(ex)
    assertTrue(handled)
    assertEquals(DecoderState.SOFTWARE_FALLBACK, manager.decoderState)
    assertFalse(manager.isHardwareAccelerated)
    assertTrue(manager.isFallbackActive())
    assertTrue(manager.isRuntimeFallbackTriggered())
    assertEquals("Codec init error", manager.getLastErrorMessage())

    // Recovery resets runtime triggered fallback
    manager.reset()
    assertFalse(manager.isRuntimeFallbackTriggered())
    assertNull(manager.getLastErrorMessage())
  }

  @Test
  fun testDecoderManagerExplicitSoftwareFallback() {
    val manager = DecoderManager()
    manager.forceSoftwareFallback("Test forced fallback")
    assertEquals(DecoderState.SOFTWARE_FALLBACK, manager.decoderState)
    assertTrue(manager.isFallbackActive())
    assertTrue(manager.isRuntimeFallbackTriggered())
    assertEquals("Test forced fallback", manager.getLastErrorMessage())

    manager.reset()
    assertFalse(manager.isRuntimeFallbackTriggered())
    assertNull(manager.getLastErrorMessage())
  }

  @Test
  fun testDecoderManagerCodecLimits() {
    assertEquals(4, DecoderManager.MAX_RECOMMENDED_HARDWARE_DECODERS)
  }

  @Test
  fun testFrameTimingAndPtsMath() {
    // Verify CFR timestamp math for standard framerates
    val fps30 = 30
    val frameIndex30 = 45L // 1.5 seconds
    val ptsUs30 = (frameIndex30 * 1_000_000L) / fps30
    assertEquals(1_500_000L, ptsUs30)

    val fps60 = 60
    val frameIndex60 = 90L // 1.5 seconds
    val ptsUs60 = (frameIndex60 * 1_000_000L) / fps60
    assertEquals(1_500_000L, ptsUs60)

    val fps24 = 24
    val frameIndex24 = 24L // 1 second
    val ptsUs24 = (frameIndex24 * 1_000_000L) / fps24
    assertEquals(1_000_000L, ptsUs24)

    val fps120 = 120
    val frameIndex120 = 120L // 1 second
    val ptsUs120 = (frameIndex120 * 1_000_000L) / fps120
    assertEquals(1_000_000L, ptsUs120)
  }

  @Test
  fun testRotationAspectTransposition() {
    val rawWidth = 1920
    val rawHeight = 1080

    // 0 degrees rotation: no transposition
    val rot0 = 0
    val trans0 = (rot0 == 90 || rot0 == 270)
    assertFalse(trans0)
    val w0 = if (trans0) rawHeight else rawWidth
    val h0 = if (trans0) rawWidth else rawHeight
    assertEquals(1920, w0)
    assertEquals(1080, h0)

    // 90 degrees rotation: transposed
    val rot90 = 90
    val trans90 = (rot90 == 90 || rot90 == 270)
    assertTrue(trans90)
    val w90 = if (trans90) rawHeight else rawWidth
    val h90 = if (trans90) rawWidth else rawHeight
    assertEquals(1080, w90)
    assertEquals(1920, h90)

    // 180 degrees rotation: no transposition
    val rot180 = 180
    val trans180 = (rot180 == 90 || rot180 == 270)
    assertFalse(trans180)
    val w180 = if (trans180) rawHeight else rawWidth
    val h180 = if (trans180) rawWidth else rawHeight
    assertEquals(1920, w180)
    assertEquals(1080, h180)

    // 270 degrees rotation: transposed
    val rot270 = 270
    val trans270 = (rot270 == 90 || rot270 == 270)
    assertTrue(trans270)
    val w270 = if (trans270) rawHeight else rawWidth
    val h270 = if (trans270) rawWidth else rawHeight
    assertEquals(1080, w270)
    assertEquals(1920, h270)
  }

  @Test
  fun testLruEvictionPolicy() {
    val maxDecoders = DecoderManager.MAX_RECOMMENDED_HARDWARE_DECODERS
    val decoders = mutableMapOf<String, String>()

    // Simulate 4 decoders for clips
    decoders["clip-1"] = "decoder-1"
    decoders["clip-2"] = "decoder-2"
    decoders["clip-3"] = "decoder-3"
    decoders["clip-4"] = "decoder-4"
    assertEquals(maxDecoders, decoders.size)

    // Active clips in current frame: clip-2 and clip-4
    val activeIds = setOf("clip-2", "clip-4")

    // Clip 5 arrives
    val clip5 = "clip-5"
    if (decoders.size >= maxDecoders) {
      val evictCandidate = decoders.keys.firstOrNull { it !in activeIds }
      assertNotNull(evictCandidate)
      assertEquals("clip-1", evictCandidate)
      decoders.remove(evictCandidate)
    }
    decoders[clip5] = "decoder-5"

    assertEquals(maxDecoders, decoders.size)
    assertFalse(decoders.containsKey("clip-1"))
    assertTrue(decoders.containsKey("clip-2"))
    assertTrue(decoders.containsKey("clip-3"))
    assertTrue(decoders.containsKey("clip-4"))
    assertTrue(decoders.containsKey("clip-5"))
  }
}
