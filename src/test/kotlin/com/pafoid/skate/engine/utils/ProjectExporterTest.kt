package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.pack.PackManifest
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

class ProjectExporterTest {
    @TempDir
    lateinit var dir: File

    private fun project(): Project {
        File(dir, "Textures").mkdirs()
        File(dir, "Scenes").mkdirs()
        File(dir, "Textures/used.png").writeText("png")
        File(dir, "Textures/unused.png").writeText("png")
        val scene = File(dir, "Scenes/Main.scene")
        scene.writeText("""{"t":"Textures/used.png"}""")
        val file = File(dir, "Game.skateproject").also { it.writeText("{}") }
        return Project(name = "Game", projectPath = file.absolutePath, defaultScene = scene.absolutePath, scenesPath = listOf(scene.absolutePath))
    }

    @Test
    fun Export_PacksOnlyReferencedAssetsWritesManifestAndRunnableLayout() {
        val jar = File(dir, "fake-game.jar").also { it.writeText("jar") }
        val ok = ProjectExporter(mockk<LoggerService>(relaxed = true)) { jar }.export(project())
        assertTrue(ok)

        val builds = File(dir, "builds")
        val manifest = Serializer().json.decodeFromString(PackManifest.serializer(), File(builds, "manifest.json").readText())
        val paths = manifest.entries.map { it.path }
        assertTrue("Textures/used.png" in paths)
        assertFalse("Textures/unused.png" in paths)
        assertTrue(paths.none { it.startsWith("builds/") })

        val bin = File(builds, "game.bin").readBytes()
        val header = ByteBuffer.wrap(bin, 0, 4).int + 4
        assertTrue(PackBuilder.verify(bin.copyOfRange(header, bin.size), manifest).isEmpty())
        assertTrue(File(builds, "skate-game.jar").isFile)
        assertTrue(File(builds, "run-game.bat").readText().contains("java -jar skate-game.jar"))
    }

    @Test
    fun Export_FailsClearlyWhenGameJarIsMissing() {
        assertFalse(ProjectExporter(mockk<LoggerService>(relaxed = true)) { null }.export(project()))
    }

    @Test
    fun Export_ReexportDoesNotPackPreviousBuild() {
        val jar = File(dir, "fake-game.jar").also { it.writeText("jar") }
        val exporter = ProjectExporter(mockk<LoggerService>(relaxed = true)) { jar }
        val p = project()
        exporter.export(p); exporter.export(p)
        val m = Serializer().json.decodeFromString(PackManifest.serializer(), File(dir, "builds/manifest.json").readText())
        assertTrue(m.entries.none { it.path.contains("game.bin") || it.path.contains("manifest") })
    }
}
