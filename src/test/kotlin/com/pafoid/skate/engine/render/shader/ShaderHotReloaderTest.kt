package com.pafoid.skate.engine.render.shader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-31 / AST-26 §5.5: only watched dependencies, throttled, reported once stable for two polls. */
class ShaderHotReloaderTest {
    private val stamps = HashMap<String, String>()
    private var folder = "a.meta"
    private var folderChanges = 0
    private var now = 0L
    private val reported = ArrayList<Set<String>>()
    private val reloader = ShaderHotReloader(500, { now }, { stamps[it] ?: "-" }, { folder }, { folderChanges++ }) { reported += it }

    private fun pollAt(t: Long) { now = t; reloader.poll() }

    @Test
    fun change_IsReportedOnceStableForTwoPolls_OnlyForWatchedIds() {
        stamps["a"] = "1"; stamps["b"] = "1"; stamps["unwatched"] = "1"
        reloader.watch(listOf("a", "b"))
        pollAt(0)
        stamps["a"] = "2"; stamps["unwatched"] = "2"
        pollAt(100)
        assertTrue(reported.isEmpty(), "throttled")
        pollAt(600)
        assertTrue(reported.isEmpty(), "seen once: may be half-written")
        pollAt(1200)
        assertEquals(listOf(setOf("a")), reported)
        pollAt(1800)
        assertEquals(1, reported.size, "reported once")
    }

    @Test
    fun stillChanging_IsNotReportedUntilItSettles() {
        stamps["a"] = "1"
        reloader.watch(listOf("a"))
        pollAt(0)
        stamps["a"] = "2"; pollAt(600)
        stamps["a"] = "3"; pollAt(1200)
        assertTrue(reported.isEmpty())
        pollAt(1800)
        assertEquals(listOf(setOf("a")), reported)
    }

    @Test
    fun retain_DropsUnloadedIds_AndWatchKeepsExistingBaselines() {
        stamps["a"] = "1"
        reloader.watch(listOf("a", "b"))
        stamps["a"] = "2"
        reloader.watch(listOf("a"))
        reloader.retain(setOf("a"))
        assertEquals(setOf("a"), reloader.watched)
        pollAt(0); pollAt(600)
        assertEquals(listOf(setOf("a")), reported, "the baseline was kept, so the edit is seen")
    }

    @Test
    fun newMetaInTheShaderFolder_RunsTheFolderCallbackFirst() {
        reloader.watch(listOf("a"))
        pollAt(0)
        assertEquals(0, folderChanges)
        folder = "a.meta|b.meta"
        pollAt(600)
        assertEquals(1, folderChanges)
    }
}
