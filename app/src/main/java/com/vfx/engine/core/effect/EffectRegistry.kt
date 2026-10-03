package com.vfx.engine.core.effect

/**
 * Thread-safe registry for effect definitions.
 */
class EffectRegistry {
    private val definitions = LinkedHashMap<String, EffectDefinition>()

    @Synchronized
    fun register(definition: EffectDefinition) {
        definitions[definition.id] = definition
        definition.aliases.forEach { alias ->
            definitions.putIfAbsent(alias, definition)
        }
    }

    @Synchronized
    fun get(id: String): EffectDefinition? = definitions[id]

    @Synchronized
    fun all(): List<EffectDefinition> = definitions.values.distinct()

    @Synchronized
    fun byCategory(category: EffectCategory): List<EffectDefinition> =
        all().filter { it.category == category }

    @Synchronized
    fun createInstance(id: String): EffectInstance {
        val def = get(id) ?: throw IllegalArgumentException("Effect not found: $id")
        return EffectInstance(def)
    }

    companion object {
        val default = EffectRegistry()
    }
}
