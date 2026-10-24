package com.pafoid.skate.cli

import java.nio.file.Files
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
}
