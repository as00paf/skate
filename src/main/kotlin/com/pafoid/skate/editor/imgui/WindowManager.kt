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
        windowRegistry.windows.forEach { window ->
            val dockId = when (DefaultDockLayout.zoneOf(window.name)) {
                DefaultDockLayout.Zone.LEFT -> leftId
                DefaultDockLayout.Zone.RIGHT -> rightId
                DefaultDockLayout.Zone.BOTTOM -> bottomId
                DefaultDockLayout.Zone.CENTER -> mainBodyId.get()
                null -> return@forEach
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