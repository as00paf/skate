package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.ui.windows.ProjectSettingsChoices
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-57: Project Settings changes are undoable commands. */
class ProjectSettingCommandsTest {
    private fun project() = Project(
        name = "P", projectPath = "C:/p/P.skateproject", defaultScene = "C:/p/Scenes/MainScene.scene",
        scenesPath = listOf("C:/p/Scenes/MainScene.scene", "C:/p/Scenes/FeatureShowcase.scene"),
    )

    @Test
    fun DefaultScene_SetAndUndo() {
        val p = project()
        val command = SetDefaultSceneCommand(p, p.scenesPath[1])
        command.execute()
        assertEquals(p.scenesPath[1], p.defaultScene)
        command.undo()
        assertEquals(p.scenesPath[0], p.defaultScene)
        assertTrue(SetDefaultSceneCommand(p, p.defaultScene).isNoOp)
    }

    @Test
    fun PhysicsRate_SetsAppliesAndUndoes() {
        val p = project()
        val applied = mutableListOf<Int>()
        val command = SetPhysicsRateCommand(p, 120) { applied += it }
        command.execute()
        assertEquals(120, p.physicsFPS)
        command.undo()
        assertEquals(60, p.physicsFPS)
        assertEquals(listOf(120, 60), applied)
    }

    @Test
    fun PhysicsRate_FixedStepIsOneOverTheRate() {
        assertEquals(1f / 120f, PhysicsRates.fixedStep(120), 1e-7f)
        assertEquals(1f / 60f, PhysicsRates.fixedStep(0), 1e-7f)
    }

    @Test
    fun GameLanguage_SetAndUndo() {
        var locale = "en"
        val command = SetGameLanguageCommand("P", locale, "fr") { locale = it }
        command.execute()
        assertEquals("fr", locale)
        command.undo()
        assertEquals("en", locale)
    }

    @Test
    fun Choices_FindTheCurrentValues() {
        assertEquals(1, ProjectSettingsChoices.languageIndex("fr"))
        assertEquals(0, ProjectSettingsChoices.languageIndex("de"))
        assertEquals(PhysicsRates.CHOICES.indexOf(60), ProjectSettingsChoices.rateIndex(59))
        val p = project()
        assertEquals(1, ProjectSettingsChoices.sceneIndex(p.scenesPath, "C:\\p\\Scenes\\FeatureShowcase.scene"))
        assertEquals(-1, ProjectSettingsChoices.sceneIndex(p.scenesPath, "C:/p/Scenes/Gone.scene"))
    }
}
