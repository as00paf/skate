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
        assertTrue(ok.success)

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
        val result = ProjectExporter(mockk<LoggerService>(relaxed = true)) { null }.export(project())
        assertEquals(ExportFailure.GAME_JAR_MISSING, result.failure)
        assertFalse(File(dir, "builds/game.bin").exists(), "fails before packing")
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
        assertTrue(ProjectExporter(mockk<LoggerService>(relaxed = true), stringsDir) { jar }.export(project()).success)
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
        assertTrue(ProjectExporter(mockk<LoggerService>(relaxed = true), engineStrings) { jar }.export(p, ExportOptions(onlyReferenced = false)).success)

        val manifest = Serializer().json.decodeFromString(PackManifest.serializer(), File(dir, "builds/manifest.json").readText())
        assertEquals(1, manifest.entries.count { it.path == "strings/game/hud.properties" })
        assertEquals("Project", packedStrings(File(dir, "builds"), "en").getString("pause.title"))
    }

    @Test
    fun Export_WithoutGameStringsStillSucceeds() {
        val jar = File(dir, "fake-game.jar").also { it.writeText("jar") }
        assertTrue(ProjectExporter(mockk<LoggerService>(relaxed = true), File(dir, "none")) { jar }.export(project()).success)
        assertTrue(packedTables(File(dir, "builds")).isEmpty())
    }

    private fun manifestPaths(builds: File): List<String> =
        Serializer().json.decodeFromString(PackManifest.serializer(), File(builds, "manifest.json").readText()).entries.map { it.path }

    @Test
    fun Export_PackOnlyTargetNeedsNoJarAndWritesNoLauncher() {
        val result = ProjectExporter(mockk<LoggerService>(relaxed = true)) { null }.export(project(), ExportOptions(target = ExportTarget.PACK_ONLY))
        assertTrue(result.success)
        val builds = File(dir, "builds")
        assertTrue(File(builds, "game.bin").isFile && File(builds, "manifest.json").isFile)
        assertFalse(File(builds, "skate-game.jar").exists())
        assertFalse(File(builds, "run-game.bat").exists())
        assertTrue(result.assetCount > 0 && result.packedBytes > 0)
    }

    @Test
    fun Export_ExcludedScenesAreLeftOutButTheDefaultSceneStays() {
        val p = project()
        File(dir, "Scenes/Extra.scene").writeText("{}")
        val excluded = setOf(File(dir, "Scenes/Extra.scene").absolutePath, p.defaultScene)
        val options = ExportOptions(target = ExportTarget.PACK_ONLY, excludedScenes = excluded)
        assertTrue(ProjectExporter(mockk<LoggerService>(relaxed = true)).export(p, options).success)
        val paths = manifestPaths(File(dir, "builds"))
        assertTrue("Scenes/Main.scene" in paths)
        assertFalse("Scenes/Extra.scene" in paths)
    }

    @Test
    fun Export_WritesToACustomOutputFolderAndSkipsItWhenScanning() {
        val out = File(dir, "dist/win")
        val jar = File(dir, "fake-game.jar").also { it.writeText("jar") }
        val exporter = ProjectExporter(mockk<LoggerService>(relaxed = true)) { jar }
        val options = ExportOptions(outputDir = out.path, onlyReferenced = false)
        assertTrue(exporter.export(project(), options).success)
        assertTrue(exporter.export(project(), options).success)
        assertTrue(File(out, "run-game.bat").isFile)
        assertFalse(File(dir, "builds").exists())
        assertTrue(manifestPaths(out).none { it.startsWith("dist/") })
    }

    @Test
    fun Export_RejectsTheProjectFolderAsOutput() {
        val p = project()
        val result = ProjectExporter(mockk<LoggerService>(relaxed = true)) { null }
            .export(p, ExportOptions(target = ExportTarget.PACK_ONLY, outputDir = dir.path))
        assertEquals(ExportFailure.INVALID_OUTPUT, result.failure)
        assertFalse(File(dir, "game.bin").exists())
    }

    @Test
    fun Export_ReportsEveryStepInOrderWithGrowingProgress() {
        val jar = File(dir, "fake-game.jar").also { it.writeText("jar") }
        val seen = mutableListOf<ExportProgress>()
        assertTrue(ProjectExporter(mockk<LoggerService>(relaxed = true)) { jar }.export(project()) { seen += it }.success)
        assertEquals(ExportStep.entries.toList(), seen.map { it.step }.distinct())
        val overall = seen.map { it.overall }
        assertEquals(overall.sorted(), overall, "progress never goes back")
        assertEquals(1f, overall.last())
        assertTrue(seen.any { it.step == ExportStep.PACKING && it.detail == "Scenes/Main.scene" })
    }

    @Test
    fun ExportProgress_OverallSpreadsStepsEvenly() {
        assertEquals(0f, ExportProgress(ExportStep.COLLECTING).overall)
        assertEquals(1.5f / 5f, ExportProgress(ExportStep.PACKING, 0.5f).overall, 1e-6f)
        assertEquals(1f, ExportProgress(ExportStep.DONE).overall)
    }

    @Test
    fun SceneFiles_ListsProjectScenesButNotTheOutputFolder() {
        val p = project()
        File(dir, "builds").mkdirs()
        File(dir, "builds/Old.scene").writeText("{}")
        val scenes = ProjectExporter(mockk<LoggerService>(relaxed = true)).sceneFiles(p).map { it.relativeTo(dir).invariantSeparatorsPath }
        assertEquals(listOf("Scenes/Main.scene"), scenes)
    }

    @Test
    fun Export_FailuresCarryATranslatableDetail() {
        val p = project()
        val invalid = ProjectExporter(mockk<LoggerService>(relaxed = true)) { null }
            .export(p, ExportOptions(target = ExportTarget.PACK_ONLY, outputDir = dir.path))
        assertEquals(ExportDetail.OUTPUT_IS_PROJECT, invalid.detail)
        assertEquals(listOf(dir.path), invalid.detailArgs)
        assertEquals(ExportDetail.GAME_JAR_NOT_BUILT, ProjectExporter(mockk<LoggerService>(relaxed = true)) { null }.export(p).detail)
    }

    @Test
    fun ExportDetail_EveryKeyIsTranslatedInEnglishAndFrench() {
        val editorStrings = File(com.pafoid.skate.engine.assets.Assets.Strings.EDITOR_STRINGS)
        for (locale in listOf(null, "fr")) {
            val table = com.pafoid.skate.engine.core.StringTables.merged(editorStrings, locale)
            ExportDetail.entries.forEach { assertTrue(table.containsKey(it.key), "${it.key} missing ($locale)") }
            ExportFailure.entries.forEach { assertTrue(table.containsKey("lbl.export.failure.${it.name.lowercase()}"), "$it ($locale)") }
        }
    }
}
