package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.editor.assetBrowser.PrefabData
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.prefabs.PrefabsGenerator
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.GizmoSystem
import com.pafoid.skate.engine.scenes.components.MeasureTool
import com.pafoid.skate.engine.utils.Icons
import com.pafoid.skate.engine.utils.ScreenshotUtils
import com.pafoid.skate.engine.utils.SettingsManager
import com.pafoid.skate.engine.utils.StringManager
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import org.joml.Vector2f
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs
import kotlin.math.roundToInt

import com.pafoid.skate.engine.scenes.components.SelectionGizmo
import com.pafoid.skate.engine.scenes.components.Transform

class GameViewWindow : KoinComponent {

    private val logger: LoggerService by inject()
    private val mouseListener: MouseListener by inject()
    private val sceneManager: SceneManager by inject()
    private val settingsManager: SettingsManager by inject()
    private val prefabsGenerator: PrefabsGenerator by inject()
    private val stringManager: StringManager by inject()
    private val renderer: Renderer by inject()

    var imageScreenPosX = 0f
    var imageScreenPosY = 0f
    var imageSizeX = 0f
    var imageSizeY = 0f

    private var isPlaying = false
    private val gamepadOverlay = GamepadOverlay()
    private val trickUIWindow = TrickUIWindow()

    fun imgui() {
        ImGui.begin(stringManager.getString("window.game_viewport"), ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse)

        val windowSize = getLargestSizeForViewport()
        val windowPos = getCenteredPositionForViewport(windowSize)

        // Render the toolbar first
        renderToolbar(windowPos, windowSize)

        // Adjust cursor for the image to be below the toolbar
        ImGui.setCursorPos(windowPos.x, windowPos.y + TOOLBAR_HEIGHT)

        // Capture EXACT screen position before drawing image
        drawImage(windowSize)

        // Drag and Drop Target should be over the image area
        ImGui.setCursorPos(windowPos.x, windowPos.y + TOOLBAR_HEIGHT)
        if (ImGui.beginDragDropTarget()) {
            val payloadLedge = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_LEDGE")
            val payloadRail = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_RAIL")
            val payloadKicker = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_KICKER")

            val payload = payloadRail ?: payloadLedge ?: payloadKicker

            if (payload != null) {
                val scene = sceneManager.currentScene
                if (scene != null) {
                    val mousePos = ImVec2()
                    ImGui.getMousePos(mousePos)
                    val relX = mousePos.x - imageScreenPosX
                    val relY = mousePos.y - imageScreenPosY

                    val ray = scene.camera.screenToRay(relX, relY, imageSizeX, imageSizeY)

                    // Intersect ray with ground plane (Y=0)
                    // P = O + t*D -> Py = 0 -> Oy + t*Dy = 0 -> t = -Oy / Dy
                    if (abs(ray.direction.y) > 0.0001f) {
                        val t = -ray.origin.y / ray.direction.y
                        if (t > 0) {
                            val hitPoint = Vector3f(ray.direction).mul(t).add(ray.origin)

                            when {
                                payloadRail != null -> prefabsGenerator.spawnRail(hitPoint, payload.material)
                                payloadLedge != null -> prefabsGenerator.spawnLedge(hitPoint, payload.material)
                                payloadKicker != null -> prefabsGenerator.spawnKicker(hitPoint, payload.material)
                            }
                        }
                    }
                }
            }
            ImGui.endDragDropTarget()
        }

        renderViewportOverlays(windowPos, windowSize)

        // Render Gamepad Overlay
        if (settingsManager.settings.showGamepadOverlay) {
            gamepadOverlay.imgui(Vector2f(imageScreenPosX, imageScreenPosY), Vector2f(imageSizeX, imageSizeY))
        }

        mouseListener.setGameViewportPos(Vector2f(imageScreenPosX, imageScreenPosY))
        mouseListener.setGameViewportSize(Vector2f(imageSizeX, imageSizeY))

        // Debug Info Overlay
        // We now get the hovered object from SelectionGizmo (via getHoveredObject)
        val hovered = getHoveredObject()
        if (hovered != null) {
            ImGui.setCursorPos(windowPos.x + 10f, windowPos.y + 10f)
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

    private fun drawImage(windowSize: ImVec2) {
        val screenPos = ImVec2()
        ImGui.getCursorScreenPos(screenPos)
        imageScreenPosX = screenPos.x
        imageScreenPosY = screenPos.y
        imageSizeX = windowSize.x
        imageSizeY = windowSize.y - TOOLBAR_HEIGHT

        val texId = renderer.frameBuffer.getTextureId()
        ImGui.image(texId.toLong(), imageSizeX, imageSizeY, 0f, 1f, 1f, 0f)
    }

    private fun renderViewportOverlays(windowPos: ImVec2, windowSize: ImVec2) {
        val isPlaying = sceneManager.runtimePlaying
        val scene = sceneManager.currentScene

        // FPS Overlay (Top Left)
        ImGui.setCursorPos(windowPos.x + OVERLAY_PADDING, windowPos.y + OVERLAY_PADDING)
        ImGui.beginChild(
            "FPS_Overlay",
            FPS_OVERLAY_WIDTH,
            FPS_OVERLAY_HEIGHT,
            false,
            ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoDecoration
        )
        ImGui.textColored(0f, 1f, 0f, 1f, "FPS: ${ImGui.getIO().framerate.toInt()}")
        ImGui.endChild()

        // Speedometer Overlay (Bottom Left)
        val skateGo = scene?.gameObjectManager?.gameObjects?.find { it.name == "Skateboard" }
        val rb = skateGo?.getComponent<RigidBody3D>()
        val velocity = rb?.rawBody?.getLinearVelocity(null)
        if (velocity != null) {
            val speedMS = velocity.length()
            val settings = settingsManager.settings
            val (speedDisplay, unitLabel) = if (settings.unitSystem == UnitSystem.METRIC) {
                Pair(speedMS * 3.6f, "km/h")
            } else {
                Pair(speedMS * 2.23694f, "mph")
            }

            ImGui.setCursorPos(
                windowPos.x + OVERLAY_PADDING,
                windowPos.y + windowSize.y - SPEED_OVERLAY_HEIGHT - OVERLAY_PADDING
            )
            ImGui.beginChild(
                "Speed_Overlay",
                SPEED_OVERLAY_WIDTH,
                SPEED_OVERLAY_HEIGHT,
                false,
                ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoDecoration
            )
            ImGui.textColored(1f, 1f, 1f, 1f, "${speedDisplay.roundToInt()} $unitLabel")
            ImGui.endChild()
        }

        // Trick UI Overlay (Bottom Left, above Speedometer)
        skateGo?.let { go ->
            trickUIWindow.setTrickGameObject(go)
            // Position above the speed overlay
            val trickX = windowPos.x + OVERLAY_PADDING
            val trickY =
                windowPos.y + windowSize.y - SPEED_OVERLAY_HEIGHT - TRICK_OVERLAY_HEIGHT - (OVERLAY_PADDING * 2)

            trickUIWindow.imgui(trickX, trickY, TRICK_OVERLAY_WIDTH, TRICK_OVERLAY_HEIGHT)
        }
    }

    fun getHoveredObject(): GameObject? {
        val scene = sceneManager.currentScene ?: return null
        for (go in scene.gameObjectManager.gameObjects) {
            val gizmo = go.getComponent<SelectionGizmo>()
            if (gizmo != null) return gizmo.hoveredGameObject
        }
        return null
    }

    private fun getLargestSizeForViewport(): ImVec2 {
        val windowSize = ImVec2()
        ImGui.getContentRegionAvail(windowSize)

        val targetAspectRatio = 1920f / 1080f
        var aspectWidth = windowSize.x
        var aspectHeight = aspectWidth / targetAspectRatio
        if (aspectHeight > windowSize.y) {
            aspectHeight = windowSize.y
            aspectWidth = aspectHeight * targetAspectRatio
        }

        return ImVec2(aspectWidth, aspectHeight)
    }

    private fun getCenteredPositionForViewport(aspectSize: ImVec2): ImVec2 {
        val windowSize = ImVec2()
        ImGui.getContentRegionAvail(windowSize)

        val viewportX = (windowSize.x / 2.0f) - (aspectSize.x / 2.0f)
        val viewportY = (windowSize.y / 2.0f) - (aspectSize.y / 2.0f)

        return ImVec2(viewportX + ImGui.getCursorPosX(), viewportY + ImGui.getCursorPosY())
    }

    private fun renderToolbar(windowPos: ImVec2, windowSize: ImVec2) {
        val isPlaying = sceneManager.runtimePlaying
        val scene = sceneManager.currentScene
        val toolbarPosY = windowPos.y + OVERLAY_PADDING

        val buttons = mutableListOf<() -> Unit>()

        // --- Gizmo Controls ---
        val editorTools = scene?.gameObjectManager?.gameObjects?.find { it.name == "EditorTools" }
        val gizmoSystem = editorTools?.getComponent<GizmoSystem>()

        if (gizmoSystem != null && !isPlaying) {
            // Select Tool
            buttons.add {
                val isActive = gizmoSystem.usingGizmo == GizmoSystem.SELECTION_GIZMO
                if (isActive) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
                if (ImGui.button(Icons.MOUSE_POINTER, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    gizmoSystem.toggleGizmo(GizmoSystem.SELECTION_GIZMO)
                }
                if (isActive) ImGui.popStyleColor()
                if (ImGui.isItemHovered()) ImGui.setTooltip("Select Tool (Q)")
            }

            // Translate Tool
            buttons.add {
                val isActive = gizmoSystem.usingGizmo == GizmoSystem.TRANSLATE_GIZMO
                if (isActive) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
                if (ImGui.button(Icons.MOVE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    gizmoSystem.toggleGizmo(GizmoSystem.TRANSLATE_GIZMO)
                }
                if (isActive) ImGui.popStyleColor()
                if (ImGui.isItemHovered()) ImGui.setTooltip("Translate Tool (W)")
            }

            // Rotate Tool
            buttons.add {
                val isActive = gizmoSystem.usingGizmo == GizmoSystem.ROTATION_GIZMO
                if (isActive) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
                if (ImGui.button(Icons.ROTATE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    gizmoSystem.toggleGizmo(GizmoSystem.ROTATION_GIZMO)
                }
                if (isActive) ImGui.popStyleColor()
                if (ImGui.isItemHovered()) ImGui.setTooltip("Rotate Tool (E)")
            }

            // Scale Tool
            buttons.add {
                val isActive = gizmoSystem.usingGizmo == GizmoSystem.SCALE_GIZMO
                if (isActive) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
                if (ImGui.button(Icons.SCALE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    gizmoSystem.toggleGizmo(GizmoSystem.SCALE_GIZMO)
                }
                if (isActive) ImGui.popStyleColor()
                if (ImGui.isItemHovered()) ImGui.setTooltip("Scale Tool (R)")
            }

            // Measure Tool
            buttons.add {
                val isActive = gizmoSystem.usingGizmo == GizmoSystem.MEASURE_GIZMO
                if (isActive) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
                if (ImGui.button(Icons.RULER, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    gizmoSystem.toggleGizmo(GizmoSystem.MEASURE_GIZMO)
                }
                if (isActive) {
                    ImGui.popStyleColor()
                    // Render measurement tooltip
                    editorTools.getComponent<MeasureTool>()?.let { tool ->
                        tool.measurementText?.let { text ->
                            tool.measurementPos?.let { pos ->
                                ImGui.setNextWindowPos(pos.x, pos.y)
                                ImGui.beginTooltip()
                                ImGui.text(text)
                                ImGui.endTooltip()
                            }
                        }
                    }
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("Measure Tool (M)")
            }
        }

        // --- Center-aligned Buttons ---
        if (isPlaying) {
            buttons.add {
                if (scene?.timeScale == 1.0f) {
                    if (ImGui.button(Icons.PAUSE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                        scene.timeScale = 0.0f
                        logger.logEditor("Simulation paused")
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip("Pause Simulation (Time Scale: 0.0)")
                } else {
                    if (ImGui.button(Icons.PLAY, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                        scene?.timeScale = 1.0f
                        logger.logEditor("Simulation resumed")
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip("Resume Simulation (Time Scale: 1.0)")
                }
            }
            buttons.add {
                if (ImGui.button(Icons.STOP, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    sceneManager.runtimePlaying = false
                    scene?.timeScale = 1.0f // Reset timescale when stopping
                    logger.logEditor("Simulation stopped")
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("Stop Simulation")
            }
        } else {
            buttons.add {
                if (ImGui.button(Icons.PLAY, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    sceneManager.runtimePlaying = true
                    logger.logEditor("Simulation started")
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("Play Simulation")
            }
        }

        // --- All Buttons ---
        buttons.add {
            if (ImGui.button(Icons.GEAR, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                // Reset logic
                scene?.let{
                    scene.gameObjectManager.gameObjects.find { it.name == "Skateboard" }?.let { skate ->
                        skate.getComponent<Transform>()?.translation?.set(0f, 0.5f, 0f)
                        skate.getComponent<Transform>()?.rotation?.set(0f, 0f, 0f)
                        val rb = skate.getComponent<RigidBody3D>()
                        rb?.linearVelocity = Vector3f(0f, 0f, 0f)
                        rb?.angularVelocity = Vector3f(0f, 0f, 0f)
                        logger.logEditor("Scene reset")
                    }

                    scene.camera.position.set(0f, 5f, 20f)
                    scene.camera.yaw = 0f
                }
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Reset Scene")
        }
        buttons.add {
            val physicsDebugEnabled = scene?.physics3d?.debugEnabled ?: false
            if (physicsDebugEnabled) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            }
            if (ImGui.button(Icons.ATOM, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                scene?.physics3d?.debugEnabled = !physicsDebugEnabled
                logger.logEditor("Physics debug toggled")
            }
            if (physicsDebugEnabled) {
                ImGui.popStyleColor()
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Toggle Physics Debug Wireframe")
        }
        buttons.add {
            if (ImGui.button(Icons.CAMERA, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                val fbo = renderer.frameBuffer
                ScreenshotUtils.takeScreenshot(fbo.width, fbo.height, fbo.getFboId())
                logger.logEditor("Screenshot taken")
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Take Screenshot")
        }
        val totalButtonWidth = (TOOLBAR_BUTTON_HEIGHT * buttons.size) + (TOOLBAR_BUTTON_SPACING * (buttons.size - 1))
        val toolbarPosX = windowPos.x + (windowSize.x / 2f) - (totalButtonWidth / 2f)
        ImGui.setCursorPos(toolbarPosX, toolbarPosY)
        ImGui.beginChild(
            "GameViewportToolbar",
            totalButtonWidth,
            TOOLBAR_HEIGHT,
            false,
            ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoDecoration
        )

        buttons.forEachIndexed { index, button ->
            button()
            if (index < buttons.size - 1) {
                ImGui.sameLine(0f, TOOLBAR_BUTTON_SPACING)
            }
        }

        ImGui.endChild()
    }

    companion object {
        // Overlay Constants
        private const val OVERLAY_PADDING = 10f
        private const val FPS_OVERLAY_WIDTH = 80f
        private const val FPS_OVERLAY_HEIGHT = 30f
        private const val SPEED_OVERLAY_WIDTH = 120f
        private const val SPEED_OVERLAY_HEIGHT = 30f
        private const val TRICK_OVERLAY_WIDTH = 200f // Adjusted width for trick names
        private const val TRICK_OVERLAY_HEIGHT = 30f
        private const val TOOLBAR_HEIGHT = 40f
        private const val TOOLBAR_BUTTON_HEIGHT = 30f
        private const val TOOLBAR_BUTTON_SPACING = 10f
    }
}
