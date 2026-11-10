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

    /** Smallest gap between the button and the top of the clip rectangle it is drawn into (EDT-72): room for the
     * anti-aliased border and the focus outline, and for the backend truncating a fractional scissor. */
    const val MIN_CLIP_CLEARANCE = 2f

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

    /**
     * The header row in screen pixels (EDT-72). [top] is where the tools and the `?` start; [buttonX] is the `?`'s
     * left edge; [toolsWidth] is what is left of it for the tools.
     */
    data class Row(val top: Float, val buttonX: Float, val toolsWidth: Float)

    /**
     * Lays out a header row that starts at ([startX], [startY]) in a window whose content ends at [contentMaxX] and
     * whose clip rectangle starts at [clipTop]. The `?` sits on whole pixels, flush right, and at least
     * [MIN_CLIP_CLEARANCE] below the clip top, so neither its border, its focus outline nor a truncated scissor cut
     * it (it was drawn exactly on a table cell's clip edge before).
     */
    fun row(startX: Float, startY: Float, contentMaxX: Float, clipTop: Float, buttonWidth: Float, itemSpacingX: Float): Row {
        val top = kotlin.math.ceil(maxOf(startY, clipTop + MIN_CLIP_CLEARANCE))
        val buttonX = kotlin.math.floor(maxOf(startX, contentMaxX - buttonWidth))
        return Row(top, buttonX, (buttonX - itemSpacingX - startX).coerceAtLeast(0f))
    }

    /** Where the line after the header row starts: below the taller of the tools and the `?`. */
    fun nextLineY(rowTop: Float, buttonHeight: Float, toolsNextLineY: Float, itemSpacingY: Float): Float =
        maxOf(toolsNextLineY, rowTop + buttonHeight + itemSpacingY)

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
     * The window's [tools] on the left, then the `?` button at the right end of the same line, then a separator
     * (EDT-67, EDT-72). The tools go in a one-column table as wide as the space left of the `?`, so a stretched
     * search box gets the right width and is clipped before the button. The `?` itself is drawn in the window's own
     * clip rectangle at [HelpButtonLayout.row], clear of every clip edge.
     */
    fun render(strings: StringManager, window: EditorWindow, tools: () -> Unit = {}) {
        val style = ImGui.getStyle()
        val size = buttonSize()
        val startX = ImGui.getCursorScreenPosX()
        val row = HelpButtonLayout.row(
            startX, ImGui.getCursorScreenPosY(), ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX(),
            ImGui.getWindowDrawList().clipRectMinY, size.width, style.itemSpacingX,
        )
        val clearance = HelpButtonLayout.MIN_CLIP_CLEARANCE
        // The table starts `clearance` above the row with as much cell padding, so the tools have the same margin
        // from the cell's clip top. ImGui reads the vertical cell padding when the row begins.
        ImGui.setCursorScreenPos(startX, row.top - clearance)
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 0f, clearance)
        val table = row.toolsWidth > 0f &&
            ImGui.beginTable(TABLE_ID, 1, ImGuiTableFlags.NoSavedSettings or ImGuiTableFlags.NoPadOuterX, row.toolsWidth, 0f)
        if (table) {
            ImGui.tableSetupColumn("##tools", ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableNextRow()
            ImGui.tableSetColumnIndex(0)
            ImGui.popStyleVar()
            tools()
            ImGui.endTable()
        } else {
            // Hidden, collapsed or too narrow: ImGui skips the items anyway; keep the tools' own logic running.
            ImGui.popStyleVar()
            ImGui.setCursorScreenPos(startX, row.top)
            tools()
        }
        val toolsNextLine = ImGui.getCursorScreenPosY()
        ImGui.setCursorScreenPos(row.buttonX, row.top)
        button(strings, window, size.width, size.height)
        ImGui.setCursorScreenPos(startX, HelpButtonLayout.nextLineY(row.top, size.height, toolsNextLine, style.itemSpacingY))
        ImGui.separator()
    }

    /**
     * A header strip with only the `?`, right-aligned, without a table (modals size themselves to their content).
     * Returns true when it was clicked.
     */
    fun helpRow(strings: StringManager): Boolean {
        val style = ImGui.getStyle()
        val size = buttonSize()
        val startX = ImGui.getCursorScreenPosX()
        val row = HelpButtonLayout.row(
            startX, ImGui.getCursorScreenPosY(), ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX(),
            ImGui.getWindowDrawList().clipRectMinY, size.width, style.itemSpacingX,
        )
        ImGui.setCursorScreenPos(row.buttonX, row.top)
        val clicked = clicked(strings, size.width, size.height)
        ImGui.setCursorScreenPos(startX, row.top + size.height + style.itemSpacingY)
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

    private fun clicked(strings: StringManager, width: Float, height: Float): Boolean {
        val pressed = strings.tippedWith(ImGui.button("${Icons.QUESTION_CIRCLE}$BUTTON_ID", width, height), "window.header.help.tooltip")
        // The tooltip adds no item to this window: the last item is still the button.
        val list = ImGui.getWindowDrawList()
        lastDrawn = DrawnButton(
            Rect(ImGui.getItemRectMinX(), ImGui.getItemRectMinY(), ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY()),
            Rect(list.clipRectMinX, list.clipRectMinY, list.clipRectMaxX, list.clipRectMaxY),
        )
        return pressed
    }

    /** A screen rectangle. */
    data class Rect(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

    /** Where the last `?` was drawn and the clip rectangle it was drawn into (EDT-72 layout tests). */
    data class DrawnButton(val button: Rect, val clip: Rect)

    /** The last `?` drawn this frame; read by tests. */
    @Volatile
    var lastDrawn: DrawnButton? = null
        private set

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
