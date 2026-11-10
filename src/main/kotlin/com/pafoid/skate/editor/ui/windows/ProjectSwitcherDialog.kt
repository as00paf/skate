package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectRequested
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.EditorModal
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.project.RecentProjectDisplayInfo
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseCursor
import java.io.File

class ProjectSwitcherDialog(
    engine: Engine,
    private val settingsManager: EditorSettingsManager,
) : EditorWindow("window.project_switcher") {

    override val icon: String = Icons.FOLDER_OPEN

    private val stringManager = engine.stringManager
    private val eventSystem = engine.eventSystem

    private fun renderRecentProjectItem(project: RecentProjectDisplayInfo) {
        ImGui.pushID(project.path)

        val isClickable = project.exists

        if (!isClickable) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
        }

        ImGui.textColored(
            if (isClickable) 0.3f else 0.5f,
            if (isClickable) 0.6f else 0.5f,
            if (isClickable) 0.9f else 0.5f,
            1f,
            project.name
        )

        ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, project.path)

        ImGui.textColored(
            0.4f, 0.4f, 0.4f, 1f,
            stringManager.getString("lbl.switch_project.last_opened").format(project.getLastOpenedString(stringManager))
        )

        if (!isClickable) {
            ImGui.popStyleColor()
            MImGui.errorText(stringManager.getString("lbl.switch_project.not_found"))
        } else {
            if (ImGui.isItemHovered()) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand)
            }

            if (ImGui.isItemClicked()) {
                eventSystem.publish(OpenProjectRequested(File(project.path).absolutePath))
            }
        }

        ImGui.separator()
        ImGui.popID()
    }

    private fun getRecentProjectsDisplayInfo(): List<RecentProjectDisplayInfo> {
        return settingsManager.recentProjects.map { recent ->
            val exists = File(recent.projectPath).exists()
            RecentProjectDisplayInfo(
                name = recent.name,
                path = recent.projectPath,
                lastOpened = recent.lastOpenedDate,
                exists = exists
            )
        }
    }

    override fun imgui() = modal.render()

    /**
     * A real modal (EDT-51 §3): dims and blocks the editor, Esc = Cancel. The window stays registered so View and
     * `WindowAction.Show/Hide("window.project_switcher")` keep working; its open state drives the modal.
     */
    private val modal = object : EditorModal(stringManager, Icons.FOLDER_OPEN, minWidth = 500f) {
        override val titleKey = "window.switch_project"
        override val wantsOpen: Boolean get() = isOpen.get()
        override val helpWindow: EditorWindow get() = this@ProjectSwitcherDialog

        override fun content() {
            // Hidden from outside this frame (a project opened from the list): close the popup with it.
            if (!isOpen.get()) {
                ImGui.closeCurrentPopup()
                return
            }
            // The `?` row is EditorModal's (helpWindow), above this line.
            ImGui.text(stringManager.getString("lbl.switch_project.select_or_create"))
            ImGui.spacing()

            val recentProjects = getRecentProjectsDisplayInfo()
            if (recentProjects.isEmpty()) {
                EmptyState.nothingYet(stringManager, Icons.FOLDER_OPEN, "lbl.no_recent_projects")
                return
            }
            ImGui.text(stringManager.getString("lbl.switch_project.recent"))
            ImGui.spacing()
            ImGui.beginChild("##recent_projects", 0f, minOf(320f, recentProjects.size * 72f), false)
            recentProjects.forEach { renderRecentProjectItem(it) }
            ImGui.endChild()
        }

        override fun buttons() = listOf(
            FooterButton("btn.new_project", FooterButton.Role.SECONDARY) {
                isOpen.set(false)
                eventSystem.publish(WindowAction.Show("window.project_wizard"))
            },
            FooterButton("btn.cancel", FooterButton.Role.CANCEL) { isOpen.set(false) },
            // Stays open behind the file picker; a successful open hides it.
            FooterButton("btn.open_project", FooterButton.Role.PRIMARY, closes = false) {
                eventSystem.publish(ProjectEvent.OpenProjectFileRequested)
            },
        )
    }
}
