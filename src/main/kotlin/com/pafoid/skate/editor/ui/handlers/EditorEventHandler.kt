package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.systems.WindowRegistry
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager

class EditorEventHandler(
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
    private val viewportRenderer: ViewportRenderer,
    private val windowRegistry: WindowRegistry,
    private val imGuiLayer: ImGuiLayer,
) {
    fun init() {
        eventSystem.subscribe<ViewportAction.GameObjectSelected> { event ->
            sceneManager.currentScene?.selectedGameObject = event.gameObject
        }

        eventSystem.subscribe<ViewportAction.SelectionCleared> {
            sceneManager.currentScene?.selectedGameObject = null
        }

        eventSystem.subscribe<ViewportAction.ScreenshotRequested> {
            viewportRenderer.captureScreenshot()
        }

        // Windows
        eventSystem.subscribe<ViewportAction.ToggleFullScreen> {
            imGuiLayer.isViewportMaximized = !imGuiLayer.isViewportMaximized
        }

        eventSystem.subscribe<ViewportAction.OpenSearch> {
            windowRegistry.searchEverywhereWindow.open()
        }


    }
}