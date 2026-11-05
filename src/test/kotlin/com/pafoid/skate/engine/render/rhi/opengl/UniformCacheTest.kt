package com.pafoid.skate.engine.render.rhi.opengl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UniformCacheTest {
    private val located = ArrayList<String>()
    private val cache = UniformCache { name -> located += name; if (name == "uMissing") -1 else located.size }

    @Test
    fun Slot_ResolvesEachNameOnce() {
        val a = cache.slot("uA")
        cache.slot("uA")
        cache.slot("uB")
        assertEquals(listOf("uA", "uB"), located)
        assertEquals(1, a.location)
    }

    @Test
    fun Vector_UploadsOnlyChangedValues() {
        val s = cache.slot("uColor")
        assertTrue(cache.changed(s, 3, 1f, 2f, 3f))
        assertFalse(cache.changed(s, 3, 1f, 2f, 3f))
        assertTrue(cache.changed(s, 3, 1f, 2f, 4f))
        assertTrue(cache.changed(s, 4, 1f, 2f, 4f, 0f), "a different component count is a different value")
    }

    @Test
    fun Float_ComparesBitsSoNegativeZeroIsUploaded() {
        val s = cache.slot("uBias")
        assertTrue(cache.changed(s, 1, 0f))
        assertTrue(cache.changed(s, 1, -0f))
        assertFalse(cache.changed(s, 1, -0f))
        assertTrue(cache.changed(s, 1, Float.NaN))
        assertFalse(cache.changed(s, 1, Float.NaN))
    }

    @Test
    fun IntAndFloatOfTheSameBits_AreDifferentValues() {
        val s = cache.slot("uFlag")
        assertTrue(cache.changed(s, 1, Float.fromBits(1)))
        assertTrue(cache.changed(s, 1))
        assertFalse(cache.changed(s, 1))
    }

    @Test
    fun Arrays_DetectAChangeInAnyElement() {
        val s = cache.slot("uMatrix")
        val m = FloatArray(16) { it.toFloat() }
        assertTrue(cache.changed(s, m))
        assertFalse(cache.changed(s, m.copyOf()))
        m[15] = 99f
        assertTrue(cache.changed(s, m))

        val t = cache.slot("uTextures")
        assertTrue(cache.changed(t, intArrayOf(0, 1, 2)))
        assertFalse(cache.changed(t, intArrayOf(0, 1, 2)))
        assertTrue(cache.changed(t, intArrayOf(0, 1)))
    }

    @Test
    fun MissingUniform_NeverNeedsAnUpload() {
        val s = cache.slot("uMissing")
        assertFalse(cache.changed(s, 1, 1f))
        assertFalse(cache.changed(s, 7))
        assertFalse(cache.changed(s, FloatArray(16)))
        assertFalse(cache.changed(s, intArrayOf(1)))
    }
}
