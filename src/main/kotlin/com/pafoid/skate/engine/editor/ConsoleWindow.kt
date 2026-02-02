package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.editor.logs.LogEntry
import com.pafoid.skate.engine.editor.logs.LogLevel
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.utils.Icons
import com.pafoid.skate.engine.utils.StringManager
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class ConsoleWindow : KoinComponent {

    private val logger: LoggerService by inject()
    private val stringManager: StringManager by inject()

    fun imgui(pOpen: ImBoolean) {
        if (!ImGui.begin(stringManager.getString("window.console"), pOpen)) {
            ImGui.end()
            return
        }

        if (ImGui.beginTabBar("ConsoleTabs")) {
            if (ImGui.beginTabItem("${Icons.GEAR} Engine")) {
                renderLogList(logger.engineLogs)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem("${Icons.USER} Editor")) {
                renderLogList(logger.editorLogs)
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }

        ImGui.end()
    }

    private fun renderLogList(logs: Iterable<LogEntry>) {
        ImGui.beginChild("LogScrollingRegion", 0f, 0f, false, ImGuiWindowFlags.HorizontalScrollbar)
        
        logs.forEach { entry ->
            val color = when (entry.level) {
                LogLevel.INFO -> floatArrayOf(1f, 1f, 1f, 1f)
                LogLevel.WARN -> floatArrayOf(1f, 0.8f, 0f, 1f)
                LogLevel.ERROR -> floatArrayOf(1f, 0.2f, 0.2f, 1f)
                LogLevel.ACTION -> floatArrayOf(0.4f, 0.7f, 1f, 1f)
            }
            ImGui.textColored(color[0], color[1], color[2], color[3], "[${entry.level}] ${entry.message}")
        }
        
        // Auto-scroll to bottom
        if (ImGui.getScrollY() >= ImGui.getScrollMaxY()) {
            ImGui.setScrollHereY(1.0f)
        }
        
        ImGui.endChild()
    }
}

