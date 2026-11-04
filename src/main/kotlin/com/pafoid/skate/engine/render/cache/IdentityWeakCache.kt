package com.pafoid.skate.engine.render.cache

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/**
 * Cache keyed by object **identity** that does not keep its keys alive (a weak identity map).
 *
 * `WeakHashMap` hashes and compares keys with `hashCode`/`equals`. Asset types such as `3dModel` and `MeshPart` are
 * data classes holding vertex arrays, so each lookup hashed the whole mesh (REN-22: ~16 ms/frame for the skater in
 * the shadow pass). Identity lookups cost O(1) regardless of mesh size.
 */
class IdentityWeakCache<K : Any, V : Any> {
    private val queue = ReferenceQueue<K>()
    private val map = HashMap<Key<K>, V>()
    private val probe = Probe()

    val size: Int get() { purge(); return map.size }

    operator fun get(key: K): V? {
        purge()
        probe.target = key
        return map[probe as Any].also { probe.target = null }
    }

    fun getOrPut(key: K, compute: () -> V): V = get(key) ?: compute().also { map[Key(key, queue)] = it }

    private fun purge() {
        while (true) map.remove(queue.poll() ?: return)
    }

    /** Weak key; equal to another key or the lookup probe when both point at the same object. */
    private class Key<K : Any>(referent: K, queue: ReferenceQueue<K>) : WeakReference<K>(referent, queue) {
        private val hash = System.identityHashCode(referent)
        override fun hashCode() = hash
        override fun equals(other: Any?): Boolean = other === this || when (other) {
            null -> false
            is Key<*> -> get().let { it != null && it === other.get() }
            is Probe -> get().let { it != null && it === other.target }
            else -> false
        }
    }

    /** Allocation-free lookup key. */
    private class Probe {
        var target: Any? = null
        override fun hashCode() = System.identityHashCode(target)
        override fun equals(other: Any?): Boolean = other is Key<*> && other.get().let { it != null && it === target }
    }
}
