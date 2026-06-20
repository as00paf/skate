package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import imgui.internal.ImGui.dockBuilderDockWindow
import imgui.type.ImInt

class WindowManager(
    private val stringManager: StringManager,
    private val windowRegistry: WindowRegistry,
    private val projectManager: ProjectManager,
    private val eventSystem: EventSystem,
) {

    private var hadProjectLastFrame = false
    private var hasAttemptedAutoLoad = false

    fun init() {
        eventSystem.subscribe<WindowAction.Show> { event ->
            windowRegistry.getWindow(event.name)?.showFlag?.set(true)
        }
        eventSystem.subscribe<WindowAction.Hide> { event -> windowRegistry.getWindow(event.name)?.showFlag?.set(false) }
        eventSystem.subscribe<WindowAction.ShowDefault> { windowRegistry.showDefaultWindows() }
        eventSystem.subscribe<WindowAction.HideAll> { windowRegistry.hideAllWindows() }
    }

    fun dockWindows(mainBodyId: ImInt, leftId: Int, rightId: Int, bottomId: Int) {
        windowRegistry.windows.filter { it.showFlag.get() }.forEach { window ->
            val dockId = when (window.nameKey) {
                "window.hierarchy", "window.properties", "window.systems", "window.asset_browser", "window.command_history", "window.render_graph" -> leftId
                "window.console", "window.profiler", "window.physics_tuner", "window.environment" -> bottomId
                "window.game_viewport" -> mainBodyId.get()
                else -> mainBodyId.get()
            }
            dockBuilderDockWindow(stringManager.getString(window.nameKey), dockId)
        }
    }

    fun update(currentScene: Scene?) {
        //if (projectManager.hasProject()) {
            windowRegistry.windows.forEach { window ->
                if (!window.showFlag.get()) return@forEach
                when {
                    window.requiresScene && currentScene != null -> (window.instance as? IWindowWithScene)?.imgui(
                        currentScene
                    )

                    !window.requiresScene -> (window.instance as? IWindow)?.imgui(window.showFlag)
                }
            }
        /* } else {
             processProjectStartupFlow()
         }*/
    }

    internal fun processProjectStartupFlow() {
        val hasProject = attemptAutoLoadIfNeeded(projectManager.hasProject())
        val wizard = windowRegistry.projectWizardWindow.wizard

        handleProjectTransitions(hasProject, wizard)
        hadProjectLastFrame = hasProject
        handleProjectWizardFallback(hasProject)
    }

    private fun attemptAutoLoadIfNeeded(initialHasProject: Boolean): Boolean {
        if (hasAttemptedAutoLoad || initialHasProject) {
            return initialHasProject
        }
        hasAttemptedAutoLoad = true
        eventSystem.publish(ProjectEvent.LoadLastProjectRequested)
        return projectManager.hasProject()
    }

    private fun handleProjectTransitions(hasProject: Boolean, wizard: ProjectWizard) {
        // Detect when a project was just closed — hide all project windows
        if (hadProjectLastFrame && !hasProject) {
            windowRegistry.hideAllWindows()
        }

        // Project was just opened — show default windows and dismiss wizard
        if (!hadProjectLastFrame && hasProject) {
            windowRegistry.showDefaultWindows()
            dismissProjectWizardIfOpen()
        }
    }

    private fun dismissProjectWizardIfOpen() {
        if (windowRegistry.isOpen("window.project_wizard")) {
            eventSystem.publish(WindowAction.Hide("window.project_wizard"))
        }
    }

    private fun handleProjectWizardFallback(hasProject: Boolean) {
        if (shouldOpenWizard(hasProject)) {
            eventSystem.publish(WindowAction.Show("window.project_wizard"))
        }
    }

    private fun shouldOpenWizard(
        hasProject: Boolean
    ): Boolean = !hasProject && !windowRegistry.isOpen("window.project_wizard")
}