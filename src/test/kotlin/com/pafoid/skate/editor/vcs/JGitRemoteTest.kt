package com.pafoid.skate.editor.vcs

import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-60 phase 2: remotes, fetch, pull, push and clone, against a local bare repository as `file://` (no network). */
class JGitRemoteTest {
    @TempDir
    lateinit var tmp: File

    private val alice = VcsAuthor("Alice", "alice@example.com")
    private val bob = VcsAuthor("Bob", "bob@example.com")

    private val bare by lazy { File(tmp, "remote.git").also { Git.init().setBare(true).setDirectory(it).setInitialBranch("main").call().close() } }
    private val remoteUrl get() = bare.toURI().toString()

    private fun write(root: File, path: String, text: String) = File(root, path).apply { parentFile.mkdirs(); writeText(text) }

    /** Alice's project, pushed to [bare]. */
    private fun published(): Pair<File, JGitVcsService> {
        val root = File(tmp, "alice").apply { mkdirs() }
        write(root, "Game.skateproject", "{}\n")
        write(root, "Scenes/Main.scene", "{\n  \"x\": 1\n}\n")
        val vcs = JGitVcsService(root)
        vcs.init(alice, "first")
        vcs.setRemote(remoteUrl)
        vcs.push(null)
        return root to vcs
    }

    private fun cloneAs(name: String): Pair<File, JGitVcsService> {
        val root = File(tmp, name)
        JGitCloner.clone(remoteUrl, root, null)
        return root to JGitVcsService(root)
    }

    @Test
    fun SetRemote_AddsThenChangesOrigin() {
        val root = File(tmp, "p").apply { mkdirs() }
        val vcs = JGitVcsService(root).apply { init(alice, "first") }
        assertEquals(emptyList<VcsRemote>(), vcs.remotes())
        vcs.setRemote("https://github.com/me/game.git")
        vcs.setRemote("https://github.com/me/other.git")
        assertEquals(listOf(VcsRemote("origin", "https://github.com/me/other.git")), vcs.remotes())
    }

    @Test
    fun SetRemote_RefusesCredentialsInTheUrl() {
        val root = File(tmp, "p").apply { mkdirs() }
        val vcs = JGitVcsService(root).apply { init(alice, "first") }
        val e = assertThrows<VcsException> { vcs.setRemote("https://me:ghp_secret@github.com/me/game.git") }
        assertEquals("vcs.error.credentials_in_url", e.messageKey)
        assertFalse(File(root, ".git/config").readText().contains("ghp_secret"))
    }

    @Test
    fun Push_FirstPushSetsTheUpstream() {
        val (root, vcs) = published()
        assertEquals(VcsTracking("origin/main", 0, 0), vcs.branches().tracking)
        assertTrue(File(root, ".git/config").readText().contains("merge = refs/heads/main"))
        write(root, "Scenes/Main.scene", "{\n  \"x\": 2\n}\n")
        vcs.commit("x = 2", listOf("Scenes/Main.scene"), alice)
        assertEquals(1, vcs.branches().tracking?.ahead)
        vcs.push(null)
        assertEquals(0, vcs.branches().tracking?.ahead)
    }

    @Test
    fun Clone_CopiesTheProjectAndFetchShowsWhatIsBehind() {
        val (aliceRoot, aliceVcs) = published()
        val (bobRoot, bobVcs) = cloneAs("bob")
        assertEquals("{\n  \"x\": 1\n}\n", File(bobRoot, "Scenes/Main.scene").readText())
        assertEquals("main", bobVcs.branches().current)
        write(aliceRoot, "Scenes/Main.scene", "{\n  \"x\": 2\n}\n")
        aliceVcs.commit("x = 2", listOf("Scenes/Main.scene"), alice)
        aliceVcs.push(null)
        bobVcs.fetch(null)
        assertEquals(1, bobVcs.branches().tracking?.behind)
        // Fetch never touches the working tree.
        assertEquals("{\n  \"x\": 1\n}\n", File(bobRoot, "Scenes/Main.scene").readText())
    }

    @Test
    fun Clone_RefusesANonEmptyFolderAndCleansUpAfterAFailure() {
        val target = File(tmp, "full").apply { mkdirs(); File(this, "x.txt").writeText("x") }
        assertEquals("vcs.error.clone_target_not_empty", assertThrows<VcsException> { JGitCloner.clone(remoteUrl, target, null) }.messageKey)
        val missing = File(tmp, "nothing-here")
        assertThrows<VcsException> { JGitCloner.clone(File(tmp, "no-such.git").toURI().toString(), missing, null) }
        assertFalse(missing.exists())
    }

