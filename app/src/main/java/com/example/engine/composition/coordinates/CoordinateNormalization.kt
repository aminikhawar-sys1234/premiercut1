package com.example.engine.composition.coordinates

import androidx.annotation.OptIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlaySettings

/**
 * Normalized coordinate container representing position and size in 0.0f..1.0f UV space.
 */
data class NormalizedCoordinates(
  val uCenterX: Float = 0.5f,
  val vCenterY: Float = 0.5f,
  val normWidth: Float = 0.3f,
  val normHeight: Float = 0.2f,
  val rotationDegrees: Float = 0.0f,
  val alpha: Float = 1.0f
)

/**
 * Absolute pixel coordinates mapped to target export resolution.
 */
data class TargetPixelCoordinates(
  val centerPxX: Float,
  val centerPxY: Float,
  val widthPx: Float,
  val heightPx: Float,
  val rotationDegrees: Float,
  val alpha: Float
)

/**
 * Service that maps Compose UI coordinates (dp) to absolute pixel values
 * based on the project's target export resolution.
 */
class CoordinateNormalizationService(
  val targetResolution: IntSize = IntSize(1080, 1920)
) {

  /**
   * Maps Compose UI coordinates (dp offset and size) inside a preview viewport (dp size)
   * to normalized UV coordinates (0.0f..1.0f).
   */
  fun normalizeFromComposeDp(
    centerDpOffset: DpOffset,
    overlayDpSize: DpSize,
    viewportDpSize: DpSize,
    rotationDegrees: Float = 0.0f,
    alpha: Float = 1.0f
  ): NormalizedCoordinates {
    val uCenterX = if (viewportDpSize.width > 0.dp) (centerDpOffset.x / viewportDpSize.width).coerceIn(0f, 1f) else 0.5f
    val vCenterY = if (viewportDpSize.height > 0.dp) (centerDpOffset.y / viewportDpSize.height).coerceIn(0f, 1f) else 0.5f
    val normWidth = if (viewportDpSize.width > 0.dp) (overlayDpSize.width / viewportDpSize.width).coerceIn(0f, 1f) else 0.3f
    val normHeight = if (viewportDpSize.height > 0.dp) (overlayDpSize.height / viewportDpSize.height).coerceIn(0f, 1f) else 0.2f

    return NormalizedCoordinates(
      uCenterX = uCenterX,
      vCenterY = vCenterY,
      normWidth = normWidth,
      normHeight = normHeight,
      rotationDegrees = rotationDegrees,
      alpha = alpha
    )
  }

  /**
   * Maps Compose UI screen pixel offsets inside a preview viewport size (px)
   * to normalized UV coordinates (0.0f..1.0f).
   */
  fun normalizeFromPreviewPixels(
    centerPxOffset: Offset,
    overlayPxSize: Size,
    viewportPxSize: Size,
    rotationDegrees: Float = 0.0f,
    alpha: Float = 1.0f
  ): NormalizedCoordinates {
    val uCenterX = if (viewportPxSize.width > 0f) (centerPxOffset.x / viewportPxSize.width).coerceIn(0f, 1f) else 0.5f
    val vCenterY = if (viewportPxSize.height > 0f) (centerPxOffset.y / viewportPxSize.height).coerceIn(0f, 1f) else 0.5f
    val normWidth = if (viewportPxSize.width > 0f) (overlayPxSize.width / viewportPxSize.width).coerceIn(0f, 1f) else 0.3f
    val normHeight = if (viewportPxSize.height > 0f) (overlayPxSize.height / viewportPxSize.height).coerceIn(0f, 1f) else 0.2f

    return NormalizedCoordinates(
      uCenterX = uCenterX,
      vCenterY = vCenterY,
      normWidth = normWidth,
      normHeight = normHeight,
      rotationDegrees = rotationDegrees,
      alpha = alpha
    )
  }

  /**
   * Maps normalized UV coordinates to absolute target pixel coordinates for export.
   */
  fun toTargetAbsolutePixels(norm: NormalizedCoordinates): TargetPixelCoordinates {
    val widthPx = norm.normWidth * targetResolution.width
    val heightPx = norm.normHeight * targetResolution.height
    val centerPxX = norm.uCenterX * targetResolution.width
    val centerPxY = norm.vCenterY * targetResolution.height

    return TargetPixelCoordinates(
      centerPxX = centerPxX,
      centerPxY = centerPxY,
      widthPx = widthPx,
      heightPx = heightPx,
      rotationDegrees = norm.rotationDegrees,
      alpha = norm.alpha
    )
  }
}

// =========================================================================================
// EXTENSION FUNCTIONS FOR MEDIA3 OVERLAYSETTINGS
// =========================================================================================

/**
 * Translates NormalizedCoordinates into Media3 [OverlaySettings] for GPU-accelerated rendering.
 * Media3 uses Normalized Device Coordinates (NDC) where center is (0, 0) and Y points upwards [-1.0, 1.0].
 */
@OptIn(UnstableApi::class)
fun NormalizedCoordinates.toMedia3OverlaySettings(): OverlaySettings {
  // Translate UV space [0.0..1.0] to OpenGL NDC space [-1.0..1.0]
  val ndcCenterX = (uCenterX * 2f) - 1.0f
  val ndcCenterY = 1.0f - (vCenterY * 2f) // Invert Y axis for OpenGL coordinate frame

  return OverlaySettings.Builder()
    .setOverlayFrameAnchor(0f, 0f) // Center anchor point of overlay texture
    .setBackgroundFrameAnchor(ndcCenterX, ndcCenterY)
    .setScale(normWidth, normHeight)
    .setRotationDegrees(-rotationDegrees) // Media3 rotates counter-clockwise
    .setAlphaScale(alpha)
    .build()
}

/**
 * Extension on Compose DpOffset & DpSize to directly produce Media3 [OverlaySettings].
 */
@OptIn(UnstableApi::class)
fun DpOffset.toMedia3OverlaySettings(
  overlayDpSize: DpSize,
  viewportDpSize: DpSize,
  rotationDegrees: Float = 0.0f,
  alpha: Float = 1.0f
): OverlaySettings {
  val service = CoordinateNormalizationService()
  val norm = service.normalizeFromComposeDp(
    centerDpOffset = this,
    overlayDpSize = overlayDpSize,
    viewportDpSize = viewportDpSize,
    rotationDegrees = rotationDegrees,
    alpha = alpha
  )
  return norm.toMedia3OverlaySettings()
}
