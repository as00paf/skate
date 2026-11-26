package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.imgui.MaximizeMode
import com.pafoid.skate.editor.imgui.theme.EditorTheme
import com.pafoid.skate.engine.utils.UnitSystem
import kotlinx.serialization.Serializable

@Serializable
data class EditorSettings(
    var autoSaveEnabled: Boolean = true,
    var autorSaveIntervalMinutes: Int = 5,
    var language: String = "en",
    /** Id of the editor theme (`src/main/resources/editor/themes/<id>.theme.json`, EDT-07). */
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
    /** Game jar (or folder holding it) used for exports when none is found next to the editor (AST-11). */
    var gameJarPath: String? = null,
    var defaultProjectsPath: String? = null,
    /** `blender.exe` for `.blend` import (AST-19); null finds it on PATH, the file association or Program Files. */
    var blenderPath: String? = null,
    /** A `.blend` import is cancelled after this long. */
    var blenderTimeoutSeconds: Int = DEFAULT_BLENDER_TIMEOUT,
    /** Import `.blend` files that changed while the project was closed, when it opens. */
    var blenderImportOnOpen: Boolean = true,
    /** Video to animation (ANI-17): the Python and MediaPipe model used by the mocap adapter. Per machine. */
    var mocap: MocapSettings = MocapSettings(),
    /**
     * Button and menu tooltips (EDT-57). Off hides them; inspector and settings field explanations stay
     * (docs/design/EDT-51.md §4.1).
     */
    var showTooltips: Boolean = true,
    var gizmoSnap: GizmoSnapSettings = GizmoSnapSettings(),
    var camera: EditorCameraSettings = EditorCameraSettings(),
    /** Spacing presets and overrides over the theme (EDT-76). */
    var appearance: AppearanceSettings = AppearanceSettings(),
    /** Where Maximize, a tab double-click and the shortcut put a tool window (EDT-88). */
    var maximizeMode: MaximizeMode = MaximizeMode.FULL_EDITOR,
    /** EDT-84: the tool window bars on the left and right (View > Tool Window Bars). */
    var showToolWindowBars: Boolean = true,
    /** Export wizard choices per project path (AST-29); build choices, kept out of the `.skateproject`. */
    var exportPreferences: Map<String, com.pafoid.skate.editor.project.ExportPreferences> = emptyMap(),
    /**
     * City from OpenStreetMap (AST-34, docs/design/AST-17.md §3.1): an optional raster tile template
     * (`https://…/{z}/{x}/{y}.png`) replacing the OpenFreeMap vector map, with its mandatory attribution, and the
     * Overpass endpoint. See `MapSettings.validate`.
     */
    var osmTileUrl: String? = null,
    var osmTileAttribution: String? = null,
    var overpassUrl: String = com.pafoid.skate.editor.assetimport.osm.OverpassClient.DEFAULT_ENDPOINT,
    /** `steamcmd.exe` or its folder (NET-07); null uses the managed install, then PATH. Per machine. */
    var steamCmdPath: String? = null,
    /** Steam build account name (NET-07). The password is never stored; steamcmd keeps its own login session. */
    var steamUsername: String? = null,
) {

    companion object {
        const val MIN_UI_SCALE = 0.75f
        const val MAX_UI_SCALE = 2f
        const val DEFAULT_BLENDER_TIMEOUT = 120
        const val MIN_BLENDER_TIMEOUT = 10
        const val MAX_BLENDER_TIMEOUT = 3600
    }

    fun addRecentProject(projectPath: String): EditorSettings {
        val updatedList = mutableListOf<String>()
        updatedList.add(projectPath)
        updatedList.addAll(recentProjects.filter { it != projectPath }.take(4))
        return copy(recentProjects = updatedList)
    }
}

/**
 * Mocap tool setup (ANI-17, docs/mocap-setup.md). [pythonPath] is the Python executable (or a venv's) that has
 * MediaPipe; [modelPath] an optional MediaPipe `pose_landmarker_*.task` file (without it the legacy pose solution is
 * used, when the installed MediaPipe still has it).
 */
@Serializable
data class MocapSettings(
    val pythonPath: String = "python",
    val modelPath: String? = null,
)
