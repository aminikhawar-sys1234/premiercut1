package com.example.engine.composition

import android.graphics.Bitmap
import android.graphics.Color
import com.example.domain.model.ChromaKeySettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ChromaKeyProcessor {

  /**
   * Applies chroma key (green screen / custom color keying) to a Bitmap in-place or returns keyed copy.
   */
  fun applyChromaKey(source: Bitmap, settings: ChromaKeySettings): Bitmap {
    if (!settings.enabled) return source

    val targetColor = settings.targetColor.toInt()
    val targetR = Color.red(targetColor)
    val targetG = Color.green(targetColor)
    val targetB = Color.blue(targetColor)

    // Convert target color to normalized UV/chrominance distance or Euclidean RGB
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    val threshold = (settings.similarity + settings.edgeControl * 0.1f).coerceIn(0.01f, 1.0f) * 255f * 1.5f
    val smoothing = max(1f, settings.smoothness * 255f * 0.8f)
    val spill = settings.spillSuppression.coerceIn(0f, 1f)
    val isSolidBg = settings.backgroundType == "SolidColor"
    val bgColor = settings.backgroundColor.toInt()

    for (i in pixels.indices) {
      val pixel = pixels[i]
      val a = Color.alpha(pixel)
      if (a == 0) continue

      var r = Color.red(pixel)
      var g = Color.green(pixel)
      var b = Color.blue(pixel)

      // Distance from key color in RGB space
      val dist = sqrt(
        ((r - targetR) * (r - targetR) +
         (g - targetG) * (g - targetG) +
         (b - targetB) * (b - targetB)).toDouble()
      ).toFloat()

      if (dist < threshold) {
        pixels[i] = if (isSolidBg) bgColor else 0
      } else if (dist < threshold + smoothing) {
        // Feather edge
        val alphaFactor = (dist - threshold) / smoothing
        val newAlpha = (a * alphaFactor).toInt().coerceIn(0, 255)

        // Spill suppression
        if (spill > 0f) {
          val maxOther = max(r, b)
          if (targetG > targetR && targetG > targetB && g > maxOther) {
            g = (g * (1f - spill) + maxOther * spill).toInt().coerceIn(0, 255)
          } else if (targetB > targetR && targetB > targetG && b > max(r, g)) {
            b = (b * (1f - spill) + max(r, g) * spill).toInt().coerceIn(0, 255)
          }
        }
        val keyedPixel = Color.argb(newAlpha, r, g, b)
        pixels[i] = if (isSolidBg) {
          blendColors(bgColor, keyedPixel)
        } else {
          keyedPixel
        }
      } else {
        // Spill suppression on fringes
        if (spill > 0f) {
          val maxOther = max(r, b)
          if (targetG > targetR && targetG > targetB && g > maxOther) {
            val despilledG = (g * (1f - spill * 0.6f) + maxOther * (spill * 0.6f)).toInt().coerceIn(0, 255)
            pixels[i] = Color.argb(a, r, despilledG, b)
          }
        }
      }
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(pixels, 0, width, 0, 0, width, height)
    return output
  }

  private fun blendColors(bg: Int, fg: Int): Int {
    val fgA = Color.alpha(fg) / 255f
    if (fgA >= 1f) return fg
    if (fgA <= 0f) return bg
    val invA = 1f - fgA
    val r = (Color.red(fg) * fgA + Color.red(bg) * invA).toInt().coerceIn(0, 255)
    val g = (Color.green(fg) * fgA + Color.green(bg) * invA).toInt().coerceIn(0, 255)
    val b = (Color.blue(fg) * fgA + Color.blue(bg) * invA).toInt().coerceIn(0, 255)
    return Color.argb(255, r, g, b)
  }
}
