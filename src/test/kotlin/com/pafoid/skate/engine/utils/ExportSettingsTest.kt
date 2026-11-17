package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringTables
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

/** AST-29 (`docs/design/AST-15.md` rev 2 §2, §4, §7): export settings reach the exported game. */
class ExportSettingsTest {
    @TempDir
    lateinit var dir: File

    private val serializer = Serializer()
    private val builds get() = File(dir, "Builds")

    /** A saved project (the export saves it first) with one scene and the engine icon, as new projects get; [configure] runs before saving. */
    private fun project(configure: Project.() -> Unit = {}): Project {
        File(dir, "Scenes").mkdirs()
        val scene = File(dir, "Scenes/Main.scene").also { it.writeText("{}") }
        val file = File(dir, "Game.skateproject")
        val p = Project(name = "Game", projectPath = file.absolutePath, defaultScene = scene.absolutePath, scenesPath = listOf(scene.absolutePath), iconPath = EngineDefaults.Textures.APP_ICON)
        p.configure()
        file.writeText(serializer.encodeFile(FileKind.PROJECT, p))
        return p
    }

    private fun exporter(stale: Boolean = false): ProjectExporter {
        val jar = File(dir, "fake-game.jar").also { it.writeText("jar") }
        return ProjectExporter(mockk<LoggerService>(relaxed = true)) { GameJarSearch(jar, emptyList(), stale = stale) }
    }

    private fun packedPaths(): List<String> {
        val bin = File(builds, AssetsPacker.BIN_FILE).readBytes()
        val header = ByteBuffer.wrap(bin, 0, 4).int + 4
        return serializer.decode<Atlas>(bin.copyOfRange(4, header).toString(Charsets.UTF_8)).values.flatten().map { it.path }
    }

    /** The project as the exported game reads it from game.bin (GameMain.loadProjectAndIcon). */
    private fun packedProject(): Project {
        val bin = File(builds, AssetsPacker.BIN_FILE).readBytes()
        val header = ByteBuffer.wrap(bin, 0, 4).int + 4
        val atlas = serializer.decode<Atlas>(bin.copyOfRange(4, header).toString(Charsets.UTF_8))
        val info = atlas.values.flatten().single { it.type == FileType.PROJECT_FILE }
        val text = PackBuilder.extract(bin, info.position + header, info.size, info.compressed).toString(Charsets.UTF_8)
        return serializer.decodeFile(FileKind.PROJECT, text)
    }

    @Test
    fun Export_BlankIconFallsBackToTheEngineIconWithAWarning() {
        val result = exporter().export(project { iconPath = "" })
        assertTrue(result.success)
        assertEquals(listOf(ExportNotice(ExportWarning.ICON_NOT_SET)), result.warnings)
        assertEquals(EngineDefaults.Textures.APP_ICON, packedProject().iconPath)
        assertTrue(EngineDefaults.Textures.APP_ICON in packedPaths(), "the game finds its window icon in the pack")
    }

    @Test
    fun Export_MissingIconFileFallsBackToTheEngineIconWithItsPath() {
        val result = exporter().export(project { iconPath = "Assets/Textures/gone.png" })
        assertEquals(listOf(ExportNotice(ExportWarning.ICON_NOT_FOUND, listOf("Assets/Textures/gone.png"))), result.warnings)
        assertEquals(EngineDefaults.Textures.APP_ICON, packedProject().iconPath)
    }

    @Test
    fun Export_ProjectIconAndCustomSplashShipWithOnlyReferencedUnderProjectPaths() {
        File(dir, "Art").mkdirs()
        val icon = File(dir, "Art/icon.png").also { it.writeText("icon") }
        val splash = File(dir, "Art/splash.png").also { it.writeText("splash") }
        val result = exporter().export(project { iconPath = icon.absolutePath; splashPath = splash.absolutePath; splashSeconds = 3.5f })
        assertTrue(result.warnings.isEmpty(), "${result.warnings}")
        val paths = packedPaths()
        assertTrue("Art/icon.png" in paths && "Art/splash.png" in paths, "$paths")
        assertFalse(EngineDefaults.Textures.SPLASH_SCREEN in paths)
        val game = packedProject()
        assertEquals("Art/icon.png", game.iconPath, "the game looks the icon up by pack path")
        assertEquals("Art/splash.png", game.splashPath)
        assertEquals(3.5f, game.splashSeconds)
    }

