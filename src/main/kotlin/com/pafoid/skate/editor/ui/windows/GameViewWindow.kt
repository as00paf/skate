package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.systems.ScreenshotFolder
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.imgui.EditorScenesTabBar
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.WindowHeader
import com.pafoid.skate.editor.imgui.data.UiConstants.TOOLBAR_HEIGHT
import com.pafoid.skate.editor.ui.handlers.ViewportActionHandler
import com.pafoid.skate.editor.ui.handlers.ViewportDragDropHandler
import com.pafoid.skate.editor.ui.menus.ViewportContextMenu
import com.pafoid.skate.editor.ui.windows.viewport.ViewportOverlays
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.editor.ui.windows.viewport.ViewportToolbar
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.editor.gizmos.UiRectGizmo
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiHoveredFlags
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiWindowFlags
import com.pafoid.skate.engine.input.ViewRect
import org.joml.Vector2f

class GameViewWindow(
    private val engine: Engine,
    private val editor: Editor,
    private val hasProject: () -> Boolean = { true },
    private val projectDir: () -> java.io.File? = { null },
) : EditorWindow("window.game_viewport", true) {
    private val stringManager = engine.stringManager
    private val sceneManager = engine.sceneManager

    private val viewportOverlays = ViewportOverlays(TrickUIWindow(engine.eventSystem), editor.settingsManager)

    private val viewportRenderer = ViewportRenderer(engine) { ScreenshotFolder.resolve(projectDir()) }
    private val uiRectGizmo = UiRectGizmo(engine, editor.undoRedoManager, viewportRenderer) { editor.gizmoSystem.uiSnap }.also { editor.gizmoSystem.uiGizmo = it }
    private val viewportContextMenu = ViewportContextMenu(stringManager, engine.eventSystem)
    private val viewportDragDropHandler =
        ViewportDragDropHandler(viewportRenderer, editor.editorCamera, engine.eventSystem, engine.inputProvider)
    private val viewportToolbar = ViewportToolbar(
        engine, stringManager, editor.gizmoSystem, editor.playModeHandler.controller,
        helpButton = { size -> WindowHeader.helpButton(stringManager, this, size) },
    )

    override val icon: String = Icons.CUBE
    override val relatedWindows = listOf("window.hierarchy", "window.properties")
    private val viewportActionHandler = ViewportActionHandler(
        engine = engine,
        undoRedoManager = editor.undoRedoManager,
        clipboardService = editor.clipboardService,
        editorCamera = editor.editorCamera,
        viewportRenderer = viewportRenderer,
        gizmoSystem = editor.gizmoSystem
    )

    private val gamepadOverlay = GamepadOverlay(
        engine.assetsManager,
        engine.inputProvider,
        editor.settingsManager,
        stringManager,
        engine.eventSystem
    )
    private val scenesTabBar = EditorScenesTabBar(engine.eventSystem, stringManager)

    private val tempVec2 = ImVec2()

    init {
        viewportActionHandler.init()
    }

    override fun imgui() {
        val noTabItem = 1 shl 23
        // No title bar or tab: the `?` is the toolbar's last button (EDT-51 §2.2).
        beginWindow(stringManager, ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoTitleBar or noTabItem, closable = false)

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
        if (sceneManager.currentScene == null) renderEmptyState(windowPos)

        if (sceneManager.currentScene?.isRunning != true) {
            viewportContextMenu.render(windowPos, sceneManager.currentScene)
            viewportDragDropHandler.renderDragDropTarget(sceneManager.currentScene)
        }

        uiRectGizmo.render(sceneManager.currentScene)
        viewportOverlays.render(windowPos, windowSize, sceneManager.currentScene)

        if (editor.settingsManager.editorSettings.showGamepadOverlay) {
            gamepadOverlay.imgui(
                Vector2f(viewportRenderer.imageScreenPosX, viewportRenderer.imageScreenPosY),
                Vector2f(viewportRenderer.imageSizeX, viewportRenderer.imageSizeY)
            )
        }

        // The image rect in ImGui desktop coordinates, the space of the editor's pointer source.
        engine.inputProvider.mouseListener.setViewRect(
            ViewRect(
                viewportRenderer.imageScreenPosX, viewportRenderer.imageScreenPosY,
                viewportRenderer.imageSizeX, viewportRenderer.imageSizeY,
            )
        )

        reportFocus()

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

    /**
     * Tells the camera input whether this window is focused / hovered (EDT-73). ImGui focuses a window on left and
     * right click only, so a middle click (orbit) focuses the viewport too.
     */
    private fun reportFocus() {
        val hovered = ImGui.isWindowHovered(ImGuiHoveredFlags.RootAndChildWindows)
        if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Middle)) ImGui.setWindowFocus()
        editor.viewportFocus.report(ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows), hovered)
    }

    /** Over the blank viewport when no scene is open (EDT-10). */
    private fun renderEmptyState(windowPos: ImVec2) {
        ImGui.setCursorPos(windowPos.x, windowPos.y + TOOLBAR_HEIGHT)
        if (ImGui.beginChild("##viewport_empty", 0f, 0f, false, ImGuiWindowFlags.NoBackground)) {
            if (hasProject()) EmptyState.noScene(stringManager, "empty.viewport.hint")
            else EmptyState.noProject(stringManager, engine.eventSystem)
        }
        ImGui.endChild()
    }

    private fun getLargestSizeForViewport(): ImVec2 {
        ImGui.getContentRegionAvail(tempVec2)
        return ImVec2(tempVec2.x, tempVec2.y)
    }
}
