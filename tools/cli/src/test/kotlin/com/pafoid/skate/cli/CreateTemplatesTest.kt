package com.pafoid.skate.cli

import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import com.pafoid.skate.templates.NewProjectSettings
import com.pafoid.skate.templates.SkateGameTemplate
import com.pafoid.skate.templates.TemplateRegistry
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** EDT-59: `skate create --template <id>` makes a project that validates in both engine-default modes and runs headless with referenced engine defaults. */
class CreateTemplatesTest {

    // The Skate Game's skater and board load their models even with a path-only PrefabsGenerator, which needs the
    // real asset pipeline; it is covered with mocked prefabs in ProjectCreatorTest until that works headless.
    private val headlessIds = TemplateRegistry.ids() - SkateGameTemplate.ID

    @Test
    fun CreateTemplates_EveryTemplateValidatesInBothModesAndRunsReference() {
        val dir = Files.createTempDirectory("skate-templates").toFile()
        try {
            headlessIds.forEach { id ->
                listOf("copy", "reference").forEach { mode ->
                    val name = "P_${id.replace('-', '_')}_$mode"
                    assertEquals(0, runCli(listOf("create", name, "--dir", dir.path, "--template", id, "--engine-defaults", mode)), "$id $mode")
                    val project = File(dir, "$name/$name.skateproject")
                    val service = SkateCliService(out = {})
                    val report = service.validate(project.path)
                    assertTrue(report.isValid, "$id $mode: $report")
                    assertEquals(TemplateRegistry.require(id).sceneNames.size, report.sceneCount, id)
                    // A copied cube.obj makes the scene load a model, which needs a GL context; headless has none.
                    if (mode == "reference") service.run(project.path, null, 3, 0.016f)
                    assertEquals(mode == "copy", File(dir, "$name/Assets/Models/cube.obj").isFile, "$id $mode")
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun CreateTemplates_CreateDefaultsToTheEmptyTemplate() {
        val dir = Files.createTempDirectory("skate-templates").toFile()
        try {
            val project = SkateCliService(out = {}).createProject("Blank", dir)
            assertEquals(listOf("MainScene.scene"), File(project.parentFile, "Scenes").list()!!.toList())
            assertFalse(File(project.parentFile, "Scenes/FeatureShowcase.scene").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun CliArguments_CreateParsesTheTemplateAndSettings() {
        val command = CliArguments.parse(listOf(
            "create", "Game", "--template", "tetris", "--physics", "jolt", "--graphics", "vulkan", "--fps", "60",
            "--engine-defaults", "reference", "--ai-files", "--vcs", "--splash", "s.png",
        )) as CliCommand.Create
        assertEquals(
            NewProjectSettings(
                template = "tetris", targetFps = 60, physicsBackend = PhysicsBackend.JOLT, graphicsBackend = GraphicsBackend.VULKAN,
                includeAiFiles = true, splashImage = File("s.png"), copyEngineDefaults = false, versionControl = true,
            ),
            command.settings,
        )
        assertEquals("Game", command.name)
        assertEquals(0, (CliArguments.parse(listOf("create", "G", "--fps", "uncapped")) as CliCommand.Create).settings.targetFps)
    }

    @Test
    fun CliArguments_CreateRejectsBadSettings() {
        listOf(
            listOf("--template", "nope"), listOf("--physics", "havok"), listOf("--fps", "10"), listOf("--fps", "0"),
            listOf("--engine-defaults", "maybe"),
        ).forEach { bad -> assertFailsWith<CliParseException>(bad.toString()) { CliArguments.parse(listOf("create", "G") + bad) } }
    }
}
