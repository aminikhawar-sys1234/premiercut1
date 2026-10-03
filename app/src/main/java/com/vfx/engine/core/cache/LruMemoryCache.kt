package com.vfx.engine.core.cache

data class CacheKey(
  val clipId: String,
  val effectChainHash: Int,
  val ptsValue: Long
)

class LruMemoryCache<K, V>(private val maxCapacity: Int = 100) {
  private val map = object : LinkedHashMap<K, V>(maxCapacity, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
      return size > maxCapacity
    }
  }

  @Synchronized
  fun get(key: K): V? = map[key]

  @Synchronized
  fun put(key: K, value: V) {
    map[key] = value
  }

  @Synchronized
  fun clear() {
    map.clear()
  }
}
