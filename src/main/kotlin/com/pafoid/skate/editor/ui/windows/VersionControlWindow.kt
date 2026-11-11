package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.ui.windows.vcs.DiffView
import com.pafoid.skate.editor.ui.windows.vcs.VcsModals
import com.pafoid.skate.editor.vcs.CommitGraph
import com.pafoid.skate.editor.vcs.DiffSlot
import com.pafoid.skate.editor.vcs.GitLogFilter
import com.pafoid.skate.editor.vcs.VcsCommit
import com.pafoid.skate.editor.vcs.VcsSession
import com.pafoid.skate.editor.vcs.VcsWindows
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiSelectableFlags
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import java.text.DateFormat
import java.util.Date

/**
 * EDT-79 Q7: the Version Control window is the project's Git log, like IntelliJ's Git tool window. It lists the
 * commits of every local branch with a graph column (lanes, branch tips as `[name]`), message, author and date;
 * selecting a commit lists its files, and a file shows its diff. A branch combo and a text field filter the list.
 * Everyday Git work (changes, commit, branches, conflicts) lives in the status-bar branch button and its dialogs.
 * Reads run in the background through [VcsSession] and refresh when the window gains focus.
 */
class VersionControlWindow(
    private val engine: Engine,
    private val projectManager: ProjectManager,
    private val session: VcsSession,
    private val modals: VcsModals,
) : EditorWindow(NAME) {

    companion object {
        const val NAME = "window.version_control"
        private const val LANE_WIDTH = 14f
    }

    private val strings = engine.stringManager
    private val eventSystem = engine.eventSystem
    private val textFilter = ImString(128)
    private var branchFilter: String? = null
    private var selectedCommit: String? = null
    private var selectedFile: String? = null
    private val diff = DiffSlot()
    private var wasFocused = false

    /** The layout is only recomputed when the commits or the filters change. */
    private var cacheKey: Any? = null
    private var shown: List<VcsCommit> = emptyList()
    private var rows: List<CommitGraph.Row> = emptyList()

    override val icon = Icons.CODE_BRANCH
    override val relatedWindows = listOf(VcsWindows.COMMIT, "window.project", "window.shell")

    override fun imgui() {
        if (!beginWindow(strings)) {
            wasFocused = false
            ImGui.end()
            return
        }
        val focused = ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows)
        if (focused && !wasFocused) session.requestRefresh()
        wasFocused = focused

        val state = session.state
        header(strings) { if (state.isRepository) toolbar() }
        when {
            projectManager.currentProject == null -> EmptyState.noProject(strings, eventSystem)
            !state.loaded -> EmptyState.nothingYet(strings, Icons.CODE_BRANCH, "vcs.loading")
            !state.isRepository -> EmptyState.render(
                strings, Icons.CODE_BRANCH, "empty.vcs", "empty.vcs.hint",
                listOf(EmptyState.Action("vcs.initialize", Icons.PLUS) { modals.initialize.open() }),
            )
            state.history.isEmpty() -> EmptyState.nothingYet(strings, Icons.HISTORY, "vcs.no_history")
            else -> log()
        }
        ImGui.end()
    }

    private fun toolbar() {
        val state = session.state
        ImGui.setNextItemWidth(160f)
        val allLabel = strings.getString("vcs.log.all_branches")
        if (ImGui.beginCombo(strings.label("vcs.log.branch"), branchFilter ?: allLabel)) {
            if (ImGui.selectable("$allLabel##vcs_log_all", branchFilter == null)) branchFilter = null
            state.branches?.local.orEmpty().forEach { b ->
                if (ImGui.selectable("$b##vcs_log_branch_$b", branchFilter == b)) branchFilter = b
            }
            ImGui.endCombo()
        }
        Tooltips.show(strings, "vcs.log.branch.tooltip")
        if (branchFilter != null && branchFilter !in state.branches?.local.orEmpty()) branchFilter = null
        ImGui.sameLine()
        ImGui.setNextItemWidth(220f)
        ImGui.inputTextWithHint("##vcs_log_text", "${Icons.SEARCH} " + strings.getString("vcs.log.filter_hint"), textFilter)
        Tooltips.show(strings, "vcs.log.filter.tooltip")
        ImGui.sameLine()
        ImGui.beginDisabled(state.busy)
        if (strings.tipped(ImGui.button(strings.label("vcs.refresh", "${Icons.ROTATE} ")), "vcs.refresh")) session.refresh()
        ImGui.endDisabled()
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("vcs.popup.commit", "${Icons.CHECK} ")), "vcs.popup.commit")) {
            eventSystem.publish(WindowAction.Show(VcsWindows.COMMIT))
        }
        if (state.busy) {
            ImGui.sameLine()
            ImGui.textDisabled(strings.getString("vcs.busy"))
        }
    }

    private fun updateCache() {
        val state = session.state
        val key = Triple(state.version, branchFilter, textFilter.get())
        if (key == cacheKey) return
        cacheKey = key
        // The graph is laid out on the branch-filtered list, so lanes stay connected. A text filter leaves gaps
        // between the rows it keeps, so the graph is hidden while one is typed.
        val onBranch = GitLogFilter.apply(state.history, state.branches, branchFilter, "")
        val layout = CommitGraph.layout(onBranch)
        val matching = GitLogFilter.apply(onBranch, state.branches, null, textFilter.get()).map { it.id }.toSet()
        val keep = onBranch.indices.filter { onBranch[it].id in matching }
        shown = keep.map { onBranch[it] }
        rows = if (textFilter.get().isBlank()) layout else emptyList()
        if (selectedCommit != null && shown.none { it.id == selectedCommit }) {
            selectedCommit = null
            selectedFile = null
            diff.clear()
        }
    }

    private fun log() {
        updateCache()
        val width = ImGui.getContentRegionAvailX()
        ImGui.beginChild("vcs_log_list", width * 0.62f, 0f, false)
        commitTable()
        ImGui.endChild()
        ImGui.sameLine()
        ImGui.beginChild("vcs_log_detail", 0f, 0f, true)
        commitDetail()
        ImGui.endChild()
    }

    private fun commitTable() {
        if (shown.isEmpty()) {
            EmptyState.nothingYet(strings, Icons.SEARCH, "vcs.log.no_match")
            return
        }
        val state = session.state
        val labels = GitLogFilter.labels(state.branches)
        val current = state.branches?.current
        val graphWidth = ((rows.maxOfOrNull { it.width } ?: 1).coerceAtMost(12)) * LANE_WIDTH + 6f
        val flags = ImGuiTableFlags.Resizable or ImGuiTableFlags.RowBg or ImGuiTableFlags.ScrollY or
            ImGuiTableFlags.BordersInnerV or ImGuiTableFlags.SizingFixedFit
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, ImGui.getStyle().cellPaddingX, 0f)
        if (ImGui.beginTable("##vcs_log_table", 4, flags)) {
            ImGui.tableSetupScrollFreeze(0, 1)
            ImGui.tableSetupColumn(strings.label("vcs.log.column.graph"), ImGuiTableColumnFlags.WidthFixed, graphWidth)
            ImGui.tableSetupColumn(strings.label("vcs.log.column.message"), ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn(strings.label("vcs.log.column.author"), ImGuiTableColumnFlags.WidthFixed, 110f)
            ImGui.tableSetupColumn(strings.label("vcs.log.column.date"), ImGuiTableColumnFlags.WidthFixed, 120f)
            ImGui.tableHeadersRow()
            val rowHeight = ImGui.getFrameHeight()
            val dates = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            shown.forEachIndexed { i, commit ->
                ImGui.tableNextRow(0, rowHeight)
                ImGui.tableSetColumnIndex(0)
                val x = ImGui.getCursorScreenPosX()
                val y = ImGui.getCursorScreenPosY()
                val selected = selectedCommit == commit.id
                if (ImGui.selectable("##c_${commit.id}", selected, ImGuiSelectableFlags.SpanAllColumns or ImGuiSelectableFlags.AllowOverlap, 0f, rowHeight)) {
                    selectedCommit = commit.id
                    selectedFile = null
                    diff.clear()
                }
                rows.getOrNull(i)?.let { drawGraph(it, x, y, rowHeight, labels[commit.id].orEmpty().contains(current)) }
                ImGui.tableSetColumnIndex(1)
                ImGui.alignTextToFramePadding()
                labels[commit.id]?.forEach { name ->
                    val color = if (name == current) floatArrayOf(1f, 0.8f, 0.35f, 1f) else floatArrayOf(0.45f, 0.75f, 1f, 1f)
                    ImGui.textColored(color[0], color[1], color[2], color[3], "[$name]")
                    ImGui.sameLine()
                }
                ImGui.textUnformatted(commit.message.lineSequence().first())
                if (ImGui.isItemHovered()) Tooltips.always("${commit.shortId}\n${commit.message}")
                ImGui.tableSetColumnIndex(2)
                ImGui.alignTextToFramePadding()
                ImGui.textDisabled(commit.author)
                ImGui.tableSetColumnIndex(3)
                ImGui.alignTextToFramePadding()
                ImGui.textDisabled(dates.format(Date(commit.timeMs)))
            }
            ImGui.endTable()
        }
        ImGui.popStyleVar()
    }

    /** One row of the graph: straight lines for passing lanes, lines into and out of the dot, then the dot. */
    private fun drawGraph(row: CommitGraph.Row, x: Float, top: Float, height: Float, onCurrent: Boolean) {
        val draw = ImGui.getWindowDrawList()
        fun laneX(lane: Int) = x + LANE_WIDTH * (lane.coerceAtMost(11) + 0.5f)
        val mid = top + height / 2f
        val bottom = top + height
        val dotX = laneX(row.lane)
        row.through.forEach { draw.addLine(laneX(it), top, laneX(it), bottom, laneColor(it), 1.5f) }
        row.up.forEach { draw.addLine(laneX(it), top, dotX, mid, laneColor(it), 1.5f) }
        row.down.forEach { draw.addLine(dotX, mid, laneX(it), bottom, laneColor(it), 1.5f) }
        val dot = if (onCurrent) ImGui.getColorU32(1f, 0.8f, 0.35f, 1f) else laneColor(row.lane)
        draw.addCircleFilled(dotX, mid, 4f, dot)
    }

    private fun laneColor(lane: Int): Int {
        val c = LANE_COLORS[lane % LANE_COLORS.size]
        return ImGui.getColorU32(c[0], c[1], c[2], 1f)
    }

    private val LANE_COLORS = listOf(
        floatArrayOf(0.45f, 0.75f, 1f), floatArrayOf(0.55f, 0.85f, 0.5f), floatArrayOf(0.85f, 0.55f, 0.9f),
        floatArrayOf(0.95f, 0.6f, 0.4f), floatArrayOf(0.4f, 0.85f, 0.8f), floatArrayOf(0.9f, 0.85f, 0.45f),
    )

    private fun commitDetail() {
        val commit = shown.firstOrNull { it.id == selectedCommit }
        if (commit == null) {
            EmptyState.nothingSelected(strings, "vcs.log.select_commit")
            return
        }
        val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        ImGui.textDisabled("${commit.shortId} · ${commit.author} · ${dates.format(Date(commit.timeMs))}")
        ImGui.pushTextWrapPos(0f)
        ImGui.textUnformatted(commit.message)
        ImGui.popTextWrapPos()
        ImGui.separatorText(strings.getString("vcs.log.files", commit.files.size))
        val filesHeight = (ImGui.getTextLineHeightWithSpacing() * (commit.files.size.coerceIn(1, 8) + 0.5f))
        ImGui.beginChild("vcs_log_files", 0f, filesHeight, false)
        commit.files.forEach { f ->
            if (ImGui.selectable("$f##cf_${commit.id}_$f", selectedFile == f)) {
                selectedFile = f
                session.showCommitDiff(diff, commit.id, f)
            }
        }
        ImGui.endChild()
        ImGui.separator()
        ImGui.beginChild("vcs_log_diff", 0f, 0f, false, ImGuiWindowFlags.HorizontalScrollbar)
        DiffView.render(strings, diff.diff.takeIf { selectedFile != null })
        ImGui.endChild()
    }
}
