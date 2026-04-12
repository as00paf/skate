package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.EditorCamera
import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.data.PrefabData
import com.pafoid.skate.editor.imgui.EditorScenesTabBar
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.menus.ViewportContextMenu
import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabType
import com.pafoid.skate.editor.ui.windows.viewport.ViewportOverlays
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.editor.ui.windows.viewport.ViewportToolbar
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.EventSystem
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.events.ViewportDropAnimation
import com.pafoid.skate.engine.events.ViewportDropSound
import com.pafoid.skate.engine.events.ViewportDropTexture
import com.pafoid.skate.engine.events.ViewportSpawnPrefab
import com.pafoid.skate.engine.input.listeners.MouseListener
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import org.joml.Vector2f
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

class GameViewWindow : IWindow, KoinComponent {

    private val logger: LoggerService by inject()
    private val mouseListener: MouseListener by inject()
    private val sceneManager: SceneManager by inject()
    private val settingsManager: SettingsManager by inject()
    private val stringManager: StringManager by inject()
    private val eventSystem: EventSystem by inject()

    private val viewportRenderer: ViewportRenderer by inject()
    private val viewportToolbar: ViewportToolbar by inject()
    private val viewportContextMenu: ViewportContextMenu by inject()
    private val viewportOverlays: ViewportOverlays by inject()

    private val gamepadOverlay = GamepadOverlay()
    private val sceneInitializer: LevelEditorSceneInitializer by inject()
    private val scenesTabBar by lazy { EditorScenesTabBar(sceneInitializer, eventSystem, stringManager) }

    private val tempVec2 = ImVec2()
    private val tempMousePos = ImVec2()

    override fun imgui(pOpen: ImBoolean?) {
        val noTabItem = 1 shl 23
        ImGui.begin(stringManager.getString("window.game_viewport"), ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoTitleBar or noTabItem)

        scenesTabBar.render(sceneManager)

        val windowSize = getLargestSizeForViewport()
        val windowPos = ImVec2(0f, TAB_BAR_HEIGHT)

        viewportToolbar.render(windowPos)

        ImGui.setCursorPos(
            windowPos.x + 10f / 2f + ImGui.getStyle().framePaddingX,
            windowPos.y + TOOLBAR_HEIGHT + ImGui.getStyle().framePaddingY
        )

        viewportRenderer.render(windowSize)
        viewportRenderer.updateFramebuffer()

        viewportContextMenu.render(windowPos, sceneManager.currentScene)

        ImGui.setCursorPos(windowPos.x, windowPos.y + TOOLBAR_HEIGHT)
        if (ImGui.beginDragDropTarget()) {
            val payloadLedge = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_LEDGE")
            val payloadRail = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_RAIL")
            val payloadKicker = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_KICKER")
            val payloadManualPad = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_MANUAL_PAD")
            val payloadBank = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_BANK")
            val payloadQuarterPipe = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_QUARTER_PIPE")
            val payloadSkateboard = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_SKATEBOARD")

            val payloadTexture = ImGui.acceptDragDropPayload<String>("TEXTURE")
            val payloadSound = ImGui.acceptDragDropPayload<String>("SOUND")
            val payloadAnimation = ImGui.acceptDragDropPayload<String>("ANIMATION")
            val prefabPayload = payloadRail ?: payloadLedge ?: payloadKicker ?: payloadManualPad ?: payloadBank ?: payloadQuarterPipe ?: payloadSkateboard

            if (prefabPayload != null) {
                val scene = sceneManager.currentScene
                if (scene != null) {
                    ImGui.getMousePos(tempMousePos)
                    val relX = tempMousePos.x - viewportRenderer.imageScreenPosX
                    val relY = tempMousePos.y - viewportRenderer.imageScreenPosY

                    val ray = scene.camera.screenToRay(relX, relY, viewportRenderer.imageSizeX, viewportRenderer.imageSizeY)

                    if (abs(ray.direction.y) > 0.0001f) {
                        val t = -ray.origin.y / ray.direction.y
                        if (t > 0) {
                            val hitPoint = Vector3f(ray.direction).mul(t).add(ray.origin)

                            val prefabType = when {
                                payloadRail != null -> PrefabType.RAIL
                                payloadLedge != null -> PrefabType.LEDGE
                                payloadKicker != null -> PrefabType.KICKER
                                payloadManualPad != null -> PrefabType.MANUAL_PAD
                                payloadBank != null -> PrefabType.BANK
                                payloadQuarterPipe != null -> PrefabType.QUARTER_PIPE
                                payloadSkateboard != null -> PrefabType.SKATEBOARD
                                else -> null
                            }

                            prefabType?.let { type ->
                                eventSystem.publish(ViewportSpawnPrefab(type, hitPoint))
                            }
                        }
                    }
                }
            }

            if (payloadTexture != null) {
                val scene = sceneManager.currentScene
                val hoveredObject = getHoveredObject()

                if (scene != null) {
                    val dropPosition = computeDropPosition(scene, viewportRenderer)
                    eventSystem.publish(ViewportDropTexture(payloadTexture, hoveredObject, dropPosition))
                }
            }

            if (payloadSound != null) {
                val hoveredObject = getHoveredObject()
                if (hoveredObject != null) {
                    eventSystem.publish(ViewportDropSound(payloadSound, hoveredObject))
                } else {
                    logger.logEditor("Drop sound on an object to add AudioComponent")
                }
            }

            if (payloadAnimation != null) {
                val hoveredObject = getHoveredObject()
                if (hoveredObject != null) {
                    eventSystem.publish(ViewportDropAnimation(payloadAnimation, hoveredObject))
                } else {
                    logger.logEditor("Drop animation on a skater object")
                }
            }

            ImGui.endDragDropTarget()
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

        val editorInput = sceneManager.currentScene?.systemManager?.getSystem<EditorCamera>()?.editorInput
        editorInput?.isFocused = ImGui.isWindowFocused()

        val hovered = getHoveredObject()
        if (hovered != null) {
            ImGui.setCursorPos(windowPos.x + 10f, windowPos.y + 20f)
            ImGui.textColored(
                1f,
                1f,
                1f,
                0.5f,
                "Picked ID: ${hovered.getUid()}"
            )
        }

        ImGui.end()
    }

    fun getHoveredObject(): GameObject? {
        return sceneManager.currentScene?.systemManager?.getSystem<GizmoSystem>()?.getHoveredGameObject()
    }

    private fun getLargestSizeForViewport(): ImVec2 {
        ImGui.getContentRegionAvail(tempVec2)
        return ImVec2(tempVec2.x, tempVec2.y)
    }

    private fun computeDropPosition(scene: com.pafoid.skate.engine.ecs.Scene, viewportRenderer: ViewportRenderer): Vector3f? {
        val mousePos = ImVec2()
        ImGui.getMousePos(mousePos)
        val relX = mousePos.x - viewportRenderer.imageScreenPosX
        val relY = mousePos.y - viewportRenderer.imageScreenPosY

        val ray = scene.camera.screenToRay(relX, relY, viewportRenderer.imageSizeX, viewportRenderer.imageSizeY)

        return if (abs(ray.direction.y) > 0.0001f) {
            val t = -ray.origin.y / ray.direction.y
            if (t > 0) {
                Vector3f(ray.direction).mul(t).add(ray.origin)
            } else {
                null
            }
        } else {
            null
        }
    }

    private companion object {
        private const val TOOLBAR_HEIGHT = 40f
        private const val TAB_BAR_HEIGHT = 25f
    }
}
