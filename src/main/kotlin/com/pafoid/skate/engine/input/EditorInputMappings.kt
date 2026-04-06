package com.pafoid.skate.engine.input

import kotlinx.serialization.Serializable
import org.lwjgl.glfw.GLFW

/**
 * Complete input mapping configuration for editor actions.
 *
 * This class contains all input bindings for the level editor, organized by category:
 * - Gizmo Tools: Translate, Rotate, Scale, Select
 * - Editor Tools: Measure, Deselect
 *
 * All bindings are serializable for saving/loading to configuration files.
 *
 * ## Default Bindings
 *
 * ### Gizmo Tools
 * - Translate: W key
 * - Rotate: E key
 * - Scale: R key
 * - Select: Q key
 *
 * ### Editor Tools
 * - Measure: M key
 * - Deselect: Escape key
 */
@Serializable
class EditorInputMappings {

    // =========================================================================
    // GIZMO TOOL MAPPINGS
    // =========================================================================

    /**
     * Gizmo translate mode input binding.
     * Default: W key
     */
    var gizmoTranslate: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_W
    )

    /**
     * Gizmo rotate mode input binding.
     * Default: E key
     */
    var gizmoRotate: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_E
    )

    /**
     * Gizmo scale mode input binding.
     * Default: R key
     */
    var gizmoScale: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_R
    )

    /**
     * Gizmo select mode input binding.
     * Default: Q key
     */
    var gizmoSelect: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_Q
    )

    // =========================================================================
    // EDITOR TOOL MAPPINGS
    // =========================================================================

    /**
     * Measure tool input binding.
     * Default: M key
     */
    var measureTool: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_M
    )

    /**
     * Deselect all input binding.
     * Default: Escape key
     */
    var deselectAll: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_ESCAPE
    )

    /**
     * Get all bindings as a map for iteration and UI display.
     * @return Map of binding name to InputBinding
     */
    fun getAllBindings(): Map<String, InputBinding> = mapOf(
        // Gizmo Tools
        "gizmoTranslate" to gizmoTranslate,
        "gizmoRotate" to gizmoRotate,
        "gizmoScale" to gizmoScale,
        "gizmoSelect" to gizmoSelect,

        // Editor Tools
        "measureTool" to measureTool,
        "deselectAll" to deselectAll
    )

    /**
     * Reset all bindings to default values.
     * This creates new InputBinding instances with default values.
     */
    fun resetToDefaults() {
        // Gizmo Tools
        gizmoTranslate = InputBinding(keyboardKey = GLFW.GLFW_KEY_W)
        gizmoRotate = InputBinding(keyboardKey = GLFW.GLFW_KEY_E)
        gizmoScale = InputBinding(keyboardKey = GLFW.GLFW_KEY_R)
        gizmoSelect = InputBinding(keyboardKey = GLFW.GLFW_KEY_Q)

        // Editor Tools
        measureTool = InputBinding(keyboardKey = GLFW.GLFW_KEY_M)
        deselectAll = InputBinding(keyboardKey = GLFW.GLFW_KEY_ESCAPE)
    }

    /**
     * Create a copy of this EditorInputMappings instance.
     */
    fun copy(): EditorInputMappings {
        val new = EditorInputMappings()
        new.gizmoTranslate = gizmoTranslate.copy()
        new.gizmoRotate = gizmoRotate.copy()
        new.gizmoScale = gizmoScale.copy()
        new.gizmoSelect = gizmoSelect.copy()
        new.measureTool = measureTool.copy()
        new.deselectAll = deselectAll.copy()
        return new
    }
}
