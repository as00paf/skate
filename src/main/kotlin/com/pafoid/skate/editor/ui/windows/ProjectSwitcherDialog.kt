package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectRequested
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants
import com.pafoid.skate.editor.project.RecentProjectDisplayInfo
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiMouseCursor
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import java.io.File

class ProjectSwitcherDialog(
    private val projectManager: ProjectManager,
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
) : IWindow, KoinComponent {

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

        ImGui.textColored(0.4f, 0.4f, 0.4f, 1f, "${stringManager.getString("lbl.switch_project.last_opened").format(project.getLastOpenedString())}")

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
        return projectManager.getRecentProjects().map { recent ->
            val exists = File(recent.path).exists()
            RecentProjectDisplayInfo(
                name = recent.name,
                path = recent.path,
                lastOpened = recent.lastOpened,
                exists = exists
            )
        }
    }

    override fun imgui(pOpen: ImBoolean?) {
        val centerX = ImGui.getMainViewport().centerX
        val centerY = ImGui.getMainViewport().centerY

        ImGui.setNextWindowPos(centerX, centerY, ImGuiCond.Always, 0.5f, 0.5f)
        ImGui.setNextWindowSize(500f, 400f)

        if (ImGui.begin(
                stringManager.getString("window.switch_project"),
                pOpen,
                ImGuiWindowFlags.NoResize or ImGuiWindowFlags.Modal
            )
        ) {
            ImGui.text(stringManager.getString("lbl.switch_project.select_or_create"))
            ImGui.spacing()

            val recentProjects = getRecentProjectsDisplayInfo()
            if (recentProjects.isNotEmpty()) {
                ImGui.text(stringManager.getString("lbl.switch_project.recent"))
                ImGui.spacing()

                for (project in recentProjects) {
                    renderRecentProjectItem(project)
                }
            } else {
                MImGui.textDisabled(stringManager.getString("lbl.no_recent_projects"))
            }

            ImGui.separator()
            ImGui.spacing()

            val buttonHeight = UiConstants.DEFAULT_BUTTON_HEIGHT
            val newButtonWidth = 150f
            val openButtonWidth = 130f
            val cancelWidth = 100f
            val spacing = UiConstants.SECTION_SPACING
            val totalWidth = newButtonWidth + openButtonWidth + cancelWidth + (2 * spacing)

            val contentRegionWidth = ImGui.getContentRegionAvailX()
            ImGui.setCursorPosX(ImGui.getCursorPosX() + contentRegionWidth - totalWidth)

            if (ImGui.button(
                    "${Icons.PLUS} ${stringManager.getString("btn.new_project")}",
                    newButtonWidth,
                    buttonHeight
                )
            ) {
                eventSystem.publish(WindowAction.Hide("window.project_switcher"))
                eventSystem.publish(WindowAction.Show("window.project_wizard"))
            }

            ImGui.sameLine(0f, spacing)

            if (ImGui.button(
                    "${Icons.FOLDER_OPEN} ${stringManager.getString("btn.open_project")}",
                    openButtonWidth,
                    buttonHeight
                )
            ) {
                eventSystem.publish(ProjectEvent.OpenProjectFileRequested)
            }

            ImGui.sameLine(0f, spacing)

            if (ImGui.button(stringManager.getString("btn.cancel"), cancelWidth, buttonHeight)) {
                eventSystem.publish(WindowAction.Hide("window.project_switcher"))
            }

            ImGui.end()
        }
    }
}
