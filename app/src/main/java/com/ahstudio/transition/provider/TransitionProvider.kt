package com.ahstudio.transition.provider

import com.ahstudio.transition.core.TransitionDefinition

interface TransitionProvider { fun loadDefinitions(): List<TransitionDefinition> }

/**
 * Immutable registry of available definitions. First provider wins on id collision —
 * put the bundled provider first so remote packages can never override builtins.
 */
class TransitionRegistry(providers: List<TransitionProvider>) {
    private val definitions = LinkedHashMap<String, TransitionDefinition>()

    init {
        providers.forEach { p ->
            runCatching { p.loadDefinitions() }.getOrDefault(emptyList())
                .forEach { d -> definitions.putIfAbsent(d.id, d) }
        }
    }

    fun definition(id: String): TransitionDefinition? = definitions[id]
    fun all(): List<TransitionDefinition> = definitions.values.toList()
}
