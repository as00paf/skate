package com.pafoid.skate.editor.project

import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.engine.utils.BuildMode
import com.pafoid.skate.engine.utils.ExportArt
import com.pafoid.skate.engine.utils.ExportFailure
import com.pafoid.skate.engine.utils.ExportProgress
import com.pafoid.skate.engine.utils.ExportResult
import com.pafoid.skate.engine.utils.ExportStep
import com.pafoid.skate.engine.utils.ExportTarget
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExportWizardTest {
    @TempDir
    lateinit var dir: File

    private fun project(name: String = "Game"): Project {
        val folder = File(dir, name).also { File(it, "Scenes").mkdirs() }
        val main = File(folder, "Scenes/Main.scene").also { it.writeText("{}") }
        File(folder, "Scenes/Extra.scene").writeText("{}")
        val file = File(folder, "$name.skateproject").also { it.writeText("{}") }
        return Project(name = name, projectPath = file.absolutePath, defaultScene = main.absolutePath, scenesPath = listOf(main.absolutePath))
    }

    private fun scenes(p: Project) = File(File(p.projectPath).parentFile, "Scenes").listFiles()!!.sortedBy { it.name }

    private fun jar() = File(dir, "game.jar").also { it.writeText("jar") }

    @Test
    fun ExportWizard_DefaultSceneCannotBeExcluded() {
        val p = project()
        val w = ExportWizard().also { it.load(p, scenes(p)) }
        val extra = w.scenes.first { it.endsWith("Extra.scene") }
        w.setIncluded(p.defaultScene, false)
        w.setIncluded(extra, false)
        assertTrue(w.isIncluded(p.defaultScene))
        assertFalse(w.isIncluded(extra))
        assertEquals(1, w.includedSceneCount)
        assertEquals(setOf(extra), w.options().excludedScenes)
    }

    @Test
    fun ExportWizard_OptionsMirrorTheChoices() {
        val p = project()
        val w = ExportWizard().also { it.load(p, scenes(p)) }
        assertNull(w.options().outputDir, "blank output means the default builds folder")
        w.target = ExportTarget.PACK_ONLY
        w.compress = false
        w.onlyReferenced = false
        w.outputDir = "  C:/out  "
        val o = w.options()
        assertEquals(ExportTarget.PACK_ONLY, o.target)
        assertFalse(o.compress)
        assertFalse(o.onlyReferenced)
        assertEquals("C:/out", o.outputDir)
    }

    @Test
    fun ExportWizard_ReportsMissingJarOnlyForWindowsAndRejectsProjectFolder() {
        val p = project()
        val w = ExportWizard().also { it.load(p, scenes(p)) }
        assertEquals(listOf(ExportWizard.Issue.GAME_JAR_MISSING), w.issues(p, null))
        assertTrue(w.canStart(p, jar()))
        w.target = ExportTarget.PACK_ONLY
        assertTrue(w.canStart(p, null))
        w.outputDir = File(p.projectPath).parent
        assertEquals(listOf(ExportWizard.Issue.INVALID_OUTPUT), w.issues(p, null))
    }

    @Test
    fun ExportWizard_FollowsTheExportLifecycle() {
        val p = project()
        val w = ExportWizard().also { it.load(p, scenes(p)) }
        w.start()
        assertEquals(ExportWizard.Phase.SAVING, w.phase)
        assertFalse(w.canStart(p, jar()), "no second export while one runs")
        w.onProgress(ExportProgress(ExportStep.PACKING, 0.5f, "Scenes/Main.scene"))
        assertEquals(ExportWizard.Phase.RUNNING, w.phase)
        assertEquals(ExportStep.PACKING, w.progress?.step)
        w.onFinished(ExportResult(File("out"), assetCount = 3))
        assertEquals(ExportWizard.Phase.SUCCEEDED, w.phase)
        w.onProgress(ExportProgress(ExportStep.DONE, 1f))
        assertEquals(ExportWizard.Phase.SUCCEEDED, w.phase, "late progress does not reopen a finished export")
        w.reset()
        assertEquals(ExportWizard.Phase.EDITING, w.phase)
        w.start()
        w.onFinished(ExportResult(File("out"), ExportFailure.SAVE_FAILED))
        assertEquals(ExportWizard.Phase.FAILED, w.phase)
    }

    @Test
    fun ExportWizard_KeepsChoicesForTheSameProjectAndResetsForAnother() {
        val p = project()
        val w = ExportWizard().also { it.load(p, scenes(p)) }
        val extra = w.scenes.first { it.endsWith("Extra.scene") }
        w.setIncluded(extra, false)
        w.outputDir = "C:/out"
        w.load(p, scenes(p))
        assertFalse(w.isIncluded(extra))
        assertEquals("C:/out", w.outputDir)

        val other = project("Other")
        w.load(other, scenes(other))
        assertTrue(w.scenes.all(w::isIncluded))
        assertEquals("", w.outputDir)
    }

    /** [EditorSettings] kept in memory, as `EditorSettingsManager.update` keeps (and saves) them. */
    private class FakeSettings {
        var settings = EditorSettings()
        var saves = 0
        val memory = ExportMemory.inSettings({ settings }) { change -> settings = change(settings); saves++ }
    }

    @Test
    fun ExportWizard_RemembersChoicesPerProjectAcrossSessions() {
        val p = project()
        val other = project("Other")
        val store = FakeSettings()
        val first = ExportWizard(store.memory).also { it.load(p, scenes(p)) }
        val extra = first.scenes.first { it.endsWith("Extra.scene") }
        first.target = ExportTarget.PACK_ONLY
        first.mode = BuildMode.DEBUG
        first.compress = false
        first.onlyReferenced = false
        first.outputDir = "C:/out"
        first.setIncluded(extra, false)
        first.start()
        assertEquals(1, store.saves)

        // A new editor session (new wizard) on the same settings.
        val next = ExportWizard(store.memory).also { it.load(p, scenes(p)) }
        assertEquals(ExportTarget.PACK_ONLY, next.target)
        assertEquals(BuildMode.DEBUG, next.options().mode)
        assertFalse(next.compress)
        assertFalse(next.onlyReferenced)
        assertEquals("C:/out", next.outputDir)
        assertFalse(next.isIncluded(extra))

        next.load(other, scenes(other))
        assertEquals(ExportTarget.WINDOWS, next.target, "another project starts from the defaults")
        assertEquals(BuildMode.RELEASE, next.mode)
        next.load(p, scenes(p))
        assertEquals(BuildMode.DEBUG, next.mode, "back to the first project: its choices again")

        next.reset(); next.start()
        assertEquals(1, store.saves, "unchanged choices are not saved again")
    }

    @Test
    fun ExportPreferences_SurviveTheEditorSettingsFile() {
        val prefs = ExportPreferences(ExportTarget.PACK_ONLY, BuildMode.DEBUG, false, false, "out", setOf("C:/a.scene"))
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val text = json.encodeToString(EditorSettings.serializer(), EditorSettings(exportPreferences = mapOf("C:/p/Game.skateproject" to prefs)))
        assertEquals(prefs, json.decodeFromString(EditorSettings.serializer(), text).exportPreferences["C:/p/Game.skateproject"])
        assertTrue(json.decodeFromString(EditorSettings.serializer(), "{}").exportPreferences.isEmpty())
    }

    @Test
    fun ExportWizard_BlocksOnlyWhenNoIconAtAllCanBeShipped() {
        val p = project()
        val w = ExportWizard().also { it.load(p, scenes(p)) }
        val fallback = ExportArt("engine:Textures/app_icon.png", "", 0f, emptyList())
        assertTrue(w.issues(p, jar(), fallback).isEmpty(), "a missing project icon is only a warning")
        assertEquals(listOf(ExportWizard.Issue.ICON_UNREADABLE), w.issues(p, jar(), fallback.copy(iconPath = "")))
        assertFalse(w.canStart(p, jar(), fallback.copy(iconPath = "")))
    }
}
