package com.vfx.engine.core.registry

import com.vfx.engine.core.EffectEngineException
import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.effect.EffectInstance

/**
 * Extensible effect registry — the single extension point of the engine.
 * Adding a new effect = registering a definition. Nothing else changes.
 *
 * Pure core class: NO reference to built-in effects (that would invert the
 * module graph). Hosts compose their registry explicitly:
 *   val registry = EffectRegistry().also { BuiltinEffects.install(it) }
 */
class EffectRegistry {

    private val canonical = LinkedHashMap<String, EffectDefinition>() // real definitions only
    private val byId = LinkedHashMap<String, EffectDefinition>()      // ids + aliases -> def

    /** Register a definition. Aliases resolve to the same definition. */
    @Synchronized
    fun registerEffect(def: EffectDefinition) {
        check(!canonical.containsKey(def.id)) { "Effect '${def.id}' already registered" }
        canonical[def.id] = def
        byId[def.id] = def
        def.aliases.forEach { alias ->
            check(!byId.containsKey(alias)) { "Alias '$alias' conflicts with existing id/alias" }
            byId[alias] = def
        }
    }

    @Synchronized
    fun unregisterEffect(id: String): Boolean {
        val def = canonical.remove(id) ?: return false
        byId.entries.removeAll { it.value === def }
        return true
    }

    @Synchronized
    fun hasEffect(id: String): Boolean = byId.containsKey(id)

    /** @throws UnsupportedFeature if not registered. */
    @Synchronized
    fun getEffect(id: String): EffectDefinition =
        byId[id] ?: throw EffectEngineException.UnsupportedFeature("effect '$id'", "not registered")

    @Synchronized
    fun getEffectOrNull(id: String): EffectDefinition? = byId[id]

    @Synchronized
    fun createEffect(id: String): EffectInstance = EffectInstance(getEffect(id))

    /** Canonical definitions (aliases excluded), in registration order. */
    @Synchronized
    fun listEffects(): List<EffectDefinition> = canonical.values.toList()

    @Synchronized
    fun listByCategory(category: EffectCategory): List<EffectDefinition> =
        canonical.values.filter { it.category == category }

    @Synchronized
    fun listIds(): List<String> = canonical.keys.toList()

    @Synchronized
    fun size(): Int = canonical.size

    companion object {
        val shared = EffectRegistry()

        @JvmStatic
        fun register(def: EffectDefinition, factory: (() -> Any)? = null) {
            if (!shared.hasEffect(def.id)) {
                shared.registerEffect(def)
            }
        }

        @JvmStatic
        fun getDefinition(id: String): EffectDefinition? = shared.getEffectOrNull(id)

        @JvmStatic
        fun getAllDefinitions(): List<EffectDefinition> = shared.listEffects()
    }
}
