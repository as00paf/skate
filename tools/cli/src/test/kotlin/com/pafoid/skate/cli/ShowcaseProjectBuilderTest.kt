package com.pafoid.skate.cli

import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.JointType
import com.pafoid.skate.game.samples.FeatureShowcase
import java.nio.file.Files
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShowcaseProjectBuilderTest {

    @Test
    fun Showcase_GeneratesValidProjectWithTheSharedShowcaseScene() {
        val dir = Files.createTempDirectory("skate-showcase").toFile()
        try {
            val project = ShowcaseProjectBuilder().build(dir)
            val service = SkateCliService(out = {})
            val report = service.validate(project.path)
            assertTrue(report.isValid, report.toString())
            assertEquals(1, report.sceneCount)
            val stats = service.run(project.path, FeatureShowcase.SCENE_NAME, 2, 0.016f)
            assertTrue(stats.gameObjects > 40, stats.toString())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun Showcase_SavedSceneKeepsEveryAreaAndTheUiOverlay() {
        val dir = Files.createTempDirectory("skate-showcase-ui").toFile()
        try {
            val project = ShowcaseProjectBuilder().build(dir)
            val service = SkateCliService(out = {})
            val scene = service.loadScenes(service.loadProject(project.path)).single()
            FeatureShowcase.AREAS.forEach { area -> assertTrue(scene.children.any { it.name == "[${area.label}] Area" }, area.key) }
            val types = scene.children.first { it.name == "[UI] Canvas" }.children.flatMap { it.components }.map { it::class.simpleName }
            listOf("UITextComponent", "UIImageComponent", "UIButtonComponent", "UIProgressBarComponent", "UISliderComponent")
                .forEach { assertTrue(it in types, "missing $it") }
            assertTrue(java.io.File(dir, "FeatureShowcase/Assets/${FeatureShowcase.TONE_FILE}").exists())
            // The camera's focus targets survive the save/load round trip, so legend buttons work after reopening.
            val focus = scene.children.single { it.name == "Camera" }.components
                .filterIsInstance<com.pafoid.skate.engine.ecs.components.CameraFocusComponent>().single()
            assertEquals(FeatureShowcase.AREAS.size + 1, focus.targets.size)
            assertEquals(FeatureShowcase.focusButtonName("vehicle"), focus.targets.single { it.name == "vehicle" }.buttonName)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun CliArguments_SampleDefaultsToCurrentDirectory() {
        assertEquals(CliCommand.Sample("."), CliArguments.parse(listOf("sample")))
        assertEquals(CliCommand.Sample("x"), CliArguments.parse(listOf("sample", "--dir", "x")))
    }

    @Test
    fun MarbleRun_GeneratesValidProjectWithMarblesAndJoints() {
        val dir = Files.createTempDirectory("skate-marble").toFile()
        try {
            val project = MarbleRunProjectBuilder().build(dir)
            val service = SkateCliService(out = {})
            assertTrue(service.validate(project.path).isValid)
            val scene = service.loadScenes(service.loadProject(project.path)).single()
            assertEquals(MarbleRunProjectBuilder.MARBLE_COUNT, scene.children.count { it.name.startsWith("Marble_") })
            val joints = scene.children.flatMap { it.components }.filterIsInstance<JointComponent>().map { it.jointType }
            assertEquals(setOf(JointType.HINGE, JointType.SLIDER), joints.toSet())
            service.run(project.path, null, 2, 0.016f)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun CliArguments_SampleNameIsValidated() {
        assertEquals(CliCommand.Sample("x", "marble-run"), CliArguments.parse(listOf("sample", "marble-run", "--dir", "x")))
        assertFailsWith<CliParseException> { CliArguments.parse(listOf("sample", "nope")) }
    }
}