    @Test
    fun Export_BlankSplashShipsTheEngineSplashForTwoSeconds() {
        val p = project()
        assertEquals(Project.DEFAULT_SPLASH_SECONDS, p.splashSeconds)
        assertTrue(exporter().export(p).success)
        assertTrue(EngineDefaults.Textures.SPLASH_SCREEN in packedPaths())
        assertEquals(EngineDefaults.Textures.SPLASH_SCREEN, packedProject().splashPath)
        assertEquals(2f, packedProject().splashSeconds)
    }

    @Test
    fun Export_ZeroSplashSecondsShipsNoSplash() {
        File(dir, "Art").mkdirs()
        val splash = File(dir, "Art/splash.png").also { it.writeText("splash") }
        val result = exporter().export(project { splashPath = splash.absolutePath; splashSeconds = 0f })
        assertTrue(result.warnings.isEmpty())
        // The saved project still names its custom splash (kept for when it is turned back on), so that small file may
        // ship; the engine splash does not, and the packed project tells the game to show none.
        assertFalse(EngineDefaults.Textures.SPLASH_SCREEN in packedPaths())
        assertEquals("", packedProject().splashPath)
        assertEquals(0f, packedProject().splashSeconds)
    }

    @Test
    fun Export_MissingSplashFallsBackToTheEngineSplashWithAWarning() {
        val result = exporter().export(project { splashPath = "Art/gone.png" })
        assertEquals(listOf(ExportNotice(ExportWarning.SPLASH_NOT_FOUND, listOf("Art/gone.png"))), result.warnings)
        assertEquals(EngineDefaults.Textures.SPLASH_SCREEN, packedProject().splashPath)
    }

    @Test
    fun Art_WithoutTheEngineSplashTheGameStartsWithoutOneAndSaysSo() {
        val p = project()
        // An engine root without assets/: only the classpath copy of the icon exists.
        val art = AssetsPacker(LoggerService(), File(dir, "no-strings"), engineRoot = File(dir, "no-engine")).art(p)
        assertEquals(EngineDefaults.Textures.APP_ICON, art.iconPath)
        assertEquals("", art.splashPath)
        assertEquals(0f, art.splashSeconds)
        assertEquals(listOf(ExportNotice(ExportWarning.SPLASH_DEFAULT_MISSING)), art.notices)
    }

    @Test
    fun Export_TargetFpsReachesThePackedProjectNormalized() {
        exporter().export(project { targetFps = 1000 })
        assertEquals(Project.MAX_TARGET_FPS, packedProject().targetFps)
        exporter().export(project { targetFps = 5 })
        assertEquals(Project.MIN_TARGET_FPS, packedProject().targetFps)
        exporter().export(project { targetFps = 144 })
        assertEquals(144, packedProject().targetFps)
        exporter().export(project { targetFps = 0 })
        assertEquals(0, packedProject().targetFps, "0 stays uncapped")
    }

    @Test
    fun Export_PhysicsBackendIsPackedAndJoltWarnsAboutTheFallback() {
        val result = exporter().export(project { physicsBackend = PhysicsBackend.JOLT })
        assertTrue(ExportNotice(ExportWarning.PHYSICS_JOLT_FALLBACK) in result.warnings)
        assertEquals(PhysicsBackend.JOLT, packedProject().physicsBackend)
        assertTrue(exporter().export(project()).warnings.none { it.warning == ExportWarning.PHYSICS_JOLT_FALLBACK })
    }

