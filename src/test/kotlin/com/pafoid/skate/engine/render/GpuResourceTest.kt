package com.pafoid.skate.engine.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpuResourceTest {
    @Test
    fun GpuResource_CloseDeletesOnceAndIsIdempotent() {
        val deleted = mutableListOf<Int>()
        val r = GpuResource(7) { deleted += it }
        assertFalse(r.isClosed)
        r.close(); r.close()
        assertEquals(listOf(7), deleted)
        assertTrue(r.isClosed)
    }

    @Test
    fun GpuResource_UseBlockClosesOnException() {
        val deleted = mutableListOf<Int>()
        runCatching { GpuResource(3) { deleted += it }.use { error("boom") } }
        assertEquals(listOf(3), deleted)
    }
}
