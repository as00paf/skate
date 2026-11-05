package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.core.StringManager
import imgui.type.ImBoolean

abstract class EditorWindow(
    val name: String,
    val isDefault: Boolean = false,
    val isOpen: ImBoolean = ImBoolean(false)
) {
    abstract fun imgui()

    /**
     * Called once when the window goes from open to closed by any path (title-bar X, View > Windows, Hide All, code).
     * Closed windows get no [imgui] call, so per-session state (previews, drafts) must be dropped here.
     */
    open fun onClosed() {}

    /**
     * Window title: translated text (of [textKey], [name] by default) with [name] as its stable ImGui ID, so docking,
     * imgui.ini and the open state survive a language change. Every `begin` of this window must use it.
     */
    fun title(strings: StringManager, prefix: String = "", textKey: String = name): String =
        ImGuiIds.stable(prefix + strings.getString(textKey), name)
}