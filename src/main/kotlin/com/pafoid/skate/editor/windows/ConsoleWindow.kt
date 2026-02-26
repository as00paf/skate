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

    override fun imgui(pOpen: ImBoolean?) {
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

        // Temporarily adjust style to make input fields look like text
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 0f) // Frame padding X, Y
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0f) // Frame rounding
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 0f) // Frame border size
        
        logs.forEach { entry ->
            val color = when (entry.level) {
                LogLevel.INFO -> floatArrayOf(1f, 1f, 1f, 1f)
                LogLevel.WARN -> floatArrayOf(1f, 0.8f, 0f, 1f)
                LogLevel.ERROR -> floatArrayOf(1f, 0.2f, 0.2f, 1f)
                LogLevel.ACTION -> floatArrayOf(0.4f, 0.7f, 1f, 1f)
            }
            
            // Push the color for this log entry (text color only)
            ImGui.pushStyleColor(ImGuiCol.Text, color[0], color[1], color[2], color[3]) // Text color
            // For the background, we'll use the actual console background color (SLATE)
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.13f, 0.14f, 0.17f, 1.0f) // SLATE background color
            
            // Use read-only input text which allows for selection
            val logText = "[${entry.level}] ${entry.message}"
            val imString = ImString(logText)
            ImGui.inputText("##log_${entry.timestamp}_${entry.hashCode()}", 
                imString, 
                ImGuiInputTextFlags.ReadOnly or ImGuiInputTextFlags.NoHorizontalScroll)
            
            // Pop the colors
            ImGui.popStyleColor(2)
        }
        
        // Restore original style
        ImGui.popStyleVar(3)

        // Auto-scroll to bottom
        if (ImGui.getScrollY() >= ImGui.getScrollMaxY()) {
            ImGui.setScrollHereY(1.0f)
        }

        ImGui.endChild()
    }
}

