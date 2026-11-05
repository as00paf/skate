package com.pafoid.skate.game

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.utils.AssetsPacker
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.utils.Atlas
import com.pafoid.skate.engine.utils.ProjectExporter
import io.mockk.mockk
import java.io.File
import java.nio.ByteBuffer

class GameBinTest {

    @TempDir
    lateinit var dir: File

    private val console = mutableListOf<String>()
    private val logger = LoggerService().apply { consoleSink = { text, _ -> console += text } }

    @Test
    fun GameBin_DefaultsToThePackerFileNextToTheGame() {
        assertEquals(File(AssetsPacker.BIN_FILE), GameBin.file(override = null))
        assertEquals(File(AssetsPacker.BIN_FILE), GameBin.file(override = " "))
    }

    @Test
    fun GameBin_PathPropertyOverridesTheLocation() {
        val custom = File(dir, "custom.bin")
        assertEquals(custom, GameBin.file(override = custom.path))
    }

    @Test
    fun GameBin_ReadsThePackBytes() {
        val file = File(dir, AssetsPacker.BIN_FILE).apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertArrayEquals(byteArrayOf(1, 2, 3), GameBin.read(logger, file))
        assertTrue(logger.logs.isEmpty())
    }

    @Test
    fun GameBin_MissingPackIsLoggedAsAGameErrorNotPrinted() {
        val missing = File(dir, "missing.bin")

        assertNull(GameBin.read(logger, missing))

        val entry = logger.logs.single()
        assertEquals(LoggerService.LogLevel.ERROR, entry.level)
        assertEquals("game", entry.source)
        assertTrue(entry.message.contains(missing.absolutePath) && entry.message.contains(GameBin.PATH_PROPERTY), entry.message)
        assertEquals(1, console.size, "errors still reach the console through the logger's sink")
    }

    @Test
    fun GameBin_DirectoryInsteadOfAFileIsNotRead() {
        assertNull(GameBin.read(logger, dir))
        assertEquals(LoggerService.LogLevel.ERROR, logger.logs.single().level)
    }

    @Test
    fun GameBin_ReadsARealExportThroughThePathOverride() {
        val scene = File(dir, "Scenes/Main.scene").apply { parentFile.mkdirs(); writeText("{}") }
        val projectFile = File(dir, "Game.skateproject").apply { writeText("{}") }
        val project = Project(name = "Game", projectPath = projectFile.absolutePath, defaultScene = scene.absolutePath, scenesPath = listOf(scene.absolutePath))
        val jar = File(dir, "fake-game.jar").apply { writeText("jar") }
        assertTrue(ProjectExporter(mockk(relaxed = true), File(dir, "no-strings")) { jar }.export(project))
        val exported = File(dir, "builds/${AssetsPacker.BIN_FILE}")

        val bin = GameBin.read(logger, GameBin.file(override = exported.absolutePath))!!

        val headerSize = ByteBuffer.wrap(bin, 0, Int.SIZE_BYTES).int + Int.SIZE_BYTES
        val atlas = Serializer().decode<Atlas>(bin.copyOfRange(Int.SIZE_BYTES, headerSize).toString(Charsets.UTF_8))
        assertTrue(atlas.values.flatten().any { it.path.endsWith(".skateproject") }, "the pack carries the project file GameMain opens")
        assertTrue(logger.logs.isEmpty())
    }
}
