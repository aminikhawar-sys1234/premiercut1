package com.vfx.engine.effects

import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.effect.EffectRegistry

/**
 * Registers all 30+ production GPU effects into an [EffectRegistry].
 * Call once at app startup: `BuiltinEffects.registerAll(registry)`
 */
object BuiltinEffects {

    val ALL: List<EffectDefinition> by lazy {
        buildList {
            addAll(ColorEffects.all())
            addAll(BlurEffects.all())
            addAll(LightEffects.all())
            addAll(DistortEffects.all())
            addAll(StylizeEffects.all())
            addAll(LutEffects.all())
            addAll(TemporalEffects.all())
        }
    }

    fun registerAll(registry: EffectRegistry) {
        ALL.forEach { registry.register(it) }
    }

    fun byCategory(category: EffectCategory): List<EffectDefinition> =
        ALL.filter { it.category == category }
}
