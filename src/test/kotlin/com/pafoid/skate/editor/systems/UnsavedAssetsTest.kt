package com.pafoid.skate.editor.systems

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-82: the dirty-document registry and the close prompt's flow. */
class UnsavedAssetsTest {
    /** A fake document: dirty until saved (or until a failing save is fixed). */
    private class Doc(val kind: UnsavedKind, val name: String, var dirty: Boolean = true, var saveWorks: Boolean = true) {
        var discarded = false
        fun asset() = UnsavedAsset(kind, "$kind/$name", name, save = { if (saveWorks) dirty = false }, discard = { discarded = true; dirty = false })
    }

    private fun registry(vararg docs: Doc) = UnsavedAssetRegistry().also { r ->
        r.register { docs.filter { it.dirty }.map { it.asset() } }
    }

    @Test
    fun Registry_ListsEverySourceScenesFirstAndSkipsFailingSources() {
        val r = UnsavedAssetRegistry()
        r.register { listOf(Doc(UnsavedKind.ANIMATOR_CONTROLLER, "hero").asset(), Doc(UnsavedKind.ANIMATION_CLIP, "walk").asset()) }
        r.register { listOf(Doc(UnsavedKind.SCENE, "Main").asset(), Doc(UnsavedKind.SCENE, "Main").asset()) }
        r.register { error("a window that is gone") }
        assertEquals(listOf("Main", "walk", "hero"), r.unsaved().map { it.name }, "grouped by kind, duplicates once")
        assertTrue(r.hasUnsaved)
    }

    @Test
    fun Registry_SourcesAreAskedEachTimeAndCanBeRemoved() {
        val clip = Doc(UnsavedKind.ANIMATION_CLIP, "walk", dirty = false)
        val r = UnsavedAssetRegistry()
        val remove = r.register { listOfNotNull(clip.takeIf { it.dirty }?.asset()) }
        assertFalse(r.hasUnsaved)
        clip.dirty = true
        assertTrue(r.hasUnsaved, "the list is always current")
        remove()
        assertFalse(r.hasUnsaved)
    }

    @Test
    fun Registry_SaveAllReportsWhatIsStillUnsaved() {
        val scene = Doc(UnsavedKind.SCENE, "Main", saveWorks = false)
        val clip = Doc(UnsavedKind.ANIMATION_CLIP, "walk")
        val r = registry(scene, clip)
        assertEquals(listOf("Main"), r.saveAll().map { it.name })
        assertFalse(clip.dirty)
        r.discardAll()
        assertTrue(scene.discarded)
        assertFalse(r.hasUnsaved)
    }

    @Test
    fun Guard_NothingUnsaved_ClosesAtOnce() {
        val guard = CloseGuard(registry(Doc(UnsavedKind.SCENE, "Main", dirty = false)))
        var closed = 0
        assertTrue(guard.request(CloseTarget.EDITOR) { closed++ })
        assertEquals(1, closed)
        assertNull(guard.pending)
    }

    @Test
    fun Guard_UnsavedAsksFirst_AndCancelKeepsEverything() {
        val clip = Doc(UnsavedKind.ANIMATION_CLIP, "walk")
        val guard = CloseGuard(registry(clip))
        var closed = 0
        assertFalse(guard.request(CloseTarget.PROJECT) { closed++ })
        assertEquals(CloseTarget.PROJECT, guard.pending)
        assertEquals(listOf("walk"), guard.unsaved().map { it.name })
        guard.cancel()
        assertEquals(0, closed)
        assertNull(guard.pending)
        assertTrue(clip.dirty && !clip.discarded)
        assertFalse(guard.saveAll(), "no prompt, nothing to do")
    }

