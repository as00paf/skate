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
    /** UI scale (EDT-57), multiplied with the theme's own font scale; 1 = 100 %. */
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
    var graphics: GraphicsSettings = GraphicsSettings(),
    /** Game jar (or folder holding it) used for exports when none is found next to the editor (AST-11). */
    var gameJarPath: String? = null,
    /**
     * Button and menu tooltips (EDT-57). Off hides them; inspector and settings field explanations stay
     * (docs/design/EDT-51.md §4.1).
     */
    var showTooltips: Boolean = true,
    var gizmoSnap: GizmoSnapSettings = GizmoSnapSettings(),
    var camera: EditorCameraSettings = EditorCameraSettings(),
) {

    companion object {
        const val MIN_UI_SCALE = 0.75f
        const val MAX_UI_SCALE = 2f
    }

    fun addRecentProject(projectPath: String): EditorSettings {
        val updatedList = mutableListOf<String>()
        updatedList.add(projectPath)
        updatedList.addAll(recentProjects.filter { it != projectPath }.take(4))
        return copy(recentProjects = updatedList)
    }
}
