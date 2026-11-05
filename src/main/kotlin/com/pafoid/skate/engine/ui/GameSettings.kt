package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.core.LoggerService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** The player's settings for the running game (GUI-10); separate from the editor's settings. */
@Serializable
data class GameSettings(
    /** Locale of runtime UI text (pause menu, HUD, game widgets). */
    val locale: String = DEFAULT_LOCALE,
) {
    companion object {
        const val DEFAULT_LOCALE = "en"
        const val FILE_NAME = "game.settings.json"

        /** The settings file in [dir]: the project directory in the editor, the game.bin directory when exported. */
        fun fileIn(dir: File): File = File(dir, FILE_NAME)
    }
}

/** Loads and saves [GameSettings] as JSON in [file]; a missing or unreadable file gives the defaults. */
class GameSettingsStore(val file: File, private val logger: LoggerService) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    var settings: GameSettings = load()
        private set

    private fun load(): GameSettings {
        if (!file.isFile) return GameSettings()
        return runCatching { json.decodeFromString(GameSettings.serializer(), file.readText()) }
            .onFailure { logger.logError("Could not read game settings ${file.absolutePath}; using defaults", it, source = "game") }
            .getOrDefault(GameSettings())
    }

    fun update(change: (GameSettings) -> GameSettings) {
        settings = change(settings)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(GameSettings.serializer(), settings))
        }.onFailure { logger.logError("Could not save game settings ${file.absolutePath}", it, source = "game") }
    }
}

/**
 * Runtime UI text with the game's own locale (GUI-10). [strings] is a StringManager used only by runtime UI, so a
 * language switch from the game never touches the editor's strings; [store], when set, keeps the choice.
 */
class GameLocale(val strings: com.pafoid.skate.engine.core.StringManager) {
    var store: GameSettingsStore? = null
        private set

    /** Uses [store]'s saved locale from now on (project opened in the editor, or the exported game started). */
    fun attach(store: GameSettingsStore) {
        this.store = store
        strings.setLocale(store.settings.locale)
    }

    fun set(locale: String) {
        strings.setLocale(locale)
        store?.update { it.copy(locale = locale) }
    }

    /** The pause menu's Language button: English <-> French. */
    fun toggle() = set(if (strings.locale == "fr") "en" else "fr")
}
