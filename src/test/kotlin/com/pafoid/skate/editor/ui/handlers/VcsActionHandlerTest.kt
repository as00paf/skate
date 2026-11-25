package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.vcs.CommitChangesCommand
import com.pafoid.skate.editor.commands.vcs.SwitchBranchCommand
import com.pafoid.skate.editor.events.EditorError
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.VcsCommitted
import com.pafoid.skate.editor.events.VcsAction
import com.pafoid.skate.editor.events.VcsSignInRequested
import com.pafoid.skate.editor.vcs.VcsException
import com.pafoid.skate.editor.vcs.VcsService
import com.pafoid.skate.editor.vcs.auth.GitCredential
import com.pafoid.skate.editor.vcs.auth.GitCredentials
import com.pafoid.skate.editor.vcs.auth.SessionCredentialStore
import org.eclipse.jgit.api.Git
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

    private val toasts = mutableListOf<String>()
    private val errors = mutableListOf<EditorError>()

    private fun handler(): VcsActionHandler {
        events.subscribe<EditorError> { errors += it }
        return VcsActionHandler(events, undo, session, { playing }, { unsaved }, { "C:/p/Game.skateproject" }) { key, args -> toasts += "$key$args" }
    }

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
        val id = session.state.history.first().id
        assertEquals("vcs.toast.committed[${id.take(7)}, 1]", toasts.last())
    }

    @Test
    fun Commit_PublishesTheNewCommitId() {
        handler()
        repo()
        val committed = mutableListOf<String>()
        events.subscribe<VcsCommitted> { committed += it.commitId }
        File(dir, "Scenes/A.scene").writeText("{\"a\":1}\n")
        events.publish(VcsAction.Commit("Edit A", listOf("Scenes/A.scene")))
        assertEquals(listOf(session.state.history.first().id), committed)
    }

    @Test
    fun CreateBranch_FromAnotherBranchWithoutCheckout() {
        handler()
        repo()
        events.publish(VcsAction.CreateBranch("side", from = "main", checkout = false))
        assertEquals("main", session.state.branches?.current)
        assertTrue("side" in session.state.branches?.local.orEmpty())
        assertEquals("vcs.toast.branch_created[side]", toasts.last())
    }

    @Test
    fun AFailedAction_ShowsAnEditorError() {
        handler()
        repo()
        events.publish(VcsAction.DeleteBranch("main"))
        val error = errors.single()
        assertEquals(VcsActionHandler.ERROR_TITLE, error.titleKey)
        assertEquals("vcs.error.delete_current", error.messageKey)
        assertEquals(listOf<Any>("main"), error.messageArgs)
    }

    @Test
    fun SavesAndFileChanges_RequestARefresh() {
        var now = 0L
        val s = VcsSession({ dir }, VcsRunner.Immediate, clock = { now })
        VcsActionHandler(events, undo, s, { false }, { false }, { null })
        s.followProject()
        val v = s.state.version
        events.publish(com.pafoid.skate.editor.events.FileSystemEvent.FileSystemChangedEvent(null))
        now += VcsSession.REFRESH_INTERVAL_MS
        s.followProject()
        assertEquals(v + 1, s.state.version)
    }

    @Test
    fun Actions_AreRefusedDuringPlay() {
        handler()
        repo()
        playing = true
        File(dir, "Scenes/A.scene").writeText("{\"a\":1}\n")
        events.publish(VcsAction.Commit("Edit A", listOf("Scenes/A.scene")))
        assertEquals("vcs.error.playing", errors.single().messageKey)
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
        assertEquals("vcs.error.unsaved_scenes", errors.single().messageKey)
        assertEquals("other", session.state.branches?.current)
        unsaved = false
        events.publish(VcsAction.SwitchBranch("main"))
        assertEquals("main", session.state.branches?.current)
        assertEquals(listOf("C:/p/Game.skateproject"), reloads)
        assertEquals("vcs.toast.switched[main]", toasts.last())
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

    // --- EDT-60 phase 2: remotes and sign-in ---

    @Test
    fun SetRemote_MovesUrlCredentialsToTheStoreAndWritesNoSecretUnderTheProject() {
        handler()
        repo()
        events.publish(VcsAction.SetRemote("https://me:ghp_SECRET@github.com/me/game.git"))
        assertEquals("https://github.com/me/game.git", session.state.origin?.url)
        assertEquals(GitCredential("me", "ghp_SECRET"), session.credentials.forUrl("https://github.com/me/game.git"))
        assertEquals("vcs.toast.remote_set_credentials_moved[https://github.com/me/game.git]", toasts.last())
        val leaks = dir.walkTopDown().filter { it.isFile && it.readBytes().toString(Charsets.ISO_8859_1).contains("ghp_SECRET") }.toList()
        assertEquals(emptyList<File>(), leaks)
    }

    @Test
    fun SignIn_StoresTheTokenAndSignOutRemovesIt() {
        handler()
        events.publish(VcsAction.SignIn("github.com", " ", " tok "))
        assertEquals(GitCredential("git", "tok"), session.credentials.forUrl("https://github.com/x.git"))
        assertEquals("vcs.toast.signed_in_session[github.com]", toasts.last())
        events.publish(VcsAction.SignIn("github.com", "me", "  "))
        assertEquals("vcs.signin.token_required", errors.last().messageKey)
        events.publish(VcsAction.SignOut("github.com"))
        assertEquals(null, session.credentials.forUrl("https://github.com/x.git"))
    }

    @Test
    fun PushAndPull_ThroughALocalRemote_ReloadOnlyWhenFilesChanged() {
        handler()
        repo()
        val bare = File(dir.parentFile, "${dir.name}-remote.git")
        Git.init().setBare(true).setDirectory(bare).setInitialBranch("main").call().close()
        try {
            val reloads = mutableListOf<String>()
            events.subscribe<ProjectEvent.OpenProjectRequested> { reloads += it.projectPath }
            events.publish(VcsAction.SetRemote(bare.toURI().toString()))
            events.publish(VcsAction.Push)
            assertEquals("vcs.toast.pushed[]", toasts.last())
            assertEquals(0, session.state.branches?.tracking?.ahead)
            events.publish(VcsAction.Pull)
            assertEquals("vcs.toast.up_to_date[]", toasts.last())
            assertEquals(emptyList<String>(), reloads)
            unsaved = true
            events.publish(VcsAction.Pull)
            assertEquals("vcs.error.unsaved_scenes", errors.last().messageKey)
        } finally {
            bare.deleteRecursively()
        }
    }

    @Test
    fun AuthenticationFailure_AsksForSignInInsteadOfAnError() {
        val vcs = mockk<VcsService>(relaxed = true)
        every { vcs.isRepository() } returns true
        every { vcs.push(any()) } throws VcsException("vcs.error.auth_required", "github.com")
        val authSession = VcsSession({ dir }, VcsRunner.Immediate, factory = { vcs }, credentials = GitCredentials(SessionCredentialStore()))
        VcsActionHandler(events, undo, authSession, { false }, { false }, { null })
        events.subscribe<EditorError> { errors += it }
        val asked = mutableListOf<String>()
        events.subscribe<VcsSignInRequested> { asked += it.host }
        authSession.followProject()
        events.publish(VcsAction.Push)
        assertEquals(listOf("github.com"), asked)
        assertTrue(errors.isEmpty())
    }
}
