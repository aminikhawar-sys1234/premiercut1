package com.example.domain.model

enum class AspectRatio(val label: String, val ratio: Float, val iconDesc: String) {
  RATIO_9_16("9:16", 9f / 16f, "TikTok / Reels / Shorts"),
  RATIO_16_9("16:9", 16f / 9f, "YouTube / Landscape"),
  RATIO_1_1("1:1", 1f, "Instagram Square"),
  RATIO_4_5("4:5", 4f / 5f, "Instagram Portrait"),
  RATIO_4_3("4:3", 4f / 3f, "Standard Landscape (4:3)"),
  RATIO_3_4("3:4", 3f / 4f, "Classic Portrait"),
  RATIO_21_9("21:9", 21f / 9f, "Cinematic Ultrawide (21:9)"),
  CUSTOM("Custom", 1f, "Freeform");

  companion object {
    fun resolveEffectiveDimensions(width: Int, height: Int, rotationDegrees: Int = 0): Pair<Int, Int> {
      if (width <= 0 || height <= 0) return Pair(1920, 1080)
      val isRotated90or270 = (rotationDegrees % 180 != 0)
      return if (isRotated90or270 && width > height) {
        Pair(height, width)
      } else {
        Pair(width, height)
      }
    }

    fun fromDimensions(width: Int, height: Int, rotationDegrees: Int = 0): AspectRatio {
      if (width <= 0 || height <= 0) return RATIO_16_9
      val (effWidth, effHeight) = resolveEffectiveDimensions(width, height, rotationDegrees)
      val targetRatio = effWidth.toFloat() / effHeight.toFloat()
      val standardRatios = listOf(
        RATIO_9_16,
        RATIO_16_9,
        RATIO_1_1,
        RATIO_4_5,
        RATIO_4_3,
        RATIO_3_4,
        RATIO_21_9
      )
      return standardRatios.minByOrNull { kotlin.math.abs(it.ratio - targetRatio) } ?: RATIO_16_9
    }
  }
}

enum class Resolution(
  val label: String,
  val width: Int,
  val height: Int,
  val category: String = "Standard"
) {
  RES_480P("480p SD", 480, 854, "Standard"),
  RES_720P("720p HD", 720, 1280, "Standard"),
  RES_1080P("Full HD (1080p)", 1080, 1920, "Standard"),
  RES_2K("2K QHD (1440p)", 1440, 2560, "Professional"),
  RES_4K("4K UHD (2160p)", 2160, 3840, "Ultra HD"),
  RES_VERTICAL_2K("Vertical 2K (1440×2560)", 1440, 2560, "Vertical Pro"),
  RES_VERTICAL_4K("Vertical 4K (2160×3840)", 2160, 3840, "Vertical Ultra"),
  RES_SQUARE_2K("Square 2K (2048×2048)", 2048, 2048, "Square Pro")
}

enum class FrameRate(val fps: Int) {
  FPS_24(24),
  FPS_25(25),
  FPS_30(30),
  FPS_50(50),
  FPS_60(60)
}

enum class ExportQuality(
  val label: String,
  val bitrateMultiplier: Float,
  val description: String = ""
) {
  DRAFT("Draft", 0.5f, "Fast preview rendering"),
  STANDARD("Standard", 1.0f, "Balanced quality"),
  HIGH("High", 1.6f, "High-quality export"),
  ULTRA("Ultra", 2.4f, "Maximum quality, optimized for 4K / 2K"),
  CUSTOM("Custom", 2.0f, "Pro manual bitrate");

  companion object {
    val LOW get() = DRAFT
    val MEDIUM get() = STANDARD
  }
}

