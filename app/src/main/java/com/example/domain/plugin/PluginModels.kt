package com.example.domain.plugin

import java.io.File

enum class PluginCategory(val key: String, val displayName: String, val iconName: String) {
  FILTER("filter", "Filters", "Filter"),
  EFFECT("effect", "Effects", "AutoAwesome"),
  STICKER("sticker", "Stickers", "EmojiEmotions"),
  FONT("font", "Fonts", "TextFields"),
  TEXT_TEMPLATE("text_template", "Text Templates", "Title"),
  TRANSITION("transition", "Transitions", "Transform"),
  OVERLAY("overlay", "Overlays", "Layers"),
  AUDIO("audio", "Audio & Sound FX", "Audiotrack"),
  BACKGROUND("background", "Backgrounds", "Wallpaper"),
  ANIMATION("animation", "Animations", "Animation"),
  AI_TOOL("ai_tool", "AI Tools", "Psychology"),
  OTHER("other", "Other Extensions", "Extension");

  companion object {
    fun fromKey(key: String): PluginCategory {
      val normalized = key.trim().lowercase().replace("-", "_").replace(" ", "_")
      return when {
        normalized in listOf("font", "fonts", "typography", "urdu_font", "english_font", "calligraphy", "font_pack") -> FONT
        normalized in listOf("text_template", "template", "templates", "text_templates", "caption", "captions", "quote", "quotes", "title", "titles", "reels", "youtube", "islamic", "business") -> TEXT_TEMPLATE
        normalized in listOf("filter", "filters", "lut", "luts", "color_grade", "color_grading", "video_filter", "video_filters", "cinematic", "preset", "presets", "filter_pack") -> FILTER
        normalized in listOf("effect", "effects", "fx", "vfx") -> EFFECT
        normalized in listOf("sticker", "stickers", "badge", "badges", "emoji", "emojis") -> STICKER
        normalized in listOf("transition", "transitions") -> TRANSITION
        normalized in listOf("overlay", "overlays", "pip") -> OVERLAY
        normalized in listOf("audio", "audios", "sound", "sounds", "music", "sfx") -> AUDIO
        normalized in listOf("background", "backgrounds", "bg") -> BACKGROUND
        normalized in listOf("animation", "animations", "anim") -> ANIMATION
        normalized in listOf("ai_tool", "ai_tools", "ai") -> AI_TOOL
        normalized in listOf("items", "assets", "item", "asset", "plugin", "plugins") -> OTHER
        else -> values().find { 
          it.key.equals(normalized, ignoreCase = true) || 
          it.name.equals(normalized, ignoreCase = true) ||
          it.displayName.equals(key, ignoreCase = true)
        } ?: OTHER
      }
    }
  }
}

data class PluginItemManifest(
  val id: String,
  val name: String,
  val description: String = "",
  val preview: String = "", // relative file path in ZIP or asset name
  val file: String = "",    // relative asset file path in ZIP (e.g. .ttf, .png, .mp3, .cube)
  val emoji: String = "🎬",
  val categoryKey: String = "", // Specific category for multi-asset plugins (font, text_template, etc.)
  val parameters: Map<String, Any> = emptyMap()
) {
  val itemCategory: PluginCategory get() = if (categoryKey.isNotBlank() && categoryKey != "items" && categoryKey != "assets") {
    PluginCategory.fromKey(categoryKey)
  } else {
    PluginCategory.OTHER
  }

  // Helper getters for parameters
  val brightness: Float get() = (parameters["brightness"] as? Number)?.toFloat() ?: 0f
  val contrast: Float get() = (parameters["contrast"] as? Number)?.toFloat() ?: 1f
  val saturation: Float get() = (parameters["saturation"] as? Number)?.toFloat() ?: 1f
  val temperature: Float get() = (parameters["temperature"] as? Number)?.toFloat() ?: 0f
  val tint: Float get() = (parameters["tint"] as? Number)?.toFloat() ?: 0f
  val vignette: Float get() = (parameters["vignette"] as? Number)?.toFloat() ?: 0f
  val colorMatrix: FloatArray? get() {
    val list = parameters["colorMatrix"] as? List<*>
    if (list != null && list.size >= 20) {
      return FloatArray(20) { idx -> (list[idx] as? Number)?.toFloat() ?: 0f }
    }
    return null
  }
}

data class PluginManifest(
  val id: String,
  val name: String,
  val version: String = "1.0.0",
  val author: String = "Unknown Creator",
  val description: String = "",
  val type: String = "other", // filter, sticker, font, text_template, etc.
  val minimumAppVersion: String = "1.0.0",
  val icon: String = "",
  val items: List<PluginItemManifest> = emptyList()
) {
  val category: PluginCategory get() = PluginCategory.fromKey(type)
}

data class InstalledPlugin(
  val manifest: PluginManifest,
  val installDirAbsolutePath: String,
  val isEnabled: Boolean = true,
  val installedTimestamp: Long = System.currentTimeMillis()
) {
  fun getItemFile(item: PluginItemManifest): File? {
    if (item.file.isBlank()) return null
    val f = File(installDirAbsolutePath, item.file)
    return if (f.exists()) f else null
  }

  fun getItemPreviewFile(item: PluginItemManifest): File? {
    if (item.preview.isBlank()) return null
    val f = File(installDirAbsolutePath, item.preview)
    return if (f.exists()) f else null
  }
}

sealed class PluginValidationResult {
  data class Success(val installedPlugin: InstalledPlugin, val message: String) : PluginValidationResult()
  data class Error(val reason: String) : PluginValidationResult()
}
