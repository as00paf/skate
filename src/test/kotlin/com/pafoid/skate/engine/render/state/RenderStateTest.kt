package com.pafoid.skate.engine.render.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RenderStateTest {
    @Test
    fun SortKey_RoundTripsFields() {
        val k = SortKey.build(true, 5, 1234, 99)
        assertTrue(SortKey.isTransparent(k))
        assertEquals(5, SortKey.shader(k))
        assertEquals(1234, SortKey.material(k))
        assertEquals(99, SortKey.mesh(k))
    }

    @Test
    fun SortKey_OrdersOpaqueBeforeTransparentThenShaderMaterialMesh() {
        val opaqueHigh = SortKey.build(false, 65535, 0xFFFFFF, 0x3FFFFF)
        val transparentLow = SortKey.build(true, 0, 0, 0)
        assertTrue(opaqueHigh < transparentLow)
        assertTrue(SortKey.build(false, 1, 9, 9) < SortKey.build(false, 2, 0, 0))
        assertTrue(SortKey.build(false, 1, 1, 9) < SortKey.build(false, 1, 2, 0))
        assertTrue(SortKey.build(false, 1, 1, 1) < SortKey.build(false, 1, 1, 2))
    }

    @Test
    fun SortKey_MasksOverflowingIdsWithoutCorruptingNeighbours() {
        val k = SortKey.build(false, 0x1_0001, 0, 0)
        assertEquals(1, SortKey.shader(k))
        assertFalse(SortKey.isTransparent(k))
    }

    @Test
    fun DrawQueue_SortsByKeyAndIsStableForEqualKeys() {
        val q = DrawQueue<String>()
        q.add(2, "c"); q.add(1, "a1"); q.add(1, "a2"); q.add(0, "z")
        q.sort()
        assertEquals(listOf("z", "a1", "a2", "c"), (0 until q.size).map { q.itemAt(it) })
        q.clear()
        assertEquals(0, q.size)
        q.add(5, "x")
        assertEquals("x", q.itemAt(0))
    }

    @Test
    fun BindingCache_SkipsRedundantVaoAndTextureBinds() {
        val calls = mutableListOf<String>()
        var changes = 0
        val c = BindingCache({ calls += "vao$it" }, { calls += "unit$it" }, { calls += "tex$it" }, { changes++ })
        assertTrue(c.bindVao(3))
        assertFalse(c.bindVao(3))
        assertTrue(c.bindTexture(0, 10))
        assertFalse(c.bindTexture(0, 10))
        assertTrue(c.bindTexture(1, 11))
        assertTrue(c.bindTexture(0, 12))
        assertEquals(listOf("vao3", "unit0", "tex10", "unit1", "tex11", "unit0", "tex12"), calls)
        assertEquals(4, changes)
    }

    @Test
    fun BindingCache_InvalidateForcesRebindAndResetUnbindsKnownVao() {
        val calls = mutableListOf<String>()
        val c = BindingCache({ calls += "vao$it" }, { calls += "unit$it" }, { calls += "tex$it" })
        c.bindVao(4)
        c.invalidate()
        assertTrue(c.bindVao(4))
        calls.clear()
        c.reset()
        assertEquals(listOf("vao0"), calls)
        calls.clear()
        c.reset()
        assertTrue(calls.isEmpty())
    }
}
