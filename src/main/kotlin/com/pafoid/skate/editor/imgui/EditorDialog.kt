package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiWindowFlags

/**
 * Floating, non-dockable tool dialog. It closes with the title-bar X, its Close button or Esc, is not listed in
 * View > Windows and is not part of the docked layout (ImGui still remembers its size and position).
 * Subclasses implement [content].
 */
abstract class EditorDialog(
    name: String,
    private val stringManager: StringManager,
    private val defaultWidth: Float = 520f,
    private val defaultHeight: Float = 600f,
) : EditorWindow(name) {

    protected abstract fun content()

    fun open() = isOpen.set(true)

    fun close() = isOpen.set(false)

    fun toggle() = isOpen.set(!isOpen.get())

    final override fun imgui() {
        ImGui.setNextWindowSize(defaultWidth, defaultHeight, ImGuiCond.FirstUseEver)
        val visible = ImGui.begin(
            stringManager.getString(name), isOpen, ImGuiWindowFlags.NoDocking or ImGuiWindowFlags.NoCollapse,
        )
        if (visible) {
            content()
            ImGui.separator()
            if (ImGui.button(stringManager.getString("btn.close"))) close()
            if (ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows) && ImGui.isKeyPressed(ImGuiKey.Escape)) close()
        }
        ImGui.end()
    }
}