    @Test
    fun Guard_SaveAllClosesOnlyWhenEverythingSaved() {
        val scene = Doc(UnsavedKind.SCENE, "Main", saveWorks = false)
        val controller = Doc(UnsavedKind.ANIMATOR_CONTROLLER, "hero")
        val guard = CloseGuard(registry(scene, controller))
        var closed = 0
        guard.request(CloseTarget.EDITOR) { closed++ }
        assertFalse(guard.saveAll())
        assertEquals(0, closed, "a failed save keeps the editor open")
        assertEquals(listOf("Main"), guard.failed.map { it.name })
        assertEquals(CloseTarget.EDITOR, guard.pending)
        scene.saveWorks = true
        assertTrue(guard.saveAll())
        assertEquals(1, closed)
        assertNull(guard.pending)
        assertTrue(guard.failed.isEmpty())
    }

    @Test
    fun Guard_DiscardDropsChangesAndCloses() {
        val clip = Doc(UnsavedKind.ANIMATION_CLIP, "walk")
        val guard = CloseGuard(registry(clip))
        var closed = 0
        guard.request(CloseTarget.PROJECT) { closed++ }
        guard.discard()
        assertTrue(clip.discarded)
        assertEquals(1, closed)
        guard.discard()
        assertEquals(1, closed, "discard without a prompt does nothing")
    }

    @Test
    fun Guard_ALaterRequestTakesOver() {
        val guard = CloseGuard(registry(Doc(UnsavedKind.SCENE, "Main")))
        val closed = ArrayList<String>()
        guard.request(CloseTarget.PROJECT) { closed += "project" }
        guard.request(CloseTarget.EDITOR) { closed += "editor" }
        assertEquals(CloseTarget.EDITOR, guard.pending)
        guard.discard()
        assertEquals(listOf("editor"), closed)
    }
}

/** EDT-82 review: Discard really discards scenes, and the prompt is drawn whatever the layout. */
class UnsavedAssetsReviewTest {
    @Test
    fun SceneDiscard_ClearsTheDirtyFlagSoClosingTheProjectDoesNotSaveIt() {
        val main = com.pafoid.skate.engine.ecs.Scene("Main").apply { isDirty = true }
        val other = com.pafoid.skate.engine.ecs.Scene("Other")
        val saved = ArrayList<String>()
        val registry = UnsavedAssetRegistry().also { it.register(UnsavedScenes.source({ listOf(main, other) }) { s -> saved += s.name }) }
        val guard = CloseGuard(registry)
        var closed = false
        guard.request(CloseTarget.PROJECT) {
            // What ProjectManager.closeProject does first: save the scenes still dirty.
            listOf(main, other).filter { it.isDirty }.forEach { saved += "closeProject:${it.name}" }
            closed = true
        }
        assertEquals(listOf("Main"), guard.unsaved().map { it.name })
        guard.discard()
        assertTrue(closed)
        assertFalse(main.isDirty)
        assertTrue(saved.isEmpty(), "a discarded scene is never written: $saved")
    }

    @Test
    fun ScenesSave_GoesThroughTheSceneHandler() {
        val main = com.pafoid.skate.engine.ecs.Scene("Main").apply { isDirty = true }
        val registry = UnsavedAssetRegistry().also { it.register(UnsavedScenes.source({ listOf(main) }) { s -> s.isDirty = false }) }
        assertTrue(registry.saveAll().isEmpty())
    }

    @Test
    fun Prompt_IsDrawnOutsideTheDockedLayout() {
        // F12 viewport full screen skips setupDockSpace(): the prompt must be drawn by update() itself.
        val src = java.io.File("src/main/kotlin/com/pafoid/skate/editor/imgui/ImGuiLayer.kt").readText()
        val update = src.substring(src.indexOf("fun update(dt: Float)"), src.indexOf("private fun loadThemes()"))
        val dock = src.substring(src.indexOf("private fun setupDockSpace()"))
        assertTrue(update.contains("unsavedAssetsModal.render()"), "update() draws the prompt")
        assertFalse(dock.substringBefore("\n    }\n").contains("unsavedAssetsModal.render()"), "not only in the docked layout")
    }
}
