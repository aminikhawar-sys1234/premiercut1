package com.example.engine.composition.coordinates

import androidx.compose.ui.geometry.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlaySettings

/**
 * Universal resolution-independent coordinates (0.0f to 1.0f UV Space).
 * Guarantees pixel-perfect positioning across Preview Viewport & Export Output Canvas.
 */
data class NormalizedTransform(
  val centerX: Float = 0.5f,   // Center X in [0.0f, 1.0f]
  val centerY: Float = 0.5f,   // Center Y in [0.0f, 1.0f]
  val normWidth: Float = 0.35f, // Width relative to canvas width
  val normHeight: Float = 0.25f,// Height relative to canvas height
  val rotationDegrees: Float = 0.0f,
  val alpha: Float = 1.0f
) {

  /**
   * Converts 0..1 UV coordinates to Media3 OpenGL NDC space [-1.0, 1.0].
   */
  @androidx.annotation.OptIn(UnstableApi::class)
  fun toMedia3OverlaySettings(): OverlaySettings {
    val ndcCenterX = (centerX * 2f) - 1.0f
    val ndcCenterY = 1.0f - (centerY * 2f) // Invert Y axis for OpenGL ES

    return OverlaySettings.Builder()
      .setOverlayFrameAnchor(0f, 0f) // Center anchor
      .setBackgroundFrameAnchor(ndcCenterX, ndcCenterY)
      .setScale(normWidth, normHeight)
      .setRotationDegrees(-rotationDegrees)
      .setAlphaScale(alpha)
      .build()
  }

  /**
   * Converts normalized 0..1 UV coordinates to Preview pixels.
   */
  fun toPreviewPixels(previewSize: Size): OverlayPixelBounds {
    val widthPx = normWidth * previewSize.width
    val heightPx = normHeight * previewSize.height
    val leftPx = (centerX * previewSize.width) - (widthPx / 2f)
    val topPx = (centerY * previewSize.height) - (heightPx / 2f)
    return OverlayPixelBounds(leftPx, topPx, widthPx, heightPx, rotationDegrees, alpha)
  }
}

data class OverlayPixelBounds(
  val left: Float,
  val top: Float,
  val width: Float,
  val height: Float,
  val rotation: Float,
  val alpha: Float
)
