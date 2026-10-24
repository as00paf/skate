package com.pafoid.skate.cli

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SkateCliServiceTest {

    @Test
    fun CliService_CreatedProjectValidatesAndRunsHeadless() {
        val dir = Files.createTempDirectory("skate-cli").toFile()
        try {
            val service = SkateCliService(out = {})
            val projectFile = service.createProject("Demo", dir)
            assertTrue(projectFile.exists())
            assertTrue(service.validate(projectFile.path).isValid)
            assertTrue(service.validate(projectFile.parent).isValid)

            val stats = service.run(projectFile.path, null, 5, 0.1f)
            assertEquals("MainScene", stats.sceneName)
            assertEquals(5, stats.frames)
            assertFailsWith<CliException> { service.createProject("Demo", dir) }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun CliService_ValidateReportsMissingScene() {
        val dir = Files.createTempDirectory("skate-cli").toFile()
        try {
            val service = SkateCliService(out = {})
            val projectFile = service.createProject("Demo", dir)
            File(projectFile.parentFile, "Scenes").deleteRecursively()
            val report = service.validate(projectFile.path)
            assertTrue(!report.isValid)
            assertFailsWith<CliException> { service.run(projectFile.path, null, 1, 0.1f) }
        } finally {
            dir.deleteRecursively()
        }
    }
}
