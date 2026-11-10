package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags

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

/**
 * Layout math of the `?` help button and the header row (EDT-67), kept free of ImGui calls so it is unit-tested.
 * All values are in pixels at the current font scale.
 */
object HelpButtonLayout {
    /** Smallest gap kept between the icon glyph and the top/bottom edge of the button background. */
    const val MIN_ICON_MARGIN_Y = 2f

    data class Size(val width: Float, val height: Float)

    /**
     * The button: a full frame (not `smallButton`, whose background is one text line high and cut off the top of the
     * taller icon glyph), at least square, and tall enough for the icon glyph at any font scale or theme padding.
     */
    fun size(iconWidth: Float, fontSize: Float, framePaddingX: Float, framePaddingY: Float): Size {
        val iconHeight = fontSize * ICON_FONT_SIZE / BASE_FONT_SIZE
        val height = maxOf(fontSize + framePaddingY * 2f, iconHeight + MIN_ICON_MARGIN_Y * 2f)
        val width = maxOf(height, iconWidth + framePaddingX * 2f)
        return Size(width, height)
    }

    /** Width of the header's fixed right column: the button plus one item spacing from the tools. */
    fun columnWidth(buttonWidth: Float, itemSpacingX: Float): Float = buttonWidth + itemSpacingX

    /** X of the button in its cell: flush right, never left of the cell start when the cell is narrower. */
    fun buttonX(cellStartX: Float, cellAvailX: Float, buttonWidth: Float): Float =
        cellStartX + (cellAvailX - buttonWidth).coerceAtLeast(0f)

    /**
     * Width a toolbar keeps free after a stretched field (a search box) for its trailing buttons of text widths
     * [labelWidths]: each button with its frame padding, preceded by one item spacing. The `?` is not part of it:
     * the header already gives the tools only the width left of its own column.
     */
    fun trailingWidth(labelWidths: List<Float>, framePaddingX: Float, itemSpacingX: Float): Float =
        labelWidths.sumOf { (it + framePaddingX * 2f + itemSpacingX).toDouble() }.toFloat()
}

/** The shared window header strip and tab menu (EDT-51 §2.2, §2.3). */
object WindowHeader {
    private const val TAB_MENU_ID = "##window_tab_menu"
    private const val TABLE_ID = "##window_header"
    private const val BUTTON_ID = "##window_help"

    /**
     * The window's [tools] on the left, then the `?` button at the right end of the same line, then a separator.
     * A two-column table lays it out: the tools get (and are clipped to) the width left of the `?` column, so a
     * stretched search box or a long toolbar can never cover or push out the button.
     */
    fun render(strings: StringManager, window: EditorWindow, tools: () -> Unit = {}) {
        val style = ImGui.getStyle()
        val size = buttonSize()
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 0f, 0f)
        val table = ImGui.beginTable(TABLE_ID, 2, ImGuiTableFlags.NoSavedSettings or ImGuiTableFlags.NoPadOuterX or ImGuiTableFlags.NoPadInnerX)
        ImGui.popStyleVar()
        if (table) {
            ImGui.tableSetupColumn("##tools", ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn("##help", ImGuiTableColumnFlags.WidthFixed, HelpButtonLayout.columnWidth(size.width, style.itemSpacingX))
            ImGui.tableNextRow()
            ImGui.tableSetColumnIndex(0)
            tools()
            ImGui.tableSetColumnIndex(1)
            ImGui.setCursorPosX(HelpButtonLayout.buttonX(ImGui.getCursorPosX(), ImGui.getContentRegionAvailX(), size.width))
            button(strings, window, size.width, size.height)
            ImGui.endTable()
        } else {
            // Hidden or collapsed window: ImGui skips the items anyway; keep the tools' own logic running.
            tools()
            ImGui.sameLine()
            button(strings, window, size.width, size.height)
        }
        ImGui.separator()
    }

    /**
     * A header strip with only the `?`, right-aligned, without a table (modals size themselves to their content).
     * Returns true when it was clicked.
     */
    fun helpRow(strings: StringManager): Boolean {
        val size = buttonSize()
        ImGui.setCursorPosX(maxOf(ImGui.getCursorPosX(), ImGui.getWindowContentRegionMaxX() - size.width))
        val clicked = clicked(strings, size.width, size.height)
        ImGui.separator()
        return clicked
    }

    /**
     * The `?` button alone, for windows that put it in their own toolbar (the Game Viewport). [squareSize] makes it
     * a square of that size to match the toolbar's buttons; by default it takes [HelpButtonLayout.size].
     */
    fun helpButton(strings: StringManager, window: EditorWindow, squareSize: Float? = null) {
        val size = buttonSize()
        button(strings, window, squareSize ?: size.width, squareSize ?: size.height)
    }

    /** [HelpButtonLayout.size] with the current font and style. */
    fun buttonSize(): HelpButtonLayout.Size {
        val style = ImGui.getStyle()
        return HelpButtonLayout.size(ImGui.calcTextSize(Icons.QUESTION_CIRCLE).x, ImGui.getTextLineHeight(), style.framePaddingX, style.framePaddingY)
    }

    /** [HelpButtonLayout.trailingWidth] for buttons labelled [labels], with the current font and style. */
    fun trailingWidth(vararg labels: String): Float {
        val style = ImGui.getStyle()
        return HelpButtonLayout.trailingWidth(labels.map { ImGui.calcTextSize(it).x }, style.framePaddingX, style.itemSpacingX)
    }

    private fun button(strings: StringManager, window: EditorWindow, width: Float, height: Float) {
        if (clicked(strings, width, height)) window.openHelp()
    }

    private fun clicked(strings: StringManager, width: Float, height: Float): Boolean =
        strings.tippedWith(ImGui.button("${Icons.QUESTION_CIRCLE}$BUTTON_ID", width, height), "window.header.help.tooltip")

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
        TabMenuItem.HELP -> window.openHelp()
        TabMenuItem.RESET_POSITION -> window.resetPositionRequested = true
        TabMenuItem.CLOSE -> window.isOpen.set(false)
    }
}
