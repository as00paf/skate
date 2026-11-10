package com.pafoid.skate.editor.vcs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VcsStatusModelTest {
    private fun c(path: String, kind: ChangeKind = ChangeKind.MODIFIED) = FileChange(path, kind)

    @Test
    fun Entries_PairAnAssetWithItsMeta() {
        val entries = VcsStatusModel.entries(listOf(c("Assets/rail.png", ChangeKind.UNTRACKED), c("Assets/rail.png.meta", ChangeKind.UNTRACKED)))
        assertEquals(1, entries.size)
        assertEquals(listOf("Assets/rail.png", "Assets/rail.png.meta"), entries.single().paths)
    }

    @Test
    fun Entries_KeepALoneMetaAndConflictedFilesAsTheirOwnRows() {
        val lone = VcsStatusModel.entries(listOf(c("Assets/a.png.meta")))
        assertEquals(listOf("Assets/a.png.meta"), lone.single().paths)
        val conflicted = VcsStatusModel.entries(listOf(c("Assets/a.png", ChangeKind.CONFLICTING), c("Assets/a.png.meta")))
        assertEquals(listOf(listOf("Assets/a.png"), listOf("Assets/a.png.meta")), conflicted.map { it.paths })
    }

    @Test
    fun Entries_LeaveOutTheNeverCommittedFolders() {
        val entries = VcsStatusModel.entries(listOf(c("Builds/game.bin"), c("builds/x"), c(".skate/s.json"), c("Library/cache"), c("Scenes/A.scene")))
        assertEquals(listOf("Scenes/A.scene"), entries.map { it.path })
        assertFalse(VcsStatusModel.isIgnored("Builds.txt"))
    }

    @Test
    fun Groups_FollowTheFileKind() {
        fun g(path: String, kind: ChangeKind = ChangeKind.MODIFIED) = VcsStatusModel.groupOf(c(path, kind))
        assertEquals(ChangeGroup.SCENES, g("Scenes/Main.scene"))
        assertEquals(ChangeGroup.PREFABS, g("Assets/prefabs/Rail.prefab"))
        assertEquals(ChangeGroup.SCRIPTS, g("Assets/scripts/spin.kts"))
        assertEquals(ChangeGroup.ASSETS, g("Assets/textures/rail.png"))
        assertEquals(ChangeGroup.ASSETS, g("Assets/textures/rail.png.meta"))
        assertEquals(ChangeGroup.SETTINGS, g("Game.skateproject"))
        assertEquals(ChangeGroup.SETTINGS, g("game.settings.json"))
        assertEquals(ChangeGroup.SETTINGS, g(".gitignore"))
        assertEquals(ChangeGroup.OTHER, g("README.md"))
        assertEquals(ChangeGroup.CONFLICTS, g("Scenes/Main.scene", ChangeKind.CONFLICTING))
        val grouped = VcsStatusModel.grouped(VcsStatusModel.entries(listOf(c("README.md"), c("Scenes/Main.scene"), c("x.scene", ChangeKind.CONFLICTING))))
        assertEquals(listOf(ChangeGroup.CONFLICTS, ChangeGroup.SCENES, ChangeGroup.OTHER), grouped.keys.toList())
    }

    @Test
    fun PathsFor_AddsMetasAndSkipsConflicts() {
        val entries = VcsStatusModel.entries(
            listOf(c("Assets/a.png"), c("Assets/a.png.meta"), c("Scenes/B.scene"), c("Scenes/C.scene", ChangeKind.CONFLICTING)),
        )
        assertEquals(
            listOf("Assets/a.png", "Assets/a.png.meta", "Scenes/B.scene"),
            VcsStatusModel.pathsFor(entries, setOf("Assets/a.png", "Scenes/B.scene", "Scenes/C.scene")),
        )
    }

    @Test
    fun CommitBlockedReason_ExplainsWhyCommitIsDisabled() {
        val paths = listOf("a")
        assertEquals("vcs.commit.blocked.playing", VcsStatusModel.commitBlockedReason("m", paths, playing = true, busy = false, hasConflicts = false))
        assertEquals("vcs.commit.blocked.busy", VcsStatusModel.commitBlockedReason("m", paths, playing = false, busy = true, hasConflicts = false))
        assertEquals("vcs.commit.blocked.conflicts", VcsStatusModel.commitBlockedReason("m", paths, playing = false, busy = false, hasConflicts = true))
        assertEquals("vcs.commit.blocked.nothing_selected", VcsStatusModel.commitBlockedReason("m", emptyList(), playing = false, busy = false, hasConflicts = false))
        assertEquals("vcs.commit.blocked.no_message", VcsStatusModel.commitBlockedReason(" ", paths, playing = false, busy = false, hasConflicts = false))
        assertNull(VcsStatusModel.commitBlockedReason("m", paths, playing = false, busy = false, hasConflicts = false))
    }

    @Test
    fun ActionRefusal_BlocksPlayAndFileRewritesWithUnsavedScenes() {
        assertEquals("vcs.error.playing", VcsStatusModel.actionRefusal(changesFiles = false, playing = true, unsavedScenes = false))
        assertEquals("vcs.error.unsaved_scenes", VcsStatusModel.actionRefusal(changesFiles = true, playing = false, unsavedScenes = true))
        assertNull(VcsStatusModel.actionRefusal(changesFiles = false, playing = false, unsavedScenes = true))
        assertNull(VcsStatusModel.actionRefusal(changesFiles = true, playing = false, unsavedScenes = false))
    }

    @Test
    fun NeedsReload_OnlyForScenesPrefabsAndProjectFiles() {
        assertTrue(VcsStatusModel.needsReload(listOf("Scenes/A.scene")))
        assertTrue(VcsStatusModel.needsReload(listOf("Game.skateproject")))
        assertTrue(VcsStatusModel.needsReload(listOf("Assets/p/Rail.prefab")))
        assertFalse(VcsStatusModel.needsReload(listOf("Assets/t/rail.png", "Assets/t/rail.png.meta")))
    }
}
