package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.VcsAction
import com.pafoid.skate.editor.imgui.EditorModal
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.vcs.ChangeEntry
import com.pafoid.skate.editor.vcs.ChangeKind
import com.pafoid.skate.editor.vcs.DiffLine
import com.pafoid.skate.editor.vcs.FileDiff
import com.pafoid.skate.editor.vcs.Resolution
import com.pafoid.skate.editor.vcs.VcsSession
import com.pafoid.skate.editor.vcs.VcsStatusModel
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import java.text.DateFormat
import java.util.Date

/**
 * EDT-60 phase 1: the project's local Git repository. Initialize, see what changed, view diffs, commit the selected
 * files (a `.meta` goes with its asset), browse history, create, switch and delete branches, discard changes and
 * resolve conflicted files as a whole (Keep mine / Take theirs). Every Git action is a command ([VcsAction]); reads
 * run in the background through [VcsSession] and refresh when the window gains focus.
 */
class VersionControlWindow(
    private val engine: Engine,
    private val projectManager: ProjectManager,
    private val session: VcsSession,
) : EditorWindow(NAME) {

    companion object {
        const val NAME = "window.version_control"
    }

    private val strings = engine.stringManager
    private val eventSystem = engine.eventSystem
    private val message = ImString(4096)
    private val branchName = ImString(128)
    private val selected = linkedSetOf<String>()
    private var focusedPath: String? = null
    private var selectedCommit: String? = null
    private var wasFocused = false
    private var knownPaths = emptySet<String>()

    /** The newest commit when Commit was pressed: the message is cleared once a newer one lands, kept on failure. */
    private var commitPendingSince: String? = null
    private var commitPending = false

    override val icon = Icons.CODE_BRANCH
    override val relatedWindows = listOf("window.project", "window.shell")

    private val discardModal = object : EditorModal(strings, Icons.TRASH) {
        var paths: List<String> = emptyList()
        override val titleKey = "vcs.discard.title"
        override fun content() {
            ImGui.textWrapped(strings.getString("vcs.discard.message", paths.size))
            paths.take(12).forEach { ImGui.bulletText(it) }
            if (paths.size > 12) ImGui.textDisabled(strings.getString("vcs.discard.more", paths.size - 12))
        }
        override fun buttons() = listOf(
            FooterButton("btn.cancel", FooterButton.Role.CANCEL),
            FooterButton("vcs.discard.confirm", FooterButton.Role.DANGER) { eventSystem.publish(VcsAction.Discard(paths)) },
        )
    }

    override fun imgui() {
        if (projectManager.currentProject != null) session.followProject()
        if (!beginWindow(strings)) {
            wasFocused = false
            ImGui.end()
            return
        }
        val focused = ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows)
        if (focused && !wasFocused) session.refresh()
        wasFocused = focused

        val state = session.state
        header(strings) { if (state.isRepository) toolbar() }
        when {
            projectManager.currentProject == null -> EmptyState.noProject(strings, eventSystem)
            !state.loaded -> EmptyState.nothingYet(strings, Icons.CODE_BRANCH, "vcs.loading")
            !state.isRepository -> {
                errorLine()
                EmptyState.render(
                    strings, Icons.CODE_BRANCH, "empty.vcs", "empty.vcs.hint",
                    listOf(EmptyState.Action("vcs.initialize", Icons.PLUS) {
                        eventSystem.publish(VcsAction.Initialize(strings.getString("vcs.first_commit_message")))
                    }),
                )
            }
            else -> {
                errorLine()
                if (ImGui.beginTabBar("vcs_tabs")) {
                    if (ImGui.beginTabItem(strings.label("vcs.tab.changes"))) {
                        changesTab()
                        ImGui.endTabItem()
                    }
                    if (ImGui.beginTabItem(strings.label("vcs.tab.history"))) {
                        historyTab()
                        ImGui.endTabItem()
                    }
                    ImGui.endTabBar()
                }
            }
        }
        ImGui.end()
        discardModal.render()
    }

    private fun toolbar() {
        val state = session.state
        val current = state.branches?.current ?: "?"
        ImGui.setNextItemWidth(160f)
        if (ImGui.beginCombo(strings.label("vcs.branch"), "${Icons.CODE_BRANCH} $current")) {
            state.branches?.local.orEmpty().forEach { branch ->
                if (ImGui.selectable(branch, branch == current) && branch != current) {
                    eventSystem.publish(VcsAction.SwitchBranch(branch))
                }
                if (branch != current && ImGui.beginPopupContextItem("vcs_branch_$branch")) {
                    if (strings.tipped(ImGui.menuItem(strings.label("vcs.branch.delete", "${Icons.TRASH} ")), "vcs.branch.delete")) {
                        eventSystem.publish(VcsAction.DeleteBranch(branch))
                    }
                    ImGui.endPopup()
                }
            }
            ImGui.endCombo()
        }
        Tooltips.show(strings, "vcs.branch.tooltip")
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("vcs.branch.new", "${Icons.PLUS} ")), "vcs.branch.new")) {
            branchName.set("")
            ImGui.openPopup("vcs_new_branch")
        }
        if (ImGui.beginPopup("vcs_new_branch")) {
            ImGui.setNextItemWidth(200f)
            val enter = ImGui.inputTextWithHint("##vcs_branch_name", strings.getString("vcs.branch.name_hint"), branchName, ImGuiInputTextFlags.EnterReturnsTrue)
            ImGui.sameLine()
            val create = strings.tipped(ImGui.button(strings.label("vcs.branch.create")), "vcs.branch.create")
            if ((enter || create) && branchName.get().isNotBlank()) {
                eventSystem.publish(VcsAction.CreateBranch(branchName.get().trim()))
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
        ImGui.sameLine()
        ImGui.beginDisabled(state.busy)
        if (strings.tipped(ImGui.button(strings.label("vcs.refresh", "${Icons.ROTATE} ")), "vcs.refresh")) session.refresh()
        ImGui.endDisabled()
        if (state.busy) {
            ImGui.sameLine()
            ImGui.textDisabled(strings.getString("vcs.busy"))
        }
    }

    private fun errorLine() {
        val error = session.state.error ?: return
        ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.45f, 0.4f, 1f)
        ImGui.textWrapped(strings.getString(error.messageKey, *error.args))
        ImGui.popStyleColor()
        ImGui.sameLine()
        if (strings.tipped(ImGui.smallButton(strings.label("vcs.error.dismiss")), "vcs.error.dismiss")) session.clearError()
    }

    private fun changesTab() {
        val state = session.state
        val entries = state.entries
        syncSelection(entries)
        val footer = ImGui.getFrameHeightWithSpacing() * 3.2f + ImGui.getTextLineHeightWithSpacing()
        val width = ImGui.getContentRegionAvailX()
        ImGui.beginChild("vcs_changes", width * 0.4f, -footer, true)
        if (entries.isEmpty()) {
            EmptyState.nothingYet(strings, Icons.CHECK, "vcs.no_changes")
        } else {
            val all = entries.all { it.path in selected }
            if (ImGui.checkbox(strings.label("vcs.select_all"), all)) {
                if (all) selected.clear() else selected.addAll(entries.map { it.path })
            }
            VcsStatusModel.grouped(entries).forEach { (group, rows) ->
                ImGui.separatorText(strings.getString(group.labelKey, rows.size))
                rows.forEach { changeRow(it) }
            }
        }
        ImGui.endChild()
        ImGui.sameLine()
        ImGui.beginChild("vcs_diff", 0f, -footer, true, ImGuiWindowFlags.HorizontalScrollbar)
        diffView(state.diff.takeIf { state.diffCommit == null })
        ImGui.endChild()
        commitBar(entries)
    }

    private fun syncSelection(entries: List<ChangeEntry>) {
        val paths = entries.map { it.path }.toSet()
        // New changes start selected, like most Git clients; a file that is no longer changed drops out.
        (paths - knownPaths).forEach { p -> if (entries.first { it.path == p }.change.kind != ChangeKind.CONFLICTING) selected += p }
        selected.retainAll(paths)
        knownPaths = paths
    }

    private fun changeRow(entry: ChangeEntry) {
        val kind = entry.change.kind
        if (kind != ChangeKind.CONFLICTING) {
            val on = entry.path in selected
            if (ImGui.checkbox("##sel_${entry.path}", on)) if (on) selected -= entry.path else selected += entry.path
            ImGui.sameLine()
        }
        val label = buildString {
            append(VcsStatusModel.letter(kind)).append("  ").append(entry.path)
            if (entry.meta != null) append("  ").append(strings.getString("vcs.with_meta"))
        }
        if (ImGui.selectable("$label##row_${entry.path}", focusedPath == entry.path)) {
            focusedPath = entry.path
            session.showDiff(entry.path)
        }
        if (kind == ChangeKind.CONFLICTING) {
            ImGui.indent()
            if (strings.tipped(ImGui.smallButton(strings.label("vcs.resolve.mine") + "##mine_${entry.path}"), "vcs.resolve.mine")) {
                eventSystem.publish(VcsAction.Resolve(entry.path, Resolution.MINE))
            }
            ImGui.sameLine()
            if (strings.tipped(ImGui.smallButton(strings.label("vcs.resolve.theirs") + "##theirs_${entry.path}"), "vcs.resolve.theirs")) {
                eventSystem.publish(VcsAction.Resolve(entry.path, Resolution.THEIRS))
            }
            ImGui.unindent()
        }
    }

    private fun commitBar(entries: List<ChangeEntry>) {
        val state = session.state
        val paths = VcsStatusModel.pathsFor(entries, selected)
        ImGui.textDisabled(strings.getString("vcs.message_hint"))
        ImGui.inputTextMultiline("##vcs_message", message, -1f, ImGui.getFrameHeightWithSpacing() * 1.2f)
        if (commitPending && !state.busy) {
            if (state.error == null && state.history.firstOrNull()?.id != commitPendingSince) message.set("")
            commitPending = false
        }
        val blocked = VcsStatusModel.commitBlockedReason(message.get(), paths, engine.runtimePlaying, state.busy, state.hasConflicts)
        ImGui.beginDisabled(blocked != null)
        if (strings.tipped(ImGui.button(strings.label("vcs.commit", "${Icons.CHECK} ")), "vcs.commit")) {
            commitPendingSince = state.history.firstOrNull()?.id
            commitPending = true
            eventSystem.publish(VcsAction.Commit(message.get(), paths))
        }
        ImGui.endDisabled()
        // Why Commit is grayed out is data, not help: shown even with tooltips off.
        if (blocked != null) Tooltips.always(strings.getString(blocked))
        ImGui.sameLine()
        ImGui.textDisabled(strings.getString("vcs.selected_count", paths.size))
        ImGui.sameLine()
        ImGui.beginDisabled(paths.isEmpty() || state.busy)
        if (strings.tipped(ImGui.button(strings.label("vcs.discard", "${Icons.UNDO} ")), "vcs.discard")) {
            discardModal.paths = paths
            discardModal.open()
        }
        ImGui.endDisabled()
    }

    private fun historyTab() {
        val state = session.state
        val width = ImGui.getContentRegionAvailX()
        ImGui.beginChild("vcs_history", width * 0.4f, 0f, true)
        if (state.history.isEmpty()) EmptyState.nothingYet(strings, Icons.HISTORY, "vcs.no_history")
        val dates = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        state.history.forEach { commit ->
            val title = commit.message.lineSequence().first()
            if (ImGui.selectable("${commit.shortId}  $title##c_${commit.id}", selectedCommit == commit.id)) {
                selectedCommit = commit.id
            }
            if (selectedCommit == commit.id) {
                ImGui.indent()
                ImGui.textDisabled("${commit.author} · ${dates.format(Date(commit.timeMs))}")
                commit.files.forEach { f ->
                    if (ImGui.selectable("$f##cf_${commit.id}_$f", state.diffCommit == commit.id && state.diff?.path == f)) {
                        session.showCommitDiff(commit.id, f)
                    }
                }
                ImGui.unindent()
            }
        }
        ImGui.endChild()
        ImGui.sameLine()
        ImGui.beginChild("vcs_history_diff", 0f, 0f, true, ImGuiWindowFlags.HorizontalScrollbar)
        diffView(state.diff.takeIf { state.diffCommit != null && state.diffCommit == selectedCommit })
        ImGui.endChild()
    }

    private fun diffView(diff: FileDiff?) {
        when (diff) {
            null -> EmptyState.nothingSelected(strings, "vcs.diff.none")
            is FileDiff.Binary -> {
                ImGui.textUnformatted(diff.path)
                ImGui.textDisabled(strings.getString("vcs.diff.binary", size(diff.oldSize), size(diff.newSize)))
            }
            is FileDiff.Text -> {
                ImGui.textUnformatted(diff.path)
                ImGui.separator()
                if (diff.lines.isEmpty()) ImGui.textDisabled(strings.getString("vcs.diff.identical"))
                diff.lines.forEach { line ->
                    val (prefix, color) = when (line.kind) {
                        DiffLine.Kind.HUNK -> "" to floatArrayOf(0.45f, 0.7f, 1f, 1f)
                        DiffLine.Kind.ADDED -> "+ " to floatArrayOf(0.45f, 0.9f, 0.45f, 1f)
                        DiffLine.Kind.REMOVED -> "- " to floatArrayOf(1f, 0.45f, 0.45f, 1f)
                        DiffLine.Kind.CONTEXT -> "  " to null
                    }
                    if (color != null) ImGui.pushStyleColor(ImGuiCol.Text, color[0], color[1], color[2], color[3])
                    ImGui.textUnformatted(prefix + line.text)
                    if (color != null) ImGui.popStyleColor()
                }
            }
        }
    }

    private fun size(bytes: Long?): String = when {
        bytes == null -> "-"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
