package com.pafoid.skate.editor.ui.windows.assetBrowser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AsyncAssetCacheTest {
    /** IO jobs queued here and run when the test says so, like a worker that has not finished yet. */
    private val io = ArrayDeque<() -> Unit>()
    private val decoded = mutableListOf<String>()
    private val uploaded = mutableListOf<String>()
    private val disposed = mutableListOf<String>()
    private val discarded = mutableListOf<String>()
    private var mainThreadDecodes = 0
    private var onMain = true

    private fun cache(perFrame: Int = 2, fail: Set<String> = emptySet()) = AsyncAssetCache<String, String, String>(
        io = { io.addLast(it) },
        decode = { key ->
            if (onMain) mainThreadDecodes++
            if (key in fail) error("bad $key")
            decoded += key
            "data:$key"
        },
        upload = { key, data -> uploaded += key; "tex:$data" },
        dispose = { disposed += it },
        discard = { discarded += it },
        uploadsPerFrame = perFrame,
    )

    private fun runWorkers() {
        onMain = false
        while (io.isNotEmpty()) io.removeFirst()()
        onMain = true
    }

    @Test
    fun Get_NeverDecodesOnTheCallingThread_AndReturnsAPlaceholderFirst() {
        val c = cache()

        assertNull(c.get("a"))
        assertNull(c.get("a"), "still loading")

        assertEquals(0, mainThreadDecodes)
        assertEquals(1, io.size, "one job per key, however often the tile is drawn")
    }

    @Test
    fun Pump_UploadsAtMostTheBudgetPerFrame() {
        val c = cache(perFrame = 2)
        listOf("a", "b", "c", "d", "e").forEach { c.get(it) }
        runWorkers()

        assertEquals(2, c.pump())
        assertEquals(2, c.pump())
        assertEquals(1, c.pump())
        assertEquals(0, c.pump())
        assertEquals("tex:data:a", c.get("a"))
        assertEquals(5, uploaded.size)
    }

    @Test
    fun NothingUploadsBeforeTheWorkerFinishes() {
        val c = cache()
        c.get("a")

        assertEquals(0, c.pump())
        assertTrue(uploaded.isEmpty())
    }

    @Test
    fun DecodeFailure_IsRememberedAndNotRetried() {
        val c = cache(fail = setOf("bad"))
        c.get("bad")
        runWorkers()
        c.pump()

        assertTrue(c.state("bad") is AsyncAssetCache.State.Failed)
        assertNull(c.get("bad"))
        assertTrue(io.isEmpty())
    }

    @Test
    fun Clear_DisposesUploadsAndDiscardsLateResults() {
        val c = cache()
        c.get("a")
        runWorkers()
        c.pump()
        c.get("b")

        c.clear()
        runWorkers()
        c.pump()

        assertEquals(listOf("tex:data:a"), disposed)
        assertEquals(listOf("data:b"), discarded)
        assertEquals(listOf("a"), uploaded)
    }
}
