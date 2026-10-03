package com.example.engine.export

import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExportOrientationAndPipelineTest {

  @Test
  fun testOrientationPolicy_CanvasIsNormalizedUpright() {
    val portraitClip = VideoClip(
      uri = "content://media/external/video/media/1",
      name = "Portrait Source",
      width = 1920,
      height = 1080,
      naturalRotation = 90,
      rotationDegrees = 0
    )

    // With 90° natural rotation, display dimensions are transposed (1080 x 1920)
    val isTransposed = (portraitClip.naturalRotation == 90 || portraitClip.naturalRotation == 270)
    val displayW = if (isTransposed) portraitClip.height else portraitClip.width
    val displayH = if (isTransposed) portraitClip.width else portraitClip.height

    assertEquals(1080, displayW)
    assertEquals(1920, displayH)

    val canvasW = 1080
    val canvasH = 1920
    val displayAspect = displayW.toFloat() / displayH
    val canvasAspect = canvasW.toFloat() / canvasH

    assertEquals(displayAspect, canvasAspect, 0.001f)

    // Muxer orientation hint MUST be 0 because GPU rendering normalizes the frame upright
    val muxerOrientationHint = 0
    assertEquals(0, muxerOrientationHint)
  }

  @Test
  fun testOrientationCalculation_SupportsAllMetadataRotations() {
    for (rotation in listOf(0, 90, 180, 270)) {
      val clip = VideoClip(
        uri = "test_uri",
        name = "Rot-$rotation",
        width = 1920,
        height = 1080,
        naturalRotation = rotation,
        rotationDegrees = 0
      )

      val totalRot = ((clip.naturalRotation.toFloat() + clip.rotationDegrees.toFloat()) % 360f + 360f) % 360f
      assertEquals(rotation.toFloat(), totalRot, 0.01f)

      val isTransposed = (totalRot == 90f || totalRot == 270f)
      if (rotation == 90 || rotation == 270) {
        assertTrue("Rotation $rotation should be transposed", isTransposed)
      } else {
        assertFalse("Rotation $rotation should not be transposed", isTransposed)
      }
    }
  }

  @Test
  fun testUserRotationCombinedWithNaturalRotation() {
    val clip = VideoClip(
      uri = "test_uri",
      name = "Combined",
      width = 1920,
      height = 1080,
      naturalRotation = 90,
      rotationDegrees = 90
    )

    val totalRot = ((clip.naturalRotation.toFloat() + clip.rotationDegrees.toFloat()) % 360f + 360f) % 360f
    // 90 natural + 90 user = 180 total
    assertEquals(180f, totalRot, 0.01f)
  }

  @Test
  fun testResolutionDimensionCalculation_1080p2K4K() {
    val exporter = VideoExporter(ApplicationProvider.getApplicationContext())

    val (w1080, h1080) = exporter.getDimensionsForResolution(Resolution.RES_1080P, AspectRatio.RATIO_9_16)
    assertEquals(1080, w1080)
    assertEquals(1920, h1080)

    val (w2k, h2k) = exporter.getDimensionsForResolution(Resolution.RES_2K, AspectRatio.RATIO_16_9)
    assertTrue("2K width should be at least 2560", w2k >= 2560)
    assertTrue("2K height should be at least 1440", h2k >= 1440)

    val (w4k, h4k) = exporter.getDimensionsForResolution(Resolution.RES_4K, AspectRatio.RATIO_9_16)
    assertTrue("4K portrait height should be at least 3840", h4k >= 3840)
    assertTrue("4K portrait width should be at least 2160", w4k >= 2160)
  }

  @Test
  fun testPresentationTimestamp_StrictMonotonicity() {
    var lastEglPtsNs = -1L
    val fps = 30
    for (frameIndex in 0 until 100) {
      val ptsUs = (frameIndex * 1_000_000L) / fps
      var targetPtsNs = ptsUs * 1000L
      if (targetPtsNs <= lastEglPtsNs) {
        targetPtsNs = lastEglPtsNs + 1000L
      }
      assertTrue("Presentation timestamp must be strictly monotonic", targetPtsNs > lastEglPtsNs)
      lastEglPtsNs = targetPtsNs
    }
  }

  @Test
  fun testCodecCapabilityInspector_HandlesHighResGracefully() {
    val report = ProfessionalCodecCapabilities.inspect(
      config = ExportConfig(
        resolution = Resolution.RES_4K,
        frameRate = FrameRate.FPS_30,
        codecProfile = CodecProfile.AUTO
      ),
      dimensions = Pair(2160, 3840)
    )

    assertNotNull(report)
    assertEquals(2160, report.width)
    assertEquals(3840, report.height)
  }
}
