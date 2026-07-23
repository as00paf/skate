package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.imgui.EditorScenesTabBar
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorMutationGate
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.ViewportActionHandler
import com.pafoid.skate.editor.ui.handlers.ViewportDragDropHandler
import com.pafoid.skate.editor.ui.menus.ViewportContextMenu
import com.pafoid.skate.editor.ui.windows.viewport.ViewportOverlays
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.editor.ui.windows.viewport.ViewportToolbar
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import org.joml.Vector2f

class GameViewWindow(
    private val engine: Engine,
    private val settingsManager: SettingsManager,
    private val stringManager: StringManager,
    private val editorState: EditorInputState,
    private val undoRedoManager: UndoRedoManager,
    private val clipboardService: ClipboardService,
    private val mutationGate: EditorMutationGate,
    private val prefabsGenerator: PrefabsGenerator,
    private val editorCamera: EditorCamera,
    private val gizmoSystem: GizmoSystem,
) : IWindow {
    private val sceneManager = engine.sceneManager

    private val viewportOverlays = ViewportOverlays(TrickUIWindow(engine.eventSystem), settingsManager)

    private val viewportRenderer = ViewportRenderer(engine)
    private val viewportContextMenu = ViewportContextMenu(stringManager, engine.eventSystem)
    private val viewportDragDropHandler = ViewportDragDropHandler(viewportRenderer, engine.eventSystem)
    private val viewportToolbar = ViewportToolbar(engine, stringManager, gizmoSystem)
    private val viewportActionHandler = ViewportActionHandler(
        engine = engine,
        undoRedoManager = undoRedoManager,
        clipboardService = clipboardService,
        mutationGate = mutationGate,
        prefabsGenerator = prefabsGenerator,
        editorCamera = editorCamera,
        viewportRenderer = viewportRenderer,
        gizmoSystem = gizmoSystem
    )

    private val gamepadOverlay = GamepadOverlay(
        engine.assetsManager,
        engine.inputProvider.gamepadListener,
        settingsManager,
        stringManager,
        engine.eventSystem
    )
    private val scenesTabBar = EditorScenesTabBar(engine.eventSystem, stringManager)

    private val tempVec2 = ImVec2()

    init {
        viewportActionHandler.init()
    }

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

        engine.inputProvider.mouseListener.setGameViewportPos(
            Vector2f(
                viewportRenderer.imageScreenPosX,
                viewportRenderer.imageScreenPosY
            )
        )
        engine.inputProvider.mouseListener.setGameViewportSize(
            Vector2f(
                viewportRenderer.imageSizeX,
                viewportRenderer.imageSizeY
            )
        )

        editorState.isFocused = ImGui.isWindowFocused()

        val hovered = sceneManager.currentScene?.hoveredGameObject
        if (hovered != null) {
            ImGui.setCursorPos(windowPos.x + 10f, windowPos.y + 20f)
            ImGui.textColored(
                1f,
                1f,
                1f,
                0.5f,
                stringManager.getString("lbl.gameview.picked_id", hovered.uId)
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
