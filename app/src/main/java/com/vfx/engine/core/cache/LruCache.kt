package com.vfx.engine.core.cache

/**
 * Deterministic, thread-safe LRU cache used for shader programs, LUT textures,
 * curve textures and effect definitions. Keys must be content-derived
 * (deterministic) so cache hits are stable across runs.
 */
class LruCache<K, V>(private val maxSize: Int) {

    private val map = LinkedHashMap<K, V>(16, 0.75f, true)

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? {
        val old = map.put(key, value)
        evictIfNeeded()
        return old
    }

    @Synchronized
    fun getOrPut(key: K, loader: (K) -> V): V {
        map[key]?.let { return it }
        val v = loader(key)
        map[key] = v
        evictIfNeeded()
        return v
    }

    @Synchronized
    fun remove(key: K, releaser: ((V) -> Unit)? = null): V? {
        val v = map.remove(key) ?: return null
        releaser?.invoke(v)
        return v
    }

    @Synchronized
    fun removeStale(predicate: (K, V) -> Boolean, releaser: ((V) -> Unit)? = null) {
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (predicate(e.key, e.value)) { it.remove(); releaser?.invoke(e.value) }
        }
    }

    @Synchronized
    fun clear(releaser: ((V) -> Unit)? = null) {
        releaser?.let { rel -> map.values.forEach(rel) }
        map.clear()
    }

    @Synchronized
    fun snapshotKeys(): List<K> = map.keys.toList()

    val size: Int
        @Synchronized get() = map.size

    private fun evictIfNeeded() {
        while (map.size > maxSize) {
            val eldest = map.entries.iterator()
            eldest.next()
            eldest.remove()
        }
    }
}
