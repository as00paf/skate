package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.data.LogEntry
import com.pafoid.skate.editor.events.ConsoleAction
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.data.LogLevel
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import org.lwjgl.glfw.GLFW

class ConsoleWindow(
    private val logger: LoggerService,
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
) : IWindow {

    private val searchText = ImString(256)
    private val selectedLogs = mutableSetOf<LogEntry>()
    private var lastSelectedIndex = -1

    /** null = show all levels */
    private var logLevelFilter: LogLevel? = null

    private val autoScroll = ImBoolean(true)

    override fun imgui(pOpen: ImBoolean?) {
        if (!ImGui.begin(stringManager.getString("window.console"), pOpen)) {
            ImGui.end()
            return
        }

        renderToolbar()

        if (ImGui.beginTabBar("ConsoleTabs")) {
            if (ImGui.beginTabItem("${Icons.GEAR} ${stringManager.getString("tab.console.engine")}")) {
                renderLogList(logger.logs.filter { it.source == "engine" }.toList(), "Engine")
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem("${Icons.USER} ${stringManager.getString("tab.console.editor")}")) {
                renderLogList(logger.logs.filter { it.source == "editor" }.toList(), "Editor")
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }

        if (ImGui.isWindowFocused() && ImGui.getIO().keyCtrl && ImGui.isKeyPressed(GLFW.GLFW_KEY_C)) {
            publishCopySelected()
        }

        ImGui.end()
    }

    private fun publishCopySelected() {
        if (selectedLogs.isEmpty()) return
        val sb = StringBuilder()
        selectedLogs.sortedBy { it.timestamp }.forEach { entry ->
            sb.append("[${entry.level}] ${entry.message}\n")
        }
        eventSystem.publish(ConsoleAction.CopyToClipboard(sb.toString()))
    }

    private fun renderToolbar() {
        // Row 1: search input + clear button + auto-scroll checkbox
        val clearLabel = "${Icons.TRASH} ${stringManager.getString("lbl.clear")}"
        val autoScrollLabel = stringManager.getString("lbl.console.auto_scroll")
        val clearBtnWidth = ImGui.calcTextSize(clearLabel).x + ImGui.getStyle().framePaddingX * 2f
        val autoScrollWidth = ImGui.calcTextSize(autoScrollLabel).x + 20f + ImGui.getStyle().framePaddingX * 2f
        val spacing = ImGui.getStyle().itemSpacingX
        val searchWidth = ImGui.getContentRegionAvailX() - clearBtnWidth - autoScrollWidth - spacing * 2f

        ImGui.pushItemWidth(searchWidth)
        ImGui.inputTextWithHint("##ConsoleSearch", "${Icons.SEARCH} ${stringManager.getString("lbl.search")}...", searchText)
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.search_console"))
        }
        ImGui.popItemWidth()

        ImGui.sameLine()
        if (ImGui.button(clearLabel)) {
            eventSystem.publish(ConsoleAction.ClearLogs)
            selectedLogs.clear()
            lastSelectedIndex = -1
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.clear_console"))
        }

        ImGui.sameLine()
        ImGui.checkbox(autoScrollLabel, autoScroll)
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.console.auto_scroll"))
        }

        ImGui.separator()

        // Row 2: log level filter buttons with per-level count badges
        val allLogs = logger.logs.toList()
        val infoCount = allLogs.count { it.level == LogLevel.INFO }
        val warnCount = allLogs.count { it.level == LogLevel.WARN }
        val errorCount = allLogs.count { it.level == LogLevel.ERROR }
        val actionCount = allLogs.count { it.level == LogLevel.ACTION }

        renderFilterButton(null,
            stringManager.getString("btn.console.filter.all"),
            stringManager.getString("tooltip.console.filter.all"))
        ImGui.sameLine()
        renderFilterButton(LogLevel.INFO,
            stringManager.getString("lbl.console.count.info", infoCount),
            stringManager.getString("tooltip.console.filter.info"))
        ImGui.sameLine()
        renderFilterButton(LogLevel.WARN,
            stringManager.getString("lbl.console.count.warn", warnCount),
            stringManager.getString("tooltip.console.filter.warn"))
        ImGui.sameLine()
        renderFilterButton(LogLevel.ERROR,
            stringManager.getString("lbl.console.count.error", errorCount),
            stringManager.getString("tooltip.console.filter.error"))
        ImGui.sameLine()
        renderFilterButton(LogLevel.ACTION,
            stringManager.getString("lbl.console.count.action", actionCount),
            stringManager.getString("tooltip.console.filter.action"))

        ImGui.separator()
    }

    private fun renderFilterButton(level: LogLevel?, label: String, tooltip: String) {
        val isActive = logLevelFilter == level
        if (isActive) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.8f, 1f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.6f, 0.9f, 1f)
        }
        if (ImGui.button(label)) {
            logLevelFilter = level
        }
        if (isActive) {
            ImGui.popStyleColor(2)
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip)
        }
    }

    private fun renderLogList(logs: List<LogEntry>, label: String) {
        val searchFilter = searchText.get()

        // Apply level filter first, then text search filter
        val levelFiltered = if (logLevelFilter != null) {
            logs.filter { it.level == logLevelFilter }
        } else {
            logs
        }
        val filteredLogs = if (searchFilter.isEmpty()) {
            levelFiltered
        } else {
            levelFiltered.filter {
                it.message.contains(searchFilter, ignoreCase = true) ||
                        it.level.name.contains(searchFilter, ignoreCase = true)
            }
        }

        ImGui.beginChild("LogScrollingRegion$label", 0f, 0f, false, ImGuiWindowFlags.HorizontalScrollbar)

        if (filteredLogs.isEmpty()) {
            ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, stringManager.getString("lbl.console.empty"))
        } else {
            filteredLogs.forEachIndexed { index, entry ->
                val color = when (entry.level) {
                    LogLevel.INFO -> floatArrayOf(1f, 1f, 1f, 1f)
                    LogLevel.WARN -> floatArrayOf(1f, 0.8f, 0f, 1f)
                    LogLevel.ERROR -> floatArrayOf(1f, 0.2f, 0.2f, 1f)
                    LogLevel.ACTION -> floatArrayOf(0.4f, 0.7f, 1f, 1f)
                }

                ImGui.pushStyleColor(ImGuiCol.Text, color[0], color[1], color[2], color[3])

                val isSelected = selectedLogs.contains(entry)
                // Unique ID per entry to avoid selection conflicts with identical messages
                val logText = "[${entry.level}] ${entry.message}##${entry.timestamp}_$index"

                if (ImGui.selectable(logText, isSelected)) {
                    val io = ImGui.getIO()
                    when {
                        io.keyCtrl -> {
                            if (isSelected) selectedLogs.remove(entry) else selectedLogs.add(entry)
                        }
                        io.keyShift && lastSelectedIndex != -1 && lastSelectedIndex < filteredLogs.size -> {
                            selectedLogs.clear()
                            val start = minOf(lastSelectedIndex, index)
                            val end = maxOf(lastSelectedIndex, index)
                            for (i in start..end) {
                                selectedLogs.add(filteredLogs[i])
                            }
                        }
                        else -> {
                            selectedLogs.clear()
                            selectedLogs.add(entry)
                        }
                    }
                    lastSelectedIndex = index
                }

                ImGui.popStyleColor()
            }
        }

        // Right-click context menu
        if (ImGui.beginPopupContextWindow()) {
            if (ImGui.menuItem(
                    stringManager.getString("context.console.copy_selected"),
                    stringManager.getString("shortcut.copy")
                )
            ) {
                publishCopySelected()
            }
            if (ImGui.menuItem(stringManager.getString("context.console.copy_all"))) {
                val sb = StringBuilder()
                logs.forEach { sb.append("[${it.level}] ${it.message}\n") }
                eventSystem.publish(ConsoleAction.CopyToClipboard(sb.toString()))
            }
            ImGui.separator()
            if (ImGui.menuItem(stringManager.getString("context.console.clear_selection"))) {
                selectedLogs.clear()
                lastSelectedIndex = -1
            }
            if (ImGui.menuItem(stringManager.getString("context.console.clear_all_logs"))) {
                eventSystem.publish(ConsoleAction.ClearLogs)
                selectedLogs.clear()
                lastSelectedIndex = -1
            }
            ImGui.endPopup()
        }

        // Auto-scroll: keep pinned to latest entry when no selection is active
        if (autoScroll.get() && selectedLogs.isEmpty()) {
            ImGui.setScrollHereY(1.0f)
        }

        ImGui.endChild()
    }
}

