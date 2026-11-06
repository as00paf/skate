package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui

/**
 * Empty states (EDT-10): what a panel shows when it has nothing to show, instead of a blank area. A dimmed icon and
 * message, an optional hint, and optional action buttons, centred in the remaining space of the current window.
 */
object EmptyState {

    /** A button under the message: [labelKey] is its label (its tooltip is `<labelKey>.tooltip`). */
    class Action(val labelKey: String, val icon: String? = null, val onClick: () -> Unit)

    fun render(strings: StringManager, icon: String, messageKey: String, hintKey: String? = null, actions: List<Action> = emptyList()) {
        val message = "$icon  ${strings.getString(messageKey)}"
        val hint = hintKey?.let { strings.getString(it) }
        val lineHeight = ImGui.getTextLineHeightWithSpacing()
        val buttonsHeight = if (actions.isEmpty()) 0f else ImGui.getFrameHeightWithSpacing() + lineHeight * 0.5f
        val blockHeight = lineHeight * (if (hint != null) 2 else 1) + buttonsHeight
        val top = ImGui.getCursorPosY() + ((ImGui.getContentRegionAvailY() - blockHeight) / 2f).coerceAtLeast(0f)
        ImGui.setCursorPosY(top)

        centredDisabled(message)
        hint?.let { centredDisabled(it) }
        if (actions.isEmpty()) return

        ImGui.dummy(0f, lineHeight * 0.5f)
        val spacing = ImGui.getStyle().itemSpacingX
        val labels = actions.map { a -> strings.label(a.labelKey, a.icon?.let { "$it " } ?: "") }
        val widths = labels.map { ImGui.calcTextSize(it.substringBefore("###")).x + ImGui.getStyle().framePaddingX * 2 }
        val total = widths.sum() + spacing * (actions.size - 1)
        ImGui.setCursorPosX(ImGui.getCursorPosX() + ((ImGui.getContentRegionAvailX() - total) / 2f).coerceAtLeast(0f))
        actions.forEachIndexed { i, action ->
            if (i > 0) ImGui.sameLine()
            if (strings.tipped(ImGui.button(labels[i]), action.labelKey)) action.onClick()
        }
    }

    /** The "no project open" state, with New Project and Open Project buttons. */
    fun noProject(strings: StringManager, eventSystem: EventSystem) = render(
        strings, Icons.FOLDER_OPEN, "empty.no_project", "empty.no_project.hint",
        listOf(
            Action("empty.no_project.new", Icons.PLUS) { eventSystem.publish(WindowAction.Show("window.project_wizard")) },
            Action("empty.no_project.open", Icons.FOLDER_OPEN) { eventSystem.publish(ProjectEvent.OpenProjectFileRequested) },
        ),
    )

    /** The "no scene open" state (EDT-51 §2.5); [hintKey] says where to get one in this window. */
    fun noScene(strings: StringManager, hintKey: String = "empty.no_scene.hint", actions: List<Action> = emptyList()) =
        render(strings, Icons.CUBE, "empty.no_scene", hintKey, actions)

    /** "Nothing selected" (EDT-51 §2.5): [messageKey] says what selecting shows here. */
    fun nothingSelected(strings: StringManager, messageKey: String) = render(strings, Icons.MOUSE_POINTER, messageKey)

    /** "No results" for a search, with a Clear search button when [onClear] is given (EDT-51 §2.5). */
    fun noResults(strings: StringManager, onClear: (() -> Unit)? = null) = render(
        strings, Icons.SEARCH, "empty.search", "empty.search.hint",
        onClear?.let { listOf(Action("empty.search.clear", Icons.WINDOW_CLOSE, it)) } ?: emptyList(),
    )

    /** An empty list (EDT-51 §2.5): [messageKey] says what is missing ("No commands yet"). */
    fun nothingYet(strings: StringManager, icon: String, messageKey: String, hintKey: String? = null) =
        render(strings, icon, messageKey, hintKey)

    private fun centredDisabled(text: String) {
        val width = ImGui.calcTextSize(text).x
        ImGui.setCursorPosX(ImGui.getCursorPosX() + ((ImGui.getContentRegionAvailX() - width) / 2f).coerceAtLeast(0f))
        ImGui.textDisabled(text)
    }
}
