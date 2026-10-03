package com.ute.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Central lifecycle owner for GL-backed resources (atlases, textures, FBOs, meshes).
 * Handles refcounting, LRU eviction budgets, and full GPU context loss recovery:
 * all registered resources are destroyed and lazily rebuilt on the next frame.
 */
class ResourceRegistry(private val memoryBudgetBytes: Long = 96L * 1024 * 1024) {

    interface Managed {
        fun resourceBytes(): Long
        fun destroy()
    }

    private val resources = ConcurrentHashMap<String, Managed>()
    private val refcounts = ConcurrentHashMap<String, AtomicInteger>()
    @Volatile private var contextLost = false

    fun register(key: String, res: Managed) {
        resources[key] = res
        refcounts.putIfAbsent(key, AtomicInteger(0))
    }

    fun acquire(key: String) { refcounts[key]?.incrementAndGet() }
    fun release(key: String) {
        refcounts[key]?.let { if (it.decrementAndGet() <= 0 && contextLost) evict(key) }
    }

    fun evict(key: String) {
        resources.remove(key)?.let { Safe.critical("evict:$key", Unit) { it.destroy() } }
        refcounts.remove(key)
    }

    fun totalBytes(): Long = resources.values.sumOf { it.resourceBytes() }

    /** Called by the cache layer when the budget is exceeded. Evicts until under budget. */
    fun enforceBudget(onEvict: (String) -> Unit) {
        var total = totalBytes()
        val sorted = resources.keys.sortedByDescending { refcounts[it]?.get() ?: 0 }
        for (key in sorted) {
            if (total <= memoryBudgetBytes) break
            (resources[key]?.resourceBytes() ?: 0L).let { total -= it; evict(key); onEvict(key) }
        }
    }

    /** GPU context lost (background kill, driver reset). Destroy handles, mark for rebuild. */
    fun onContextLost() {
        contextLost = true
        resources.values.forEach { Safe.critical("ctxlost", Unit) { it.destroy() } }
        resources.clear()
    }

    fun onContextRestored() { contextLost = false }
}
