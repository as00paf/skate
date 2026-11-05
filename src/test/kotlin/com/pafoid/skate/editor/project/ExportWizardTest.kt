package com.pafoid.skate.editor.project

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
}
