package com.vfx.engine.core.color

import com.vfx.engine.core.math.Color
import com.vfx.engine.core.math.MathUtils
import kotlin.math.pow

enum class ToneMapperType {
  LINEAR,
  ACES,
  REINHARD,
  HABLE
}

object ToneMapper {
  fun applyToneMap(color: Color, type: ToneMapperType): Color {
    return when (type) {
      ToneMapperType.LINEAR -> Color(
        MathUtils.clamp(color.r, 0f, 1f),
        MathUtils.clamp(color.g, 0f, 1f),
        MathUtils.clamp(color.b, 0f, 1f),
        color.a
      )
      ToneMapperType.REINHARD -> Color(
        color.r / (1f + color.r),
        color.g / (1f + color.g),
        color.b / (1f + color.b),
        color.a
      )
      ToneMapperType.ACES -> {
        // ACES Narkowicz approximation
        fun aces(x: Float): Float {
          val a = 2.51f
          val b = 0.03f
          val c = 2.43f
          val d = 0.59f
          val e = 0.14f
          return MathUtils.clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0f, 1f)
        }
        Color(aces(color.r), aces(color.g), aces(color.b), color.a)
      }
      ToneMapperType.HABLE -> {
        fun hable(x: Float): Float {
          val A = 0.15f
          val B = 0.50f
          val C = 0.10f
          val D = 0.20f
          val E = 0.02f
          val F = 0.30f
          return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F
        }
        val white = 11.2f
        val w = hable(white)
        Color(hable(color.r) / w, hable(color.g) / w, hable(color.b) / w, color.a)
      }
    }
  }
}

object ColorEngine {
  fun sRgbToLinear(c: Float): Float {
    return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
  }

  fun linearToSRgb(c: Float): Float {
    return if (c <= 0.0031308f) c * 12.92f else 1.055f * c.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f
  }

  fun sRgbToLinear(color: Color): Color {
    return Color(sRgbToLinear(color.r), sRgbToLinear(color.g), sRgbToLinear(color.b), color.a)
  }

  fun linearToSRgb(color: Color): Color {
    return Color(linearToSRgb(color.r), linearToSRgb(color.g), linearToSRgb(color.b), color.a)
  }
}
