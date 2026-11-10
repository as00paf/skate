package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.data.EditorFocus
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.events.ConsoleAction
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogEntry
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import org.lwjgl.glfw.GLFW

class ConsoleWindow(engine: Engine) : EditorWindow("window.console", true) {
    private val logger = engine.logger
    private val stringManager = engine.stringManager
    private val eventSystem = engine.eventSystem

    private val searchText = ImString(256)
    private val selectedLogs = mutableSetOf<LogEntry>()
    private var lastSelectedIndex = -1

    private var logLevelFilter: LogLevel? = null

    private val autoScroll = ConsoleAutoScroll()
    private val autoScrollCheckbox = ImBoolean(true)

    override val icon = Icons.LIST
    override val relatedWindows = listOf("window.shell", "window.profiler")

    override fun imgui() {
        if (!beginWindow(stringManager)) {
            ImGui.end()
            return
        }

        header(stringManager) { renderSearchRow() }
        renderFilterRow()

        if (ImGui.beginTabBar("ConsoleTabs")) {
            if (ImGui.beginTabItem(stringManager.label("tab.console.engine", "${Icons.GEAR} "))) {
                renderLogList(logger.logs.filter { it.source == "engine" }.toList(), "Engine")
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem(stringManager.label("tab.console.editor", "${Icons.USER} "))) {
                renderLogList(logger.logs.filter { it.source == "editor" }.toList(), "Editor")
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }

        if (ImGui.isWindowFocused()) {
            // Ctrl+C copies log lines here, not the selected object (EDT-10).
            EditorFocus.claim(EditorShortcuts.COPY.combo())
            if (ImGui.getIO().keyCtrl && ImGui.isKeyPressed(GLFW.GLFW_KEY_C)) publishCopySelected()
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

    /** Header tools: search input + clear button + auto-scroll checkbox (the `?` follows on the same line). */
    private fun renderSearchRow() {
        val clearLabel = "${Icons.TRASH} ${stringManager.getString("lbl.clear")}"
        val autoScrollLabel = stringManager.getString("lbl.console.auto_scroll")
        val clearBtnWidth = ImGui.calcTextSize(clearLabel).x + ImGui.getStyle().framePaddingX * 2f
        val autoScrollWidth = ImGui.calcTextSize(autoScrollLabel).x + 20f + ImGui.getStyle().framePaddingX * 2f
        val spacing = ImGui.getStyle().itemSpacingX
        val helpWidth = ImGui.calcTextSize(Icons.QUESTION_CIRCLE).x + ImGui.getStyle().framePaddingX * 2f
        val searchWidth = ImGui.getContentRegionAvailX() - clearBtnWidth - autoScrollWidth - helpWidth - spacing * 3f

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
        autoScrollCheckbox.set(autoScroll.enabled)
        if (ImGui.checkbox(autoScrollLabel, autoScrollCheckbox)) {
            autoScroll.setEnabled(autoScrollCheckbox.get())
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.console.auto_scroll"))
        }
    }

    /** Log level filter buttons with per-level count badges, under the header. */
    private fun renderFilterRow() {
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
            if (logs.isEmpty()) {
                EmptyState.nothingYet(stringManager, Icons.LIST, "lbl.console.empty")
            } else {
                EmptyState.noResults(stringManager) {
                    searchText.set("")
                    logLevelFilter = null
                }
            }
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
            if (stringManager.tipped(ImGui.menuItem(
                    "${Icons.COPY} ${stringManager.getString("context.console.copy_selected")}",
                    stringManager.getString("shortcut.copy"), false, selectedLogs.isNotEmpty()
                ), "context.console.copy_selected")
            ) {
                publishCopySelected()
            }
            if (stringManager.tipped(ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.console.copy_all")}", "", false, logs.isNotEmpty()), "context.console.copy_all")) {
                val sb = StringBuilder()
                logs.forEach { sb.append("[${it.level}] ${it.message}\n") }
                eventSystem.publish(ConsoleAction.CopyToClipboard(sb.toString()))
            }
            ImGui.separator()
            if (stringManager.tipped(ImGui.menuItem(stringManager.getString("context.console.clear_selection"), "", false, selectedLogs.isNotEmpty()), "context.console.clear_selection")) {
                selectedLogs.clear()
                lastSelectedIndex = -1
            }
            if (stringManager.tipped(ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.console.clear_all_logs")}"), "context.console.clear_all_logs")) {
                eventSystem.publish(ConsoleAction.ClearLogs)
                selectedLogs.clear()
                lastSelectedIndex = -1
            }
            ImGui.endPopup()
        }

        // Auto-scroll (EDT-47): scrolling up turns it off, scrolling back to the bottom turns it on.
        // Pinning pauses while lines are selected so the selection does not move away.
        if (autoScroll.update(label, ImGui.getScrollY(), ImGui.getScrollMaxY(), hold = selectedLogs.isNotEmpty())) {
            ImGui.setScrollHereY(1.0f)
        }

        ImGui.endChild()
    }
}

