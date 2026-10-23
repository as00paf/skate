package com.pafoid.skate.engine.render.cache

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.Texture
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GpuResourceCacheTest {
    private val disposed = mutableListOf<String>()
    private var created = 0

    private fun cache(maxIdle: Int = 64) =
        GpuResourceCache<String, String>("test", { disposed += it }, maxIdle)

    private fun GpuResourceCache<String, String>.get(key: String) = acquire(key) { created++; "res-$key" }

    @Test
    fun GpuCache_MissCreatesAndHitReusesSameResource() {
        val c = cache()
        val a = c.get("a")
        val b = c.get("a")
        assertSame(a, b)
        assertEquals(1, created)
        assertEquals(1L, c.misses)
        assertEquals(1L, c.hits)
        assertEquals(2, c.refCount("a"))
    }

    @Test
    fun GpuCache_ReleaseToZeroKeepsResourceIdleUntilReacquired() {
        val c = cache()
        c.get("a")
        assertTrue(c.release("a"))
        assertEquals(1, c.idleCount)
        assertTrue(disposed.isEmpty())
        c.get("a")
        assertEquals(0, c.idleCount)
        assertEquals(1, created)
    }

    @Test
    fun GpuCache_ReleaseUnknownOrAlreadyReleasedIsRejected() {
        val c = cache()
        assertFalse(c.release("x"))
        c.get("a")
        assertTrue(c.release("a"))
        assertFalse(c.release("a"))
    }

    @Test
    fun GpuCache_EvictsLeastRecentlyReleasedBeyondMaxIdle() {
        val c = cache(maxIdle = 1)
        c.get("a"); c.get("b")
        c.release("a")
        c.release("b")
        assertEquals(listOf("res-a"), disposed)
        assertNull(c.peek("a"))
        assertEquals("res-b", c.peek("b"))
    }

    @Test
    fun GpuCache_EvictIdleNeverDisposesReferencedEntries() {
        val c = cache()
        c.get("a"); c.get("b")
        c.release("b")
        assertEquals(1, c.evictIdle())
        assertEquals(listOf("res-b"), disposed)
        assertEquals(1, c.size)
    }

    @Test
    fun GpuCache_DisposeAllFreesEverythingEvenWhenReferencedAndContinuesOnFailure() {
        val failing = GpuResourceCache<String, String>("f", { disposed += it; if (it == "res-a") error("boom") })
        failing.get("a"); failing.get("b")
        runCatching { failing.disposeAll() }
        assertEquals(setOf("res-a", "res-b"), disposed.toSet())
        assertEquals(0, failing.size)
    }

    @Test
    fun GpuResources_DisposeAllFreesEveryKind() {
        val log = mutableListOf<String>()
        val res = GpuResources(
            destroyShader = { log += "shader" },
            destroyTexture = { log += "texture" },
            destroyMesh = { log += "mesh" },
        )
        res.shaders.acquire("s") { mockk<Shader>() }
        res.textures.acquire("t") { Texture() }
        res.textures.acquire("t") { Texture() }
        res.meshes.acquire("m") { GpuMesh(1, intArrayOf(2)) }
        assertEquals(3, res.totalCount)
        res.disposeAll()
        assertEquals(0, res.totalCount)
        assertEquals(listOf("mesh", "texture", "shader"), log)
    }

    @Test
    fun UniformLocationCache_LooksUpEachNameOnce() {
        var lookups = 0
        val c = UniformLocationCache { lookups++; 7 }
        assertEquals(7, c.get("u"))
        assertEquals(7, c.get("u"))
        assertEquals(1, lookups)
    }
}
