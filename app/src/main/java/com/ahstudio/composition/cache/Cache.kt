package com.ahstudio.composition.cache

import com.ahstudio.composition.graph.CompositionGraph

object StateHasher {
    fun fnv(seed: Long, vararg longs: Long): Long {
        var h = seed
        for (v in longs) { h = h xor v; h *= 0x100000001b3L }
        return h
    }
    fun of(v: Float) = v.toRawBits().toLong()
    fun of(b: Boolean) = if (b) 1L else 0L
}

/** LRU frame cache over GPU textures with byte accounting (pool-backed). */
class FrameCache(private val maxBytes: Long) {
    data class Key(val compositionId: Long, val timeUs: Long, val w: Int, val h: Int, val stateHash: Long)
    data class Entry(val textureId: Int, val bytes: Long)
    private val map = LinkedHashMap<Key, Entry>(16, 0.75f, true)
    private var bytes = 0L

    @Synchronized fun get(k: Key): Entry? = map[k]

    @Synchronized fun put(k: Key, e: Entry, evict: (Entry) -> Unit) {
        map[k]?.let { old -> bytes -= old.bytes; evict(old) }
        map[k] = e; bytes += e.bytes
        val it = map.entries.iterator()
        while (bytes > maxBytes && it.hasNext()) {
            val en = it.next()
            bytes -= en.value.bytes
            evict(en.value)
            it.remove()
        }
    }

    @Synchronized fun clear(evict: (Entry) -> Unit) {
        map.values.forEach(evict)
        map.clear()
        bytes = 0
    }

    @Synchronized fun sizeBytes() = bytes
}

/** Deterministic invalidation: any state change flows into the state hash → stale frames impossible. */
class CacheInvalidator(private val graph: CompositionGraph) {
    fun currentGeneration(): Long = graph.structureVersion * 31L + graph.propertyVersions.values.fold(0L) { a, b -> a xor b }
}
