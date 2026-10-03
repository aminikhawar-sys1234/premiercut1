package com.example

import com.example.domain.model.AspectRatio
import com.example.domain.model.FrameRate
import com.example.domain.model.Resolution
import com.example.domain.model.VideoClip
import com.example.engine.media.RealMediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantNewProjectPickerAndAutoAspectTest {

  @Test
  fun testAspectRatioDetection_StandardRatios() {
    // 16:9 Landscape (Full HD, 4K, 720p)
    assertEquals(AspectRatio.RATIO_16_9, AspectRatio.fromDimensions(1920, 1080))
    assertEquals(AspectRatio.RATIO_16_9, AspectRatio.fromDimensions(3840, 2160))
    assertEquals(AspectRatio.RATIO_16_9, AspectRatio.fromDimensions(1280, 720))

    // 9:16 Vertical (Reels / TikTok / Shorts)
    assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromDimensions(1080, 1920))
    assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromDimensions(2160, 3840))
    assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromDimensions(720, 1280))

    // 1:1 Square (Instagram Square)
    assertEquals(AspectRatio.RATIO_1_1, AspectRatio.fromDimensions(1080, 1080))
    assertEquals(AspectRatio.RATIO_1_1, AspectRatio.fromDimensions(2048, 2048))

    // 4:3 Landscape
    assertEquals(AspectRatio.RATIO_4_3, AspectRatio.fromDimensions(1440, 1080))
    assertEquals(AspectRatio.RATIO_4_3, AspectRatio.fromDimensions(1024, 768))

    // 3:4 Classic Portrait
    assertEquals(AspectRatio.RATIO_3_4, AspectRatio.fromDimensions(1080, 1440))
    assertEquals(AspectRatio.RATIO_3_4, AspectRatio.fromDimensions(768, 1024))

    // 4:5 Instagram Portrait
    assertEquals(AspectRatio.RATIO_4_5, AspectRatio.fromDimensions(1080, 1350))
  }

  @Test
  fun testOrientationAwareRotationCalculation() {
    // A video recorded in 1920x1080 but with 90° rotation metadata is actually vertical (1080x1920 -> 9:16)
    val rawWidth = 1920
    val rawHeight = 1080
    val rotation = 90
    val effectiveWidth = if (rotation == 90 || rotation == 270) rawHeight else rawWidth
    val effectiveHeight = if (rotation == 90 || rotation == 270) rawWidth else rawHeight

    assertEquals(1080, effectiveWidth)
    assertEquals(1920, effectiveHeight)
    assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromDimensions(effectiveWidth, effectiveHeight))

    // A video recorded in 1080x1920 with 270° rotation is actually landscape (1920x1080 -> 16:9)
    val rot270Width = 1080
    val rot270Height = 1920
    val rotation270 = 270
    val effW270 = if (rotation270 == 90 || rotation270 == 270) rot270Height else rot270Width
    val effH270 = if (rotation270 == 90 || rotation270 == 270) rot270Width else rot270Height

    assertEquals(1920, effW270)
    assertEquals(1080, effH270)
    assertEquals(AspectRatio.RATIO_16_9, AspectRatio.fromDimensions(effW270, effH270))
  }

  @Test
  fun testRealMediaMetadata_detectedAspectRatio() {
    val metaLandscape = RealMediaMetadata(
      durationMs = 5000L,
      width = 1920,
      height = 1080,
      rotationDegrees = 0,
      frameRate = 60f,
      mimeType = "video/mp4",
      hasAudio = true,
      isVideo = true
    )
    assertEquals(AspectRatio.RATIO_16_9, metaLandscape.detectedAspectRatio)

    val metaVertical = RealMediaMetadata(
      durationMs = 8000L,
      width = 1080,
      height = 1920,
      rotationDegrees = 0,
      frameRate = 30f,
      mimeType = "video/mp4",
      hasAudio = true,
      isVideo = true
    )
    assertEquals(AspectRatio.RATIO_9_16, metaVertical.detectedAspectRatio)

    val metaSquare = RealMediaMetadata(
      durationMs = 4000L,
      width = 1080,
      height = 1080,
      rotationDegrees = 0,
      frameRate = 30f,
      mimeType = "video/mp4",
      hasAudio = true,
      isVideo = true
    )
    assertEquals(AspectRatio.RATIO_1_1, metaSquare.detectedAspectRatio)
  }

  @Test
  fun testVideoClipCreation_SetsMainTrackTimelineCorrectly() {
    val durationMs = 12500L
    val clip = VideoClip(
      uri = "content://media/external/video/media/42",
      name = "Main Video",
      timelineStartMs = 0L,
      durationMs = durationMs,
      sourceStartMs = 0L,
      sourceEndMs = durationMs,
      isVideo = true,
      width = 1080,
      height = 1920,
      naturalRotation = 0,
      frameRate = 30f,
      mimeType = "video/mp4",
      hasAudio = true
    )

    assertEquals(0L, clip.timelineStartMs)
    assertEquals(durationMs, clip.durationMs)
    assertEquals(0L, clip.sourceStartMs)
    assertEquals(durationMs, clip.sourceEndMs)
    assertTrue(clip.isVideo)
    assertEquals(1080, clip.width)
    assertEquals(1920, clip.height)
  }

  @Test
  fun testAllRequiredAspectRatios_PromptSpecifications() {
    // Portrait video: 1080 x 1920 -> 9:16
    assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromDimensions(1080, 1920, 0))

    // Landscape video: 1920 x 1080 -> 16:9
    assertEquals(AspectRatio.RATIO_16_9, AspectRatio.fromDimensions(1920, 1080, 0))

    // Square video: 1080 x 1080 -> 1:1
    assertEquals(AspectRatio.RATIO_1_1, AspectRatio.fromDimensions(1080, 1080, 0))

    // 4:5 video: 1080 x 1350 -> 4:5
    assertEquals(AspectRatio.RATIO_4_5, AspectRatio.fromDimensions(1080, 1350, 0))

    // 4:3 video: 1440 x 1080 -> 4:3
    assertEquals(AspectRatio.RATIO_4_3, AspectRatio.fromDimensions(1440, 1080, 0))

    // 3:4 video: 1080 x 1440 -> 3:4
    assertEquals(AspectRatio.RATIO_3_4, AspectRatio.fromDimensions(1080, 1440, 0))

    // 21:9 video: 2560 x 1080 -> 21:9
    assertEquals(AspectRatio.RATIO_21_9, AspectRatio.fromDimensions(2560, 1080, 0))
  }

  @Test
  fun testResolveEffectiveDimensions_WithRotationMetadata() {
    // 1920x1080 landscape container recorded with 90 deg rotation -> 1080x1920 portrait
    val (w90, h90) = AspectRatio.resolveEffectiveDimensions(1920, 1080, 90)
    assertEquals(1080, w90)
    assertEquals(1920, h90)
    assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromDimensions(1920, 1080, 90))

    // 1920x1080 landscape container with 270 deg rotation -> 1080x1920 portrait
    val (w270, h270) = AspectRatio.resolveEffectiveDimensions(1920, 1080, 270)
    assertEquals(1080, w270)
    assertEquals(1920, h270)
    assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromDimensions(1920, 1080, 270))

    // Already-swapped dimensions by metadata retriever (1080x1920) with rotation 90
    val (wSwapped, hSwapped) = AspectRatio.resolveEffectiveDimensions(1080, 1920, 90)
    assertEquals(1080, wSwapped)
    assertEquals(1920, hSwapped)
    assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromDimensions(1080, 1920, 90))
  }
}
