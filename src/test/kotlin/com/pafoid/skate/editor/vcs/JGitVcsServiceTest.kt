package com.pafoid.skate.editor.vcs

import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JGitVcsServiceTest {
    @TempDir
    lateinit var dir: File

    private val author = VcsAuthor("Tester", "tester@example.com")
    private val vcs by lazy { JGitVcsService(dir) }

    private fun write(path: String, text: String) = File(dir, path).apply { parentFile.mkdirs(); writeText(text) }

    private fun project(): JGitVcsService {
        write("Game.skateproject", "{}\n")
        write("Scenes/MainScene.scene", "{\n  \"name\": \"Main\",\n  \"x\": 1\n}\n")
        write("Assets/textures/rail.png", "png")
        write("Assets/textures/rail.png.meta", "{\"guid\":\"a\"}\n")
        write("Builds/game.bin", "binary")
        write(".skate/state.json", "{}")
        vcs.init(author, "Initial commit")
        return vcs
    }

    private fun kinds() = vcs.status().associate { it.path to it.kind }

    @Test
    fun Init_CreatesRepositoryIgnoreFilesAndAFirstCommit() {
        assertFalse(vcs.isRepository())
        project()
        assertTrue(vcs.isRepository())
        assertTrue(File(dir, ".gitignore").readText().contains(GitIgnoreTemplate.BEGIN))
        assertTrue(File(dir, ".gitattributes").readText().contains("*.scene text eol=lf"))
        val first = vcs.log(10).single()
        assertEquals("Initial commit", first.message)
        assertEquals("Tester", first.author)
        assertTrue("Assets/textures/rail.png.meta" in first.files, first.files.toString())
        assertTrue("Scenes/MainScene.scene" in first.files)
        assertTrue(first.files.none { it.startsWith("Builds/") || it.startsWith(".skate/") }, first.files.toString())
        assertEquals(emptyList<FileChange>(), vcs.status())
        val branches = vcs.branches()
        assertEquals("main", branches.current)
        assertEquals(listOf("main"), branches.local)
        assertEquals(mapOf("main" to first.id), branches.tips)
        assertEquals(first.id, branches.head)
        assertFalse(branches.detached)
    }

    @Test
    fun Init_KeepsAnExistingGitignoreAndAddsTheBlockOnce() {
        write(".gitignore", "secret.txt\n")
        vcs.init(author, "first")
        val text = File(dir, ".gitignore").readText()
        assertTrue(text.startsWith("secret.txt\n"))
        assertEquals(1, text.lines().count { it == GitIgnoreTemplate.BEGIN })
        assertThrows<VcsException> { vcs.init(author, "again") }.also { assertEquals("vcs.error.already_repository", it.messageKey) }
    }

    @Test
    fun Status_ReportsModifiedAddedAndDeletedFiles() {
        project()
        write("Scenes/MainScene.scene", "{\n  \"name\": \"Main\",\n  \"x\": 2\n}\n")
        write("Scenes/Other.scene", "{}\n")
        File(dir, "Game.skateproject").delete()
        write("Builds/new.bin", "ignored")
        assertEquals(
            mapOf(
                "Game.skateproject" to ChangeKind.DELETED,
                "Scenes/MainScene.scene" to ChangeKind.MODIFIED,
                "Scenes/Other.scene" to ChangeKind.UNTRACKED,
            ),
            kinds(),
        )
    }

    @Test
    fun Diff_ShowsChangedLinesWithContext() {
        project()
        write("Scenes/MainScene.scene", "{\n  \"name\": \"Main\",\n  \"x\": 2\n}\n")
        val diff = vcs.diff("Scenes/MainScene.scene") as FileDiff.Text
        assertEquals(DiffLine.Kind.HUNK, diff.lines.first().kind)
        assertEquals(listOf(DiffLine(DiffLine.Kind.REMOVED, "  \"x\": 1")), diff.lines.filter { it.kind == DiffLine.Kind.REMOVED })
        assertEquals(listOf(DiffLine(DiffLine.Kind.ADDED, "  \"x\": 2")), diff.lines.filter { it.kind == DiffLine.Kind.ADDED })
        assertTrue(diff.lines.any { it.kind == DiffLine.Kind.CONTEXT && it.text.contains("Main") })
    }

    @Test
    fun Diff_OfANewFileIsAllAdditions_AndBinaryFilesGiveSizes() {
        project()
        write("Scenes/New.scene", "a\nb\n")
        val added = vcs.diff("Scenes/New.scene") as FileDiff.Text
        assertEquals(listOf("a", "b"), added.lines.filter { it.kind == DiffLine.Kind.ADDED }.map { it.text })
        File(dir, "Assets/textures/rail.png").writeBytes(byteArrayOf(1, 0, 2, 0, 3))
        assertEquals(FileDiff.Binary("Assets/textures/rail.png", 3, 5), vcs.diff("Assets/textures/rail.png"))
    }

    @Test
    fun Commit_RecordsOnlyTheSelectedFiles() {
        project()
        write("Scenes/MainScene.scene", "changed\n")
        write("Scenes/Other.scene", "{}\n")
        File(dir, "Game.skateproject").delete()
        val id = vcs.commit("Edit main, drop project", listOf("Scenes/MainScene.scene", "Game.skateproject"), author)
        val head = vcs.log(10).first()
        assertEquals(id, head.id)
        assertEquals(listOf("Game.skateproject", "Scenes/MainScene.scene"), head.files)
        assertEquals(mapOf("Scenes/Other.scene" to ChangeKind.UNTRACKED), kinds())
        assertEquals(2, vcs.log(10).size)
    }

    @Test
    fun Commit_IsRefusedWithoutAMessageOrFiles() {
        project()
        write("Scenes/MainScene.scene", "changed\n")
        assertEquals("vcs.error.no_message", assertThrows<VcsException> { vcs.commit("  ", listOf("Scenes/MainScene.scene"), author) }.messageKey)
        assertEquals("vcs.error.nothing_selected", assertThrows<VcsException> { vcs.commit("msg", emptyList(), author) }.messageKey)
        assertEquals(1, vcs.log(10).size)
    }

    @Test
    fun DiffInCommit_ShowsWhatThatCommitChanged() {
        project()
        write("Scenes/MainScene.scene", "{\n  \"name\": \"Main\",\n  \"x\": 3\n}\n")
        val id = vcs.commit("x to 3", listOf("Scenes/MainScene.scene"), author)
        val diff = vcs.diffInCommit(id, "Scenes/MainScene.scene") as FileDiff.Text
        assertEquals(listOf("  \"x\": 3"), diff.lines.filter { it.kind == DiffLine.Kind.ADDED }.map { it.text })
        val root = vcs.log(10).last()
        val created = vcs.diffInCommit(root.id, "Game.skateproject") as FileDiff.Text
        assertEquals(listOf("{}"), created.lines.filter { it.kind == DiffLine.Kind.ADDED }.map { it.text })
    }

    @Test
    fun Branches_CreateSwitchAndDeleteMerged() {
        project()
        vcs.createBranch("feature")
        vcs.branches().let { assertEquals("feature", it.current); assertEquals(listOf("feature", "main"), it.local) }
        write("Scenes/MainScene.scene", "feature\n")
        vcs.commit("feature work", listOf("Scenes/MainScene.scene"), author)
        vcs.checkout("main")
        assertEquals("main", vcs.branches().current)
        assertTrue(File(dir, "Scenes/MainScene.scene").readText().contains("\"x\": 1"))
        assertEquals("vcs.error.not_merged", assertThrows<VcsException> { vcs.deleteBranch("feature") }.messageKey)
        assertEquals("vcs.error.delete_current", assertThrows<VcsException> { vcs.deleteBranch("main") }.messageKey)
        vcs.createBranch("merged")
        vcs.checkout("main")
        vcs.deleteBranch("merged")
        assertEquals(listOf("feature", "main"), vcs.branches().local)
    }

    @Test
    fun Branches_SwitchIsRefusedWithUncommittedChanges_AndNamesAreChecked() {
        project()
        vcs.createBranch("other")
        write("Scenes/MainScene.scene", "dirty\n")
        assertEquals("vcs.error.uncommitted_changes", assertThrows<VcsException> { vcs.checkout("main") }.messageKey)
        assertEquals("other", vcs.branches().current)
        assertEquals("vcs.error.invalid_branch_name", assertThrows<VcsException> { vcs.createBranch("bad name..") }.messageKey)
        assertEquals("vcs.error.branch_exists", assertThrows<VcsException> { vcs.createBranch("main") }.messageKey)
    }

    @Test
    fun Branches_UntrackedFilesDoNotBlockASwitch() {
        project()
        vcs.createBranch("other")
        write("Scenes/Scratch.scene", "{}\n")
        vcs.checkout("main")
        assertEquals("main", vcs.branches().current)
    }

    @Test
    fun Discard_RestoresTrackedFilesAndDeletesNewOnes() {
        project()
        write("Scenes/MainScene.scene", "changed\n")
        File(dir, "Game.skateproject").delete()
        write("Scenes/New.scene", "{}\n")
        vcs.discard(listOf("Scenes/MainScene.scene", "Game.skateproject", "Scenes/New.scene"))
        assertEquals(emptyList<FileChange>(), vcs.status())
        assertTrue(File(dir, "Scenes/MainScene.scene").readText().contains("\"x\": 1"))
        assertTrue(File(dir, "Game.skateproject").isFile)
        assertFalse(File(dir, "Scenes/New.scene").exists())
    }

    private fun conflict() {
        project()
        vcs.createBranch("theirs")
        write("Scenes/MainScene.scene", "theirs\n")
        vcs.commit("theirs", listOf("Scenes/MainScene.scene"), author)
        vcs.checkout("main")
        write("Scenes/MainScene.scene", "mine\n")
        vcs.commit("mine", listOf("Scenes/MainScene.scene"), author)
        Git.open(dir).use { it.merge().include(it.repository.resolve("theirs")).call() }
    }

    @Test
    fun Conflict_IsListedAndBlocksCommitAndDiscard() {
        conflict()
        assertEquals(ChangeKind.CONFLICTING, kinds()["Scenes/MainScene.scene"])
        assertTrue(ConflictMarkers.hasMarkers(File(dir, "Scenes/MainScene.scene").readText()))
        assertEquals("vcs.error.discard_conflict", assertThrows<VcsException> { vcs.discard(listOf("Scenes/MainScene.scene")) }.messageKey)
        assertEquals("vcs.error.unresolved_conflicts", assertThrows<VcsException> { vcs.commit("merge", listOf("Scenes/MainScene.scene"), author) }.messageKey)
    }

    @Test
    fun Conflict_KeepMineThenCommitMakesAMergeCommit() {
        conflict()
        vcs.resolve("Scenes/MainScene.scene", Resolution.MINE)
        assertEquals("mine\n", File(dir, "Scenes/MainScene.scene").readText())
        assertTrue(vcs.status().none { it.kind == ChangeKind.CONFLICTING })
        vcs.commit("merge theirs", listOf("Scenes/MainScene.scene"), author)
        Git.open(dir).use { git -> assertEquals(2, git.log().setMaxCount(1).call().first().parentCount) }
    }

    @Test
    fun Conflict_TakeTheirsUsesTheOtherVersion() {
        conflict()
        vcs.resolve("Scenes/MainScene.scene", Resolution.THEIRS)
        assertEquals("theirs\n", File(dir, "Scenes/MainScene.scene").readText())
        assertEquals("vcs.error.not_conflicted", assertThrows<VcsException> { vcs.resolve("Scenes/MainScene.scene", Resolution.MINE) }.messageKey)
    }

    @Test
    fun ConflictDiff_ShowsMineAgainstTheirs() {
        conflict()
        val diff = vcs.conflictDiff("Scenes/MainScene.scene") as FileDiff.Text
        assertEquals(listOf(DiffLine(DiffLine.Kind.REMOVED, "mine"), DiffLine(DiffLine.Kind.ADDED, "theirs")), diff.lines.filter { it.kind != DiffLine.Kind.HUNK })
        vcs.resolve("Scenes/MainScene.scene", Resolution.MINE)
        assertEquals("vcs.error.not_conflicted", assertThrows<VcsException> { vcs.conflictDiff("Scenes/MainScene.scene") }.messageKey)
    }

    @Test
    fun CreateBranch_FromAnotherBranchWithoutSwitching() {
        project()
        vcs.createBranch("feature")
        write("Scenes/MainScene.scene", "feature\n")
        val featureTip = vcs.commit("on feature", listOf("Scenes/MainScene.scene"), author)
        vcs.checkout("main")
        vcs.createBranch("copy", from = "feature", checkout = false)
        val branches = vcs.branches()
        assertEquals("main", branches.current)
        assertEquals(featureTip, branches.tips["copy"])
        assertEquals("vcs.error.unknown_branch", assertThrows<VcsException> { vcs.createBranch("x", from = "nope") }.messageKey)
    }

    @Test
    fun Log_AllBranchesHasEveryBranchWithParents() {
        project()
        val root = vcs.log(10).single()
        vcs.createBranch("feature")
        write("Scenes/MainScene.scene", "feature\n")
        val feature = vcs.commit("on feature", listOf("Scenes/MainScene.scene"), author)
        vcs.checkout("main")
        assertEquals(listOf(root.id), vcs.log(10).map { it.id })
        val all = vcs.log(10, allBranches = true)
        assertEquals(setOf(root.id, feature), all.map { it.id }.toSet())
        assertEquals(listOf(root.id), all.first { it.id == feature }.parents)
        assertEquals(emptyList<String>(), all.first { it.id == root.id }.parents)
    }

    @Test
    fun Branches_DetachedHeadHasNoCurrentBranch() {
        project()
        val id = vcs.log(1).single().id
        Git.open(dir).use { it.checkout().setName(id).call() }
        val branches = vcs.branches()
        assertEquals(null, branches.current)
        assertEquals(id, branches.head)
        assertTrue(branches.detached)
    }

    @Test
    fun NotARepository_CallsAreRefused_EvenInsideAnotherRepository() {
        Git.init().setDirectory(dir).call().close()
        val nested = File(dir, "MyGame").apply { mkdirs() }
        val inner = JGitVcsService(nested)
        assertFalse(inner.isRepository())
        assertEquals("vcs.error.not_repository", assertThrows<VcsException> { inner.status() }.messageKey)
        assertEquals(null, inner.configuredAuthor())
    }
}
