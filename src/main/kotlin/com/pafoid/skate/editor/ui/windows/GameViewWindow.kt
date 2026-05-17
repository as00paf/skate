package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.imgui.EditorScenesTabBar
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.ui.handlers.ViewportDragDropHandler
import com.pafoid.skate.editor.ui.menus.ViewportContextMenu
import com.pafoid.skate.editor.ui.windows.viewport.ViewportOverlays
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.editor.ui.windows.viewport.ViewportToolbar
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.listeners.MouseListener
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import org.joml.Vector2f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject
import kotlin.getValue

class GameViewWindow(
    private val mouseListener: MouseListener,
    private val sceneManager: SceneManager,
    private val settingsManager: SettingsManager,
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
    private val editorState: EditorInputState,
    private val viewportRenderer: ViewportRenderer,
    private val viewportToolbar: ViewportToolbar,
    private val viewportContextMenu: ViewportContextMenu,
    private val viewportOverlays: ViewportOverlays,
    private val viewportDragDropHandler: ViewportDragDropHandler,
) : IWindow, KoinComponent {

    private val gamepadOverlay = GamepadOverlay()
    private val scenesTabBar by lazy { EditorScenesTabBar(eventSystem, stringManager) }

    private val tempVec2 = ImVec2()

    override fun imgui(pOpen: ImBoolean?) {
        val noTabItem = 1 shl 23
        ImGui.begin(stringManager.getString("window.game_viewport"), ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoTitleBar or noTabItem)

        scenesTabBar.render(sceneManager)

        val windowSize = getLargestSizeForViewport()
        val windowPos = ImVec2(0f, 25f)

        viewportToolbar.render(windowPos)

        ImGui.setCursorPos(
            windowPos.x + 10f / 2f + ImGui.getStyle().framePaddingX,
            windowPos.y + TOOLBAR_HEIGHT + ImGui.getStyle().framePaddingY
        )

        viewportRenderer.render(windowSize)
        viewportRenderer.updateFramebuffer()

        if (sceneManager.currentScene?.isRunning != true) {
            viewportContextMenu.render(windowPos, sceneManager.currentScene)
            viewportDragDropHandler.renderDragDropTarget(sceneManager.currentScene)
        }

        viewportOverlays.render(windowPos, windowSize, sceneManager.currentScene)

        if (settingsManager.engine.editor.showGamepadOverlay) {
            gamepadOverlay.imgui(
                Vector2f(viewportRenderer.imageScreenPosX, viewportRenderer.imageScreenPosY),
                Vector2f(viewportRenderer.imageSizeX, viewportRenderer.imageSizeY)
            )
        }

        mouseListener.setGameViewportPos(Vector2f(viewportRenderer.imageScreenPosX, viewportRenderer.imageScreenPosY))
        mouseListener.setGameViewportSize(Vector2f(viewportRenderer.imageSizeX, viewportRenderer.imageSizeY))

        editorState.isFocused = ImGui.isWindowFocused()

        val hovered = sceneManager.currentScene?.hoveredGameObject
        if (hovered != null) {
            ImGui.setCursorPos(windowPos.x + 10f, windowPos.y + 20f)
            ImGui.textColored(
                1f,
                1f,
                1f,
                0.5f,
                stringManager.getString("lbl.gameview.picked_id", hovered.getUid())
            )
        }

        ImGui.end()
    }

    private fun getLargestSizeForViewport(): ImVec2 {
        ImGui.getContentRegionAvail(tempVec2)
        return ImVec2(tempVec2.x, tempVec2.y)
    }

    private companion object {
        private const val TOOLBAR_HEIGHT = 40f
    }
}
