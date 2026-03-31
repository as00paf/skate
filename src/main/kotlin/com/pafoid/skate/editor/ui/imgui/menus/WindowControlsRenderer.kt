package com.pafoid.skate.editor.ui.imgui.menus

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.windows.SearchEverywhereWindow
import com.pafoid.skate.engine.core.WindowController
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.internal.ImGui.pushStyleColor
import imgui.internal.ImGui.pushStyleVar
import imgui.internal.ImGui.popStyleColor
import imgui.internal.ImGui.popStyleVar
import imgui.internal.ImGui.sameLine

/**
 * Renders the window control buttons (Search, Minimize, Maximize/Restore, Close).
 * 
 * This component handles:
 * - Search Everywhere button
 * - Window minimize button
 * - Window maximize/restore button
 * - Window close button (with red hover effect)
 * 
 * @param searchEverywhereWindow To open on search button click
 * @param windowController For window operations
 */
class WindowControlsRenderer(
    private val searchEverywhereWindow: SearchEverywhereWindow,
    private val windowController: WindowController
) {
    
    companion object {
        private const val BTN_SIZE = 40f
    }
    
    /**
     * Renders the window control buttons.
     */
    fun render() {
        val totalW = BTN_SIZE * 4f
        
        val currentX = ImGui.getCursorPosX()
        val availX = ImGui.getContentRegionAvailX()
        ImGui.setCursorPosX(currentX + availX - totalW)
        
        pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 0f)
        pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f)
        pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f) // Transparent base
        
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
    
    private fun renderSearchButton() {
        if (ImGui.button("${Icons.SEARCH}", BTN_SIZE, BTN_SIZE)) {
            searchEverywhereWindow.open()
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Search Everywhere (Ctrl+P)")
        }
    }
    
    private fun renderMinimizeButton() {
        if (ImGui.button(Icons.WINDOW_MINIMIZE, BTN_SIZE, BTN_SIZE)) {
            windowController.minimize()
        }
    }
    
    private fun renderMaximizeRestoreButton() {
        val maxRestoreIcon = if (windowController.isMaximized()) {
            Icons.WINDOW_RESTORE
        } else {
            Icons.WINDOW_MAXIMIZE
        }
        if (ImGui.button(maxRestoreIcon, BTN_SIZE, BTN_SIZE)) {
            windowController.toggleMaximize()
        }
    }
    
    private fun renderCloseButton() {
        pushStyleColor(ImGuiCol.ButtonHovered, 0.83f, 0.13f, 0.17f, 1f)
        pushStyleColor(ImGuiCol.ButtonActive, 0.93f, 0.23f, 0.27f, 1f)
        if (ImGui.button(Icons.WINDOW_CLOSE, BTN_SIZE, BTN_SIZE)) {
            windowController.close()
        }
        popStyleColor(2)
    }
}
