package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.systems.EditorPaths
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.shell.ShellHistory
import com.pafoid.skate.editor.shell.ShellLine
import com.pafoid.skate.editor.shell.ShellOutputBuffer
import com.pafoid.skate.editor.shell.ShellPresets
import com.pafoid.skate.editor.shell.ShellSession
import com.pafoid.skate.editor.shell.ShellStatus
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.ImGuiListClipper
import imgui.callback.ImListClipperCallback
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import java.io.File

/**
 * EDT-08: terminal-style panel. Runs one shell command at a time in the background ([ShellSession]): Gradle tasks,
 * the Skate CLI or anything else the platform shell runs. Output streams in live, Cancel kills the process tree, and
 * the command history ([ShellHistory], Up/Down to recall) persists in the user-data folder ([EditorPaths]).
 * Running a command changes no scene or project state, so nothing here goes through the undo stack.
 */
class ShellWindow(
    engine: Engine,
    private val projectManager: ProjectManager,
    private val session: ShellSession = ShellSession(),
    private val history: ShellHistory = ShellHistory(EditorPaths.file(HISTORY_FILE)).load(),
    private val workingDir: File = File(".").absoluteFile,
) : EditorWindow(NAME) {

    companion object {
        const val NAME = "window.shell"
        const val HISTORY_FILE = "shell_history.txt"
    }

    private val strings = engine.stringManager
    private val output = ShellOutputBuffer()
    private val input = ImString(2048)
    private val autoScroll = ImBoolean(true)
    private var focusInput = false

    override val icon = Icons.TERMINAL
    override val relatedWindows = listOf("window.console")

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ session.shutdown() }, "shell-panel-shutdown"))
    }

    override fun imgui() {
        output.addAll(session.drain(2000))
        if (!beginWindow(strings)) {
            ImGui.end()
            return
        }
        header(strings) { toolbar() }
        outputView()
        inputLine()
        ImGui.end()
    }

    private fun run(command: String) {
        if (session.run(command, workingDir)) {
            history.add(command)
            input.set("")
        }
        focusInput = true
    }

    private fun toolbar() {
        if (ImGui.button(strings.label("shell.presets"))) ImGui.openPopup("shell_presets_popup")
        if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("shell.tooltip.presets"))
        if (ImGui.beginPopup("shell_presets_popup")) {
            val project = projectManager.currentProject?.projectPath
            ShellPresets.all(projectFile = project).forEach { preset ->
                if (ImGui.menuItem(strings.label(preset.labelKey))) input.set(preset.command)
                if (ImGui.isItemHovered()) ImGui.setTooltip(preset.command)
            }
            ImGui.endPopup()
        }

        ImGui.sameLine()
        ImGui.beginDisabled(session.isRunning || input.get().isBlank())
        if (strings.tipped(ImGui.button(strings.label("shell.run", "${Icons.PLAY} ")), "shell.run")) run(input.get())
        ImGui.endDisabled()

        ImGui.sameLine()
        ImGui.beginDisabled(!session.isRunning)
        if (strings.tipped(ImGui.button(strings.label("shell.cancel", "${Icons.STOP} ")), "shell.cancel")) session.cancel()
        ImGui.endDisabled()

        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("shell.clear", "${Icons.TRASH} ")), "shell.clear")) output.clear()

        ImGui.sameLine()
        ImGui.checkbox(strings.label("lbl.console.auto_scroll"), autoScroll)

        ImGui.sameLine()
        ImGui.textDisabled(statusText())
        if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("shell.tooltip.working_dir", workingDir.path))
    }

    private fun statusText(): String = when (val s = session.status) {
        ShellStatus.Idle -> strings.getString("shell.status.idle")
        is ShellStatus.Running -> strings.getString("shell.status.running", (System.currentTimeMillis() - s.startedAtMs) / 1000)
        is ShellStatus.Exited -> strings.getString("shell.status.exited", s.exitCode, s.durationMs / 1000.0)
        is ShellStatus.Cancelled -> strings.getString("shell.status.cancelled")
        is ShellStatus.Failed -> strings.getString("shell.status.failed")
    }

    private fun outputView() {
        val footer = ImGui.getFrameHeightWithSpacing() + ImGui.getStyle().itemSpacingY
        ImGui.beginChild("shell_output", 0f, -footer, true, ImGuiWindowFlags.HorizontalScrollbar)
        if (output.size == 0) EmptyState.nothingYet(strings, Icons.TERMINAL, "empty.shell", "empty.shell.hint")
        ImGuiListClipper.forEach(output.size, object : ImListClipperCallback() {
            override fun accept(index: Int) {
                val line = output[index]
                val color = when (line.kind) {
                    ShellLine.Kind.COMMAND -> floatArrayOf(0.45f, 0.75f, 1f, 1f)
                    ShellLine.Kind.INFO -> floatArrayOf(0.6f, 0.9f, 0.6f, 1f)
                    ShellLine.Kind.ERROR -> floatArrayOf(1f, 0.4f, 0.4f, 1f)
                    ShellLine.Kind.OUTPUT -> null
                }
                if (color != null) ImGui.pushStyleColor(ImGuiCol.Text, color[0], color[1], color[2], color[3])
                ImGui.textUnformatted(line.key?.let { strings.getString(it, *line.args.toTypedArray()) } ?: line.text)
                if (color != null) ImGui.popStyleColor()
            }
        })
        if (ImGui.beginPopupContextWindow("shell_output_context")) {
            val hasOutput = output.size > 0
            if (strings.tipped(ImGui.menuItem(strings.label("context.console.copy_all", "${Icons.COPY} "), "", false, hasOutput), "context.console.copy_all")) ImGui.setClipboardText(output.text())
            ImGui.separator()
            if (strings.tipped(ImGui.menuItem(strings.label("shell.clear", "${Icons.TRASH} "), "", false, hasOutput), "shell.clear")) output.clear()
            ImGui.endPopup()
        }
        if (autoScroll.get() && ImGui.getScrollY() >= ImGui.getScrollMaxY() - 1f) ImGui.setScrollHereY(1f)
        ImGui.endChild()
    }

    private fun inputLine() {
        ImGui.setNextItemWidth(-1f)
        if (focusInput) {
            ImGui.setKeyboardFocusHere()
            focusInput = false
        }
        val hint = strings.getString(if (session.isRunning) "shell.hint.running" else "shell.hint.input")
        if (ImGui.inputTextWithHint("##shell_input", hint, input, ImGuiInputTextFlags.EnterReturnsTrue)) {
            if (!session.isRunning) run(input.get())
        }
        if (ImGui.isItemActive()) {
            val recalled = when {
                ImGui.isKeyPressed(ImGuiKey.UpArrow) -> history.previous()
                ImGui.isKeyPressed(ImGuiKey.DownArrow) -> history.next()
                else -> null
            }
            if (recalled != null) {
                // An active InputText keeps its own copy of the text: drop focus, set it, refocus next frame.
                imgui.internal.ImGui.clearActiveID()
                input.set(recalled)
                focusInput = true
            }
        }
    }
}
