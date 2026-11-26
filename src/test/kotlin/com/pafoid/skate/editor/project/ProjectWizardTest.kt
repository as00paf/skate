package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import com.pafoid.skate.templates.NewProjectSettings
import com.pafoid.skate.templates.TemplateRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-114: the wizard model collects the template and settings and validates them without ImGui. */
class ProjectWizardTest {
    private val dir: File = kotlin.io.path.createTempDirectory("skate-wizard").toFile()

    @AfterEach
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun wizard() = ProjectWizard().apply {
        setProjectName("Game")
        setProjectLocation(dir.path)
    }

    @Test
    fun ProjectWizard_SettingsMirrorTheChoices() {
        val splash = File(dir, "s.png").apply { writeBytes(byteArrayOf(1)) }
        val wizard = wizard().apply {
            templateId = "tetris"; targetFps = 60; physicsBackend = PhysicsBackend.JOLT; graphicsBackend = GraphicsBackend.VULKAN
            splashImage = splash; includeAiFiles = true; copyEngineDefaults = false; versionControl = true
        }

        assertEquals(
            NewProjectSettings("tetris", 60, PhysicsBackend.JOLT, GraphicsBackend.VULKAN, true, splash, false, true),
            wizard.settings(),
        )
        assertEquals(TemplateRegistry.DEFAULT_ID, ProjectWizard().templateId)
    }

    @Test
    fun ProjectWizard_ValidatesFpsSplashAndExistingProject() {
        val wizard = wizard()
        assertTrue(wizard.canCreate())

        wizard.targetFps = 10
        assertFalse(wizard.canCreate())
        wizard.targetFps = 240
        assertTrue(wizard.canCreate())

        wizard.splashImage = File(dir, "missing.png")
        assertFalse(wizard.canCreate())
        wizard.splashImage = File(dir, "note.txt").apply { writeText("x") }
        assertFalse(wizard.canCreate())
        wizard.splashImage = null

        File(dir, "Game").mkdirs()
        File(dir, "Game/Game.skateproject").writeText("{}")
        assertTrue(wizard.projectFileExists())
        assertFalse(wizard.canCreate())
    }

    @Test
    fun ProjectWizard_NonEmptyFolderOnlyWarns() {
        val wizard = wizard()
        assertFalse(wizard.isTargetFolderNonEmpty())

        File(dir, "Game").mkdirs()
        File(dir, "Game/readme.txt").writeText("x")

        assertTrue(wizard.isTargetFolderNonEmpty())
        assertTrue(wizard.canCreate())
    }

    @Test
    fun ProjectWizard_StructurePreviewComesFromTheTemplate() {
        val wizard = wizard().apply { templateId = "tetris" }

        val names = wizard.getProjectStructureItems().map { it.name.trim() }

        assertTrue("Game.scene" in names && "GameOver.scene" in names, names.toString())
        assertTrue("Game.skateproject" in names)
        wizard.reset()
        assertEquals(TemplateRegistry.DEFAULT_ID, wizard.templateId)
    }
}
