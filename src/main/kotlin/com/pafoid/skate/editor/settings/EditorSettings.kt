package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.engine.utils.UnitSystem
import kotlinx.serialization.Serializable

@Serializable
data class EditorSettings(
    var autoSaveEnabled: Boolean = true,
    var autorSaveIntervalMinutes: Int = 5,
    var language: String = "en",
    var theme: String = "Islands Dark",
    var fontSize: Float = 1.0f,
    var showGamepadOverlay: Boolean = true,
    var gamepadOverlaySize: Float = 0.225f,
    var unitSystem: UnitSystem = UnitSystem.METRIC,
    var recentProjects: List<String> = emptyList(),
    var lastOpenedProject: String? = null,
    var loadLastProjectOnStartup: Boolean = true,
    var lastClosedProjectPath: String? = null,
    var editorInputMappings: EditorInputMappings = EditorInputMappings()
) {

    fun addRecentProject(projectPath: String): EditorSettings {
        val updatedList = mutableListOf<String>()
        updatedList.add(projectPath)
        updatedList.addAll(recentProjects.filter { it != projectPath }.take(4))
        return copy(recentProjects = updatedList)
    }
}
