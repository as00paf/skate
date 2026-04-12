package com.pafoid.skate.editor.settings

import java.io.File

object SettingsData {
    const val ENGINE_SETTINGS_FILE = "engine_settings.json"
    const val USER_SETTINGS_FILE = "user_settings.json"

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

    fun getUserSettingsFile(): File {
        return File(getSettingsDirectory(), USER_SETTINGS_FILE)
    }
}

