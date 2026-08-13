package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.internal.ImGui.dockBuilderDockWindow
import imgui.type.ImInt

class WindowManager(
    private val stringManager: StringManager,
    private val windowRegistry: WindowRegistry,
    private val eventSystem: EventSystem,
) {
    fun init() {
        eventSystem.subscribe<WindowAction.Show> { event ->
            windowRegistry.getWindow(event.name)?.isOpen?.set(true)
        }
        eventSystem.subscribe<WindowAction.Hide> { event -> windowRegistry.getWindow(event.name)?.isOpen?.set(false) }
        eventSystem.subscribe<WindowAction.ShowDefault> { windowRegistry.showDefaultWindows() }
        eventSystem.subscribe<WindowAction.HideAll> { windowRegistry.hideAllWindows() }
    }

    fun dockWindows(mainBodyId: ImInt, leftId: Int, rightId: Int, bottomId: Int) {
        windowRegistry.windows.filter { it.isOpen.get() }.forEach { window ->
            val dockId = when (window.name) {
                "window.hierarchy", "window.properties", "window.systems", "window.asset_browser", "window.command_history", "window.render_graph" -> leftId
                "window.console", "window.profiler", "window.physics_tuner", "window.environment" -> bottomId
                "window.game_viewport" -> mainBodyId.get()
                else -> mainBodyId.get()
            }
            dockBuilderDockWindow(stringManager.getString(window.name), dockId)
        }
    }

    fun update() {
        windowRegistry.windows.forEach { window ->
            if (!window.isOpen.get()) return@forEach
            window.imgui()
        }
        windowRegistry.menuBar.render()
        windowRegistry.statusBar.render()
    }
}