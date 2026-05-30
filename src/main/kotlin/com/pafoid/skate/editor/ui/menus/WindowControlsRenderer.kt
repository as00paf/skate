package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.events.EditorEvent
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.WindowRegistry
import com.pafoid.skate.engine.core.EventSystem
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.internal.ImGui.popStyleColor
import imgui.internal.ImGui.popStyleVar
import imgui.internal.ImGui.pushStyleColor
import imgui.internal.ImGui.pushStyleVar
import imgui.internal.ImGui.sameLine
import imgui.type.ImBoolean

class WindowControlsRenderer(
    private val eventSystem: EventSystem,
    private val stringManager: StringManager,
    private val windowRegistry: WindowRegistry,
) {
    private val projectSettingsShowFlag: ImBoolean
        get() = windowRegistry.windows.find { it.nameKey == "window.project_settings" }?.showFlag ?: ImBoolean(false)

    private val editorSettingsShowFlag: ImBoolean
        get() = windowRegistry.windows.find { it.nameKey == "window.editor_settings" }?.showFlag ?: ImBoolean(false)

    var isMaximized = true

    companion object {
        private const val BTN_SIZE = 40f
    }

    /**
     * Renders the window control buttons.
     */
    fun render() {
        val totalW = BTN_SIZE * 5f

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
    }

    private fun renderSettingsButton() {
        if (ImGui.button(Icons.GEAR, BTN_SIZE, BTN_SIZE)) {
            ImGui.openPopup("##SettingsPopup")
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.settings"))
        }

        if (ImGui.beginPopup("##SettingsPopup")) {
            if (ImGui.menuItem(stringManager.getString("window.editor_settings"))) {
                editorSettingsShowFlag.set(true)
            }
            if (ImGui.menuItem(stringManager.getString("window.project_settings"))) {
                projectSettingsShowFlag.set(true)
            }
            ImGui.endPopup()
        }
    }

    private fun renderSearchButton() {
        if (ImGui.button("${Icons.SEARCH}", BTN_SIZE, BTN_SIZE)) {
            windowRegistry.searchEverywhereWindow.open()
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.search_everywhere_shortcut"))
        }
    }

    private fun renderMinimizeButton() {
        if (ImGui.button(Icons.WINDOW_MINIMIZE, BTN_SIZE, BTN_SIZE)) {
            eventSystem.publish(EditorEvent.Minimize)
        }
    }

    private fun renderMaximizeRestoreButton() {
        val maxRestoreIcon = if (isMaximized) {
            Icons.WINDOW_RESTORE
        } else {
            Icons.WINDOW_MAXIMIZE
        }
        if (ImGui.button(maxRestoreIcon, BTN_SIZE, BTN_SIZE)) {
            eventSystem.publish(EditorEvent.ToggleMaximize)
        }
    }

    private fun renderCloseButton() {
        pushStyleColor(ImGuiCol.ButtonHovered, 0.83f, 0.13f, 0.17f, 1f)
        pushStyleColor(ImGuiCol.ButtonActive, 0.93f, 0.23f, 0.27f, 1f)
        if (ImGui.button(Icons.WINDOW_CLOSE, BTN_SIZE, BTN_SIZE)) {
            eventSystem.publish(EditorEvent.Exit)
        }
        popStyleColor(2)
    }
}
