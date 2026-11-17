package com.pafoid.skate.editor.ui.windows.vcs

import com.pafoid.skate.editor.events.VcsAction
import com.pafoid.skate.editor.events.VcsCommitted
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.DialogFooter
import com.pafoid.skate.editor.imgui.EditorDialog
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.theme.ThemeColors
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.vcs.BranchButtonModel
import com.pafoid.skate.editor.vcs.ChangeEntry
import com.pafoid.skate.editor.vcs.ChangeKind
import com.pafoid.skate.editor.vcs.ChangeSelection
import com.pafoid.skate.editor.vcs.DiffSlot
import com.pafoid.skate.editor.vcs.VcsSession
import com.pafoid.skate.editor.vcs.VcsStatusModel
import com.pafoid.skate.editor.vcs.VcsWindows
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.events.SceneAction
import imgui.ImGui
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString

/**
 * EDT-79 §4.1: the Commit dialog, which is also the "Changes…" view (Q2). It lists what changed since the last commit
 * (grouped, a `.meta` with its asset, new files ticked), shows the focused file's diff, and commits the ticked files
 * with a message (Ctrl+K opens it, Ctrl+Enter in the message commits). It is not modal (Q4) and never closes by itself
 * after a commit (Q3). Every Git action is a [VcsAction]; Discard asks first ([VcsModals.discard]).
 */
