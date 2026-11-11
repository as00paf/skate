package com.pafoid.skate.editor.ui.windows.vcs

import com.pafoid.skate.editor.events.VcsAction
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.DialogFooter
import com.pafoid.skate.editor.imgui.EditorDialog
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.vcs.BranchButtonModel
import com.pafoid.skate.editor.vcs.DiffSlot
import com.pafoid.skate.editor.vcs.Resolution
import com.pafoid.skate.editor.vcs.VcsSession
import com.pafoid.skate.editor.vcs.VcsWindows
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags

/**
 * EDT-79 §4.3: resolve conflicted files as a whole (EDT-60's human decision): tick files, then Keep mine or Take
 * theirs. The focused file shows our version against theirs. It opens by itself when a refresh first sees conflicts
 * ([com.pafoid.skate.editor.vcs.ConflictPrompt], driven by the branch button), is not modal (Q4), and once the last
 * conflict is gone offers to open the Commit dialog for the merge commit.
 */
class ConflictsDialog(
    private val engine: Engine,
    private val session: VcsSession,
) : EditorDialog(NAME, engine.stringManager, 820f, 520f) {

    companion object {
        const val NAME = "window.vcs_conflicts"
    }

    private val strings = engine.stringManager
    private val eventSystem = engine.eventSystem
    private val ticked = linkedSetOf<String>()
    private val diff = DiffSlot()
    private var focusedPath: String? = null

    override val icon = Icons.QUESTION_CIRCLE
    override val relatedWindows = listOf(VcsWindows.COMMIT, VcsWindows.LOG)

    override fun content() {
        val state = session.state
        val conflicts = state.conflicts
        ticked.retainAll(conflicts.toSet())
        if (focusedPath != null && focusedPath !in conflicts) {
            focusedPath = null
            diff.clear()
        }
        if (conflicts.isEmpty()) {
            EmptyState.render(
                strings, Icons.CHECK, "vcs.conflicts.none", "vcs.conflicts.none.hint",
                listOf(EmptyState.Action("vcs.conflicts.open_commit", Icons.CHECK) { eventSystem.publish(WindowAction.Show(VcsWindows.COMMIT)) }),
            )
            return
        }
        ImGui.textWrapped(strings.getString("vcs.conflicts.message", conflicts.size))
        val bottom = DialogFooter.height()
        val width = ImGui.getContentRegionAvailX()
        ImGui.beginChild("vcs_conflict_files", width * 0.38f, -bottom, true)
        val all = conflicts.all { it in ticked }
        if (ImGui.checkbox(strings.getString("vcs.select_all", conflicts.size) + "##vcs_conflicts_all", all)) {
            if (all) ticked.clear() else ticked.addAll(conflicts)
        }
        ImGui.separator()
        conflicts.forEach { path ->
            if (ImGui.checkbox("##tick_$path", path in ticked)) if (!ticked.remove(path)) ticked += path
            ImGui.sameLine()
            if (ImGui.selectable("! $path##conflict_$path", focusedPath == path)) {
                focusedPath = path
                session.showConflictDiff(diff, path)
            }
        }
        ImGui.endChild()
        ImGui.sameLine()
        ImGui.beginChild("vcs_conflict_diff", 0f, -bottom, true, ImGuiWindowFlags.HorizontalScrollbar)
        val sides = strings.getString("vcs.conflicts.sides", BranchButtonModel.branchName(state))
        DiffView.render(strings, diff.diff.takeIf { focusedPath != null }, "vcs.conflicts.pick", sides)
        ImGui.endChild()
    }

    /** The files Keep mine / Take theirs act on: the ticked ones, else the focused one. */
    private fun targets(): List<String> = ticked.toList().ifEmpty { listOfNotNull(focusedPath) }

    override fun footerButtons(): List<FooterButton> {
        val state = session.state
        if (state.conflicts.isEmpty()) return super.footerButtons()
        val targets = targets()
        val enabled = targets.isNotEmpty() && !state.busy
        return listOf(
            FooterButton("vcs.resolve.mine", FooterButton.Role.SECONDARY, enabled, closes = false) {
                eventSystem.publish(VcsAction.Resolve(targets, Resolution.MINE))
            },
            FooterButton("vcs.resolve.theirs", FooterButton.Role.SECONDARY, enabled, closes = false) {
                eventSystem.publish(VcsAction.Resolve(targets, Resolution.THEIRS))
            },
            FooterButton("btn.close", FooterButton.Role.CANCEL),
        )
    }
}
