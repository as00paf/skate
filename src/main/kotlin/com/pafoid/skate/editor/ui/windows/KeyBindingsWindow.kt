package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.engine.core.StringManager
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui.begin
import imgui.internal.ImGui.button
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.end
import imgui.internal.ImGui.isKeyPressed
import imgui.internal.ImGui.sameLine
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.text
import imgui.type.ImBoolean
import imgui.type.ImInt
import org.lwjgl.glfw.GLFW

class KeyBindingsWindow(
    private val settingsManager: EditorSettingsManager,
    private val stringManager: StringManager
) : IWindow {

    private var keyBindingAction: String? = null
    private var keyBindingTab = 0  // 0=Editor, 1=Camera, 2=Gamepad, 3=Hierarchy

    private var inputMappings: EditorInputMappings = settingsManager.editorSettings.editorInputMappings

    override fun imgui(pOpen: ImBoolean?) {
        if (pOpen?.get() == false) return

        if (begin(stringManager.getString("window.keybindings"), ImGuiWindowFlags.AlwaysAutoResize)) {
            // Tab selection using combo
            val tabNames = arrayOf(
                stringManager.getString("tab.keybindings.editor"),
                stringManager.getString("tab.keybindings.camera"),
                stringManager.getString("tab.keybindings.gamepad"),
                stringManager.getString("tab.keybindings.hierarchy")
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
                2 -> renderHierarchyBindingsTab(inputMappings)
            }

            separator()
            if (button(stringManager.getString("btn.close"))) {
                pOpen?.set(false)
                keyBindingAction = null
                settingsManager.updateInputMappings(inputMappings)
            }
            sameLine()
            if (button(stringManager.getString("btn.reset_to_defaults"))) {
                inputMappings.resetToDefault()
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

    private fun renderEditorBindingsTab(inputMappings: EditorInputMappings) {
        text(stringManager.getString("lbl.keybindings.editor_section"))
        separator()
        drawBindRow(
            stringManager.getString("lbl.keybindings.translate"),
            inputMappings.gizmoTranslate.keyboardKey,
            "gizmoTranslate"
        )
        drawBindRow(
            stringManager.getString("lbl.keybindings.rotate"),
            inputMappings.gizmoRotate.keyboardKey,
            "gizmoRotate"
        )
        drawBindRow(
            stringManager.getString("lbl.keybindings.scale"),
            inputMappings.gizmoScale.keyboardKey,
            "gizmoScale"
        )
        drawBindRow(
            stringManager.getString("lbl.keybindings.select"),
            inputMappings.gizmoSelect.keyboardKey,
            "gizmoSelect"
        )
        drawBindRow(
            stringManager.getString("lbl.keybindings.measure"),
            inputMappings.measureTool.keyboardKey,
            "measureTool"
        )
        drawBindRow(
            stringManager.getString("lbl.keybindings.deselect"),
            inputMappings.deselectAll.keyboardKey,
            "deselectAll"
        )

        text(stringManager.getString("lbl.keybindings.game_state_section"))
        separator()
        drawBindRow(stringManager.getString("lbl.keybindings.pause"), inputMappings.pause.keyboardKey, "pause")
        drawBindRow(stringManager.getString("lbl.keybindings.reset"), inputMappings.reset.keyboardKey, "reset")
    }

    private fun renderCameraBindingsTab(inputMappings: EditorInputMappings) {
        text(stringManager.getString("lbl.keybindings.camera_section"))
        separator()
        drawBindRow(stringManager.getString("lbl.keybindings.moveUp"), inputMappings.moveForward.keyboardKey, "moveUp")
        drawBindRow(
            stringManager.getString("lbl.keybindings.moveDown"),
            inputMappings.moveBackward.keyboardKey,
            "moveDown"
        )
        drawBindRow(stringManager.getString("lbl.keybindings.moveLeft"), inputMappings.moveLeft.keyboardKey, "moveLeft")
        drawBindRow(
            stringManager.getString("lbl.keybindings.moveRight"),
            inputMappings.moveRight.keyboardKey,
            "moveRight"
        )

        separator()
    }

    private fun renderHierarchyBindingsTab(inputMappings: EditorInputMappings) {
        text(stringManager.getString("lbl.keybindings.hierarchy_section"))
        separator()
        drawBindRow(stringManager.getString("lbl.hierarchy.create_new"), inputMappings.hierarchyCreateNew.keyboardKey, "hierarchyCreateNew")
        drawBindRow(stringManager.getString("lbl.hierarchy.delete"), inputMappings.hierarchyDelete.keyboardKey, "hierarchyDelete")
        drawBindRow(stringManager.getString("lbl.hierarchy.duplicate"), inputMappings.hierarchyDuplicate.keyboardKey, "hierarchyDuplicate")
        drawBindRow(stringManager.getString("lbl.hierarchy.rename"), inputMappings.hierarchyRename.keyboardKey, "hierarchyRename")
        drawBindRow(stringManager.getString("lbl.hierarchy.toggle_visibility"), inputMappings.hierarchyToggleVisibility.keyboardKey, "hierarchyToggleVisibility")
        drawBindRow(stringManager.getString("lbl.hierarchy.toggle_lock"), inputMappings.hierarchyToggleLock.keyboardKey, "hierarchyToggleLock")

        separator()
        text(stringManager.getString("lbl.keybindings.hierarchy_navigation_section"))
        separator()
        drawBindRow(stringManager.getString("lbl.hierarchy.navigate_up"), inputMappings.hierarchyNavigateUp.keyboardKey, "hierarchyNavigateUp")
        drawBindRow(stringManager.getString("lbl.hierarchy.navigate_down"), inputMappings.hierarchyNavigateDown.keyboardKey, "hierarchyNavigateDown")
        drawBindRow(stringManager.getString("lbl.hierarchy.select_first"), inputMappings.hierarchySelectFirst.keyboardKey, "hierarchySelectFirst")
        drawBindRow(stringManager.getString("lbl.hierarchy.select_last"), inputMappings.hierarchySelectLast.keyboardKey, "hierarchySelectLast")
        drawBindRow(
            stringManager.getString("lbl.hierarchy.deselect"),
            inputMappings.deselectAll.keyboardKey,
            "hierarchyDeselect"
        )
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
            "gizmoTranslate" -> inputMappings.gizmoTranslate.keyboardKey = key
            "gizmoRotate" -> inputMappings.gizmoRotate.keyboardKey = key
            "gizmoScale" -> inputMappings.gizmoScale.keyboardKey = key
            "gizmoSelect" -> inputMappings.gizmoSelect.keyboardKey = key
            "measureTool" -> inputMappings.measureTool.keyboardKey = key
            "deselectAll" -> inputMappings.deselectAll.keyboardKey = key
            // Camera
            "moveUp" -> inputMappings.moveForward.keyboardKey = key
            "moveDown" -> inputMappings.moveBackward.keyboardKey = key
            "moveLeft" -> inputMappings.moveLeft.keyboardKey = key
            "moveRight" -> inputMappings.moveRight.keyboardKey = key
            // Game State
            "pause" -> inputMappings.pause.keyboardKey = key
            "reset" -> inputMappings.reset.keyboardKey = key
            // Hierarchy
            "hierarchyCreateNew" -> inputMappings.hierarchyCreateNew.keyboardKey = key
            "hierarchyDelete" -> inputMappings.hierarchyDelete.keyboardKey = key
            "hierarchySelectFirst" -> inputMappings.hierarchySelectFirst.keyboardKey = key
            "hierarchySelectLast" -> inputMappings.hierarchySelectLast.keyboardKey = key
            "hierarchyNavigateUp" -> inputMappings.hierarchyNavigateUp.keyboardKey = key
            "hierarchyNavigateDown" -> inputMappings.hierarchyNavigateDown.keyboardKey = key
            "hierarchyToggleVisibility" -> inputMappings.hierarchyToggleVisibility.keyboardKey = key
            "hierarchyToggleLock" -> inputMappings.hierarchyToggleLock.keyboardKey = key
            "hierarchyDuplicate" -> inputMappings.hierarchyDuplicate.keyboardKey = key
            "hierarchyRename" -> inputMappings.hierarchyRename.keyboardKey = key
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