    @Test
    fun Export_StaleGameJarIsAWarningNotAFailure() {
        val result = exporter(stale = true).export(project())
        assertTrue(result.success)
        assertEquals(ExportWarning.GAME_JAR_STALE, result.warnings.single().warning)
    }

    @Test
    fun Export_ReleaseAndDebugLaunchersAndReport() {
        val p = project()
        assertTrue(exporter().export(p, ExportOptions(mode = BuildMode.DEBUG)).success)
        val debug = File(builds, ProjectExporter.LAUNCHER).readText()
        assertTrue("java -Dskate.debug=true -jar skate-game.jar" in debug, debug)
        assertTrue("if errorlevel 1 pause" in debug)
        assertTrue(File(builds, AssetsPacker.REPORT_FILE).isFile, "debug keeps the atlas report")

        assertTrue(exporter().export(p, ExportOptions(mode = BuildMode.RELEASE)).success)
        val release = File(builds, ProjectExporter.LAUNCHER).readText()
        assertEquals("@ECHO OFF\r\ncd /d \"%~dp0\"\r\njava -jar skate-game.jar\r\n", release)
        assertFalse(File(builds, AssetsPacker.REPORT_FILE).exists(), "release lists no packed paths, even after a debug build")
    }

    @Test
    fun Export_CompressionOffStoresEntriesRaw() {
        File(dir, "Scenes").mkdirs()
        val p = project()
        exporter().export(p, ExportOptions(compress = false))
        val bin = File(builds, AssetsPacker.BIN_FILE).readBytes()
        val header = ByteBuffer.wrap(bin, 0, 4).int + 4
        val atlas = serializer.decode<Atlas>(bin.copyOfRange(4, header).toString(Charsets.UTF_8))
        assertTrue(atlas.values.flatten().none { it.compressed })
        exporter().export(p, ExportOptions(compress = true))
        val bin2 = File(builds, AssetsPacker.BIN_FILE).readBytes()
        val header2 = ByteBuffer.wrap(bin2, 0, 4).int + 4
        assertTrue(serializer.decode<Atlas>(bin2.copyOfRange(4, header2).toString(Charsets.UTF_8)).values.flatten().any { it.compressed })
    }

    @Test
    fun Estimate_CountsWhatThePackHoldsBeforeCompression() {
        val p = project()
        val estimate = exporter().estimate(p)
        val selection = AssetsPacker(LoggerService()).select(p)
        assertEquals(selection.files.size + selection.engineReferences.size, estimate.assetCount)
        assertTrue(estimate.bytes >= File(p.projectPath).length())
        assertFalse(File(builds, AssetsPacker.BIN_FILE).exists(), "the estimate writes nothing")
    }

    @Test
    fun Project_FileWithoutTheExportFieldsGetsTheDefaults() {
        val text = serializer.encodeFile(FileKind.PROJECT, project { splashPath = "x.png"; splashSeconds = 4f; targetFps = 60; physicsBackend = PhysicsBackend.JOLT })
        val json = serializer.json.parseToJsonElement(text) as JsonObject
        val old = JsonObject(json - setOf("splashPath", "splashSeconds", "targetFps", "physicsBackend")).toString()
        val loaded = serializer.decodeFile<Project>(FileKind.PROJECT, old)
        assertEquals("", loaded.splashPath)
        assertEquals(Project.DEFAULT_SPLASH_SECONDS, loaded.splashSeconds)
        assertEquals(0, loaded.targetFps)
        assertEquals(PhysicsBackend.BULLET, loaded.physicsBackend)
    }

    @Test
    fun ExportWarning_EveryKeyIsTranslatedInEnglishAndFrench() {
        val editorStrings = File(Assets.Strings.EDITOR_STRINGS)
        for (locale in listOf(null, "fr")) {
            val table = StringTables.merged(editorStrings, locale)
            ExportWarning.entries.forEach { assertTrue(table.containsKey(it.key), "${it.key} missing ($locale)") }
        }
    }
}
