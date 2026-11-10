package com.pafoid.skate.editor.vcs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class VcsSessionTest {
    @TempDir
    lateinit var dir: File

    private var root: File? = null
    private val session by lazy { VcsSession({ root }, VcsRunner.Immediate) }

    @Test
    fun FollowProject_ReadsANonRepositoryProject() {
        root = dir
        session.followProject()
        assertTrue(session.state.loaded)
        assertFalse(session.state.isRepository)
        assertFalse(session.state.busy)
    }

    @Test
    fun Run_InitializesThenShowsStatusBranchesAndHistory() {
        root = dir
        session.followProject()
        File(dir, "Scenes").mkdirs()
        File(dir, "Scenes/A.scene").writeText("{}\n")
        var done = false
        assertTrue(session.run({ it.init(VcsAuthor("T", "t@example.com"), "first") }) { done = true })
        assertTrue(done)
        val state = session.state
        assertTrue(state.isRepository)
        assertEquals("main", state.branches?.current)
        assertEquals(listOf("first"), state.history.map { it.message })
        assertEquals(emptyList<FileChange>(), state.changes)
        File(dir, "Scenes/A.scene").writeText("{\"a\":1}\n")
        session.refresh()
        assertEquals(listOf(FileChange("Scenes/A.scene", ChangeKind.MODIFIED)), session.state.changes)
        session.showDiff("Scenes/A.scene")
        assertEquals("Scenes/A.scene", session.state.diff?.path)
        assertNull(session.state.diffCommit)
    }

    @Test
    fun Run_KeepsARefusalUntilDismissed_AndSkipsOnSuccess() {
        root = dir
        session.followProject()
        var done = false
        session.run({ it.status() }) { done = true }
        assertFalse(done)
        assertEquals("vcs.error.not_repository", session.state.error?.messageKey)
        session.refresh()
        assertEquals("vcs.error.not_repository", session.state.error?.messageKey)
        session.clearError()
        assertNull(session.state.error)
    }

    @Test
    fun Run_RefusesWhileAnotherActionRuns() {
        val queued = mutableListOf<() -> Unit>()
        val deferred = object : VcsRunner {
            override fun background(block: () -> Unit) { queued += block }
            override fun main(block: () -> Unit) = block()
        }
        root = dir
        val s = VcsSession({ root }, deferred)
        assertTrue(s.run(null))
        assertTrue(s.state.busy)
        assertFalse(s.run(null))
        queued.single().invoke()
        assertFalse(s.state.busy)
    }

    @Test
    fun Run_DoesNothingWithoutAProject() {
        assertFalse(session.run(null))
    }

    @Test
    fun Author_FallsBackToTheOsUser() {
        root = dir
        session.followProject()
        assertTrue(session.author().name.isNotBlank())
    }
}
