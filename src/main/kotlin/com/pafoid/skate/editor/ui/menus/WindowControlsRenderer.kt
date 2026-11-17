package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.events.EditorEvent
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants.WINDOW_BTN_SIZE
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.internal.ImGui.popStyleColor
import imgui.internal.ImGui.popStyleVar
import imgui.internal.ImGui.pushStyleColor
import imgui.internal.ImGui.pushStyleVar
import imgui.internal.ImGui.sameLine

class WindowControlsRenderer(
    private val eventSystem: EventSystem,
    private val stringManager: StringManager,
) {
    var isMaximized = true

    fun render() {
        val totalW = WINDOW_BTN_SIZE * 5f

        val currentX = ImGui.getCursorPosX()
        val availX = ImGui.getContentRegionAvailX()
        ImGui.setCursorPosX(currentX + availX - totalW)

        pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 0f)
        pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f)
        pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f) // Transparent base

        renderSettingsButton()
        sameLine(0f, 0f)
        renderSearchButton()
        sameLine(0f, 0f)
        renderMinimizeButton()
        sameLine(0f, 0f)
        renderMaximizeRestoreButton()
        sameLine(0f, 0f)
        renderCloseButton()

        popStyleColor(1)
        popStyleVar(2)

        // After the pops: the popup's menu items must get the editor's padding, not the icon buttons' zero padding
        // (EDT-76: they were drawn flush against each other).
        renderSettingsPopup()
    }

    private fun renderSettingsButton() {
        if (stringManager.tippedWith(ImGui.button(Icons.GEAR, WINDOW_BTN_SIZE, WINDOW_BTN_SIZE), "tooltip.settings")) {
            ImGui.openPopup(SETTINGS_POPUP)
        }
    }

    private fun renderSettingsPopup() {
        if (ImGui.beginPopup(SETTINGS_POPUP)) {
            if (stringManager.tipped(ImGui.menuItem("${Icons.GEAR} ${stringManager.getString("window.editor_settings")}"), "window.editor_settings")) {
                eventSystem.publish(WindowAction.Show("window.editor_settings"))
            }
            if (stringManager.tipped(ImGui.menuItem("${Icons.SLIDERS} ${stringManager.getString("window.project_settings")}"), "window.project_settings")) {
                eventSystem.publish(WindowAction.Show("window.project_settings"))
            }
            ImGui.endPopup()
        }
    }

    private fun renderSearchButton() {
        if (stringManager.tippedWith(ImGui.button(Icons.SEARCH, WINDOW_BTN_SIZE, WINDOW_BTN_SIZE), "tooltip.search_everywhere_shortcut", EditorShortcuts.SEARCH)) {
            eventSystem.publish(WindowAction.Show("window.search"))
        }
    }

    private fun renderMinimizeButton() {
        if (stringManager.tippedWith(ImGui.button(Icons.WINDOW_MINIMIZE, WINDOW_BTN_SIZE, WINDOW_BTN_SIZE), "tooltip.window_controls.minimize")) {
            eventSystem.publish(EditorEvent.Minimize)
        }
    }

    private fun renderMaximizeRestoreButton() {
        val maxRestoreIcon = if (isMaximized) {
            Icons.WINDOW_RESTORE
        } else {
            Icons.WINDOW_MAXIMIZE
        }
        val tooltipKey = if (isMaximized) "tooltip.window_controls.restore" else "tooltip.window_controls.maximize"
        if (stringManager.tippedWith(ImGui.button(maxRestoreIcon, WINDOW_BTN_SIZE, WINDOW_BTN_SIZE), tooltipKey)) {
            eventSystem.publish(EditorEvent.ToggleMaximize)
        }
    }

    private fun renderCloseButton() {
        pushStyleColor(ImGuiCol.ButtonHovered, 0.83f, 0.13f, 0.17f, 1f)
        pushStyleColor(ImGuiCol.ButtonActive, 0.93f, 0.23f, 0.27f, 1f)
        if (stringManager.tippedWith(ImGui.button(Icons.WINDOW_CLOSE, WINDOW_BTN_SIZE, WINDOW_BTN_SIZE), "tooltip.window_controls.close")) {
            eventSystem.publish(EditorEvent.Exit)
        }
        popStyleColor(2)
    }

    private companion object {
        const val SETTINGS_POPUP = "##SettingsPopup"
    }
}
