package com.seventv.stickers.data.repository

/**
 * Lightweight, thread-safe in-memory LRU cache with per-entry TTL expiration.
 */
internal class TtlLruCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val ttlMs: Long,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private data class Entry<V>(val value: V, val expiresAtMs: Long)

    private val lock = Any()
    private val map = object : LinkedHashMap<K, Entry<V>>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean {
            return size > maxEntries
        }
    }

    fun get(key: K): V? = synchronized(lock) {
        val entry = map[key] ?: return null
        if (clockMs() >= entry.expiresAtMs) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: K, value: V) = synchronized(lock) {
        if (maxEntries <= 0 || ttlMs <= 0L) return
        map[key] = Entry(value = value, expiresAtMs = clockMs() + ttlMs)
    }

    fun clear() = synchronized(lock) {
        map.clear()
    }
}
