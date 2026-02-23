package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.input.InputMappings
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui.begin
import imgui.internal.ImGui.button
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.end
import imgui.internal.ImGui.isKeyPressed
import imgui.internal.ImGui.sameLine
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.text
import imgui.type.ImInt
import org.lwjgl.glfw.GLFW

/**
 * Window for configuring keyboard and gamepad input bindings.
 *
 * Provides a tabbed interface for organizing bindings by category:
 * - Editor: Gizmo selection and editor tools
 * - Camera: Camera controls and game state
 * - Gamepad: Gamepad-specific bindings
 *
 * @param settingsManager Settings manager for loading/saving bindings
 * @param stringManager String manager for localization
 */
class KeyBindingsWindow(
    private val settingsManager: SettingsManager,
    private val stringManager: StringManager
) {
    var isOpen = false
    private var keyBindingAction: String? = null
    private var keyBindingTab = 0  // 0=Editor, 1=Camera, 2=Gamepad

    /**
     * Renders the key bindings window.
     */
    fun render() {
        if (!isOpen) return

        if (begin(stringManager.getString("window.keybindings"), ImGuiWindowFlags.AlwaysAutoResize)) {
            val inputMappings = settingsManager.settings.inputMappings

            // Tab selection using combo
            val tabNames = arrayOf("Editor", "Camera", "Gamepad")
            val tabSelector = ImInt(keyBindingTab)
            if (combo("##TabSelector", tabSelector, tabNames, tabNames.size)) {
                keyBindingTab = tabSelector.get()
            }

            separator()

            // Render selected tab
            when (keyBindingTab) {
                0 -> renderEditorBindingsTab(inputMappings)
                1 -> renderCameraBindingsTab(inputMappings)
                2 -> renderGamepadBindingsTab(inputMappings)
            }

            separator()
            if (button(stringManager.getString("btn.close"))) {
                isOpen = false
                keyBindingAction = null
                settingsManager.save()
            }
            sameLine()
            if (button("Reset to Defaults")) {
                inputMappings.resetToDefaults()
                settingsManager.save()
            }

            // Handle Binding
            if (keyBindingAction != null) {
                // Check for key press
                for (i in 0..348) { // GLFW_KEY_LAST is 348
                    if (isKeyPressed(i)) {
                        assignKeyBinding(inputMappings, keyBindingAction!!, i)
                        keyBindingAction = null
                        settingsManager.save()
                        break
                    }
                }
            }
        }
        end()
    }

    private fun renderEditorBindingsTab(inputMappings: InputMappings) {
        text(stringManager.getString("lbl.keybindings.editor_section"))
        separator()
        drawBindRow(
            stringManager.getString("lbl.keybindings.translate"),
            inputMappings.editorGizmoTranslate.keyboardKey,
            "editorGizmoTranslate"
        )
        drawBindRow(
            stringManager.getString("lbl.keybindings.rotate"),
            inputMappings.editorGizmoRotate.keyboardKey,
            "editorGizmoRotate"
        )
        drawBindRow(
            stringManager.getString("lbl.keybindings.scale"),
            inputMappings.editorGizmoScale.keyboardKey,
            "editorGizmoScale"
        )
        drawBindRow(
            stringManager.getString("lbl.keybindings.select"),
            inputMappings.editorGizmoSelect.keyboardKey,
            "editorGizmoSelect"
        )
        drawBindRow(
            stringManager.getString("lbl.keybindings.measure"),
            inputMappings.editorMeasure.keyboardKey,
            "editorMeasure"
        )
        drawBindRow(
            stringManager.getString("lbl.keybindings.deselect"),
            inputMappings.editorDeselect.keyboardKey,
            "editorDeselect"
        )
    }

    private fun renderCameraBindingsTab(inputMappings: InputMappings) {
        text(stringManager.getString("lbl.keybindings.camera_section"))
        separator()
        drawBindRow("Camera Reset", inputMappings.cameraReset.keyboardKey, "cameraReset")

        separator()
        text(stringManager.getString("lbl.keybindings.game_state_section"))
        separator()
        drawBindRow("Pause", inputMappings.pause.keyboardKey, "pause")
        drawBindRow("Reset", inputMappings.reset.keyboardKey, "reset")
        drawBindRow("Stance Change", inputMappings.stanceChange.keyboardKey, "stanceChange")
    }

    private fun renderGamepadBindingsTab(inputMappings: InputMappings) {
        text(stringManager.getString("lbl.keybindings.gamepad_section"))
        separator()
        text("Gamepad bindings are auto-configured for standard Xbox/PS controllers")
        text("Keyboard overrides gamepad when both are connected")

        separator()
        text(stringManager.getString("lbl.keybindings.movement_section"))
        drawBindRow("Move (Axis)", inputMappings.moveUp.gamepadAxis, "gamepadMove")
        drawBindRow("Camera (Axis)", inputMappings.cameraLookX.gamepadAxis, "gamepadCamera")
        drawBindRow("Jump", inputMappings.jump.gamepadButton, "gamepadJump")
        drawBindRow("Sprint (Trigger)", inputMappings.sprint.gamepadAxis, "gamepadSprint")
    }

    private fun drawBindRow(label: String, currentKey: Int, bindAction: String) {
        text(label)
        sameLine(200f)

        val keyName = getKeyName(currentKey)
        val btnText =
            if (keyBindingAction == bindAction) stringManager.getString("lbl.keybindings.press_key") else keyName

        if (button("$btnText##$bindAction", 120f, 0f)) {
            keyBindingAction = bindAction
        }
    }

    private fun assignKeyBinding(inputMappings: InputMappings, action: String, key: Int) {
        when (action) {
            // Editor
            "editorGizmoTranslate" -> inputMappings.editorGizmoTranslate.keyboardKey = key
            "editorGizmoRotate" -> inputMappings.editorGizmoRotate.keyboardKey = key
            "editorGizmoScale" -> inputMappings.editorGizmoScale.keyboardKey = key
            "editorGizmoSelect" -> inputMappings.editorGizmoSelect.keyboardKey = key
            "editorMeasure" -> inputMappings.editorMeasure.keyboardKey = key
            "editorDeselect" -> inputMappings.editorDeselect.keyboardKey = key
            // Camera
            "cameraReset" -> inputMappings.cameraReset.keyboardKey = key
            // Game State
            "pause" -> inputMappings.pause.keyboardKey = key
            "reset" -> inputMappings.reset.keyboardKey = key
            "stanceChange" -> inputMappings.stanceChange.keyboardKey = key
        }
    }

    private fun getKeyName(key: Int): String {
        return when (key) {
            GLFW.GLFW_KEY_ESCAPE -> "Esc"
            GLFW.GLFW_KEY_ENTER -> "Enter"
            GLFW.GLFW_KEY_TAB -> "Tab"
            GLFW.GLFW_KEY_BACKSPACE -> "Backspace"
            GLFW.GLFW_KEY_INSERT -> "Insert"
            GLFW.GLFW_KEY_DELETE -> "Del"
            GLFW.GLFW_KEY_RIGHT -> "Right"
            GLFW.GLFW_KEY_LEFT -> "Left"
            GLFW.GLFW_KEY_DOWN -> "Down"
            GLFW.GLFW_KEY_UP -> "Up"
            GLFW.GLFW_KEY_PAGE_UP -> "PgUp"
            GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn"
            GLFW.GLFW_KEY_HOME -> "Home"
            GLFW.GLFW_KEY_END -> "End"
            else -> {
                val name = GLFW.glfwGetKeyName(key, 0)
                name?.uppercase() ?: "Key $key"
            }
        }
    }
}
