package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.data.LogEntry
import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject
import org.lwjgl.glfw.GLFW
import kotlin.getValue

class ConsoleWindow(
    private val logger: LoggerService,
    private val stringManager: StringManager,
) : IWindow, KoinComponent {


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
            if (ImGui.beginTabItem("${Icons.GEAR} ${stringManager.getString("tab.console.engine")}")) {
                renderLogList(logger.engineLogs.toList(), "Engine")
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem("${Icons.USER} ${stringManager.getString("tab.console.editor")}")) {
                renderLogList(logger.editorLogs.toList(), "Editor")
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }

        if (ImGui.isWindowFocused() && ImGui.getIO().keyCtrl && ImGui.isKeyPressed(GLFW.GLFW_KEY_C)) {
            copySelectedToClipboard()
        }

        ImGui.end()
    }

    private fun copySelectedToClipboard() {
        if (selectedLogs.isEmpty()) return
        
        val sb = StringBuilder()
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
                    if (isSelected) selectedLogs.remove(entry) else selectedLogs.add(entry)
                } else if (io.keyShift && lastSelectedIndex != -1 && lastSelectedIndex < filteredLogs.size) {
                    selectedLogs.clear()
                    val start = Math.min(lastSelectedIndex, index)
                    val end = Math.max(lastSelectedIndex, index)
                    for (i in start..end) {
                        selectedLogs.add(filteredLogs[i])
                    }
                } else {
                    selectedLogs.clear()
                    selectedLogs.add(entry)
                }
                lastSelectedIndex = index
            }
            
            ImGui.popStyleColor()
        }

        if (ImGui.beginPopupContextWindow()) {
            if (ImGui.menuItem(stringManager.getString("context.console.copy_selected"), stringManager.getString("shortcut.copy"))) {
                copySelectedToClipboard()
            }
            if (ImGui.menuItem(stringManager.getString("context.console.copy_all"))) {
                val sb = StringBuilder()
                logs.forEach { sb.append("[${it.level}] ${it.message}\n") }
                ImGui.setClipboardText(sb.toString())
            }
            ImGui.separator()
            if (ImGui.menuItem(stringManager.getString("context.console.clear_selection"))) {
                selectedLogs.clear()
                lastSelectedIndex = -1
            }
            if (ImGui.menuItem(stringManager.getString("context.console.clear_all_logs"))) {
                logger.clearAllLogs()
                selectedLogs.clear()
                lastSelectedIndex = -1
            }
            ImGui.endPopup()
        }

        if (selectedLogs.isEmpty() && ImGui.getScrollY() >= ImGui.getScrollMaxY()) {
            ImGui.setScrollHereY(1.0f)
        }

        ImGui.endChild()
    }
}

