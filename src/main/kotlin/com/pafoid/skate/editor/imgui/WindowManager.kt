package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.data.EditorFocus
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.components.InspectorDrafts
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
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
            dockBuilderDockWindow(window.title(stringManager), dockId)
        }
    }

    private val closeTracker = WindowCloseTracker()

    /** Failed user actions (EDT-10); drawn last so it sits above every window. */
    private val errorDialog = ErrorDialog(eventSystem, stringManager)

    /** Asks before deleting objects, from any path (EDT-62). */
    private val deleteDialog = DeleteObjectsDialog(eventSystem, stringManager)

    fun update() {
        windowRegistry.windows.forEach { window ->
            if (!window.isOpen.get()) return@forEach
            window.imgui()
        }
        closeTracker.update(windowRegistry.windows)
        InspectorDrafts.endFrame(eventSystem, ImGui.getFrameCount(), ImGui.isAnyItemActive())
        windowRegistry.menuBar.render()
        windowRegistry.statusBar.render()
        deleteDialog.render()
        errorDialog.render()
        EditorFocus.endFrame()
    }
}