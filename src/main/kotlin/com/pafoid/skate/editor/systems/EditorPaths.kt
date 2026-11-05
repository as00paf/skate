package com.pafoid.skate.editor.systems

import java.io.File

/**
 * Where the editor keeps per-user files (EDT-18), instead of the working directory:
 * - Windows: `%APPDATA%\Skate`
 * - macOS: `~/Library/Application Support/Skate`
 * - Linux/other: `$XDG_CONFIG_HOME/skate` or `~/.config/skate`
 *
 * The JVM property [OVERRIDE_PROPERTY] (e.g. `-Dskate.userDataDir=build/userdata`) replaces the folder, for tests
 * and side-by-side installs. New projects default to [defaultProjectsDir].
 */
object EditorPaths {
    const val OVERRIDE_PROPERTY = "skate.userDataDir"
    const val APP_NAME = "Skate"
    const val PROJECTS_FOLDER = "Skate Projects"

    const val SETTINGS_FILE = "editor.config"
    const val IMGUI_INI = "imgui.ini"
    const val OPEN_WINDOWS_FILE = "imgui.windows"
    const val SEARCH_HISTORY_FILE = "search_history.json"

    /** The user-data folder for this OS; pure, so callers can pass their own environment. */
    fun userDataDir(
        env: Map<String, String> = System.getenv(),
        osName: String = System.getProperty("os.name").orEmpty(),
        home: String = System.getProperty("user.home").orEmpty(),
        override: String? = System.getProperty(OVERRIDE_PROPERTY),
    ): File {
        if (!override.isNullOrBlank()) return File(override)
        val os = osName.lowercase()
        return when {
            os.startsWith("windows") -> File(env["APPDATA"]?.takeIf { it.isNotBlank() } ?: File(home, "AppData/Roaming").path, APP_NAME)
            os.startsWith("mac") -> File(home, "Library/Application Support/$APP_NAME")
            else -> File(env["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() } ?: File(home, ".config").path, APP_NAME.lowercase())
        }
    }

    /** `Documents/Skate Projects` when the user has a Documents folder, else `~/Skate Projects`. */
    fun defaultProjectsDir(home: String = System.getProperty("user.home").orEmpty()): File {
        val documents = File(home, "Documents")
        return File(if (documents.isDirectory) documents else File(home), PROJECTS_FOLDER)
    }

    /** [userDataDir], created on first use. */
    val dir: File by lazy { userDataDir().also { it.mkdirs() } }

    fun file(name: String): File = File(dir, name)
}
