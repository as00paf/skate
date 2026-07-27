package com.pafoid.skate.editor.settings

import com.pafoid.skate.engine.assets.Assets.Files.ENGINE_SETTINGS_FILE
import java.io.File

object SettingsData {

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

