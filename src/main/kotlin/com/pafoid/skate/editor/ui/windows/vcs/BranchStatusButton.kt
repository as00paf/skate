package com.pafoid.skate.editor.ui.windows.vcs

import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.events.VcsAction
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.ImGuiIds
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.editor.imgui.theme.ThemeColors
import com.pafoid.skate.editor.vcs.BranchButtonModel
import com.pafoid.skate.editor.vcs.BranchPopupModel
import com.pafoid.skate.editor.vcs.BranchPopupModel.Action
import com.pafoid.skate.editor.vcs.ConflictPrompt
import com.pafoid.skate.editor.vcs.VcsSession
import com.pafoid.skate.editor.vcs.VcsWindows
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiMouseButton
import imgui.type.ImString

/**
 * EDT-79 §2–3: the IntelliJ-style branch button, the last item of the status bar (Q1), and its popup: Update
 * (disabled until remotes exist), Commit…, Changes…, Resolve Conflicts…, History…, New Branch…, then the local
 * branches with Checkout, New Branch from here… and Delete…. It only publishes events ([VcsAction], [WindowAction])
 * and opens the shared [VcsModals]; the labels and enabling rules come from [BranchButtonModel] and [BranchPopupModel].
 */
class BranchStatusButton(
    private val strings: StringManager,
    private val eventSystem: EventSystem,
    private val session: VcsSession,
    private val modals: VcsModals,
    private val hasProject: () -> Boolean,
    private val playing: () -> Boolean,
    private val unsavedScenes: () -> Boolean,
) {
    private val search = ImString(64)
    private var focusSearch = false
    private var appWasUnfocused = false
    private var hadUnsavedScenes = false
    private val conflictPrompt = ConflictPrompt()

    /** Call every frame, before [width] and [render]: follows the project and keeps the count fresh (§6). */
    fun update() {
        if (!hasProject()) return
        session.followProject()
        // The OS window got focus back (files may have changed outside), or the scenes were just saved.
        val unfocused = ImGui.getIO().appFocusLost
        if (appWasUnfocused && !unfocused) session.requestRefresh()
        appWasUnfocused = unfocused
        val unsaved = unsavedScenes()
        if (hadUnsavedScenes && !unsaved) session.requestRefresh()
        hadUnsavedScenes = unsaved
        val state = session.state
        if (state.loaded && !state.busy && conflictPrompt.shouldOpen(state.conflicts)) {
            eventSystem.publish(WindowAction.Show(VcsWindows.CONFLICTS))
        }
    }

    private fun currentLabel(): BranchButtonModel.Label? = BranchButtonModel.label(session.state, hasProject())

    private fun text(label: BranchButtonModel.Label): String =
        "${Icons.CODE_BRANCH} " + strings.getString(label.textKey, *label.args.toTypedArray())

    /** The width the button takes, 0 when hidden, so the status bar can right-align it. */
    fun width(): Float {
        val label = currentLabel() ?: return 0f
        return ImGui.calcTextSize(text(label)).x + ImGui.getStyle().framePaddingX * 2f
    }

    /** Draws the button at the cursor, then its popup. */
    fun render() {
        val label = currentLabel() ?: return
        val color = when (label.tone) {
            BranchButtonModel.Tone.NORMAL -> null
            BranchButtonModel.Tone.DIM -> ThemeColors.textDisabled
            BranchButtonModel.Tone.WARNING -> ThemeColors.warning
            BranchButtonModel.Tone.DANGER -> ThemeColors.danger
        }
        ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
        color?.let { ImGui.pushStyleColor(ImGuiCol.Text, it.x, it.y, it.z, it.w) }
        val clicked = ImGui.smallButton(text(label) + BUTTON_ID)
        color?.let { ImGui.popStyleColor() }
        ImGui.popStyleColor()
        Tooltips.always(strings.getString(label.tooltipKey, *label.tooltipArgs.toTypedArray()))
        val anchorX = ImGui.getItemRectMaxX()
        val anchorY = ImGui.getItemRectMinY()
        val rightClicked = ImGui.isItemClicked(ImGuiMouseButton.Right)
        when {
            clicked && label.click == BranchButtonModel.Click.CONFLICTS -> {
                session.requestRefresh()
                eventSystem.publish(WindowAction.Show(VcsWindows.CONFLICTS))
            }
            clicked || rightClicked -> openPopup()
        }
        ImGui.setNextWindowPos(anchorX, anchorY, ImGuiCond.Appearing, 1f, 1f)
        // Sizes in font heights, so the popup follows the UI scale.
        val em = ImGui.getFontSize()
        ImGui.setNextWindowSizeConstraints(POPUP_WIDTH_EM * em, 0f, POPUP_WIDTH_EM * 1.6f * em, POPUP_MAX_HEIGHT_EM * em)
        if (ImGui.beginPopup(POPUP_ID)) {
            popup()
            ImGui.endPopup()
        }
    }

    private fun openPopup() {
        search.set("")
        focusSearch = true
        session.requestRefresh()
        ImGui.openPopup(POPUP_ID)
    }

    private fun popup() {
        val state = session.state
        val rows = BranchPopupModel.branches(state, search.get())
        if (state.isRepository) {
            if (focusSearch) {
                ImGui.setKeyboardFocusHere()
                focusSearch = false
            }
            ImGui.setNextItemWidth(-1f)
            val enter = ImGui.inputTextWithHint("##vcs_branch_search", "${Icons.SEARCH} " + strings.getString("vcs.popup.search_hint"), search, ImGuiInputTextFlags.EnterReturnsTrue)
            if (enter) BranchPopupModel.enterTarget(rows)?.let { checkout(it) }
            ImGui.separator()
        }
        BranchPopupModel.actions(state, playing()).forEach { entry -> actionRow(entry) }
        if (!state.isRepository) return
        ImGui.separatorText(strings.getString("vcs.popup.local"))
        if (rows.isEmpty()) ImGui.textDisabled(strings.getString("vcs.popup.no_match"))
        rows.forEach { row -> branchRow(row) }
    }

    private fun actionRow(entry: BranchPopupModel.Entry) {
        val (key, icon) = when (entry.action) {
            Action.UPDATE -> "vcs.popup.update" to Icons.ARROW_DOWN
            Action.COMMIT -> "vcs.popup.commit" to Icons.CHECK
            Action.CHANGES -> "vcs.popup.changes" to Icons.LIST
            Action.RESOLVE -> "vcs.popup.resolve" to Icons.WARNING
            Action.HISTORY -> "vcs.popup.history" to Icons.HISTORY
            Action.NEW_BRANCH -> "vcs.popup.new_branch" to Icons.PLUS
            Action.INITIALIZE -> "vcs.popup.initialize" to Icons.CODE_BRANCH
            Action.CLONE -> "vcs.popup.clone" to Icons.CLOUD
        }
        val shortcut = if (entry.action == Action.COMMIT) EditorShortcuts.VCS_COMMIT.display() else null
        val count = if (entry.count > 0) "  (${entry.count})" else ""
        val picked = strings.tipped(
            ImGui.menuItem(ImGuiIds.stable("$icon " + strings.getString(key) + count, key), shortcut, false, entry.enabled), key,
            if (entry.action == Action.COMMIT) EditorShortcuts.VCS_COMMIT else null,
        )
        entry.reasonKey?.let { if (!entry.enabled) Tooltips.always(strings.getString(it)) }
        if (!picked) return
        when (entry.action) {
            Action.COMMIT, Action.CHANGES -> show(VcsWindows.COMMIT)
            Action.RESOLVE -> show(VcsWindows.CONFLICTS)
            Action.HISTORY -> show(VcsWindows.LOG)
            Action.NEW_BRANCH -> modals.newBranch.show(null)
            Action.INITIALIZE -> modals.initialize.open()
            Action.UPDATE, Action.CLONE -> Unit
        }
    }

    private fun branchRow(row: BranchPopupModel.BranchRow) {
        val state = session.state
        val mark = if (row.current) "${Icons.CHECK} " else "    "
        if (!ImGui.beginMenu("$mark${row.name}##vcs_branch_${row.name}")) return
        if (!row.current) {
            val refusal = BranchPopupModel.checkoutRefusal(state, playing(), unsavedScenes())
            if (strings.tipped(ImGui.menuItem(strings.label("vcs.popup.checkout", "${Icons.CODE_BRANCH} "), null, false, refusal == null), "vcs.popup.checkout")) {
                checkout(row.name)
            }
            refusal?.let { Tooltips.always(strings.getString(it)) }
        }
        val newRefusal = if (state.busy) "vcs.busy" else null
        val fromLabel = "${Icons.PLUS} " + strings.getString("vcs.popup.new_from", row.name)
        if (strings.tippedWith(ImGui.menuItem("$fromLabel##vcs_new_from", null, false, newRefusal == null), "vcs.popup.new_from.tooltip")) {
            modals.newBranch.show(row.name)
        }
        if (!row.current) {
            ImGui.separator()
            val refusal = BranchPopupModel.deleteRefusal(state, row.name, playing())
            if (strings.tipped(ImGui.menuItem(strings.label("vcs.popup.delete", "${Icons.TRASH} "), null, false, refusal == null), "vcs.popup.delete")) {
                modals.confirmDelete(row.name)
            }
            refusal?.let { Tooltips.always(strings.getString(it)) }
        }
        ImGui.endMenu()
    }

    private fun checkout(branch: String) {
        eventSystem.publish(VcsAction.SwitchBranch(branch))
        ImGui.closeCurrentPopup()
    }

    private fun show(window: String) {
        session.requestRefresh()
        eventSystem.publish(WindowAction.Show(window))
    }

    private companion object {
        const val POPUP_ID = "##vcs_branch_popup"
        const val BUTTON_ID = "###vcs_branch_button"
        const val POPUP_WIDTH_EM = 22f
        const val POPUP_MAX_HEIGHT_EM = 38f
    }
}
