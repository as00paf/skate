package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.pack.PackedStrings
import com.pafoid.skate.engine.assets.pack.PackManifest
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.StringTables
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

    /** Reads an exported game.bin the way GameMain does: header atlas, then the packed string tables. */
    private fun packedTables(builds: File): Map<String, ByteArray> {
        val bin = File(builds, "game.bin").readBytes()
        val header = ByteBuffer.wrap(bin, 0, 4).int + 4
        val atlas = Serializer().decode<Atlas>(bin.copyOfRange(4, header).toString(Charsets.UTF_8))
        val resolver = AssetsResolver(Serializer(), mockk(relaxed = true)).also { it.initialize(atlas, bin, header) }
        return resolver.readAll(PackedStrings::isTable)
    }

    private fun packedStrings(builds: File, locale: String): StringManager {
        val packed = packedTables(builds)
        return StringManager(mockk(relaxed = true), "en").also {
            assertTrue(it.addStrings(PackedStrings.source(packed)))
            it.setLocale(locale)
        }
    }

    @Test
    fun Export_ShipsGameStringsAndTheGameReadsThemInEnglishAndFrench() {
        val jar = File(dir, "fake-game.jar").also { it.writeText("jar") }
        val stringsDir = File(Assets.Strings.GAME_STRINGS)
        assertTrue(ProjectExporter(mockk<LoggerService>(relaxed = true), stringsDir) { jar }.export(project()))
        val builds = File(dir, "builds")

        val manifest = Serializer().json.decodeFromString(PackManifest.serializer(), File(builds, "manifest.json").readText())
        val shipped = manifest.entries.map { it.path }.filter(PackedStrings::isTable)
        assertEquals(stringsDir.list { _, n -> n.endsWith(".properties") }!!.sorted().map { "strings/game/$it" }, shipped.sorted())

        for (locale in listOf("en", "fr")) {
            val expected = StringTables.merged(stringsDir, locale.takeIf { it != "en" })
            val strings = packedStrings(builds, locale)
            assertTrue(expected.isNotEmpty())
            expected.stringPropertyNames().forEach { key ->
                assertEquals(expected.getProperty(key), strings.getString(key), "$locale: $key")
            }
        }
        assertEquals("Paused", packedStrings(builds, "en").getString("pause.title"))
        assertNotEquals("Paused", packedStrings(builds, "fr").getString("pause.title"))
    }

    @Test
    fun Export_ProjectStringTableOverridesTheEngineOne() {
        val jar = File(dir, "fake-game.jar").also { it.writeText("jar") }
        val engineStrings = File(dir, "engine-strings").also { it.mkdirs() }
        File(engineStrings, "hud.properties").writeText("pause.title=Engine")
        val p = project()
        File(dir, "strings/game").mkdirs()
        File(dir, "strings/game/hud.properties").writeText("pause.title=Project")
        assertTrue(ProjectExporter(mockk<LoggerService>(relaxed = true), engineStrings) { jar }.export(p, onlyReferenced = false))

        val manifest = Serializer().json.decodeFromString(PackManifest.serializer(), File(dir, "builds/manifest.json").readText())
        assertEquals(1, manifest.entries.count { it.path == "strings/game/hud.properties" })
        assertEquals("Project", packedStrings(File(dir, "builds"), "en").getString("pause.title"))
    }

    @Test
    fun Export_WithoutGameStringsStillSucceeds() {
        val jar = File(dir, "fake-game.jar").also { it.writeText("jar") }
        assertTrue(ProjectExporter(mockk<LoggerService>(relaxed = true), File(dir, "none")) { jar }.export(project()))
        assertTrue(packedTables(File(dir, "builds")).isEmpty())
    }
}
