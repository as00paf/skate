package com.pafoid.skate.editor.settings

import java.io.File

object SettingsData {
    // TODO: remove
    const val ENGINE_SETTINGS_FILE = "engine_settings.json"

    fun getSettingsDirectory(): File {
        val userHome = System.getProperty("user.home")
        val settingsDir = File(userHome, ".skateSim/settings")
        if (!settingsDir.exists()) {
            settingsDir.mkdirs()
        }
        return settingsDir
    }

    fun getEngineSettingsFile(): File {
        return File(getSettingsDirectory(), ENGINE_SETTINGS_FILE)
    }

}

