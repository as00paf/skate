package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.help.HelpRouter
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
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

    /** The icon in front of the title (EDT-51 §2.2). Null for windows not converted yet (EDT-52). */
    open val icon: String? = null

    /** Windows the Help window offers to open below this window's article ("Related windows"), by [name]. */
    open val relatedWindows: List<String> = emptyList()

    /** Help topic: the article `editor/help/<locale>/<helpId>.md` (EDT-53, EDT-68); `window.console` -> `console`. */
    val helpId: String get() = helpIdOf(name)

    /** Opens the Help window on this window's article (the `?` and the tab menu's Help, EDT-68). */
    fun openHelp() = HelpRouter.open(helpId)

    /** Set by the tab menu's Reset position; applied before the next `begin`. */
    internal var resetPositionRequested = false

    /** Dialogs float: no dock to reset to. */
    open val isDialog: Boolean get() = false

    /**
     * Window title: the [icon], then translated text (of [textKey], [name] by default) with [name] as its stable
     * ImGui ID, so docking, imgui.ini and the open state survive a language change. Every `begin` of this window must
     * use it.
     */
    fun title(strings: StringManager, prefix: String = "", textKey: String = name): String =
        ImGuiIds.stable(prefix + (icon?.let { "$it " } ?: "") + strings.getString(textKey), name)

    /**
     * The standard `begin` (EDT-51): the title with its icon and the tab / title-bar right-click menu (Help, Close,
     * Reset position). As with `ImGui.begin`, call `ImGui.end()` whatever this returns.
     * Draw [header] first in the content.
     */
    protected fun beginWindow(strings: StringManager, flags: Int = 0, closable: Boolean = true): Boolean {
        if (resetPositionRequested) {
            resetPositionRequested = false
            DefaultDockLayout.docks.resetTarget(name)?.let { ImGui.setNextWindowDockID(it) }
        }
        val visible = if (closable) ImGui.begin(title(strings), isOpen, flags) else ImGui.begin(title(strings), flags)
        if (!isDialog) DefaultDockLayout.docks.record(name, ImGui.getWindowDockID())
        WindowHeader.tabMenu(strings, this)
        return visible
    }

    /** The one-line header strip: the window's own [tools] on the left, `?` on the right (EDT-51 §2.2). */
    protected fun header(strings: StringManager, tools: () -> Unit = {}) = WindowHeader.render(strings, this, tools)

    companion object {
        /** `window.console` -> `console`. */
        fun helpIdOf(windowName: String): String = windowName.removePrefix("window.")
    }
}
