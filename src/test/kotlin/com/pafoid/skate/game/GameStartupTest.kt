package com.pafoid.skate.game

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ui.FrameLimiter
import com.pafoid.skate.engine.ui.FrameRateOption
import com.pafoid.skate.engine.ui.GameOptions
import com.pafoid.skate.engine.ui.GameSettings
import com.pafoid.skate.engine.ui.GameSettingsStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/** GUI-20: GameMain's start-up steps that shape the pause menu. */
class GameStartupTest {

    private val dir: File = kotlin.io.path.createTempDirectory("startup").toFile()
    private val store = GameSettingsStore(GameSettings.fileIn(dir), LoggerService())
    private val project = Project(name = "P", projectPath = File(dir, "P.skateproject").path, defaultScene = "", scenesPath = emptyList())
        .apply { supportedLanguages = listOf("en", "fr"); startupLanguage = "en"; targetFps = 90 }

    @AfterEach
    fun cleanup() {
        runCatching { dir.deleteRecursively() }
    }

    @Test
    fun applyLanguage_StartsInTheExportedStartupLanguage() {
        store.update { it.copy(startupLanguage = "fr") }
        GameStartup.applyLanguage(project, store)
        assertEquals("fr", store.settings.locale)
        assertEquals(listOf("en", "fr"), store.settings.supportedLanguages)
    }

    @Test
    fun applyLanguage_KeepsThePlayersChoice_AcrossLaunches() {
        store.update { it.copy(startupLanguage = "fr", locale = "en", localeChosen = true) }
        GameStartup.applyLanguage(project, store)
        assertEquals("en", store.settings.locale, "before GUI-20 every launch reset it to the startup language")
    }

    @Test
    fun installFrameRate_ReplacesTheEngineRowInPlace_AndAppliesTheSavedChoice() {
        val options = GameOptions()
        options.register(FrameRateOption.option(FrameLimiter(), projectFps = 0))
        options.register(com.pafoid.skate.engine.ui.GameOption("other", listOf("x"), "x", { it }, {}))
        store.update { it.copy(options = mapOf(FrameRateOption.OPTION_ID to "30")) }
        val limiter = FrameLimiter()

        GameStartup.installFrameRate(options, limiter, project, store)

        assertEquals(listOf(FrameRateOption.OPTION_ID, "other"), options.all.map { it.id })
        assertEquals(30, limiter.targetFps)
        options.all.first().apply(FrameRateOption.PROJECT)
        assertEquals(90, limiter.targetFps, "Game default is the project's cap")
    }
}
