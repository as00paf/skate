package com.pafoid.skate.engine.assets

import java.util.concurrent.ConcurrentHashMap

/**
 * Cache that owns the native resources of its values: removing an entry (or [clear]) frees it through [deleter].
 * An instance cached under several keys (e.g. a shared fallback asset) is freed only with its last key.
 */
class ResourceCache<T : Any>(private val deleter: (T) -> Unit) {
    private val map = ConcurrentHashMap<String, T>()

    val values: Collection<T> get() = map.values
    val size: Int get() = map.size

    operator fun get(key: String): T? = map[key]

    operator fun set(key: String, value: T) {
        val previous = map.put(key, value)
        if (previous != null && previous !== value) free(previous)
    }

    /** Removes [key] and frees its resource. Returns false if the key was not cached. */
    fun unload(key: String): Boolean {
        val removed = map.remove(key) ?: return false
        free(removed)
        return true
    }

    fun clear() {
        val all = map.values.distinctBy { System.identityHashCode(it) }
        map.clear()
        all.forEach(deleter)
    }

    private fun free(value: T) {
        if (map.values.none { it === value }) deleter(value)
    }
}
