package com.example.engine.text.registry

import androidx.compose.runtime.mutableStateListOf
import com.example.domain.model.TextClip

/**
 * Data specification for a registered Text Template.
 * Extensible for third-party template packs and dynamic asset plugins.
 */
data class RegisteredTextTemplate(
  val id: String,
  val name: String,
  val category: String = "General",
  val templateClip: TextClip,
  val previewThumbnailUrl: String? = null
)

/**
 * Data specification for a registered Font Asset.
 */
data class RegisteredFont(
  val id: String,
  val name: String,
  val category: String, // "My Fonts", "Urdu Fonts", "English Fonts", "Arabic", "Hindi", "Chinese", "Other supported languages"
  val fontFamilyName: String,
  val fontFilePath: String? = null,
  val isCustom: Boolean = false
)

/**
 * Data specification for a registered Text Animation.
 */
data class RegisteredTextAnimation(
  val id: String,
  val name: String,
  val category: String, // "In", "Out", "Combo"
  val animationKey: String
)

/**
 * Data specification for a registered Text Style.
 */
data class RegisteredTextStyle(
  val id: String,
  val name: String,
  val strokeWidth: Float = 0f,
  val strokeColor: Long = 0xFF000000,
  val textColor: Long = 0xFFFFFFFF,
  val shadowBlur: Float = 0f
)

/**
 * Data specification for a registered 3D Text Asset.
 */
data class Registered3DText(
  val id: String,
  val name: String,
  val depth3D: Float,
  val bevelAngle3D: Float = 0f,
  val color3D: Long = 0xFF1E293B
)

/**
 * Data specification for a registered Text Effect.
 */
data class RegisteredTextEffect(
  val id: String,
  val name: String,
  val effectKey: String
)

/**
 * Production-ready, extensible Text Asset Registry.
 *
 * CRITICAL RULE:
 * Absolutely NO dummy, fake, sample, or placeholder assets are seeded here.
 * The registry starts completely EMPTY. Real plugins and asset packages register
 * through these methods at runtime.
 */
object TextAssetRegistry {

  private val _templates = mutableStateListOf<RegisteredTextTemplate>()
  private val _fonts = mutableStateListOf<RegisteredFont>()
  private val _animations = mutableStateListOf<RegisteredTextAnimation>()
  private val _styles = mutableStateListOf<RegisteredTextStyle>()
  private val _threeDAssets = mutableStateListOf<Registered3DText>()
  private val _effects = mutableStateListOf<RegisteredTextEffect>()

  // --- Registration API for Plugins and Asset Bundles ---

  fun registerTemplate(template: RegisteredTextTemplate) {
    if (_templates.none { it.id == template.id }) {
      _templates.add(template)
    }
  }

  fun registerFont(font: RegisteredFont) {
    if (_fonts.none { it.id == font.id }) {
      _fonts.add(font)
    }
  }

  fun registerAnimation(animation: RegisteredTextAnimation) {
    if (_animations.none { it.id == animation.id }) {
      _animations.add(animation)
    }
  }

  fun registerStyle(style: RegisteredTextStyle) {
    if (_styles.none { it.id == style.id }) {
      _styles.add(style)
    }
  }

  fun register3DAsset(asset: Registered3DText) {
    if (_threeDAssets.none { it.id == asset.id }) {
      _threeDAssets.add(asset)
    }
  }

  fun registerEffect(effect: RegisteredTextEffect) {
    if (_effects.none { it.id == effect.id }) {
      _effects.add(effect)
    }
  }

  // --- Query API ---

  fun getTemplates(): List<RegisteredTextTemplate> = _templates.toList()

  fun getFonts(category: String): List<RegisteredFont> {
    return _fonts.filter { it.category.equals(category, ignoreCase = true) }
  }

  fun getAllFonts(): List<RegisteredFont> = _fonts.toList()

  fun getAnimations(category: String): List<RegisteredTextAnimation> {
    return _animations.filter { it.category.equals(category, ignoreCase = true) }
  }

  fun getAllAnimations(): List<RegisteredTextAnimation> = _animations.toList()

  fun getStyles(): List<RegisteredTextStyle> = _styles.toList()

  fun get3DAssets(): List<Registered3DText> = _threeDAssets.toList()

  fun getEffects(): List<RegisteredTextEffect> = _effects.toList()

  /**
   * Resets all registered items. Ensures zero leftover mock or temporary data.
   */
  fun clearAll() {
    _templates.clear()
    _fonts.clear()
    _animations.clear()
    _styles.clear()
    _threeDAssets.clear()
    _effects.clear()
  }
}
