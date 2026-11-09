package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.imgui.theme.EditorTheme
import com.pafoid.skate.engine.utils.UnitSystem
import kotlinx.serialization.Serializable

@Serializable
data class EditorSettings(
    var autoSaveEnabled: Boolean = true,
    var autorSaveIntervalMinutes: Int = 5,
    var language: String = "en",
    /** Id of the editor theme (`assets/themes/<id>.theme.json`, EDT-07). */
    var theme: String = EditorTheme.DEFAULT_ID,
    var fontSize: Float = 1.0f,
    var showGamepadOverlay: Boolean = true,
    var gamepadOverlaySize: Float = 0.225f,
    var unitSystem: UnitSystem = UnitSystem.METRIC,
    var recentProjects: List<String> = emptyList(),
    var lastOpenedProject: String? = null,
    var loadLastProjectOnStartup: Boolean = true,
    var lastClosedProjectPath: String? = null,
    var editorInputMappings: EditorInputMappings = EditorInputMappings(),
    var completionAutoPopup: Boolean = true,
    var completionDelayMs: Int = 200,
    /** Game jar (or folder holding it) used for exports when none is found next to the editor (AST-11). */
    var gameJarPath: String? = null,
) {

    /**
     * Add a recent project to the editor settings (default: true for Fullscreen, false for VSync)
     */
    fun addRecentProject(projectPath: String): EditorSettings {
        val updatedList = mutableListOf<String>()
        updatedList.add(projectPath)
        updatedList.addAll(recentProjects.filter { it != projectPath }.take(4))
        return copy(recentProjects = updatedList)
    }
}