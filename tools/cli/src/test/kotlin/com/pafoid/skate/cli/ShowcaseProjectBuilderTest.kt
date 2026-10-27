package com.pafoid.skate.cli

import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.JointType
import java.nio.file.Files
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShowcaseProjectBuilderTest {

    @Test
    fun Showcase_GeneratesValidProjectWithOneScenePerFeature() {
        val dir = Files.createTempDirectory("skate-showcase").toFile()
        try {
            val project = ShowcaseProjectBuilder().build(dir)
            val service = SkateCliService(out = {})
            val report = service.validate(project.path)
            assertTrue(report.isValid, report.toString())
            assertEquals(ShowcaseProjectBuilder.SCENE_NAMES.size, report.sceneCount)
            ShowcaseProjectBuilder.SCENE_NAMES.forEach {
                service.run(project.path, it, 2, 0.016f)
            }
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
