package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** GUI-20: the exported game starts in the editor's game language and keeps the player's own choice. */
class GameStartLocaleTest {

    private val dir: File = kotlin.io.path.createTempDirectory("startlocale").toFile()

    @AfterEach
    fun cleanup() {
        runCatching { dir.deleteRecursively() }
    }

    @Test
    fun startLocale_PlayersChoiceWins_ThenStartupLanguage_ThenFirstSupported() {
        val both = listOf("en", "fr")
        assertEquals("fr", GameSettings(locale = "fr", localeChosen = true, startupLanguage = "en").startLocale(both))
        assertEquals("fr", GameSettings(locale = "en", startupLanguage = "fr").startLocale(both), "a locale never chosen does not win")
        assertEquals("en", GameSettings(locale = "de", localeChosen = true, startupLanguage = "en").startLocale(both), "unsupported choice")
        assertEquals("fr", GameSettings(startupLanguage = "de").startLocale(listOf("fr", "en")))
        assertEquals("en", GameSettings(startupLanguage = "de").startLocale(emptyList()))
    }

    @Test
    fun withEditorChoices_TakesTheEditorLanguage_AndOptionsThePlayerNeverSet() {
        val editor = GameSettings(locale = "fr", options = mapOf("a" to "1", "b" to "2"))
        val player = GameSettings(locale = "en", localeChosen = true, options = mapOf("b" to "9"))

        val merged = player.withEditorChoices(editor, listOf("en", "fr"), fallbackStartup = "en")

        assertEquals("fr", merged.startupLanguage)
        assertEquals(mapOf("a" to "1", "b" to "9"), merged.options, "the player's own value wins")
        assertEquals("en", merged.locale)
        assertTrue(merged.localeChosen)
    }

    @Test
    fun withEditorChoices_UnsupportedEditorLanguage_UsesTheFallback() {
        val merged = GameSettings().withEditorChoices(GameSettings(locale = "fr"), listOf("en"), fallbackStartup = "en")
        assertEquals("en", merged.startupLanguage)
    }

    @Test
    fun languageChange_IsMarkedChosen_AndSaved() {
        val store = GameSettingsStore(GameSettings.fileIn(dir), LoggerService())
        val locale = GameLocale(StringManager(LoggerService())).also { it.attach(store) }
        assertFalse(store.settings.localeChosen)

        locale.toggle()

        val reloaded = GameSettingsStore(store.file, LoggerService()).settings
        assertEquals("fr", reloaded.locale)
        assertTrue(reloaded.localeChosen)
        assertEquals("fr", reloaded.startLocale(listOf("en", "fr")), "the next start keeps it")
    }
}
