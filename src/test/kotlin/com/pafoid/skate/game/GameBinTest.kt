package com.pafoid.skate.game

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.utils.AssetsPacker
import com.pafoid.skate.engine.utils.Atlas
import com.pafoid.skate.engine.utils.ProjectExporter
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

class GameBinTest {

    @TempDir
    lateinit var dir: File

    private val console = mutableListOf<String>()
    private val logger = LoggerService().apply { consoleSink = { text, _ -> console += text } }

    private val gameDir by lazy { File(dir, "MyGame").apply { mkdirs() } }
    private val jar by lazy { File(gameDir, "skate-game.jar").apply { writeText("jar") } }
    private val elsewhere by lazy { File(dir, "Desktop").apply { mkdirs() } }
    private fun bin(folder: File, vararg bytes: Byte) = File(folder, AssetsPacker.BIN_FILE).apply { writeBytes(bytes) }

    @Test
    fun GameBin_LooksNextToTheRunningJarThenInTheWorkingDirectory() {
        assertEquals(
            listOf(File(gameDir, AssetsPacker.BIN_FILE), File(elsewhere, AssetsPacker.BIN_FILE)),
            GameBin.candidates(override = null, codeSource = jar, workingDir = elsewhere),
        )
    }

    @Test
    fun GameBin_JarStartedFromAnotherFolderFindsThePackNextToIt() {
        // Double-clicked jar or `java -jar MyGame\skate-game.jar` from the Desktop: the working directory has no pack.
        val pack = bin(gameDir, 1, 2, 3)

        val loaded = GameBin.read(logger, GameBin.candidates(override = null, codeSource = jar, workingDir = elsewhere))!!

        assertEquals(pack.absoluteFile, loaded.file)
        assertArrayEquals(byteArrayOf(1, 2, 3), loaded.bytes)
        assertTrue(logger.logs.isEmpty())
    }

    @Test
    fun GameBin_PackNextToTheJarWinsOverOneInTheWorkingDirectory() {
        bin(gameDir, 1)
        bin(elsewhere, 2)
        assertEquals(File(gameDir, AssetsPacker.BIN_FILE).absoluteFile,
            GameBin.resolve(GameBin.candidates(override = null, codeSource = jar, workingDir = elsewhere)))
    }

    @Test
    fun GameBin_FromAClassesDirectoryOnlyTheWorkingDirectoryIsSearched() {
        // IDE/Gradle in development: the code source is build/classes/kotlin/main, not a jar.
        val classes = File(dir, "build/classes/kotlin/main").apply { mkdirs() }
        assertEquals(listOf(File(elsewhere, AssetsPacker.BIN_FILE)), GameBin.candidates(override = null, codeSource = classes, workingDir = elsewhere))
        assertEquals(listOf(File(elsewhere, AssetsPacker.BIN_FILE)), GameBin.candidates(override = null, codeSource = null, workingDir = elsewhere))
    }

    @Test
    fun GameBin_PathPropertyIsTheOnlyCandidateWhenSet() {
        bin(gameDir, 1)
        val custom = File(dir, "custom.bin")
        val candidates = GameBin.candidates(override = custom.path, codeSource = jar, workingDir = gameDir)
        assertEquals(listOf(custom.absoluteFile), candidates)
        assertNull(GameBin.resolve(candidates)) // a wrong -Dskate.bin does not silently load another pack
        assertEquals(GameBin.candidates(override = null, codeSource = jar, workingDir = elsewhere),
            GameBin.candidates(override = " ", codeSource = jar, workingDir = elsewhere))
    }

    @Test
    fun GameBin_MissingPackLogsEverySearchedPathAsAGameError() {
        val candidates = GameBin.candidates(override = null, codeSource = jar, workingDir = elsewhere)

        assertNull(GameBin.read(logger, candidates))

        val entry = logger.logs.single()
        assertEquals(LoggerService.LogLevel.ERROR, entry.level)
        assertEquals("game", entry.source)
        candidates.forEach { assertTrue(entry.message.contains(it.path), entry.message) }
        assertTrue(entry.message.contains(GameBin.PATH_PROPERTY), entry.message)
        assertEquals(1, console.size, "errors still reach the console through the logger's sink")
    }

    @Test
    fun GameBin_DirectoryNamedLikeThePackIsNotRead() {
        File(elsewhere, AssetsPacker.BIN_FILE).mkdirs()
        assertNull(GameBin.read(logger, GameBin.candidates(override = null, codeSource = null, workingDir = elsewhere)))
        assertEquals(LoggerService.LogLevel.ERROR, logger.logs.single().level)
    }

    @Test
    fun GameBin_RunningCodeSourceIsKnown() {
        val source = GameBin.runningCodeSource()
        assertTrue(source != null && source.exists(), "code source $source")
    }

    @Test
    fun GameBin_ReadsARealExportFromItsJarFolder() {
        val scene = File(dir, "Scenes/Main.scene").apply { parentFile.mkdirs(); writeText("{}") }
        val projectFile = File(dir, "Game.skateproject").apply { writeText("{}") }
        val project = Project(name = "Game", projectPath = projectFile.absolutePath, defaultScene = scene.absolutePath, scenesPath = listOf(scene.absolutePath))
        val fakeJar = File(dir, "fake-game.jar").apply { writeText("jar") }
        assertTrue(ProjectExporter(mockk(relaxed = true), File(dir, "no-strings")) { fakeJar }.export(project).success)
        val builds = File(dir, "builds")
        val exportedJar = builds.listFiles { f -> f.name.endsWith(".jar") }!!.single()

        val pack = GameBin.read(logger, GameBin.candidates(override = null, codeSource = exportedJar, workingDir = elsewhere))!!

        assertEquals(File(builds, AssetsPacker.BIN_FILE).absoluteFile, pack.file)
        val headerSize = ByteBuffer.wrap(pack.bytes, 0, Int.SIZE_BYTES).int + Int.SIZE_BYTES
        val atlas = Serializer().decode<Atlas>(pack.bytes.copyOfRange(Int.SIZE_BYTES, headerSize).toString(Charsets.UTF_8))
        assertTrue(atlas.values.flatten().any { it.path.endsWith(".skateproject") }, "the pack carries the project file GameMain opens")
        assertTrue(logger.logs.isEmpty())
    }
}
