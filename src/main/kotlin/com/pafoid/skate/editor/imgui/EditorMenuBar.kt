package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.EditorEvent
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.data.Color
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants.EDITOR_MENU_BAR_HEIGHT
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.ui.menus.EditMenuBuilder
import com.pafoid.skate.editor.ui.menus.FileMenuBuilder
import com.pafoid.skate.editor.ui.menus.SettingsMenuBuilder
import com.pafoid.skate.editor.ui.menus.ViewMenuBuilder
import com.pafoid.skate.editor.ui.menus.WindowControlsRenderer
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import imgui.ImGui
import imgui.ImGui.menuItem
import imgui.internal.ImGui.image

class EditorMenuBar(
    private val stringManager: StringManager,
    private val assetsManager: AssetsManager,
    private val projectManager: ProjectManager,
    sceneManager: SceneManager,
    settingsManager: SettingsManager,
    windowRegistry: WindowRegistry,
    private val eventSystem: EventSystem,
) {
    private var appIconTexId = -1

    private val fileMenu = FileMenuBuilder(stringManager, eventSystem)
    private val editMenu = EditMenuBuilder(stringManager, sceneManager, eventSystem)
    private val settingsMenu = SettingsMenuBuilder(stringManager, settingsManager, eventSystem)
    private val viewMenu = ViewMenuBuilder(stringManager, windowRegistry)
    private val windowControls = WindowControlsRenderer(eventSystem, stringManager)


    init {
        loadAppIconTexture()
    }

    fun render(currentScene: Scene?) {
        if (ImGui.beginMenuBar()) {
            renderAppIcon()
            renderHamburgerMenu(currentScene)
            renderProjectInfo()
            windowControls.render()

            ImGui.endMenuBar()
        }
    }

    private fun renderAppIcon() {
        if (appIconTexId == -1) {
            loadAppIconTexture()
        }
        if (appIconTexId != -1) {
            val iconSize = 32f

            ImGui.setCursorPosY((EDITOR_MENU_BAR_HEIGHT - iconSize) / 2f)
            image(appIconTexId.toLong(), iconSize, iconSize)
        }
    }

    private fun loadAppIconTexture() {
        appIconTexId = try {
            assetsManager.getTexture(Assets.Textures.APP_ICON).texId
        } catch (e: Exception) {
            -1
        }
    }

    private fun renderHamburgerMenu(currentScene: Scene?) {
        val btnSize = 30f
        val offsetY = (EDITOR_MENU_BAR_HEIGHT - btnSize) / 2f
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
                        if (menuItem(project.name)) {
                            eventSystem.publish(WindowAction.Hide("window.project_wizard"))
                            eventSystem.publish(ProjectEvent.OpenProjectRequested(project.path))
                        }
                    }
                } else {
                    ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, stringManager.getString("lbl.no_recent_projects"))
                }
                ImGui.endMenu()
            }

            ImGui.separator()

            if (menuItem("${Icons.PLUS} ${stringManager.getString("menu.file.new_project")}")) {
                eventSystem.publish(WindowAction.Show("window.project_wizard"))
            }
            if (menuItem("${Icons.FOLDER_OPEN} ${stringManager.getString("menu.file.open_project_menu")}")) {
                eventSystem.publish(ProjectEvent.OpenProjectFileRequested)
            }

            if (projectManager.hasProject()) {
                if (menuItem("${Icons.WINDOW_CLOSE} ${stringManager.getString("menu.file.close_project")}")) {
                    eventSystem.publish(ProjectEvent.CloseProjectRequested)
                }
                if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save_project")}", "Ctrl+S")) {
                    eventSystem.publish(ProjectEvent.SaveRequested)
                }
                if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save_project_as")}")) {
                    eventSystem.publish(ProjectEvent.SaveAsRequested)
                }
            }
            ImGui.separator()

            if (menuItem("${Icons.TRASH} ${stringManager.getString("menu.file.quit")}")) {
                eventSystem.publish(EditorEvent.Exit)
            }
            ImGui.endPopup()
        }
    }

    private fun renderProjectInfo() {
        val fontSize = ImGui.getFontSize()
        val textY = (EDITOR_MENU_BAR_HEIGHT - fontSize) / 2f * 0.8f
        ImGui.setCursorPosY(textY)

        ImGui.textDisabled("|")
        ImGui.setCursorPosY(textY)
        ImGui.textColored(
            Color.ISLAND_ACCENT_BLUE.x,
            Color.ISLAND_ACCENT_BLUE.y,
            Color.ISLAND_ACCENT_BLUE.z,
            Color.ISLAND_ACCENT_BLUE.w,
            Icons.CUBE
        )
        ImGui.setCursorPosY(textY)

        val currentProjectName = projectManager.getProjectName()
        if (ImGui.button(currentProjectName)) {
            eventSystem.publish(WindowAction.Show("window.project_switcher"))
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.switch_projects"))
        }
    }

    fun setMaximized(maximized: Boolean) {
        windowControls.isMaximized = maximized
    }
}
