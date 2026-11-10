package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiHoveredFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiWindowFlags

/** A button of the standard footer (EDT-51 §2.6). [closes] = the dialog or modal closes after [onClick]. */
class FooterButton(
    val labelKey: String,
    val role: Role,
    val enabled: Boolean = true,
    val closes: Boolean = true,
    val onClick: () -> Unit = {},
) {
    enum class Role {
        /** Extra actions (Copy…), leftmost. */
        SECONDARY,
        CANCEL,
        APPLY,
        /** The main action, highlighted, rightmost; Enter runs it. */
        PRIMARY,
        /** A destructive main action ("Delete"): like PRIMARY, in the danger colour. */
        DANGER,
    }

    val isMain: Boolean get() = role == Role.PRIMARY || role == Role.DANGER
}

/**
 * The standard footer of dialogs and modals (EDT-51 §2.6): a separator, then right-aligned buttons in the order
 * [Secondary…] [Cancel] [Apply] [Main]. Esc runs Cancel and Enter runs the main action (unless a field is being edited).
 */
object DialogFooter {
    private const val MIN_BUTTON_WIDTH = 96f

    fun order(buttons: List<FooterButton>): List<FooterButton> = buttons.sortedBy { it.role.ordinal }

    /**
     * The height [render] takes below the content: spacing, separator, spacing and one row of buttons, plus one item
     * spacing of slack so content sized to end above it never scrolls. Two-pane dialogs size their panes with it.
     */
    fun height(frameHeightWithSpacing: Float, itemSpacingY: Float): Float = frameHeightWithSpacing + itemSpacingY * 4f + 1f

    /** [height] with the current ImGui style. */
    fun height(): Float = height(ImGui.getFrameHeightWithSpacing(), ImGui.getStyle().itemSpacingY)

    /** The button Esc runs: Cancel, or else the only button that closes (a lone Close / OK). */
    fun escapeTarget(buttons: List<FooterButton>): FooterButton? =
        buttons.firstOrNull { it.role == FooterButton.Role.CANCEL } ?: buttons.singleOrNull { it.closes }

    /** The button Enter runs: the main action, when enabled. */
    fun enterTarget(buttons: List<FooterButton>): FooterButton? = buttons.lastOrNull { it.isMain && it.enabled }

    /** Draws the footer and returns the button clicked (or chosen by key) this frame, after running its action. */
    fun render(strings: StringManager, buttons: List<FooterButton>): FooterButton? {
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
        val ordered = order(buttons)
        val style = ImGui.getStyle()
        val widths = ordered.map {
            maxOf(MIN_BUTTON_WIDTH, ImGui.calcTextSize(strings.getString(it.labelKey)).x + style.framePaddingX * 2)
        }
        val total = widths.sum() + style.itemSpacingX * (ordered.size - 1).coerceAtLeast(0)
        ImGui.setCursorPosX(ImGui.getCursorPosX() + (ImGui.getContentRegionAvailX() - total).coerceAtLeast(0f))

        var clicked: FooterButton? = null
        ordered.forEachIndexed { i, button ->
            if (i > 0) ImGui.sameLine()
            if (drawButton(strings, button, widths[i])) clicked = button
        }

        if (clicked == null && ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows)) {
            clicked = when {
                ImGui.isKeyPressed(ImGuiKey.Escape) -> escapeTarget(buttons)
                ImGui.isKeyPressed(ImGuiKey.Enter) && !ImGui.isAnyItemActive() -> enterTarget(buttons)
                else -> null
            }
        }
        clicked?.takeIf { it.enabled }?.onClick?.invoke()
        return clicked?.takeIf { it.enabled }
    }

    private fun drawButton(strings: StringManager, button: FooterButton, width: Float): Boolean {
        val colors = when (button.role) {
            FooterButton.Role.PRIMARY -> ImGui.getStyle().getColor(ImGuiCol.ButtonActive)
            FooterButton.Role.DANGER -> DANGER
            else -> null
        }
        colors?.let { ImGui.pushStyleColor(ImGuiCol.Button, it.x, it.y, it.z, it.w) }
        ImGui.beginDisabled(!button.enabled)
        val pressed = ImGui.button(strings.label(button.labelKey), width, 0f)
        if (strings.hasString(Tooltips.keyFor(button.labelKey))) Tooltips.text(strings.getString(Tooltips.keyFor(button.labelKey)))
        ImGui.endDisabled()
        colors?.let { ImGui.popStyleColor() }
        return pressed
    }

    private val DANGER = imgui.ImVec4(0.70f, 0.16f, 0.18f, 1f)
}

/**
 * A real modal (EDT-51 §2.1): dims and blocks the editor, centred, with the standard footer. Subclasses give the
 * [content] and the footer [buttons]. Open it with [open], or keep [wantsOpen] true while it must show
 * (a queue of errors). Call [render] every frame, after the windows.
 */
abstract class EditorModal(
    protected val strings: StringManager,
    private val icon: String? = null,
    private val minWidth: Float = 360f,
) {
    private var requested = false

    /** The title's string key; also the modal's stable ImGui ID. */
    protected abstract val titleKey: String

    protected abstract fun content()

    protected abstract fun buttons(): List<FooterButton>

    /**
     * The registered window this modal stands for (a modal host such as New Project), whose help the modal's `?`
     * opens (EDT-67). Null for plain confirmations and errors, which have no help.
     */
    protected open val helpWindow: EditorWindow? = null

    /** True while the modal must stay up whether or not [open] was called. */
    protected open val wantsOpen: Boolean get() = false

    fun open() {
        requested = true
    }

    fun render() {
        val key = titleKey
        val title = ImGuiIds.stable((icon?.let { "$it " } ?: "") + strings.getString(key), key)
        if ((requested || wantsOpen) && !ImGui.isPopupOpen(title)) ImGui.openPopup(title)
        requested = false
        val viewport = ImGui.getMainViewport()
        ImGui.setNextWindowPos(
            viewport.workPosX + viewport.workSizeX / 2f, viewport.workPosY + viewport.workSizeY / 2f,
            ImGuiCond.Appearing, 0.5f, 0.5f,
        )
        ImGui.setNextWindowSizeConstraints(minWidth, 0f, 720f, 600f)
        if (!ImGui.beginPopupModal(title, null, ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoSavedSettings)) return
        helpWindow?.let {
            WindowHeader.helpRow(strings, it)
            it.help.render(strings)
        }
        content()
        val clicked = DialogFooter.render(strings, buttons())
        if (clicked?.closes == true) ImGui.closeCurrentPopup()
        ImGui.endPopup()
    }
}
