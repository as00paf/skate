package com.pafoid.skate.editor.vcs

import com.pafoid.skate.editor.vcs.BranchButtonModel.Click
import com.pafoid.skate.editor.vcs.BranchButtonModel.Tone
import com.pafoid.skate.editor.vcs.BranchPopupModel.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BranchModelsTest {
    private val root = File("p")
    private val branches = VcsBranches("main", listOf("main", "feature/rails", "experiment"), head = "a1b2c3d4e5f6")
    private val commit = VcsCommit("a1b2c3d4e5f6", "first", "T", 0, emptyList())
    private val clean = VcsSession.State(root = root, isRepository = true, loaded = true, branches = branches, history = listOf(commit))

    private fun change(path: String, kind: ChangeKind = ChangeKind.MODIFIED) = FileChange(path, kind)

    @Test
    fun Button_HiddenWithoutAProject() {
        assertNull(BranchButtonModel.label(clean, hasProject = false))
        assertNull(BranchButtonModel.label(VcsSession.State(), hasProject = true))
    }

    @Test
    fun Button_LabelStates() {
        fun label(s: VcsSession.State) = BranchButtonModel.label(s, hasProject = true)!!
        assertEquals("vcs.button.loading", label(VcsSession.State(root = root)).textKey)
        label(VcsSession.State(root = root, loaded = true)).let {
            assertEquals("vcs.button.off", it.textKey)
            assertEquals(Tone.DIM, it.tone)
        }
        label(clean).let {
            assertEquals("vcs.button.clean", it.textKey)
            assertEquals(listOf<Any>("main"), it.args)
            assertEquals(Click.POPUP, it.click)
        }
        // A .meta goes with its asset: two files, one entry.
        val changed = clean.copy(changes = listOf(change("Assets/a.png"), change("Assets/a.png.meta"), change("Scenes/A.scene")))
        assertEquals("vcs.button.changes", label(changed).textKey)
        assertEquals(listOf<Any>("main", 2), label(changed).args)
        assertEquals("vcs.button.busy", label(changed.copy(busy = true)).textKey)
        label(clean.copy(changes = listOf(change("Scenes/A.scene", ChangeKind.CONFLICTING)))).let {
            assertEquals("vcs.button.conflicts", it.textKey)
            assertEquals(Tone.DANGER, it.tone)
            assertEquals(Click.CONFLICTS, it.click)
        }
        label(clean.copy(error = VcsException("vcs.error.git", "boom"))).let {
            assertEquals(Tone.WARNING, it.tone)
            assertEquals("vcs.error.git", it.tooltipKey)
            assertEquals(listOf<Any>("boom"), it.tooltipArgs)
        }
        val detached = clean.copy(branches = branches.copy(current = null))
        assertEquals(listOf<Any>("a1b2c3d"), label(detached).args)
        assertEquals("vcs.button.detached.tooltip", label(detached).tooltipKey)
    }

    @Test
    fun Popup_ActionsOfARepository() {
        val entries = BranchPopupModel.actions(clean, playing = false)
        assertEquals(listOf(Action.UPDATE, Action.COMMIT, Action.CHANGES, Action.HISTORY, Action.NEW_BRANCH), entries.map { it.action })
        val update = entries.first { it.action == Action.UPDATE }
        assertFalse(update.enabled)
        assertEquals("vcs.popup.needs_remote", update.reasonKey)
        assertTrue(entries.first { it.action == Action.COMMIT }.enabled)
    }

    @Test
    fun Popup_ResolveOnlyWithConflicts_AndPlayOrBusyDisable() {
        val conflicted = clean.copy(changes = listOf(change("a", ChangeKind.CONFLICTING), change("b")))
        val resolve = BranchPopupModel.actions(conflicted, false).single { it.action == Action.RESOLVE }
        assertEquals(1, resolve.count)
        assertEquals(1, BranchPopupModel.actions(conflicted, false).single { it.action == Action.COMMIT }.count)
        BranchPopupModel.actions(clean, playing = true).first { it.action == Action.COMMIT }.let {
            assertFalse(it.enabled)
            assertEquals("vcs.error.playing", it.reasonKey)
        }
        assertEquals("vcs.busy", BranchPopupModel.actions(clean.copy(busy = true), false).first { it.action == Action.NEW_BRANCH }.reasonKey)
        assertEquals("vcs.error.no_commits", BranchPopupModel.actions(clean.copy(history = emptyList()), false).first { it.action == Action.NEW_BRANCH }.reasonKey)
    }

    @Test
    fun Popup_NotARepositoryOffersInitializeOnly() {
        val entries = BranchPopupModel.actions(VcsSession.State(root = root, loaded = true), false)
        assertEquals(listOf(Action.INITIALIZE, Action.CLONE), entries.map { it.action })
        assertTrue(entries[0].enabled)
        assertFalse(entries[1].enabled)
    }

    @Test
    fun Popup_BranchSearchPutsTheCurrentBranchFirst() {
        assertEquals(listOf("main", "experiment", "feature/rails"), BranchPopupModel.branches(clean, "").map { it.name })
        assertTrue(BranchPopupModel.branches(clean, "").first().current)
        assertEquals(listOf("feature/rails"), BranchPopupModel.branches(clean, " RAIL ").map { it.name })
        assertEquals("feature/rails", BranchPopupModel.enterTarget(BranchPopupModel.branches(clean, "rail")))
        assertNull(BranchPopupModel.enterTarget(BranchPopupModel.branches(clean, "mai")), "the current branch is no checkout target")
        assertNull(BranchPopupModel.enterTarget(BranchPopupModel.branches(clean, "e")))
    }

    @Test
    fun Popup_CheckoutAndDeleteRefusals() {
        assertEquals("vcs.error.unsaved_scenes", BranchPopupModel.checkoutRefusal(clean, playing = false, unsavedScenes = true))
        assertEquals("vcs.busy", BranchPopupModel.checkoutRefusal(clean.copy(busy = true), false, false))
        assertNull(BranchPopupModel.checkoutRefusal(clean, false, false))
        assertEquals("vcs.popup.delete_current", BranchPopupModel.deleteRefusal(clean, "main", false))
        assertNull(BranchPopupModel.deleteRefusal(clean, "experiment", false))
    }

    @Test
    fun BranchNames_FollowGitRules() {
        val existing = listOf("main")
        assertNull(BranchNames.problem("feature/rails", existing))
        assertNull(BranchNames.problem("  fix-1  ", existing))
        assertEquals("vcs.branch.name.empty", BranchNames.problem(" ", existing))
        assertEquals("vcs.branch.name.exists", BranchNames.problem("main", existing))
        listOf("a b", "a..b", "a~1", "a^", "a:b", "a?", "a*", "a[b", "a\\b", "/a", "a/", "a//b", "-a", "a.", "a.lock", ".a", "a/.b", "@", "a@{b")
            .forEach { assertEquals("vcs.branch.name.invalid", BranchNames.problem(it, existing), it) }
    }

    @Test
    fun ConflictPrompt_OpensOncePerNewConflict() {
        val prompt = ConflictPrompt()
        assertFalse(prompt.shouldOpen(emptyList()))
        assertTrue(prompt.shouldOpen(listOf("a", "b")))
        assertFalse(prompt.shouldOpen(listOf("a", "b")), "same set: not again")
        assertFalse(prompt.shouldOpen(listOf("b")), "one resolved: no reason to reopen")
        assertTrue(prompt.shouldOpen(listOf("b", "c")), "a new conflicted file")
        assertFalse(prompt.shouldOpen(emptyList()))
        assertTrue(prompt.shouldOpen(listOf("a")), "a new merge after all were resolved")
    }

    @Test
    fun ChangeSelection_TicksNewFilesSkipsConflictsAndDropsGoneOnes() {
        val sel = ChangeSelection()
        val first = VcsStatusModel.entries(listOf(change("a.scene"), change("b.png"), change("b.png.meta"), change("c.scene", ChangeKind.CONFLICTING)))
        sel.sync(first)
        assertEquals(listOf("a.scene", "b.png", "b.png.meta"), sel.paths(first))
        assertTrue(sel.allSelected(first))
        sel.toggle("a.scene")
        assertFalse(sel.allSelected(first))
        sel.sync(first)
        assertFalse(sel.isSelected("a.scene"), "a known file keeps the user's choice")
        sel.toggleAll(first)
        assertTrue(sel.allSelected(first))
        sel.toggleAll(first)
        assertEquals(emptyList<String>(), sel.paths(first))
        val later = VcsStatusModel.entries(listOf(change("b.png"), change("d.scene")))
        sel.sync(later)
        assertEquals(listOf("d.scene"), sel.paths(later))
    }

    @Test
    fun DiffStyle_MapsEveryLineKind() {
        assertEquals(DiffStyle.Style("+ ", DiffStyle.Role.ADDED), DiffStyle.of(DiffLine.Kind.ADDED))
        assertEquals(DiffStyle.Style("- ", DiffStyle.Role.REMOVED), DiffStyle.of(DiffLine.Kind.REMOVED))
        assertEquals(DiffStyle.Style("", DiffStyle.Role.HUNK), DiffStyle.of(DiffLine.Kind.HUNK))
        assertEquals(DiffStyle.Style("  ", DiffStyle.Role.PLAIN), DiffStyle.of(DiffLine.Kind.CONTEXT))
        assertNull(DiffStyle.color(DiffStyle.Role.PLAIN))
        assertTrue(DiffStyle.color(DiffStyle.Role.ADDED)!![1] > DiffStyle.color(DiffStyle.Role.ADDED)!![0], "added is green")
        assertEquals("-", DiffStyle.size(null))
        assertEquals("512 B", DiffStyle.size(512))
        assertEquals("2 KB", DiffStyle.size(2048))
        assertEquals("1.5 MB", DiffStyle.size(1024L * 1024 * 3 / 2))
    }

    @Test
    fun WindowNames_MatchTheDialogs() {
        assertEquals(VcsWindows.COMMIT, com.pafoid.skate.editor.ui.windows.vcs.CommitDialog.NAME)
        assertEquals(VcsWindows.CONFLICTS, com.pafoid.skate.editor.ui.windows.vcs.ConflictsDialog.NAME)
        assertEquals(VcsWindows.LOG, com.pafoid.skate.editor.ui.windows.VersionControlWindow.NAME)
    }
}
