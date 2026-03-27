package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.LogEntry
import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ConsoleWindow : IWindow, KoinComponent {

    private val logger: LoggerService by inject()
    private val stringManager: StringManager by inject()
    private val searchText = ImString(256)
    
    private val selectedLogs = mutableSetOf<LogEntry>()
    private var lastSelectedIndex = -1

    override fun imgui(pOpen: ImBoolean?) {
        if (!ImGui.begin(stringManager.getString("window.console"), pOpen)) {
            ImGui.end()
            return
        }

        renderToolbar()

        if (ImGui.beginTabBar("ConsoleTabs")) {
            if (ImGui.beginTabItem("${Icons.GEAR} Engine")) {
                renderLogList(logger.engineLogs.toList(), "Engine")
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem("${Icons.USER} Editor")) {
                renderLogList(logger.editorLogs.toList(), "Editor")
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }

        // Handle Ctrl+C for copying
        if (ImGui.isWindowFocused() && ImGui.getIO().keyCtrl && ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_C)) {
            copySelectedToClipboard()
        }

        ImGui.end()
    }

    private fun copySelectedToClipboard() {
        if (selectedLogs.isEmpty()) return
        
        val sb = StringBuilder()
        // Sort by timestamp to maintain log order
        selectedLogs.sortedBy { it.timestamp }.forEach { entry ->
            sb.append("[${entry.level}] ${entry.message}\n")
        }
        ImGui.setClipboardText(sb.toString())
    }

    private fun renderToolbar() {
        val clearLabel = "${Icons.TRASH} ${stringManager.getString("lbl.clear")}"
        val clearBtnWidth = ImGui.calcTextSize(clearLabel).x + ImGui.getStyle().framePaddingX * 2.0f
        val spacing = ImGui.getStyle().itemSpacingX
        val searchWidth = ImGui.getContentRegionAvailX() - clearBtnWidth - spacing

        ImGui.pushItemWidth(searchWidth)
        ImGui.inputTextWithHint("##ConsoleSearch", "${Icons.SEARCH} ${stringManager.getString("lbl.search")}...", searchText)
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.search_console"))
        }
        ImGui.popItemWidth()

        ImGui.sameLine()
        if (ImGui.button(clearLabel)) {
            logger.clearAllLogs()
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.clear_console"))
        }

        ImGui.separator()
    }

    private fun renderLogList(logs: List<LogEntry>, label: String) {
        val filter = searchText.get()
        val filteredLogs = if (filter.isEmpty()) {
            logs
        } else {
            logs.filter { it.message.contains(filter, ignoreCase = true) || it.level.name.contains(filter, ignoreCase = true) }
        }

        ImGui.beginChild("LogScrollingRegion$label", 0f, 0f, false, ImGuiWindowFlags.HorizontalScrollbar)
        
        filteredLogs.forEachIndexed { index, entry ->
            val color = when (entry.level) {
                LogLevel.INFO -> floatArrayOf(1f, 1f, 1f, 1f)
                LogLevel.WARN -> floatArrayOf(1f, 0.8f, 0f, 1f)
                LogLevel.ERROR -> floatArrayOf(1f, 0.2f, 0.2f, 1f)
                LogLevel.ACTION -> floatArrayOf(0.4f, 0.7f, 1f, 1f)
            }
            
            ImGui.pushStyleColor(ImGuiCol.Text, color[0], color[1], color[2], color[3])
            
            val isSelected = selectedLogs.contains(entry)
            // Use a unique ID for each selectable to avoid selection issues with identical messages
            val logText = "[${entry.level}] ${entry.message}##${entry.timestamp}_$index"
            
            if (ImGui.selectable(logText, isSelected)) {
                val io = ImGui.getIO()
                
                if (io.keyCtrl) {
                    // Toggle selection
                    if (isSelected) selectedLogs.remove(entry) else selectedLogs.add(entry)
                } else if (io.keyShift && lastSelectedIndex != -1 && lastSelectedIndex < filteredLogs.size) {
                    // Range selection
                    selectedLogs.clear()
                    val start = Math.min(lastSelectedIndex, index)
                    val end = Math.max(lastSelectedIndex, index)
                    for (i in start..end) {
                        selectedLogs.add(filteredLogs[i])
                    }
                } else {
                    // Single selection
                    selectedLogs.clear()
                    selectedLogs.add(entry)
                }
                lastSelectedIndex = index
            }
            
            ImGui.popStyleColor()
        }

        // Context Menu for right-click actions
        if (ImGui.beginPopupContextWindow()) {
            if (ImGui.menuItem("Copy Selected", "Ctrl+C")) {
                copySelectedToClipboard()
            }
            if (ImGui.menuItem("Copy All")) {
                val sb = StringBuilder()
                logs.forEach { sb.append("[${it.level}] ${it.message}\n") }
                ImGui.setClipboardText(sb.toString())
            }
            ImGui.separator()
            if (ImGui.menuItem("Clear Selection")) {
                selectedLogs.clear()
                lastSelectedIndex = -1
            }
            if (ImGui.menuItem("Clear All Logs")) {
                logger.clearAllLogs()
                selectedLogs.clear()
                lastSelectedIndex = -1
            }
            ImGui.endPopup()
        }

        // Auto-scroll to bottom if not dragging scrollbar and no selection
        if (selectedLogs.isEmpty() && ImGui.getScrollY() >= ImGui.getScrollMaxY()) {
            ImGui.setScrollHereY(1.0f)
        }

        ImGui.endChild()
    }
}

