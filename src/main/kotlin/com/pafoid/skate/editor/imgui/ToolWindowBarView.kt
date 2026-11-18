package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.data.KeyCombo
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags

/**
 * Draws one side bar (EDT-84 §1): the top section's buttons from the top, the bottom section's from the bottom, each
 * an icon-only button with the window's title and shortcut as a tooltip. Buttons can be dragged within and between
 * sections; the gap between the sections is a drop target for the end of the top section and the start of the bottom
 * one. Right-click: Move to, then the window's own tab-menu items.
 */
class ToolWindowBarView(
    private val strings: StringManager,
    private val controller: ToolWindowBarsController,
    private val windowOf: (String) -> EditorWindow?,
) {
    /**
     * The window's state when its button was pressed. Pressing moves ImGui's focus to the bar before the click lands
     * on release, so the click acts on what the user saw (a focused window closes).
     */
    private val pressedState = HashMap<String, ToolWindowState>()

    /** Last drawn button rectangles by window, for tests. */
    val drawn = LinkedHashMap<String, MaximizeSession.Area>()

    fun render(left: Boolean, x: Float, y: Float, height: Float) {
        val width = width()
        val (top, bottom) = if (left) BarSection.LEFT_TOP to BarSection.LEFT_BOTTOM else BarSection.RIGHT_TOP to BarSection.RIGHT_BOTTOM
        ImGui.setCursorScreenPos(x, y)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, PADDING, PADDING)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, PADDING, PADDING)
        val id = if (left) "##tool_window_bar_left" else "##tool_window_bar_right"
        if (ImGui.beginChild(id, width, height, false, ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse)) {
            val size = buttonSize()
            val topWindows = controller.bars.windows(top)
            val bottomWindows = controller.bars.windows(bottom)
            topWindows.forEachIndexed { i, w -> button(w, top, i, x + PADDING, y + PADDING + i * (size + PADDING), size, left) }
            val bottomStart = y + height - PADDING - bottomWindows.size * (size + PADDING) + PADDING
            bottomWindows.forEachIndexed { i, w -> button(w, bottom, i, x + PADDING, bottomStart + i * (size + PADDING), size, left) }
            // The gap: its upper half appends to the top section, its lower half puts a button first in the bottom one.
            val gapTop = y + PADDING + topWindows.size * (size + PADDING)
            val gapHeight = (bottomStart - PADDING - gapTop).coerceAtLeast(0f)
            if (gapHeight >= 2f) {
                dropZone("${id}_gap_top", x, gapTop, width, gapHeight / 2f, top, topWindows.size)
                dropZone("${id}_gap_bottom", x, gapTop + gapHeight / 2f, width, gapHeight / 2f, bottom, 0)
            }
        }
        ImGui.endChild()
        ImGui.popStyleVar(2)
    }

    private fun button(window: String, section: BarSection, index: Int, x: Float, y: Float, size: Float, left: Boolean) {
        val editorWindow = windowOf(window) ?: return
        val state = controller.state(window)
        val bg = when {
            state == ToolWindowState.FOCUSED -> ImGui.getColorU32(ImGuiCol.ButtonActive)
            state == ToolWindowState.OPEN && ToolWindowStates.inFront(window) -> ImGui.getColorU32(ImGuiCol.Header)
            state == ToolWindowState.OPEN -> ImGui.getColorU32(ImGuiCol.FrameBg)
            else -> 0
        }
        ImGui.setCursorScreenPos(x, y)
        ImGui.pushStyleColor(ImGuiCol.Button, bg)
        val icon = editorWindow.icon ?: strings.getString(window).take(1)
        val clicked = ImGui.button("$icon###tool_window_$window", size, size)
        ImGui.popStyleColor()
        drawn[window] = MaximizeSession.Area(ImGui.getItemRectMinX(), ImGui.getItemRectMinY(), size, size)
        Tooltips.always(tooltip(window))
        if (state == ToolWindowState.FOCUSED) {
            // The focused window's marker on the bar's outer edge.
            val markerX = if (left) ImGui.getItemRectMinX() - PADDING else ImGui.getItemRectMaxX() + PADDING - MARKER
            ImGui.getWindowDrawList().addRectFilled(markerX, ImGui.getItemRectMinY(), markerX + MARKER, ImGui.getItemRectMaxY(), ImGui.getColorU32(ImGuiCol.CheckMark))
        }
        if (ImGui.isItemActivated()) pressedState[window] = state
        if (clicked) controller.toggle(window, pressedState.remove(window) ?: state)

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(PAYLOAD, window)
            ImGui.text("$icon ${strings.getString(window)}")
            ImGui.endDragDropSource()
        }
        if (ImGui.beginDragDropTarget()) {
            ImGui.acceptDragDropPayload<String>(PAYLOAD)?.let { dragged -> if (dragged != window) controller.move(dragged, section, index) }
            ImGui.endDragDropTarget()
        }
        contextMenu(editorWindow)
    }

    private fun dropZone(id: String, x: Float, y: Float, width: Float, height: Float, section: BarSection, index: Int) {
        ImGui.setCursorScreenPos(x, y)
        ImGui.invisibleButton(id, width, height)
        if (ImGui.beginDragDropTarget()) {
            ImGui.acceptDragDropPayload<String>(PAYLOAD)?.let { controller.move(it, section, index) }
            ImGui.endDragDropTarget()
        }
    }

    /** Move to (the other sections), then the window's tab-menu items (Maximize, Help, Reset position, Close). */
    private fun contextMenu(window: EditorWindow) {
        if (!ImGui.beginPopupContextItem("##tool_window_menu_${window.name}")) return
        val current = controller.bars.sectionOf(window.name)
        if (ImGui.beginMenu(strings.label("lbl.toolbars.move_to"))) {
            BarSection.entries.filter { it != current }.forEach { section ->
                if (strings.tipped(ImGui.menuItem(strings.getString(section.labelKey)), section.labelKey)) {
                    controller.move(window.name, section, Int.MAX_VALUE)
                }
            }
            ImGui.endMenu()
        }
        val items = TabMenuItem.forWindow(window.isDialog, window.canMaximize, MaximizeSession.isMaximized(window.name))
        if (items.isNotEmpty()) ImGui.separator()
        items.forEach { item ->
            if (item == TabMenuItem.CLOSE && !window.isOpen.get()) return@forEach
            if (strings.tipped(ImGui.menuItem("${item.icon} ${strings.getString(item.labelKey)}"), item.labelKey)) {
                WindowHeader.run(item, window)
            }
        }
        ImGui.endPopup()
    }

    /** The window's title, and its shortcut when it has one (`Console (Alt+4)`). */
    fun tooltip(window: String): String {
        val title = strings.getString(window)
        val shortcut = EditorShortcuts.toolWindow(window)?.display()?.takeIf { it != KeyCombo.UNBOUND }
        return if (shortcut == null) title else "$title ($shortcut)"
    }

    companion object {
        const val PAYLOAD = "TOOL_WINDOW_BUTTON"
        private const val PADDING = 4f
        private const val MARKER = 2f

        /** A square button: the icon with room around it at the current font size (scales with EDT-57's UI scale). */
        fun buttonSize(): Float = kotlin.math.ceil(ImGui.getFontSize() * 1.9f)

        /** The bar's width: one button and its padding on both sides. */
        fun width(): Float = buttonSize() + PADDING * 2f

        /** Where the bars and the dock space go in the host's content rect (§1); [barWidth] 0 when the bars are hidden. */
        data class Frame(val left: MaximizeSession.Area?, val dock: MaximizeSession.Area, val right: MaximizeSession.Area?)

        fun frame(x: Float, y: Float, width: Float, height: Float, barWidth: Float): Frame {
            if (barWidth <= 0f) return Frame(null, MaximizeSession.Area(x, y, width, height), null)
            return Frame(
                left = MaximizeSession.Area(x, y, barWidth, height),
                dock = MaximizeSession.Area(x + barWidth, y, (width - 2f * barWidth).coerceAtLeast(1f), height),
                right = MaximizeSession.Area(x + width - barWidth, y, barWidth, height),
            )
        }
    }
}
