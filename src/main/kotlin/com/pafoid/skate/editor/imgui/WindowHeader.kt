package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui

/** An item of a window's tab / title-bar right-click menu (EDT-51 §2.3). */
enum class TabMenuItem(val labelKey: String, val icon: String) {
    HELP("window.menu.help", Icons.QUESTION_CIRCLE),
    RESET_POSITION("window.menu.reset_position", Icons.ARROW_ROTATE),
    CLOSE("window.menu.close", Icons.WINDOW_CLOSE);

    companion object {
        /** Help first, Close last; Reset position only for windows that dock. */
        fun forWindow(isDialog: Boolean): List<TabMenuItem> =
            if (isDialog) listOf(HELP, CLOSE) else listOf(HELP, RESET_POSITION, CLOSE)
    }
}

/** The shared window header strip and tab menu (EDT-51 §2.2, §2.3). */
object WindowHeader {
    private const val TAB_MENU_ID = "##window_tab_menu"

    /** The window's [tools] on the left, then the `?` button at the right end of the same line. */
    fun render(strings: StringManager, window: EditorWindow, tools: () -> Unit = {}) {
        val startX = ImGui.getCursorPosX()
        tools()
        val buttonWidth = ImGui.calcTextSize(Icons.QUESTION_CIRCLE).x + ImGui.getStyle().framePaddingX * 2
        val right = ImGui.getWindowContentRegionMaxX() - buttonWidth
        ImGui.sameLine(maxOf(right, startX), 0f)
        helpButton(strings, window)
        ImGui.separator()
    }

    /** The `?` button alone, for windows that put it in their own toolbar (the Game Viewport). */
    fun helpButton(strings: StringManager, window: EditorWindow) {
        if (strings.tippedWith(ImGui.smallButton("${Icons.QUESTION_CIRCLE}##window_help"), "window.header.help.tooltip")) {
            window.help.open()
        }
    }

    /** Right-click on the tab or title bar; call right after `begin`. */
    fun tabMenu(strings: StringManager, window: EditorWindow) {
        if (!ImGui.beginPopupContextItem(TAB_MENU_ID)) return
        TabMenuItem.forWindow(window.isDialog).forEach { item ->
            if (item == TabMenuItem.CLOSE) ImGui.separator()
            if (strings.tipped(ImGui.menuItem("${item.icon} ${strings.getString(item.labelKey)}"), item.labelKey)) {
                run(item, window)
            }
        }
        ImGui.endPopup()
    }

    internal fun run(item: TabMenuItem, window: EditorWindow) = when (item) {
        TabMenuItem.HELP -> window.help.open()
        TabMenuItem.RESET_POSITION -> window.resetPositionRequested = true
        TabMenuItem.CLOSE -> window.isOpen.set(false)
    }
}
