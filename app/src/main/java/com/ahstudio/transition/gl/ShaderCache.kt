package com.ahstudio.transition.gl

import com.ahstudio.transition.core.TransitionResult
import com.ahstudio.transition.diag.TransitionDiagnostics

/**
 * LRU program cache keyed by source hash + defines (§25). GL objects are context-bound:
 * one cache per GL context (preview and export each own an engine → own cache).
 */
class TransitionShaderCache(private val diagnostics: TransitionDiagnostics,
                            private val maxEntries: Int = 48) {
    private val lru = LinkedHashMap<String, GlProgram>(16, 0.75f, true)

    var hits = 0L
        private set
    var misses = 0L
        private set

    fun getOrCreate(key: String, compile: () -> TransitionResult<GlProgram>): TransitionResult<GlProgram> {
        lru.remove(key)?.let { hits++; return TransitionResult.Ok(it) }
        misses++
        return when (val r = compile()) {
            is TransitionResult.Ok -> {
                if (lru.size >= maxEntries) {
                    val iterator = lru.entries.iterator()
                    val eldest = iterator.next()
                    iterator.remove()
                    eldest.value.close()
                }
                lru[key] = r.value
                TransitionResult.Ok(r.value)
            }
            is TransitionResult.Err -> {
                diagnostics.onError(r.error)
                r
            }
        }
    }

    /** Call on GL context loss — all cached handles are invalid. */
    fun onContextLost() {
        lru.values.forEach { it.close() }
        lru.clear()
        diagnostics.log("ShaderCache cleared (context loss)")
    }
}
