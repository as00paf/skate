package com.pafoid.skate.engine.render.cache

/**
 * Ref-counted cache of GPU resources keyed by asset identity.
 *
 * GL-free: creation and destruction are injected, so it is unit-testable without a context.
 * Entries whose ref count drops to zero stay cached ("idle") so a later [acquire] is a hit;
 * once more than [maxIdle] entries are idle the least recently released ones are evicted and disposed.
 * Must be used from the GL thread.
 */
class GpuResourceCache<K : Any, V : Any>(
    val name: String,
    private val dispose: (V) -> Unit,
    private val maxIdle: Int = DEFAULT_MAX_IDLE,
) {
    private class Entry<V>(val value: V, var refCount: Int)

    private val entries = HashMap<K, Entry<V>>()

    // Insertion-ordered: first element is the least recently released idle key.
    private val idle = LinkedHashSet<K>()

    var hits = 0L
        private set
    var misses = 0L
        private set

    val size: Int get() = entries.size
    val idleCount: Int get() = idle.size

    /** Returns the cached resource (hit) or creates it (miss); increments the ref count. */
    fun acquire(key: K, create: () -> V): V {
        val existing = entries[key]
        if (existing != null) {
            hits++
            existing.refCount++
            idle.remove(key)
            return existing.value
        }
        misses++
        val entry = Entry(create(), 1)
        entries[key] = entry
        return entry.value
    }

    fun peek(key: K): V? = entries[key]?.value

    fun refCount(key: K): Int = entries[key]?.refCount ?: 0

    /** Drops one reference. Returns false when the key is unknown or already fully released. */
    fun release(key: K): Boolean {
        val entry = entries[key] ?: return false
        if (entry.refCount == 0) return false
        entry.refCount--
        if (entry.refCount == 0) {
            idle.add(key)
            evictIdle(maxIdle)
        }
        return true
    }

    /** Disposes idle entries until at most [keep] remain idle. Returns the number evicted. */
    fun evictIdle(keep: Int = 0): Int {
        var evicted = 0
        while (idle.size > keep) {
            val key = idle.first()
            idle.remove(key)
            entries.remove(key)?.let { dispose(it.value) }
            evicted++
        }
        return evicted
    }

    /** Disposes every resource regardless of ref counts. */
    fun disposeAll() {
        val all = entries.values.map { it.value }
        entries.clear()
        idle.clear()
        var failure: Throwable? = null
        for (value in all) {
            try {
                dispose(value)
            } catch (t: Throwable) {
                if (failure == null) failure = t else failure.addSuppressed(t)
            }
        }
        failure?.let { throw it }
    }

    companion object {
        const val DEFAULT_MAX_IDLE = 64
    }
}
