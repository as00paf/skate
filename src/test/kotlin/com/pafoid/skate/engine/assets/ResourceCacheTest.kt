package com.pafoid.skate.engine.assets

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ResourceCacheTest {
    private val freed = mutableListOf<String>()
    private val cache = ResourceCache<String> { freed += it }

    @Test
    fun ResourceCache_UnloadFreesOnceAndRemoves() {
        cache["a"] = "A"
        assertTrue(cache.unload("a"))
        assertFalse(cache.unload("a"))
        assertNull(cache["a"])
        assertEquals(listOf("A"), freed)
    }

    @Test
    fun ResourceCache_SharedInstanceFreedWithLastKey() {
        val shared = String(charArrayOf('d'))
        cache["x"] = shared
        cache["y"] = shared
        cache.unload("x")
        assertTrue(freed.isEmpty())
        cache.unload("y")
        assertEquals(1, freed.size)
    }

    @Test
    fun ResourceCache_ReplacingValueFreesPrevious() {
        cache["k"] = "old"
        cache["k"] = "new"
        assertEquals(listOf("old"), freed)
    }

    @Test
    fun ResourceCache_ClearFreesEachDistinctInstanceOnce() {
        val shared = String(charArrayOf('s'))
        cache["a"] = shared
        cache["b"] = shared
        cache["c"] = "C"
        cache.clear()
        assertEquals(2, freed.size)
        assertEquals(0, cache.size)
    }

    @Test
    fun AssetResult_AttemptCapturesFailureWithoutFallback() {
        val ok = AssetResult.attempt("p") { 1 }
        val bad = AssetResult.attempt<Int>("p") { error("nope") }
        assertEquals(1, ok.getOrNull())
        assertEquals("nope", (bad as AssetResult.Failure).reason)
        assertNull(bad.getOrNull())
    }
}