    @Test
    fun Pull_FastForwardsAndMergesWithTheGivenAuthor() {
        val (aliceRoot, aliceVcs) = published()
        val (bobRoot, bobVcs) = cloneAs("bob")
        assertEquals(PullOutcome.UP_TO_DATE, bobVcs.pull(null, bob))

        write(aliceRoot, "Scenes/Main.scene", "{\n  \"x\": 2\n}\n")
        aliceVcs.commit("x = 2", listOf("Scenes/Main.scene"), alice)
        aliceVcs.push(null)
        assertEquals(PullOutcome.FAST_FORWARD, bobVcs.pull(null, bob))
        assertEquals("{\n  \"x\": 2\n}\n", File(bobRoot, "Scenes/Main.scene").readText())

        // Both sides commit different files: a merge commit by Bob.
        write(aliceRoot, "Assets/a.txt", "a\n")
        aliceVcs.commit("a", listOf("Assets/a.txt"), alice)
        aliceVcs.push(null)
        write(bobRoot, "Assets/b.txt", "b\n")
        bobVcs.commit("b", listOf("Assets/b.txt"), bob)
        assertEquals(PullOutcome.MERGED, bobVcs.pull(null, bob))
        val merge = bobVcs.log(1).single()
        assertEquals("Bob", merge.author)
        assertEquals(2, merge.parents.size)
        assertTrue(File(bobRoot, "Assets/a.txt").isFile)
        bobVcs.push(null)
        assertEquals(VcsTracking("origin/main", 0, 0), bobVcs.branches().tracking)
    }

    @Test
    fun Push_RejectedWhenBehind_SaysPullFirst() {
        val (aliceRoot, aliceVcs) = published()
        val (bobRoot, bobVcs) = cloneAs("bob")
        write(aliceRoot, "Assets/a.txt", "a\n")
        aliceVcs.commit("a", listOf("Assets/a.txt"), alice)
        aliceVcs.push(null)
        write(bobRoot, "Assets/b.txt", "b\n")
        bobVcs.commit("b", listOf("Assets/b.txt"), bob)
        assertEquals("vcs.error.push_rejected", assertThrows<VcsException> { bobVcs.push(null) }.messageKey)
    }

    @Test
    fun Pull_RefusedWithUncommittedChanges() {
        published()
        val (bobRoot, bobVcs) = cloneAs("bob")
        write(bobRoot, "Scenes/Main.scene", "{\n  \"x\": 9\n}\n")
        assertEquals("vcs.error.uncommitted_changes_pull", assertThrows<VcsException> { bobVcs.pull(null, bob) }.messageKey)
    }

    @Test
    fun Pull_WithAConflictingScene_KeepsOurVersionOnDiskAndListsTheConflict() {
        val (aliceRoot, aliceVcs) = published()
        val (bobRoot, bobVcs) = cloneAs("bob")
        write(aliceRoot, "Scenes/Main.scene", "{\n  \"x\": 2\n}\n")
        write(aliceRoot, "Assets/notes.txt", "alice\n")
        aliceVcs.commit("alice", listOf("Scenes/Main.scene", "Assets/notes.txt"), alice)
        aliceVcs.push(null)
        write(bobRoot, "Scenes/Main.scene", "{\n  \"x\": 3\n}\n")
        write(bobRoot, "Assets/notes.txt", "bob\n")
        bobVcs.commit("bob", listOf("Scenes/Main.scene", "Assets/notes.txt"), bob)

        assertEquals(PullOutcome.CONFLICTS, bobVcs.pull(null, bob))
        val conflicted = bobVcs.status().filter { it.kind == ChangeKind.CONFLICTING }.map { it.path }
        assertEquals(listOf("Assets/notes.txt", "Scenes/Main.scene"), conflicted)
        // §7.2: the scene holds no markers (ours); a plain text file keeps them for the Script Editor.
        assertEquals("{\n  \"x\": 3\n}\n", File(bobRoot, "Scenes/Main.scene").readText())
        assertTrue(ConflictMarkers.hasMarkers(File(bobRoot, "Assets/notes.txt").readText()))

        bobVcs.resolve("Scenes/Main.scene", Resolution.THEIRS)
        bobVcs.resolve("Assets/notes.txt", Resolution.MINE)
        bobVcs.commit("merge", listOf("Scenes/Main.scene", "Assets/notes.txt"), bob)
        assertEquals("{\n  \"x\": 2\n}\n", File(bobRoot, "Scenes/Main.scene").readText())
        bobVcs.push(null)
    }

    @Test
    fun RemoteActions_WithoutARemote_AreRefused() {
        val root = File(tmp, "p").apply { mkdirs() }
        val vcs = JGitVcsService(root).apply { init(alice, "first") }
        assertNull(vcs.branches().tracking)
        assertEquals("vcs.error.no_remote", assertThrows<VcsException> { vcs.push(null) }.messageKey)
        assertEquals("vcs.error.no_remote", assertThrows<VcsException> { vcs.fetch(null) }.messageKey)
    }

    @Test
    fun TransportError_MapsAuthenticationFailuresToSignIn() {
        val auth = JGitVcsService.transportError("https://github.com/me/g.git", RuntimeException("https://github.com/me/g.git: not authorized"))
        assertEquals("vcs.error.auth_required", auth.messageKey)
        assertEquals(listOf<Any>("github.com"), auth.args.toList())
        assertEquals("vcs.error.network", JGitVcsService.transportError("https://x/y", RuntimeException("connection refused")).messageKey)
    }

    @Test
    fun Structured_CoversScenesPrefabsMetaAndProjectFiles() {
        listOf("A.scene", "a/B.prefab", "t.png.meta", "G.skateproject").forEach { assertTrue(JGitVcsService.isStructured(it), it) }
        listOf("s.kts", "notes.txt", "rail.png").forEach { assertFalse(JGitVcsService.isStructured(it), it) }
    }
}
