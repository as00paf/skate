package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.animation.clipeditor.AnimationEditorController
import com.pafoid.skate.editor.animation.controllereditor.AnimatorWindowController
import com.pafoid.skate.editor.animation.controllereditor.ResolvedController
import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.ui.handlers.ProjectActionHandler
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.animation.edit.SkKey
import com.pafoid.skate.engine.animation.edit.SkTrack
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-82: the animation back ends feed the registry, and Close Project goes through the prompt. */
class UnsavedAssetSourcesTest {
    @TempDir lateinit var dir: File

    private val undo = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))

    @Test
    fun AnimationClips_ListSaveAndDiscard() {
        val clip = SkClip(name = "walk", duration = 1f, tracks = listOf(SkTrack("Hips", AnimationPath.TRANSLATION, keys = listOf(SkKey(0f, listOf(0f, 0f, 0f))))))
        val file = File(dir, "walk.skanim").apply { writeText(SkAnimFormat.encode(clip)) }
        val editor = AnimationEditorController(execute = { undo.executeCommand(it) }, rigFor = { null })
        assertTrue(editor.open(file))
        assertTrue(editor.unsavedDocuments().isEmpty())
        editor.setLoop(false)
        val doc = editor.unsavedDocuments().single()

        editor.close() // still listed when the window shows another clip or none
        assertEquals(listOf(doc), editor.unsavedDocuments())
        editor.save(doc)
        assertTrue(editor.unsavedDocuments().isEmpty())
        assertFalse(SkAnimFormat.decode(file.readText()).loop, "written to the file")

        assertTrue(editor.open(file))
        editor.setLoop(true)
        editor.discard(editor.unsavedDocuments().single())
        assertTrue(editor.unsavedDocuments().isEmpty())
        assertTrue(editor.open(file))
        assertFalse(editor.clip!!.loop, "reopening reads the file again")
    }

    @Test
    fun AnimatorControllers_ListSaveAndDiscard() {
        val file = File(dir, "c.controller.json").apply { writeText(AnimationControllerAsset("c", "idle", listOf(StateDef("idle", ""))).toJson()) }
        val w = AnimatorWindowController(
            execute = { undo.executeCommand(it) },
            resolve = { p -> File(p).takeIf { it.isFile }?.let { ResolvedController(p, it, it.readText()) } },
        )
        assertTrue(w.open(file.absolutePath))
        assertTrue(w.renameState("idle", "rest"))
        val doc = w.unsavedDocuments().single()
        w.close()
        assertTrue(w.save(doc))
        assertTrue(w.unsavedDocuments().isEmpty())
        assertTrue(file.readText().contains("rest"))

        assertTrue(w.open(file.absolutePath))
        w.setDefault("rest")
        w.renameState("rest", "idle2")
        w.discard(w.unsavedDocuments().single())
        assertTrue(w.open(file.absolutePath))
        assertEquals("rest", w.asset!!.states.single().name, "the discarded rename is gone")
    }

    @Test
    fun CloseProject_WaitsForTheAnswer() {
        val events = EventSystem()
        val engine = mockk<Engine>(relaxed = true) {
            every { eventSystem } returns events
            every { jobSystem.isMainThread() } returns true
        }
        val projects = mockk<ProjectManager>(relaxed = true) { every { hasProject() } returns true }
        val executed = ArrayList<Command>()
        val undoManager = mockk<UndoRedoManager>(relaxed = true) { every { executeCommand(any()) } answers { executed += firstArg<Command>() } }
        var dirty = true
        val registry = UnsavedAssetRegistry().also { r ->
            r.register { if (dirty) listOf(UnsavedAsset(UnsavedKind.SCENE, "scene:Main", "Main", save = { dirty = false })) else emptyList() }
        }
        val guard = CloseGuard(registry)
        ProjectActionHandler(engine, projects, mockk<EditorSettingsManager>(relaxed = true), undoManager, mockk(relaxed = true), closeGuard = guard)

        events.publish(ProjectEvent.CloseProjectRequested)
        assertTrue(executed.isEmpty(), "the project stays open while the prompt is up")
        assertEquals(CloseTarget.PROJECT, guard.pending)
        guard.cancel()
        assertTrue(executed.isEmpty())

        events.publish(ProjectEvent.CloseProjectRequested)
        assertTrue(guard.saveAll())
        assertEquals("CloseProjectCommand", executed.single().javaClass.simpleName, "closes after Save all")

        executed.clear()
        events.publish(ProjectEvent.CloseProjectRequested)
        assertEquals(1, executed.size, "nothing unsaved: closes at once")
        verify(exactly = 0) { projects.closeProject() } // the command (mocked here) does the closing
    }
}
