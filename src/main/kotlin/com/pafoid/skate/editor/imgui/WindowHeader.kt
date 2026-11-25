package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags

/** An item of a window's tab / title-bar right-click menu (EDT-51 §2.3). */
enum class TabMenuItem(val labelKey: String, val icon: String) {
    /** EDT-88: maximize in the default mode (Editor Settings › Interface). */
    MAXIMIZE("context.window.maximize", Icons.WINDOW_MAXIMIZE),
    MAXIMIZE_OVER_VIEWPORT("context.window.maximize_over_viewport", Icons.WINDOW_MAXIMIZE),
    RESTORE("context.window.restore", Icons.WINDOW_RESTORE),
    HELP("window.menu.help", Icons.QUESTION_CIRCLE),
    RESET_POSITION("window.menu.reset_position", Icons.ARROW_ROTATE),
    CLOSE("window.menu.close", Icons.WINDOW_CLOSE);

    companion object {
        /**
         * Maximize items first (EDT-88: Maximize and Maximize over Game Viewport, or Restore while maximized), then
         * Help, then Close last; Reset position only for windows that dock and are not maximized.
         */
        fun forWindow(isDialog: Boolean, canMaximize: Boolean = false, maximized: Boolean = false): List<TabMenuItem> {
            val maximize = when {
                isDialog || !canMaximize -> emptyList()
                maximized -> listOf(RESTORE)
                else -> listOf(MAXIMIZE, MAXIMIZE_OVER_VIEWPORT)
            }
            val rest = when {
                isDialog -> listOf(HELP, CLOSE)
                maximized -> listOf(HELP, CLOSE)
                else -> listOf(HELP, RESET_POSITION, CLOSE)
            }
            return maximize + rest
        }
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
     *
     * The clearance applies to the unscrolled position (EDT-85): [scrollY] is the window's scroll, so a scrolled
     * header moves up with the content instead of being pushed back below the clip top, which pinned the content
     * and snapped the scroll back.
     */
    fun row(
        startX: Float, startY: Float, contentMaxX: Float, clipTop: Float, buttonWidth: Float, itemSpacingX: Float,
        scrollY: Float = 0f,
    ): Row {
        val push = (clipTop + MIN_CLIP_CLEARANCE - (startY + scrollY)).coerceAtLeast(0f)
        val top = kotlin.math.ceil(startY + push)
        val buttonX = kotlin.math.floor(maxOf(startX, contentMaxX - buttonWidth))
        return Row(top, buttonX, (buttonX - itemSpacingX - startX).coerceAtLeast(0f))
    }

    /** Width of [count] header buttons of [buttonWidth] side by side, [itemSpacingX] apart (EDT-88: Maximize and `?`). */
    fun groupWidth(buttonWidth: Float, count: Int, itemSpacingX: Float): Float =
        buttonWidth * count + itemSpacingX * (count - 1).coerceAtLeast(0)

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

/**
 * Where Maximize and `?` go in a title bar (EDT-101): right-aligned, left of the close button, the size and vertical
 * place of ImGui's own close button (one font size, centred in the bar). Pure, so it is unit-tested.
 */
object TitleBarLayout {
    data class Bar(val x: Float, val y: Float, val width: Float, val height: Float)
    data class Slot(val x: Float, val y: Float)

    /** ImGui's title bar buttons (collapse, close) are one font size square. */
    fun buttonSize(fontSize: Float): Float = fontSize

    /** The title bar of a window at ([x], [y]) [width] wide: one font size plus the vertical frame padding twice. */
    fun bar(x: Float, y: Float, width: Float, fontSize: Float, framePaddingY: Float): Bar =
        Bar(x, y, width, fontSize + framePaddingY * 2f)

    /**
     * [count] button slots, left to right, ending left of the close button when [closeButton] (ImGui draws it at the
     * bar's right end minus the horizontal frame padding), [spacing] apart.
     */
    fun slots(
        bar: Bar, size: Float, count: Int, closeButton: Boolean, framePaddingX: Float, spacing: Float,
        contentMaxX: Float = Float.MAX_VALUE,
    ): List<Slot> {
        var right = bar.x + bar.width - framePaddingX
        if (closeButton) right -= size + spacing
        // Never past the content's right edge (a vertical scrollbar narrows it): the window would count the buttons as
        // content wider than itself (EDT-100).
        right = minOf(right, contentMaxX)
        // On whole pixels, like the content-row buttons (EDT-72): a fractional window position must not blur them.
        val y = kotlin.math.floor(bar.y + (bar.height - size) / 2f)
        return (0 until count).map { i -> Slot(kotlin.math.floor(right - (count - i) * size - (count - 1 - i) * spacing), y) }
    }
}

/** The shared window header strip and tab menu (EDT-51 §2.2, §2.3). */
object WindowHeader {
    private const val TAB_MENU_ID = "##window_tab_menu"
    private const val TABLE_ID = "##window_header"
    private const val BUTTON_ID = "##window_help"
    private const val MAXIMIZE_BUTTON_ID = "##window_maximize"

    /**
     * The window's header (EDT-51 §2.2, EDT-101 option B). Call right after `beginWindow`.
     * - **Floating** (dialogs, undocked windows, the full-editor host of EDT-88): Maximize and `?` go in the title bar,
     *   left of the close button ([titleBar]); [tools], if any, get the first content row.
     * - **Docked with [tools]:** the tools, then Maximize and `?` at the right end of the same row ([toolsRow]).
     * - **Docked without tools:** Maximize and `?` sit over the top-right corner of the content ([corner]); no row.
     * The buttons are never alone on a row.
     */
    fun render(strings: StringManager, window: EditorWindow, tools: (() -> Unit)? = null) {
        if (!ImGui.isWindowDocked() && window.hasTitleBar) {
            titleBar(strings, window, closeButton = window.hasCloseButton)
            tools?.let { toolsRow(strings, window, it, withButtons = false) }
        } else if (tools != null) {
            toolsRow(strings, window, tools, withButtons = true)
        } else {
            corner(strings, window)
        }
    }

    /**
     * The tools in a one-column table as wide as the space left of the buttons, then (when [withButtons]) Maximize and
     * `?` at the right end of the same line, then a separator (EDT-67, EDT-72). A stretched search box gets the right
     * width and is clipped before the buttons; the buttons are drawn clear of every clip edge ([HelpButtonLayout.row]).
     */
    private fun toolsRow(strings: StringManager, window: EditorWindow, tools: () -> Unit, withButtons: Boolean) {
        val style = ImGui.getStyle()
        val size = buttonSize()
        val startX = ImGui.getCursorScreenPosX()
        // EDT-88: the Maximize button sits left of the `?`; the row reserves both.
        val buttons = if (!withButtons) 0 else if (window.canMaximize) 2 else 1
        val row = HelpButtonLayout.row(
            startX, ImGui.getCursorScreenPosY(), ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX(),
            ImGui.getWindowDrawList().clipRectMinY, HelpButtonLayout.groupWidth(size.width, buttons, style.itemSpacingX),
            style.itemSpacingX, ImGui.getScrollY(),
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
        if (withButtons) {
            var buttonX = row.buttonX
            if (window.canMaximize) {
                ImGui.setCursorScreenPos(buttonX, row.top)
                maximizeButton(strings, window, size.width, size.height)
                buttonX += size.width + style.itemSpacingX
            }
            ImGui.setCursorScreenPos(buttonX, row.top)
            button(strings, window, size.width, size.height)
        }
        val buttonsHeight = if (withButtons) size.height else 0f
        ImGui.setCursorScreenPos(startX, HelpButtonLayout.nextLineY(row.top, buttonsHeight, toolsNextLine, style.itemSpacingY))
        ImGui.separator()
    }

    /**
     * EDT-101: Maximize (when the window can) and `?` in the current window's title bar, right-aligned, left of the
     * close button when there is one. They are real buttons: the title bar belongs to the window, so hover and clicks
     * reach them, and pressing one does not start a window move. The cursor is left where it was.
     */
    fun titleBar(strings: StringManager, window: EditorWindow?, closeButton: Boolean, onHelp: () -> Unit = { window?.openHelp() }): Boolean {
        val style = ImGui.getStyle()
        val size = TitleBarLayout.buttonSize(ImGui.getFontSize().toFloat())
        val bar = TitleBarLayout.bar(ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getFontSize().toFloat(), style.framePaddingY)
        val slots = TitleBarLayout.slots(
            bar, size, count = if (window?.canMaximize == true) 2 else 1, closeButton, style.framePaddingX, style.itemInnerSpacingX,
            contentMaxX = ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX(),
        )
        val saved = floatArrayOf(ImGui.getCursorScreenPosX(), ImGui.getCursorScreenPosY())
        ImGui.pushClipRect(bar.x, bar.y, bar.x + bar.width, bar.y + bar.height, false)
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 0f)
        var helpClicked = false
        slots.forEachIndexed { i, slot ->
            ImGui.setCursorScreenPos(slot.x, slot.y)
            val isHelp = i == slots.lastIndex
            if (isHelp) {
                if (clicked(strings, size, size)) { helpClicked = true; onHelp() }
            } else {
                maximizeButton(strings, window!!, size, size)
            }
        }
        ImGui.popStyleVar()
        ImGui.popClipRect()
        ImGui.setCursorScreenPos(saved[0], saved[1])
        return helpClicked
    }

    /**
     * The `?` button alone, for windows that put it in their own toolbar (the Game Viewport). [squareSize] makes it
     * a square of that size to match the toolbar's buttons; by default it takes [HelpButtonLayout.size].
     */
    fun helpButton(strings: StringManager, window: EditorWindow, squareSize: Float? = null) {
        val size = buttonSize()
        button(strings, window, squareSize ?: size.width, squareSize ?: size.height)
    }

    /**
     * EDT-101: a docked window without tools gets its buttons over the top-right corner of its content instead of a
     * row of their own. They are submitted first, as invisible buttons, so the content under them cannot take their
     * hover or clicks; their look is drawn after the window's content ([drawCornerOverlay]) so it stays on top.
     */
    private fun corner(strings: StringManager, window: EditorWindow) {
        val style = ImGui.getStyle()
        val size = buttonSize()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val count = if (window.canMaximize) 2 else 1
        val row = HelpButtonLayout.row(
            startX, startY, ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX(),
            ImGui.getWindowDrawList().clipRectMinY, HelpButtonLayout.groupWidth(size.width, count, style.itemSpacingX),
            style.itemSpacingX, ImGui.getScrollY(),
        )
        val buttons = ArrayList<CornerButton>()
        var x = row.buttonX
        if (window.canMaximize) {
            val maximized = MaximizeSession.isMaximized(window.name)
            val icon = if (maximized) Icons.WINDOW_RESTORE else Icons.WINDOW_MAXIMIZE
            ImGui.setCursorScreenPos(x, row.top)
            if (ImGui.invisibleButton(MAXIMIZE_BUTTON_ID, size.width, size.height)) MaximizeSession.request(window.name)
            Tooltips.show(strings, if (maximized) "btn.window.restore.tooltip" else "btn.window.maximize.tooltip", EditorShortcuts.MAXIMIZE_WINDOW)
            buttons += CornerButton(icon, Rect(x, row.top, x + size.width, row.top + size.height), ImGui.isItemHovered(), ImGui.isItemActive())
            lastDrawnMaximize = drawnRect()
            x += size.width + style.itemSpacingX
        }
        ImGui.setCursorScreenPos(x, row.top)
        if (ImGui.invisibleButton(BUTTON_ID, size.width, size.height)) window.openHelp()
        Tooltips.show(strings, "window.header.help.tooltip", EditorShortcuts.HELP)
        buttons += CornerButton(Icons.QUESTION_CIRCLE, Rect(x, row.top, x + size.width, row.top + size.height), ImGui.isItemHovered(), ImGui.isItemActive())
        lastDrawn = drawnRect()
        window.cornerOverlay = CornerOverlay(ImGui.getFrameCount(), buttons)
        // The content starts where the header would have: on the same row as the buttons.
        ImGui.setCursorScreenPos(startX, startY)
    }

    /** One corner button's look, drawn over the content after the window drew ([drawCornerOverlay]). */
    data class CornerButton(val icon: String, val rect: Rect, val hovered: Boolean, val held: Boolean)

    data class CornerOverlay(val frame: Int, val buttons: List<CornerButton>)

    /**
     * Draws [window]'s corner buttons on top of its content (EDT-101): re-opens the window in ImGui's append mode
     * (`begin` with the same title again in the frame) and paints the buttons' frames and icons. Call after the
     * window's `imgui()`; does nothing when the window drew no corner buttons this frame.
     */
    fun drawCornerOverlay(window: EditorWindow) {
        val overlay = window.cornerOverlay ?: return
        window.cornerOverlay = null
        val title = window.lastTitle ?: return
        if (overlay.frame != ImGui.getFrameCount()) return
        ImGui.begin(title)
        val list = ImGui.getWindowDrawList()
        list.pushClipRect(ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowPosX() + ImGui.getWindowWidth(), ImGui.getWindowPosY() + ImGui.getWindowHeight(), true)
        val rounding = ImGui.getStyle().frameRounding
        for (b in overlay.buttons) {
            val r = b.rect
            list.addRectFilled(r.minX, r.minY, r.maxX, r.maxY, ImGui.getColorU32(imgui.flag.ImGuiCol.WindowBg), rounding)
            val col = when {
                b.held -> imgui.flag.ImGuiCol.ButtonActive
                b.hovered -> imgui.flag.ImGuiCol.ButtonHovered
                else -> imgui.flag.ImGuiCol.Button
            }
            list.addRectFilled(r.minX, r.minY, r.maxX, r.maxY, ImGui.getColorU32(col), rounding)
            val text = ImGui.calcTextSize(b.icon)
            list.addText(r.minX + (r.maxX - r.minX - text.x) / 2f, r.minY + (r.maxY - r.minY - text.y) / 2f, ImGui.getColorU32(imgui.flag.ImGuiCol.Text), b.icon)
        }
        list.popClipRect()
        ImGui.end()
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

    /** Maximize (or Restore while maximized) in the default mode (EDT-88). */
    private fun maximizeButton(strings: StringManager, window: EditorWindow, width: Float, height: Float) {
        val maximized = MaximizeSession.isMaximized(window.name)
        val (icon, key) = if (maximized) Icons.WINDOW_RESTORE to "btn.window.restore" else Icons.WINDOW_MAXIMIZE to "btn.window.maximize"
        val pressed = strings.tipped(ImGui.button("$icon$MAXIMIZE_BUTTON_ID", width, height), key, EditorShortcuts.MAXIMIZE_WINDOW)
        lastDrawnMaximize = drawnRect()
        if (pressed) MaximizeSession.request(window.name)
    }

    private fun drawnRect(): DrawnButton {
        val list = ImGui.getWindowDrawList()
        return DrawnButton(
            Rect(ImGui.getItemRectMinX(), ImGui.getItemRectMinY(), ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY()),
            Rect(list.clipRectMinX, list.clipRectMinY, list.clipRectMaxX, list.clipRectMaxY),
        )
    }

    private fun button(strings: StringManager, window: EditorWindow, width: Float, height: Float) {
        if (clicked(strings, width, height)) window.openHelp()
    }

    private fun clicked(strings: StringManager, width: Float, height: Float): Boolean {
        val pressed = strings.tippedWith(ImGui.button("${Icons.QUESTION_CIRCLE}$BUTTON_ID", width, height), "window.header.help.tooltip", EditorShortcuts.HELP)
        // The tooltip adds no item to this window: the last item is still the button.
        lastDrawn = drawnRect()
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

    /** The last Maximize button drawn this frame (EDT-88); read by tests. */
    @Volatile
    var lastDrawnMaximize: DrawnButton? = null
        private set

    /** Right-click on the tab or title bar; call right after `begin`. */
    fun tabMenu(strings: StringManager, window: EditorWindow) {
        if (!ImGui.beginPopupContextItem(TAB_MENU_ID)) return
        val items = TabMenuItem.forWindow(window.isDialog, window.canMaximize, MaximizeSession.isMaximized(window.name))
        items.forEach { item ->
            if (item == TabMenuItem.CLOSE || (item == TabMenuItem.HELP && items.first() != TabMenuItem.HELP)) ImGui.separator()
            if (strings.tipped(ImGui.menuItem("${item.icon} ${strings.getString(item.labelKey)}"), item.labelKey)) {
                run(item, window)
            }
        }
        ImGui.endPopup()
    }

    internal fun run(item: TabMenuItem, window: EditorWindow) = when (item) {
        TabMenuItem.MAXIMIZE -> MaximizeSession.request(window.name)
        TabMenuItem.MAXIMIZE_OVER_VIEWPORT -> MaximizeSession.request(window.name, MaximizeMode.OVER_VIEWPORT)
        TabMenuItem.RESTORE -> MaximizeSession.requestRestore()
        TabMenuItem.HELP -> window.openHelp()
        TabMenuItem.RESET_POSITION -> window.resetPositionRequested = true
        TabMenuItem.CLOSE -> window.isOpen.set(false)
    }
}
