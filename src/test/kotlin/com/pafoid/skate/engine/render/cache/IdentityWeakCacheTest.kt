package com.pafoid.skate.engine.render.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class IdentityWeakCacheTest {

    /** Hashing or comparing this key by value is the REN-22 regression (it walked whole meshes every frame). */
    private class ExpensiveKey {
        override fun hashCode(): Int = error("value hashCode must not be used")
        override fun equals(other: Any?): Boolean = error("value equals must not be used")
    }

    @Test
    fun IdentityWeakCache_NeverUsesValueHashCodeOrEquals() {
        val cache = IdentityWeakCache<ExpensiveKey, String>()
        val key = ExpensiveKey()

        cache.getOrPut(key) { "bounds" }

        assertEquals("bounds", cache[key])
        assertEquals("bounds", cache.getOrPut(key) { error("must hit the cache") })
    }

    @Test
    fun IdentityWeakCache_EqualButDistinctKeysHaveSeparateEntries() {
        val cache = IdentityWeakCache<List<Int>, Any>()
        val a = listOf(1, 2, 3)
        val b = listOf(1, 2, 3)
        val va = cache.getOrPut(a) { Any() }

        assertNull(cache[b])
        assertSame(va, cache[a])
        cache.getOrPut(b) { Any() }
        assertEquals(2, cache.size)
    }
}
