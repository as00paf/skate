package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Color
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.ui.menus.EditMenuBuilder
import com.pafoid.skate.editor.ui.menus.FileMenuBuilder
import com.pafoid.skate.editor.ui.menus.SettingsMenuBuilder
import com.pafoid.skate.editor.ui.menus.ViewMenuBuilder
import com.pafoid.skate.editor.ui.menus.WindowControlsRenderer
import com.pafoid.skate.editor.ui.windows.ProjectSwitcherDialog
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.WindowController
import com.pafoid.skate.engine.ecs.Scene
import imgui.ImGui
import imgui.internal.ImGui.image
import java.io.File

class EditorMenuBar(
    private val fileMenu: FileMenuBuilder,
    private val editMenu: EditMenuBuilder,
    private val settingsMenu: SettingsMenuBuilder,
    private val viewMenu: ViewMenuBuilder,
    private val windowControls: WindowControlsRenderer,
    private val stringManager: StringManager,
    private val resourceManager: ResourceManager,
    private val projectManager: ProjectManager,
    private val projectSwitcher: ProjectSwitcherDialog,
    private val windowController: WindowController,
    private val projectWizard: ProjectWizard,
    private val imguiLayer: ImGuiLayer
) {
    private var appIconTexId = -1
    private val projectIcon = Icons.CUBE

    init {
        appIconTexId = try {
            resourceManager.loadTextureSync(Assets.Textures.APP_ICON).texId
        } catch (e: Exception) {
            -1
        }
    }

    fun render(currentScene: Scene) {
        if (imgui.internal.ImGui.beginMenuBar()) {
            val barHeight = 48f

            renderAppIcon(barHeight)
            renderHamburgerMenu(currentScene, barHeight)
            renderProjectInfo(barHeight)
            windowControls.render()

            imgui.internal.ImGui.endMenuBar()
        }
    }

    private fun renderAppIcon(barHeight: Float) {
        if (appIconTexId != -1) {
            val iconSize = 32f

            ImGui.setCursorPosY((barHeight - iconSize) / 2f)
            image(appIconTexId.toLong(), iconSize, iconSize)
        }
    }

    private fun renderHamburgerMenu(currentScene: Scene, barHeight: Float) {
        val btnSize = 30f
        val offsetY = (barHeight - btnSize) / 2f
        ImGui.setCursorPosY(offsetY)

        if (ImGui.button(Icons.MENU, btnSize, btnSize)) {
            ImGui.openPopup("main_hamburger_menu")
        }

        if (ImGui.beginPopup("main_hamburger_menu")) {
            fileMenu.render(currentScene)
            editMenu.render()
            settingsMenu.render()
            viewMenu.render()
            ImGui.separator()

            if (ImGui.beginMenu(stringManager.getString("menu.file.recent_projects"))) {
                val currentPath = projectManager.currentProject?.getProjectFile()?.absolutePath
                val recentProjects = projectManager.getRecentProjects()
                val filteredProjects = recentProjects.filter { it.path != currentPath }

                if (filteredProjects.isNotEmpty()) {
                    for (project in filteredProjects) {
                        if (ImGui.menuItem(project.name)) {
                            projectManager.openProject(File(project.path))
                        }
                    }
                } else {
                    ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, stringManager.getString("lbl.no_recent_projects"))
                }
                ImGui.endMenu()
            }

            ImGui.separator()

            if (projectManager.hasProject()) {
                if (ImGui.menuItem("${Icons.WINDOW_CLOSE} ${stringManager.getString("menu.file.close_project")}")) {
                    imguiLayer.markWizardResetNeeded()
                    projectManager.closeProject()
                }
            }
            if (ImGui.menuItem("${Icons.PLUS} ${stringManager.getString("menu.file.new_project")}")) {
                projectWizard.open()
            }
            if (ImGui.menuItem("${Icons.FOLDER_OPEN} ${stringManager.getString("menu.file.open_project_menu")}")) {
                projectSwitcher.open()
            }

            ImGui.separator()
            if (ImGui.menuItem(stringManager.getString("menu.file.quit"))) {
                windowController.close()
            }
            ImGui.endPopup()
        }
    }

    private fun renderProjectInfo(barHeight: Float) {
        val fontSize = ImGui.getFontSize()
        val textY = (barHeight - fontSize) / 2f * 0.8f
        ImGui.setCursorPosY(textY)

        ImGui.textDisabled("|")
        ImGui.setCursorPosY(textY)
        ImGui.textColored(
            Color.ISLAND_ACCENT_BLUE.x,
            Color.ISLAND_ACCENT_BLUE.y,
            Color.ISLAND_ACCENT_BLUE.z,
            Color.ISLAND_ACCENT_BLUE.w,
            projectIcon
        )
        ImGui.setCursorPosY(textY)

        val currentProjectName = projectManager.getProjectName()
        if (ImGui.button(currentProjectName)) {
            projectSwitcher.open()
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.switch_projects"))
        }
    }
}
