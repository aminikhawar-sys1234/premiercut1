package com.example.data.presets

data class StockMediaItem(
  val id: String,
  val title: String,
  val category: String, // "Drone & Travel", "Urban & Cyberpunk", "Nature & Landscapes", "Portraits & Aesthetic", "Textures & Solids"
  val isVideo: Boolean,
  val durationMs: Long,
  val gradientStart: Long,
  val gradientEnd: Long,
  val iconEmoji: String,
  val description: String,
  val resolution: String = "1080p",
  val uri: String = ""
)

object StockMediaCatalog {
  val stockItems = listOf(
    // Videos
    StockMediaItem(
      id = "stock_vid_1",
      title = "Coastal Drone Flyover",
      category = "Drone & Travel",
      isVideo = true,
      durationMs = 4500L,
      gradientStart = 0xFF0077B6,
      gradientEnd = 0xFF00B4D8,
      iconEmoji = "🌊",
      description = "Smooth 4K scenic drone footage sweeping over turquoise ocean coast and cliffs.",
      resolution = "4K UHD"
    ),
    StockMediaItem(
      id = "stock_vid_2",
      title = "Cyberpunk Tokyo Rain",
      category = "Urban & Cyberpunk",
      isVideo = true,
      durationMs = 5000L,
      gradientStart = 0xFF3A0CA3,
      gradientEnd = 0xFF7209B7,
      iconEmoji = "🏮",
      description = "Neon illuminated night street reflection in rain puddles with glowing storefronts.",
      resolution = "1080p 60fps"
    ),
    StockMediaItem(
      id = "stock_vid_3",
      title = "Mountain Sunset Timelapse",
      category = "Nature & Landscapes",
      isVideo = true,
      durationMs = 6000L,
      gradientStart = 0xFFE85D04,
      gradientEnd = 0xFFFAA307,
      iconEmoji = "🌄",
      description = "Golden hour warm rays sinking behind jagged alpine peaks and moving clouds.",
      resolution = "4K UHD"
    ),
    StockMediaItem(
      id = "stock_vid_4",
      title = "Downtown Traffic Bokeh",
      category = "Urban & Cyberpunk",
      isVideo = true,
      durationMs = 4000L,
      gradientStart = 0xFFD00000,
      gradientEnd = 0xFFFFBA08,
      iconEmoji = "🚗",
      description = "Aesthetic blurred car headlights and streetlights creating smooth circular bokeh.",
      resolution = "1080p"
    ),
    StockMediaItem(
      id = "stock_vid_5",
      title = "Tropical Palm Breeze",
      category = "Nature & Landscapes",
      isVideo = true,
      durationMs = 4200L,
      gradientStart = 0xFF2D6A4F,
      gradientEnd = 0xFF52B788,
      iconEmoji = "🌴",
      description = "Gentle palm leaves swaying in summer sunlight with clear cyan sky.",
      resolution = "1080p 60fps"
    ),
    StockMediaItem(
      id = "stock_vid_6",
      title = "Studio Bokeh Particles",
      category = "Textures & Solids",
      isVideo = true,
      durationMs = 5000L,
      gradientStart = 0xFF10002B,
      gradientEnd = 0xFF240046,
      iconEmoji = "✨",
      description = "Floating golden dust motes and gentle light flares in dark space.",
      resolution = "1080p"
    ),
    StockMediaItem(
      id = "stock_vid_7",
      title = "Aesthetic Coffee Pour",
      category = "Portraits & Aesthetic",
      isVideo = true,
      durationMs = 3500L,
      gradientStart = 0xFF4A2810,
      gradientEnd = 0xFF8A5A36,
      iconEmoji = "☕",
      description = "Close-up creamy latte art pouring into ceramic cup with soft morning light.",
      resolution = "1080p"
    ),

    // Images
    StockMediaItem(
      id = "stock_img_1",
      title = "Golden Hour Alpine Peak",
      category = "Nature & Landscapes",
      isVideo = false,
      durationMs = 3000L,
      gradientStart = 0xFFFF7B00,
      gradientEnd = 0xFFFFB703,
      iconEmoji = "🏔️",
      description = "Crisp high-resolution landscape photo of sunlit mountain ridges.",
      resolution = "4K Photo"
    ),
    StockMediaItem(
      id = "stock_img_2",
      title = "Neon Glow Portrait",
      category = "Portraits & Aesthetic",
      isVideo = false,
      durationMs = 3000L,
      gradientStart = 0xFF4361EE,
      gradientEnd = 0xFFF72585,
      iconEmoji = "👤",
      description = "Dual-tone cyberpunk portrait lit with vibrant magenta and cobalt neon.",
      resolution = "High Res"
    ),
    StockMediaItem(
      id = "stock_img_3",
      title = "Minimalist Architecture",
      category = "Portraits & Aesthetic",
      isVideo = false,
      durationMs = 3000L,
      gradientStart = 0xFF6C757D,
      gradientEnd = 0xFFCED4DA,
      iconEmoji = "🏛️",
      description = "Clean modern geometric concrete curves and dramatic shadows.",
      resolution = "4K Photo"
    ),
    StockMediaItem(
      id = "stock_img_4",
      title = "Mist Forest Pine Trees",
      category = "Nature & Landscapes",
      isVideo = false,
      durationMs = 3000L,
      gradientStart = 0xFF1B4332,
      gradientEnd = 0xFF2D6A4F,
      iconEmoji = "🌲",
      description = "Atmospheric dense pine woodland enveloped in soft morning fog.",
      resolution = "4K Photo"
    ),
    StockMediaItem(
      id = "stock_img_5",
      title = "Green Screen Backdrop",
      category = "Textures & Solids",
      isVideo = false,
      durationMs = 3000L,
      gradientStart = 0xFF00FF00,
      gradientEnd = 0xFF00CC00,
      iconEmoji = "🟩",
      description = "Pure Chroma Key bright green canvas for keying experiments.",
      resolution = "1080p Canvas"
    ),
    StockMediaItem(
      id = "stock_img_6",
      title = "Cyber Neon Gradient",
      category = "Textures & Solids",
      isVideo = false,
      durationMs = 3000L,
      gradientStart = 0xFF00F0FF,
      gradientEnd = 0xFF7000FF,
      iconEmoji = "🔮",
      description = "Smooth holographic gradient mesh background for overlays and titles.",
      resolution = "4K Texture"
    )
  )
}
