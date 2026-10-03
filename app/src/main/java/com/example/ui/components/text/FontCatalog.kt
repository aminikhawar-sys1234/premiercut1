package com.example.ui.components.text

import androidx.compose.runtime.Immutable
import com.example.engine.text.registry.RegisteredFont
import com.example.engine.text.registry.TextAssetRegistry
import com.example.util.FontOption

@Immutable
data class BrandFontPreset(
  val id: String,
  val name: String,
  val fontFamily: String,
  val customFontPath: String? = null,
  val defaultColor: Long = 0xFFFFFFFF,
  val fontWeight: Int = 800,
  val letterSpacing: Float = 0f
)

/**
 * Clean font catalog referencing only real installed/registered fonts.
 * No hardcoded fake or dummy fonts.
 */
object FontCatalog {

  val FONT_CATEGORIES = listOf(
    "My Fonts",
    "Urdu Fonts",
    "English Fonts",
    "Arabic",
    "Hindi",
    "Chinese",
    "Other supported languages"
  )

  private val _brandFonts = mutableListOf<BrandFontPreset>()

  fun getBrandFonts(): List<BrandFontPreset> = _brandFonts.toList()

  fun addBrandFont(preset: BrandFontPreset) {
    if (_brandFonts.none { it.id == preset.id }) {
      _brandFonts.add(preset)
    }
  }

  fun getFontsForCategory(
    category: String,
    allAvailableFonts: List<FontOption> = emptyList()
  ): List<FontOption> {
    val registered = TextAssetRegistry.getFonts(category).map {
      FontOption(
        id = it.fontFamilyName,
        name = it.name,
        category = it.category,
        isCustom = it.isCustom,
        filePath = it.fontFilePath
      )
    }
    return registered + allAvailableFonts.filter { it.category.equals(category, ignoreCase = true) }
  }
}
