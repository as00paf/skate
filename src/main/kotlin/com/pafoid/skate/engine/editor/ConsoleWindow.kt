package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.utils.Icons
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import java.util.concurrent.ConcurrentLinkedQueue

class ConsoleWindow {
    
    enum class LogLevel {
        INFO, WARN, ERROR, ACTION
    }
    
    data class LogEntry(val message: String, val level: LogLevel, val timestamp: Long = System.currentTimeMillis())

    companion object {
        private val engineLogs = ConcurrentLinkedQueue<LogEntry>()
        private val editorLogs = ConcurrentLinkedQueue<LogEntry>()
        
        fun logEngine(message: String, level: LogLevel = LogLevel.INFO) {
            engineLogs.add(LogEntry(message, level))
            if (engineLogs.size > 1000) engineLogs.poll()
        }
        
        fun logEditor(message: String, level: LogLevel = LogLevel.ACTION) {
            editorLogs.add(LogEntry(message, level))
            if (editorLogs.size > 1000) editorLogs.poll()
        }
    }

    fun imgui(pOpen: ImBoolean) {
        if (!ImGui.begin("Console", pOpen)) {
            ImGui.end()
            return
        }

        if (ImGui.beginTabBar("ConsoleTabs")) {
            if (ImGui.beginTabItem("${Icons.GEAR} Engine")) {
                renderLogList(engineLogs)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem("${Icons.USER} Editor")) {
                renderLogList(editorLogs)
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