class CommitDialog(
    private val engine: Engine,
    private val session: VcsSession,
    private val modals: VcsModals,
    private val hasProject: () -> Boolean,
) : EditorDialog(NAME, engine.stringManager, 900f, 600f) {

    companion object {
        const val NAME = "window.vcs_commit"
    }

    private val strings = engine.stringManager
    private val eventSystem = engine.eventSystem
    private val message = ImString(4096)
    private val selection = ChangeSelection()
    private val diff = DiffSlot()
    private var focusedPath: String? = null
    /** The frame the dialog was last drawn, to notice it was (re)opened. */
    private var lastFrame = -2

    override val icon = Icons.CHECK
    override val relatedWindows = listOf(VcsWindows.LOG, VcsWindows.CONFLICTS)

    init {
        eventSystem.subscribe<VcsCommitted> { message.set("") }
    }

    override fun content() {
        val frame = ImGui.getFrameCount()
        if (frame - lastFrame > 1) session.requestRefresh()
        lastFrame = frame
        val state = session.state
        when {
            !hasProject() -> EmptyState.noProject(strings, eventSystem)
            !state.loaded -> EmptyState.nothingYet(strings, Icons.CODE_BRANCH, "vcs.loading")
            !state.isRepository -> EmptyState.render(
                strings, Icons.CODE_BRANCH, "empty.vcs", "empty.vcs.hint",
                listOf(EmptyState.Action("vcs.initialize", Icons.PLUS) { modals.initialize.open() }),
            )
            else -> changes()
        }
    }

    private fun toolbar() {
        val state = session.state
        ImGui.textUnformatted("${Icons.CODE_BRANCH} ${BranchButtonModel.branchName(state)}")
        ImGui.sameLine()
        ImGui.beginDisabled(state.busy)
        if (strings.tipped(ImGui.smallButton(strings.label("vcs.refresh", "${Icons.ROTATE} ")), "vcs.refresh")) session.refresh()
        ImGui.endDisabled()
        if (state.busy) {
            ImGui.sameLine()
            ImGui.textDisabled(strings.getString("vcs.busy"))
        }
        unsavedNotice()
        if (state.hasConflicts) {
            textColored(ThemeColors.danger, "${Icons.WARNING} " + strings.getString("vcs.commit.conflicts_banner", state.conflicts.size))
            ImGui.sameLine()
            if (strings.tipped(ImGui.smallButton(strings.label("vcs.popup.resolve", "${Icons.WARNING} ")), "vcs.popup.resolve")) {
                eventSystem.publish(WindowAction.Show(VcsWindows.CONFLICTS))
            }
        }
    }

    /** EDT-60 §2 said unsaved scenes are offered for saving first: name them and offer Save all. */
    private fun unsavedNotice() {
        val dirty = engine.sceneManager.openScenes.filter { it.isDirty }
        if (dirty.isEmpty()) return
        textColored(ThemeColors.warning, strings.getString("vcs.commit.unsaved", dirty.joinToString { it.name }))
        ImGui.sameLine()
        if (strings.tipped(ImGui.smallButton(strings.label("vcs.commit.save_all", "${Icons.SAVE} ")), "vcs.commit.save_all")) {
            dirty.forEach { eventSystem.publish(SceneAction.SaveRequested(it)) }
        }
    }

    private fun changes() {
        toolbar()
        val state = session.state
        val entries = state.entries
        selection.sync(entries)
        refreshDiff()
        val bottom = DialogFooter.height() + ImGui.getFrameHeightWithSpacing() * 3.4f + ImGui.getTextLineHeightWithSpacing()
        val width = ImGui.getContentRegionAvailX()
        ImGui.beginChild("vcs_commit_changes", width * 0.4f, -bottom, true)
        if (entries.isEmpty()) {
            EmptyState.nothingYet(strings, Icons.CHECK, "vcs.no_changes")
        } else {
            if (ImGui.checkbox(strings.getString("vcs.select_all", entries.size) + "##vcs_all", selection.allSelected(entries))) {
                selection.toggleAll(entries)
            }
            Tooltips.show(strings, "vcs.select_all.tooltip")
            VcsStatusModel.grouped(entries).forEach { (group, rows) ->
                ImGui.separatorText(strings.getString(group.labelKey, rows.size))
                rows.forEach { changeRow(it) }
            }
        }
        ImGui.endChild()
        ImGui.sameLine()
        ImGui.beginChild("vcs_commit_diff", 0f, -bottom, true, ImGuiWindowFlags.HorizontalScrollbar)
        DiffView.render(strings, diff.diff.takeIf { focusedPath != null })
        ImGui.endChild()
        messageBox(entries)
    }

    /** A working-tree diff goes stale with every read: reload the focused file's diff then. */
    private fun refreshDiff() {
        val path = focusedPath ?: return
        val state = session.state
        if (state.entries.none { it.path == path }) {
            focusedPath = null
            diff.clear()
        } else if (diff.version < state.version && diff.key == "work:$path" && !state.busy) {
            session.showDiff(diff, path)
        }
    }

    private fun changeRow(entry: ChangeEntry) {
        val kind = entry.change.kind
        if (kind != ChangeKind.CONFLICTING) {
            if (ImGui.checkbox("##sel_${entry.path}", selection.isSelected(entry.path))) selection.toggle(entry.path)
            ImGui.sameLine()
        }
        val label = buildString {
            append(VcsStatusModel.letter(kind)).append("  ").append(entry.path)
            if (entry.meta != null) append("  ").append(strings.getString("vcs.with_meta"))
        }
        if (ImGui.selectable("$label##row_${entry.path}", focusedPath == entry.path)) {
            focusedPath = entry.path
            session.showDiff(diff, entry.path)
        }
    }

    private fun messageBox(entries: List<ChangeEntry>) {
        ImGui.textDisabled(strings.getString("vcs.message_hint"))
        ImGui.inputTextMultiline("##vcs_message", message, -1f, ImGui.getFrameHeightWithSpacing() * 2f)
        val ctrlEnter = ImGui.isItemFocused() && ImGui.getIO().keyCtrl && ImGui.isKeyPressed(ImGuiKey.Enter)
        val paths = selection.paths(entries)
        val blocked = blockedReason(paths)
        ImGui.textDisabled(strings.getString("vcs.selected_count", paths.size))
        // Why Commit is grayed out is data, not help: shown even with tooltips off.
        if (blocked != null) {
            ImGui.sameLine()
            ImGui.textDisabled("· " + strings.getString(blocked))
        }
        if (ctrlEnter && blocked == null) commit(paths)
    }

    private fun blockedReason(paths: List<String>): String? {
        val state = session.state
        return VcsStatusModel.commitBlockedReason(message.get(), paths, engine.runtimePlaying, state.busy, state.hasConflicts)
    }

    private fun commit(paths: List<String>) {
        eventSystem.publish(VcsAction.Commit(message.get(), paths))
    }

    override fun footerButtons(): List<FooterButton> {
        val state = session.state
        if (!state.isRepository) return super.footerButtons()
        val paths = selection.paths(state.entries)
        return listOf(
            FooterButton("vcs.discard", FooterButton.Role.SECONDARY, enabled = paths.isNotEmpty() && !state.busy, closes = false) {
                modals.confirmDiscard(paths)
            },
            FooterButton("btn.close", FooterButton.Role.CANCEL),
            FooterButton("vcs.commit", FooterButton.Role.PRIMARY, enabled = blockedReason(paths) == null, closes = false) { commit(paths) },
        )
    }
}
