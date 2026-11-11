package com.pafoid.skate.cli

import org.junit.jupiter.api.Assertions.assertFalse
import com.pafoid.skate.engine.assets.ProjectLayout
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

    @Test
    fun CliService_CreatedProjectHasOneAssetsFolder() {
        val dir = Files.createTempDirectory("skate-cli").toFile()
        try {
            val projectDir = SkateCliService(out = {}).createProject("Demo", dir).parentFile
            assertTrue(ProjectLayout.duplicateAssetFolders(projectDir).isEmpty())
            assertTrue(File(ProjectLayout.assetsDir(projectDir), "Models/cube.obj").isFile, "copy mode by default")
            assertTrue(File(ProjectLayout.assetsDir(projectDir), "Shaders/shader_3d_default.glsl").isFile)
            assertFalse(File(ProjectLayout.assetsDir(projectDir), "EngineDefaults").exists())
            assertTrue(File(ProjectLayout.assetsDir(projectDir), ProjectLayout.STRINGS_FILE).isFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun CliService_ValidateWarnsAboutASecondAssetsFolder() {
        val dir = Files.createTempDirectory("skate-cli").toFile()
        try {
            val service = SkateCliService(out = {})
            val projectFile = service.createProject("Demo", dir)
            File(projectFile.parentFile, "Scenes/assets").mkdirs()
            val report = service.validate(projectFile.path)
            assertTrue(report.isValid)
            assertTrue(report.warnings.any { it.startsWith("Duplicate assets folder") }, "${report.warnings}")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun Samples_HaveOneAssetsFolder() {
        val dir = Files.createTempDirectory("skate-cli").toFile()
        try {
            listOf(TetrisProjectBuilder().build(dir), MarbleRunProjectBuilder().build(dir), ShowcaseProjectBuilder().build(dir)).forEach { project ->
                val projectDir = project.parentFile
                assertTrue(ProjectLayout.duplicateAssetFolders(projectDir).isEmpty(), "${projectDir.name}: ${ProjectLayout.duplicateAssetFolders(projectDir)}")
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
