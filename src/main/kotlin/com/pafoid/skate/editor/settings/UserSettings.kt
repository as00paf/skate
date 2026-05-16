package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

/**
 * User-specific preferences that persist across projects.
 * These settings are stored in the user's profile directory.
 *
 * @property uiTheme The UI theme name (e.g., "Islands Dark", "Light")
 * @property fontSize UI font scale multiplier (0.8f - 1.5f)
 * @property autoSaveEnabled Whether auto-save is enabled
 * @property autoSaveInterval Auto-save interval in minutes (1-60)
 * @property recentProjects List of recently opened project paths (max 5)
 * @property lastOpenedProject Path to the last opened project
 * @property loadLastProjectOnStartup Whether to auto-load last project on startup
 */
@Serializable
data class UserSettings(
    val uiTheme: String = "Islands Dark",
    val fontSize: Float = 1.0f,
    val autoSaveEnabled: Boolean = true,
    val autoSaveInterval: Int = 5,
    val recentProjects: List<String> = emptyList(),
    val lastOpenedProject: String? = null,
    val loadLastProjectOnStartup: Boolean = true,
    val lastClosedProjectPath: String? = null
) {
    /**
     * Add a project to recent projects list.
     * Maintains max 5 recent projects, removes oldest if exceeded.
     */
    fun addRecentProject(projectPath: String): UserSettings {
        val updatedList = mutableListOf<String>()
        updatedList.add(projectPath)
        updatedList.addAll(recentProjects.filter { it != projectPath }.take(4))
        return copy(recentProjects = updatedList)
    }
    
    /**
     * Validate settings are within acceptable ranges.
     */
    fun validate(): UserSettings {
        return copy(
            fontSize = fontSize.coerceIn(0.8f, 1.5f),
            autoSaveInterval = autoSaveInterval.coerceIn(1, 60)
        )
    }
}
