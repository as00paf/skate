package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags

/**
 * Floating, non-dockable tool dialog (EDT-51 §2.1). It closes with the title-bar X, Esc or its footer, is not listed
 * in View > Windows and is not part of the docked layout (ImGui still remembers its size and position). The base
 * draws the standard parts: icon title, tab menu (Help, Close), header strip with `?`, and the standard footer
 * (a lone Close unless [footerButtons] says otherwise). Subclasses implement [content].
 */
abstract class EditorDialog(
    name: String,
    private val stringManager: StringManager,
    private val defaultWidth: Float = 520f,
    private val defaultHeight: Float = 600f,
) : EditorWindow(name) {

    protected abstract fun content()

    /** The footer; Cancel-role and closing buttons close the dialog after their action. */
    protected open fun footerButtons(): List<FooterButton> = listOf(FooterButton("btn.close", FooterButton.Role.CANCEL))

    final override val isDialog: Boolean get() = true

    fun open() = isOpen.set(true)

    fun close() = isOpen.set(false)

    fun toggle() = isOpen.set(!isOpen.get())

    final override fun imgui() {
        ImGui.setNextWindowSize(defaultWidth, defaultHeight, ImGuiCond.FirstUseEver)
        // First use: centred on the editor (EDT-64); later ImGui remembers where it was left.
        val viewport = ImGui.getMainViewport()
        ImGui.setNextWindowPos(
            viewport.workPosX + viewport.workSizeX / 2f, viewport.workPosY + viewport.workSizeY / 2f,
            ImGuiCond.FirstUseEver, 0.5f, 0.5f,
        )
        if (beginWindow(stringManager, ImGuiWindowFlags.NoDocking or ImGuiWindowFlags.NoCollapse)) {
            header(stringManager)
            content()
            if (DialogFooter.render(stringManager, footerButtons())?.closes == true) close()
        }
        ImGui.end()
    }
}
