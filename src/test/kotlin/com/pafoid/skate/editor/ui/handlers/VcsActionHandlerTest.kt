package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.vcs.CommitChangesCommand
import com.pafoid.skate.editor.commands.vcs.SwitchBranchCommand
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.VcsAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.vcs.VcsAuthor
import com.pafoid.skate.editor.vcs.VcsRunner
import com.pafoid.skate.editor.vcs.VcsSession
import com.pafoid.skate.engine.core.EventSystem
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class VcsActionHandlerTest {
    @TempDir
    lateinit var dir: File

    private val events = EventSystem(mockk(relaxed = true))
    private val undo = UndoRedoManager(events, mockk(relaxed = true))
    private var playing = false
    private var unsaved = false
    private val session by lazy { VcsSession({ dir }, VcsRunner.Immediate) }

    private fun handler() = VcsActionHandler(events, undo, session, { playing }, { unsaved }, { "C:/p/Game.skateproject" })

    private fun repo() {
        session.followProject()
        File(dir, "Scenes").mkdirs()
        File(dir, "Scenes/A.scene").writeText("{}\n")
        session.run({ it.init(VcsAuthor("T", "t@example.com"), "first") })
    }

    @Test
    fun Commit_RunsAsAnExecuteOnlyCommand() {
        handler()
        repo()
        File(dir, "Scenes/A.scene").writeText("{\"a\":1}\n")
        events.publish(VcsAction.Commit("Edit A", listOf("Scenes/A.scene")))
        assertEquals(listOf("Edit A", "first"), session.state.history.map { it.message })
        assertTrue(session.state.changes.isEmpty())
    }

    @Test
    fun Actions_AreRefusedDuringPlay() {
        handler()
        repo()
        playing = true
        File(dir, "Scenes/A.scene").writeText("{\"a\":1}\n")
        events.publish(VcsAction.Commit("Edit A", listOf("Scenes/A.scene")))
        assertEquals("vcs.error.playing", session.state.error?.messageKey)
        assertEquals(1, session.state.history.size)
    }

    @Test
    fun SwitchBranch_WaitsForUnsavedScenesThenReloadsTheProject() {
        handler()
        repo()
        events.publish(VcsAction.CreateBranch("other"))
        assertEquals("other", session.state.branches?.current)
        val reloads = mutableListOf<String>()
        events.subscribe<ProjectEvent.OpenProjectRequested> { reloads += it.projectPath }
        unsaved = true
        events.publish(VcsAction.SwitchBranch("main"))
        assertEquals("vcs.error.unsaved_scenes", session.state.error?.messageKey)
        assertEquals("other", session.state.branches?.current)
        unsaved = false
        events.publish(VcsAction.SwitchBranch("main"))
        assertEquals("main", session.state.branches?.current)
        assertEquals(listOf("C:/p/Game.skateproject"), reloads)
    }

    @Test
    fun Commands_AreExecutedThroughTheUndoManager() {
        val manager = mockk<UndoRedoManager>(relaxed = true)
        val captured = slot<com.pafoid.skate.editor.commands.Command>()
        every { manager.executeCommand(capture(captured)) } returns Unit
        VcsActionHandler(events, manager, session, { false }, { false }, { null })
        events.publish(VcsAction.Commit("m", listOf("a")))
        assertTrue(captured.captured is CommitChangesCommand)
        events.publish(VcsAction.SwitchBranch("main"))
        assertTrue(captured.captured is SwitchBranchCommand)
        verify(exactly = 2) { manager.executeCommand(any()) }
    }
}
