package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
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
) : IWindow {

    private var keyBindingAction: String? = null
    private var keyBindingTab = 0  // 0=Editor, 1=Camera, 2=Gamepad

    // Load input mappings once from persisted storage (or defaults)
    private var inputMappings: com.pafoid.skate.engine.input.InputMappings =
        settingsManager.loadInputMappings() ?: com.pafoid.skate.engine.input.InputMappings()

    /**
     * Renders the key bindings window.
     */
    override fun imgui(pOpen: imgui.type.ImBoolean?) {
        if (pOpen?.get() == false) return

        if (begin(stringManager.getString("window.keybindings"), ImGuiWindowFlags.AlwaysAutoResize)) {
            // Tab selection using combo
            val tabNames = arrayOf(
                stringManager.getString("tab.keybindings.editor"),
                stringManager.getString("tab.keybindings.camera"),
                stringManager.getString("tab.keybindings.gamepad")
            )
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
                pOpen?.set(false)
                keyBindingAction = null
                settingsManager.updateInputMappings(inputMappings)
            }
            sameLine()
            if (button(stringManager.getString("btn.reset_to_defaults"))) {
                inputMappings.resetToDefaults()
                settingsManager.updateInputMappings(inputMappings)
            }

            // Handle Binding
            keyBindingAction?.let { action ->
                // Check for key press
                for (i in 0..348) { // GLFW_KEY_LAST is 348
                    if (isKeyPressed(i)) {
                        assignKeyBinding(action, i)
                        keyBindingAction = null
                        settingsManager.updateInputMappings(inputMappings)
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
        drawBindRow(stringManager.getString("lbl.keybindings.camera_reset"), inputMappings.cameraReset.keyboardKey, "cameraReset")

        separator()
        text(stringManager.getString("lbl.keybindings.game_state_section"))
        separator()
        drawBindRow(stringManager.getString("lbl.keybindings.pause"), inputMappings.pause.keyboardKey, "pause")
        drawBindRow(stringManager.getString("lbl.keybindings.reset"), inputMappings.reset.keyboardKey, "reset")
        drawBindRow(stringManager.getString("lbl.keybindings.stance"), inputMappings.stanceChange.keyboardKey, "stanceChange")
    }

    private fun renderGamepadBindingsTab(inputMappings: InputMappings) {
        text(stringManager.getString("lbl.keybindings.gamepad_section"))
        separator()
        text(stringManager.getString("lbl.keybindings.gamepad_auto"))
        text(stringManager.getString("lbl.keybindings.keyboard_override"))

        separator()
        text(stringManager.getString("lbl.keybindings.movement_section"))
        drawBindRow(stringManager.getString("lbl.keybindings.move_axis"), inputMappings.moveUp.gamepadAxis, "gamepadMove")
        drawBindRow(stringManager.getString("lbl.keybindings.camera_axis"), inputMappings.cameraLookX.gamepadAxis, "gamepadCamera")
        drawBindRow(stringManager.getString("lbl.keybindings.jump"), inputMappings.jump.gamepadButton, "gamepadJump")
        drawBindRow(stringManager.getString("lbl.keybindings.sprint_trigger"), inputMappings.sprint.gamepadAxis, "gamepadSprint")
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

    private fun assignKeyBinding(action: String, key: Int) {
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
