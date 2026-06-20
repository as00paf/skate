package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import imgui.internal.ImGui.dockBuilderDockWindow
import imgui.type.ImInt

class WindowManager(
    private val stringManager: StringManager,
    private val windowRegistry: WindowRegistry,
    private val eventSystem: EventSystem,
) {
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
        windowRegistry.windows.forEach { window ->
            if (!window.showFlag.get()) return@forEach
            when {
                window.requiresScene && currentScene != null -> (window.instance as? IWindowWithScene)?.imgui(
                    currentScene
                )

                !window.requiresScene -> (window.instance as? IWindow)?.imgui(window.showFlag)
            }
        }
    }
}