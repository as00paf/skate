package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ui.GameSettings
import com.pafoid.skate.engine.ui.GameSettingsStore
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** GUI-20: the export carries the editor's game language and pause menu options into the game's settings. */
class ExportEditorChoicesTest {
    @TempDir
    lateinit var dir: File

    private val logger = mockk<LoggerService>(relaxed = true)

    private fun project(): Project {
        File(dir, "Scenes").mkdirs()
        val scene = File(dir, "Scenes/Main.scene").also { it.writeText("{}") }
        val file = File(dir, "Game.skateproject")
        val p = Project(
            name = "Game", projectPath = file.absolutePath, defaultScene = scene.absolutePath,
            scenesPath = listOf(scene.absolutePath), iconPath = EngineDefaults.Textures.APP_ICON,
            supportedLanguages = listOf("en", "fr"),
        )
        file.writeText(Serializer().encodeFile(FileKind.PROJECT, p))
        return p
    }

    private fun exporter(): ProjectExporter {
        val jar = File(dir, "fake-game.jar").also { it.writeText("jar") }
        return ProjectExporter(logger) { GameJarSearch(jar, emptyList(), stale = false) }
    }

    @Test
    fun Export_WritesTheEditorsLanguageAndOptions_KeepingThePlayersOwn() {
        val p = project()
        GameSettingsStore(GameSettings.fileIn(dir), logger).update {
            it.copy(locale = "fr", localeChosen = true, options = mapOf("skate.controls" to "thps", "engine.frameRate" to "60"))
        }
        // A player already picked a frame rate in an earlier export of the same folder.
        val out = File(dir, "Builds").apply { mkdirs() }
        GameSettingsStore(GameSettings.fileIn(out), logger).update { it.copy(options = mapOf("engine.frameRate" to "144")) }

        assertTrue(exporter().export(p).success)

        val game = GameSettingsStore(GameSettings.fileIn(out), logger).settings
        assertEquals("fr", game.startupLanguage)
        assertEquals("fr", game.startLocale(p.supportedLanguages))
        assertEquals(mapOf("skate.controls" to "thps", "engine.frameRate" to "144"), game.options)
    }
}
