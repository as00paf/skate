package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.assetimport.blender.BlenderImportSettings
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.tools.external.BlenderInstall
import com.pafoid.skate.editor.tools.external.BlenderSource
import com.pafoid.skate.editor.tools.external.BlenderTestResult
import com.pafoid.skate.editor.tools.external.BlenderVersion
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringTables
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-66: the `.blend` inspector's selection and edit state, and the Editor Settings Blender section. */
class BlenderInspectorEdt66Test {
    @TempDir
    lateinit var dir: File

    private val blend = File("Assets/Models/cap.blend")

    @Test
    fun InspectedFile_StaysUntilTheSceneSelectionChanges() {
        val inspected = InspectedFile(exists = { true })
        val board = Any()
        inspected.select(blend, board)

        assertEquals(blend, inspected.current(board))
        assertNull(inspected.current(Any()), "another object selected")
        assertNull(inspected.current(board), "stays dropped")
    }

    @Test
    fun InspectedFile_DroppedWhenTheFileIsGone() {
        var exists = true
        val inspected = InspectedFile(exists = { exists })
        inspected.select(blend, null)
        assertEquals(blend, inspected.current(null))
        exists = false
        assertNull(inspected.current(null))
    }

    @Test
    fun Draft_KeepsEditsUntilApplyOrRevert_AndReloadsForAnotherFile() {
        val draft = BlenderSettingsDraft()
        val stored = BlenderImportSettings()
        draft.show(blend, stored)
        draft.value = stored.copy(onlyVisible = true)
        assertTrue(draft.dirty)

        draft.show(blend, stored.copy(autoReimport = false))
        assertTrue(draft.value.onlyVisible, "an edit survives the sidecar being re-read")

        draft.revert()
        assertFalse(draft.dirty)
        draft.value = stored.copy(onlyVisible = true)
        draft.applied()
        assertFalse(draft.dirty)

        val other = File("Assets/Models/board.blend")
        draft.value = stored.copy(exportActions = false)
        draft.show(other, stored)
        assertEquals(other, draft.file)
        assertEquals(stored, draft.value, "another file drops unapplied edits")
    }

    @Test
    fun Draft_PicksUpSidecarChanges_WhenNothingIsEdited() {
        val draft = BlenderSettingsDraft()
        draft.show(blend, BlenderImportSettings())
        draft.show(blend, BlenderImportSettings(onlyVisible = true))
        assertTrue(draft.value.onlyVisible)
        assertFalse(draft.dirty)
    }

    @Test
    fun BlenderSettings_PersistThroughTheManager_AndAreClamped() {
        val file = File(dir, "editor-settings.json")
        val serializer = Serializer()
        val manager = EditorSettingsManager(serializer, EventSystem(), mockk(relaxed = true), mockk(relaxed = true), file)
        manager.update { it.copy(blenderPath = "  D:/Blender/blender.exe ", blenderTimeoutSeconds = 5, blenderImportOnOpen = false) }

        val reloaded = EditorSettingsManager(serializer, EventSystem(), mockk(relaxed = true), mockk(relaxed = true), file)
        reloaded.loadSettings()
        assertEquals("D:/Blender/blender.exe", reloaded.editorSettings.blenderPath)
        assertEquals(EditorSettings.MIN_BLENDER_TIMEOUT, reloaded.editorSettings.blenderTimeoutSeconds)
        assertFalse(reloaded.editorSettings.blenderImportOnOpen)

        manager.update { it.copy(blenderPath = " ", blenderTimeoutSeconds = 99_999) }
        assertNull(manager.editorSettings.blenderPath, "blank = automatic search")
        assertEquals(EditorSettings.MAX_BLENDER_TIMEOUT, manager.editorSettings.blenderTimeoutSeconds)
    }

    @Test
    fun TestMessages_UseExistingKeys_WithMatchingArguments() {
        val keys = StringTables.merged(File(Assets.Strings.EDITOR_STRINGS))
        val install = BlenderInstall(File("C:/B/blender.exe"), BlenderVersion(5, 2, 3), BlenderSource.PATH)
        val results = listOf(
            BlenderTestResult.Found(install),
            BlenderTestResult.PathUnusable("D:/old.exe", install),
            BlenderTestResult.NotFound(null),
        )
        for (result in results) {
            val (text, _) = BlenderTestMessage.of(result)
            val pattern = keys.getProperty(text.first)
            assertTrue(pattern != null, "missing ${text.first}")
            assertEquals(Regex("%s").findAll(pattern).count(), text.second.size, text.first)
        }
        assertTrue(BlenderTestMessage.of(results[0]).second)
        assertFalse(BlenderTestMessage.of(results[2]).second)
    }
}
