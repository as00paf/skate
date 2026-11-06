package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.events.EditorEvent
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.HamburgerMenuRenderer
import com.pafoid.skate.editor.imgui.MenuAction
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.ui.windows.ExportWizardWindow
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction
import imgui.ImGui

/** File: project actions, then scene actions, then Export and Quit (EDT-51 §2.7). */
class FileMenuBuilder(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
    private val projectManager: ProjectManager,
    private val settingsManager: EditorSettingsManager,
    private val sceneManager: SceneManager,
) {
    fun render() = HamburgerMenuRenderer.menu(stringManager, HamburgerMenuModel.FILE, ::action) { renderRecentProjects() }

    private fun action(id: String): MenuAction? {
        val hasProject = projectManager.hasProject()
        val scene = sceneManager.currentScene
        return when (id) {
            "new_project" -> MenuAction { eventSystem.publish(WindowAction.Show("window.project_wizard")) }
            "open_project" -> MenuAction { eventSystem.publish(ProjectEvent.OpenProjectFileRequested) }
            "save_project" -> MenuAction(hasProject) { eventSystem.publish(ProjectEvent.SaveRequested) }
            "save_project_as" -> MenuAction(hasProject) { eventSystem.publish(ProjectEvent.SaveAsRequested) }
            "close_project" -> MenuAction(hasProject) { eventSystem.publish(ProjectEvent.CloseProjectRequested) }
            "new_scene" -> MenuAction { eventSystem.publish(SceneAction.CreateRequested) }
            "save_scene" -> MenuAction(scene != null) { scene?.let { eventSystem.publish(SceneAction.SaveRequested(it)) } }
            "save_scene_as" -> MenuAction(scene != null) { scene?.let { eventSystem.publish(SceneAction.SaveAsRequested(it)) } }
            "import_scene" -> MenuAction { eventSystem.publish(SceneAction.ImportRequested) }
            "export" -> MenuAction(hasProject) { eventSystem.publish(WindowAction.Show(ExportWizardWindow.NAME)) }
            "quit" -> MenuAction { eventSystem.publish(EditorEvent.Exit) }
            else -> null
        }
    }

    /** Recent projects other than the open one; their names are data, not translated labels. */
    private fun renderRecentProjects() {
        val currentPath = projectManager.currentProject?.getProjectFile()?.absolutePath
        val recent = settingsManager.recentProjects.filter { it.projectPath != currentPath }
        if (recent.isEmpty()) {
            ImGui.textDisabled(stringManager.getString("lbl.no_recent_projects"))
            return
        }
        recent.forEachIndexed { i, project ->
            if (ImGui.menuItem("${project.name}##recent_$i")) {
                eventSystem.publish(WindowAction.Hide("window.project_wizard"))
                eventSystem.publish(ProjectEvent.OpenProjectRequested(project.projectPath))
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(project.projectPath)
        }
    }
}
