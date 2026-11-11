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
        val slot = DiffSlot()
        session.showDiff(slot, "Scenes/A.scene")
        assertEquals("Scenes/A.scene", slot.diff?.path)
        assertEquals("work:Scenes/A.scene", slot.key)
        assertEquals(session.state.version, slot.version)
    }

    @Test
    fun Run_ReportsAFailedActionToTheCallback_NotInTheState() {
        root = dir
        session.followProject()
        val failures = mutableListOf<String>()
        session.onActionFailed = { failures += it.messageKey }
        var done = false
        session.run({ it.status() }) { done = true }
        assertFalse(done)
        assertEquals(listOf("vcs.error.not_repository"), failures)
        assertNull(session.state.error)
        session.refuse(VcsException("vcs.error.playing"))
        assertEquals(listOf("vcs.error.not_repository", "vcs.error.playing"), failures)
    }

    @Test
    fun DiffSlots_AreIndependentAndDropStaleResults() {
        val queued = mutableListOf<() -> Unit>()
        val deferred = object : VcsRunner {
            override fun background(block: () -> Unit) { queued += block }
            override fun main(block: () -> Unit) = block()
        }
        File(dir, "a.txt").writeText("a\n")
        File(dir, "b.txt").writeText("b\n")
        JGitVcsService(dir).init(VcsAuthor("T", "t@example.com"), "first")
        File(dir, "a.txt").writeText("a2\n")
        File(dir, "b.txt").writeText("b2\n")
        root = dir
        val s = VcsSession({ root }, deferred)
        s.followProject()
        queued.removeAt(0).invoke()
        val one = DiffSlot()
        val two = DiffSlot()
        s.showDiff(one, "a.txt")
        s.showDiff(one, "b.txt") // asked for b before a's result came back
        s.showDiff(two, "a.txt")
        queued.toList().forEach { it() }
        assertEquals("b.txt", one.diff?.path)
        assertEquals("a.txt", two.diff?.path)
    }

    @Test
    fun RequestRefresh_IsDebouncedToOnePerInterval() {
        var now = 10_000L
        var reads = 0
        root = dir
        val s = VcsSession({ root }, VcsRunner.Immediate, factory = { d -> reads++; JGitVcsService(d) }, clock = { now })
        s.followProject() // first read
        val versionAfterFirst = s.state.version
        s.requestRefresh()
        s.requestRefresh()
        now += 500
        s.followProject()
        assertEquals(versionAfterFirst, s.state.version, "too soon: no read yet")
        now += VcsSession.REFRESH_INTERVAL_MS
        s.followProject()
        assertEquals(versionAfterFirst + 1, s.state.version)
        now += VcsSession.REFRESH_INTERVAL_MS * 5
        s.followProject()
        assertEquals(versionAfterFirst + 1, s.state.version, "no new request, no read")
        assertEquals(1, reads, "one service per project root")
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
