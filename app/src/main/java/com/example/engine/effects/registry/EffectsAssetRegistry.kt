package com.example.engine.effects.registry

import androidx.compose.runtime.mutableStateListOf
import com.example.domain.model.EffectType

/**
 * Categories for the clean Effects system.
 */
enum class EffectCategory(val displayName: String, val tag: String) {
  VIDEO_EFFECTS("Video Effects", "nav_video_effects"),
  BODY_EFFECTS("Body Effects", "nav_body_effects"),
  PHOTO_EFFECTS("Photo Effects", "nav_photo_effects"),
  AI_EFFECTS("AI Effects", "nav_ai_effects")
}

/**
 * Data specification for a registered Effect Asset / Plugin.
 */
data class RegisteredEffect(
  val id: String,
  val name: String,
  val category: EffectCategory,
  val effectType: EffectType? = null,
  val shaderKey: String? = null,
  val intensity: Float = 1.0f,
  val previewThumbnailUrl: String? = null,
  val isCustom: Boolean = false
)

/**
 * Production-ready, extensible Effects Asset Registry.
 *
 * CRITICAL RULE:
 * Absolutely NO dummy, fake, sample, or placeholder effect assets are seeded here.
 * The registry starts completely EMPTY. Real plugins and effect asset packages register
 * through these methods at runtime.
 */
object EffectsAssetRegistry {

  private val _effects = mutableStateListOf<RegisteredEffect>()

  // --- Registration API for Plugins and Asset Bundles ---

  fun registerEffect(effect: RegisteredEffect) {
    if (_effects.none { it.id == effect.id }) {
      _effects.add(effect)
    }
  }

  fun unregisterEffect(effectId: String) {
    _effects.removeAll { it.id == effectId }
  }

  // --- Query API ---

  fun getEffects(category: EffectCategory): List<RegisteredEffect> {
    return _effects.filter { it.category == category }
  }

  fun searchEffects(category: EffectCategory, query: String): List<RegisteredEffect> {
    val catEffects = getEffects(category)
    if (query.isBlank()) return catEffects
    return catEffects.filter {
      it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
    }
  }

  fun getAllEffects(): List<RegisteredEffect> = _effects.toList()

  /**
   * Resets all registered items. Ensures zero leftover mock or temporary data.
   */
  fun clearAll() {
    _effects.clear()
  }
}
