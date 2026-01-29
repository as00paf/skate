package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.utils.Icons
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.roundToInt

class GameViewWindow {

    var imageScreenPosX = 0f
    var imageScreenPosY = 0f
    var imageSizeX = 0f
    var imageSizeY = 0f

    val imageScreenPos: Vector2f
        get() = Vector2f(imageScreenPosX, imageScreenPosY)

    val imageSize: Vector2f
        get() = Vector2f(imageSizeX, imageSizeY)

    private var isPlaying = false
    private var hoveredGameObject: GameObject? = null
    private val gamepadOverlay = GamepadOverlay()
    private val trickUIWindow = TrickUIWindow()

    // Overlay Constants
    private val OVERLAY_PADDING = 10f
    private val FPS_OVERLAY_WIDTH = 80f
    private val FPS_OVERLAY_HEIGHT = 30f
    private val SPEED_OVERLAY_WIDTH = 120f
    private val SPEED_OVERLAY_HEIGHT = 30f
    private val TRICK_OVERLAY_WIDTH = 200f // Adjusted width for trick names
    private val TRICK_OVERLAY_HEIGHT = 30f
    private val CONTROLS_OVERLAY_BUTTON_SIZE = 60f
    private val CONTROLS_OVERLAY_HEIGHT = 40f

    fun imgui() {
        ImGui.begin("Game Viewport", ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse)

        val windowSize = getLargestSizeForViewport()
        val windowPos = getCenteredPositionForViewport(windowSize)
        ImGui.setCursorPos(windowPos.x, windowPos.y)

        // Capture EXACT screen position before drawing image
        val screenPos = ImVec2()
        ImGui.getCursorScreenPos(screenPos)
        imageScreenPosX = screenPos.x
        imageScreenPosY = screenPos.y
        imageSizeX = windowSize.x
        imageSizeY = windowSize.y

        val texId = Window.getFrameBuffer()?.getTextureId() ?: 0
        ImGui.image(texId.toLong(), windowSize.x, windowSize.y, 0f, 1f, 1f, 0f)

        // Drag and Drop Target
        if (ImGui.beginDragDropTarget()) {
            val payloadRail = ImGui.acceptDragDropPayload<String>("PREFAB_RAIL")
            val payloadLedge = ImGui.acceptDragDropPayload<String>("PREFAB_LEDGE")
            val payloadKicker = ImGui.acceptDragDropPayload<String>("PREFAB_KICKER")

            val payload = payloadRail ?: payloadLedge ?: payloadKicker
            
            if (payload != null) {
                val scene = SceneManager.getCurrentScene()
                if (scene != null) {
                    val mousePos = ImVec2()
                    ImGui.getMousePos(mousePos)
                    val relX = mousePos.x - imageScreenPosX
                    val relY = mousePos.y - imageScreenPosY
                    
                    val ray = scene.camera.screenToRay(relX, relY, imageSizeX, imageSizeY)
                    
                    // Intersect ray with ground plane (Y=0)
                    // P = O + t*D -> Py = 0 -> Oy + t*Dy = 0 -> t = -Oy / Dy
                    if (Math.abs(ray.direction.y) > 0.0001f) {
                        val t = -ray.origin.y / ray.direction.y
                        if (t > 0) {
                            val hitPoint = Vector3f(ray.direction).mul(t).add(ray.origin)
                            val prefabs = Window.getImGuiLayer().prefabsWindow
                            
                            when {
                                payloadRail != null -> prefabs.spawnRail(hitPoint)
                                payloadLedge != null -> prefabs.spawnLedge(hitPoint)
                                payloadKicker != null -> prefabs.spawnKicker(hitPoint)
                            }
                        }
                    }
                }
            }
            ImGui.endDragDropTarget()
        }

        renderViewportOverlays(windowPos, windowSize)

        // Render Gamepad Overlay
        if (com.pafoid.skate.engine.utils.SettingsManager.settings.showGamepadOverlay) {
            gamepadOverlay.imgui(Vector2f(imageScreenPosX, imageScreenPosY), Vector2f(imageSizeX, imageSizeY))
        }

        MouseListener.setGameViewportPos(Vector2f(imageScreenPosX, imageScreenPosY))
        MouseListener.setGameViewportSize(Vector2f(imageSizeX, imageSizeY))

        
        // ... (rest of picking logic)

        // Handle Object Hover & Picking
        val mousePos = ImVec2()
        ImGui.getMousePos(mousePos)
        
        val isInside = mousePos.x >= imageScreenPosX && mousePos.x <= (imageScreenPosX + imageSizeX) && 
                       mousePos.y >= imageScreenPosY && mousePos.y <= (imageScreenPosY + imageSizeY)

        if (!isPlaying && isInside) {
            val relativeX = mousePos.x - imageScreenPosX
            val relativeY = mousePos.y - imageScreenPosY
            
            // Gizmo Safety: Don't select/deselect if we are interacting with a gizmo
            var gizmoInteracting = false
            SceneManager.getCurrentScene()?.gameObjects?.forEach { go ->
                go.getComponent<com.pafoid.skate.engine.scenes.components.GizmoSystem>()?.let { system ->
                    if (system.isInteracting()) {
                        gizmoInteracting = true
                    }
                }
            }

            if (!gizmoInteracting) {
                // Map relative coordinate to 1920x1080 picking texture with high precision
                val pickingX = ((relativeX / imageSizeX) * 1920f).toInt().coerceIn(0, 1919)
                val pickingY = ((relativeY / imageSizeY) * 1080f).toInt().coerceIn(0, 1079)
                
                hoveredGameObject = SceneManager.get().getPickedObject(pickingX, pickingY)
                
                // Debug Info Overlay
                ImGui.setCursorPos(windowPos.x + 10f, windowPos.y + 10f)
                ImGui.textColored(1f, 1f, 1f, 0.5f, "Picked ID: ${hoveredGameObject?.getUid() ?: -1} at ($pickingX, $pickingY)")

                if (MouseListener.mouseButtonBeginPress(0)) {
                    Window.getImGuiLayer().propertiesWindow.setActiveObject(hoveredGameObject)
                }
            }
        } else {
            hoveredGameObject = null
        }

        ImGui.end()
    }

    private fun renderViewportOverlays(windowPos: ImVec2, windowSize: ImVec2) {
        val isPlaying = SceneManager.isPlaying()
        val scene = SceneManager.getCurrentScene()
        
        // FPS Overlay (Top Left)
        ImGui.setCursorPos(windowPos.x + OVERLAY_PADDING, windowPos.y + OVERLAY_PADDING)
        ImGui.beginChild("FPS_Overlay", FPS_OVERLAY_WIDTH, FPS_OVERLAY_HEIGHT, false, imgui.flag.ImGuiWindowFlags.NoBackground or imgui.flag.ImGuiWindowFlags.NoDecoration)
        ImGui.textColored(0f, 1f, 0f, 1f, "FPS: ${ImGui.getIO().framerate.toInt()}")
        ImGui.endChild()

        // Speedometer Overlay (Bottom Left)
        val skateGo = scene?.gameObjects?.find { it.name == "Skateboard" }
        val rb = skateGo?.getComponent<com.pafoid.skate.engine.physics3d.components.RigidBody3D>()
        val velocity = rb?.rawBody?.getLinearVelocity(null)
        if (velocity != null) {
            val speedMS = velocity.length()
            val settings = com.pafoid.skate.engine.utils.SettingsManager.settings
            val (speedDisplay, unitLabel) = if (settings.unitSystem == UnitSystem.METRIC) {
                Pair(speedMS * 3.6f, "km/h")
            } else {
                Pair(speedMS * 2.23694f, "mph")
            }

            ImGui.setCursorPos(windowPos.x + OVERLAY_PADDING, windowPos.y + windowSize.y - SPEED_OVERLAY_HEIGHT - OVERLAY_PADDING)
            ImGui.beginChild("Speed_Overlay", SPEED_OVERLAY_WIDTH, SPEED_OVERLAY_HEIGHT, false, imgui.flag.ImGuiWindowFlags.NoBackground or imgui.flag.ImGuiWindowFlags.NoDecoration)
            ImGui.textColored(1f, 1f, 1f, 1f, "${speedDisplay.roundToInt()} $unitLabel")
            ImGui.endChild()
        }

        // Trick UI Overlay (Bottom Left, above Speedometer)
        skateGo?.let { go ->
            trickUIWindow.setTrickGameObject(go)
            // Position above the speed overlay
            val trickX = windowPos.x + OVERLAY_PADDING
            val trickY = windowPos.y + windowSize.y - SPEED_OVERLAY_HEIGHT - TRICK_OVERLAY_HEIGHT - (OVERLAY_PADDING * 2)

            trickUIWindow.imgui(trickX, trickY, TRICK_OVERLAY_WIDTH, TRICK_OVERLAY_HEIGHT)
        }

        // Controls Overlay (Top Right)
        val buttonSize = CONTROLS_OVERLAY_BUTTON_SIZE
        ImGui.setCursorPos(windowPos.x + windowSize.x - (buttonSize * 3f) - (OVERLAY_PADDING * 3), windowPos.y + OVERLAY_PADDING)
        ImGui.beginChild("Controls_Overlay", buttonSize * 3f + (OVERLAY_PADDING * 2), CONTROLS_OVERLAY_HEIGHT, false, imgui.flag.ImGuiWindowFlags.NoBackground or imgui.flag.ImGuiWindowFlags.NoDecoration)
        
        if (isPlaying) {
            if (ImGui.button("${Icons.STOP} Stop", buttonSize, 30f)) {
                SceneManager.setPlaying(false)
            }
        } else {
            if (ImGui.button("${Icons.PLAY} Play", buttonSize, 30f)) {
                SceneManager.setPlaying(true)
            }
        }
        ImGui.sameLine()
        
        val measureTool = scene?.gameObjects?.find { it.name == "EditorTools" }?.getComponent<com.pafoid.skate.engine.scenes.components.MeasureTool>()
        val measureActive = measureTool?.isToolActive() ?: false
        if (measureActive) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            
            // Render Measure Tooltip
            measureTool?.measurementText?.let { text ->
                measureTool.measurementPos?.let { pos ->
                    ImGui.setNextWindowPos(pos.x, pos.y)
                    ImGui.beginTooltip()
                    ImGui.text(text)
                    ImGui.endTooltip()
                }
            }
        }
        if (ImGui.button("${Icons.RULER} Measure", buttonSize + 10f, 30f)) {
            measureTool?.toggle()
        }
        if (measureActive) {
            ImGui.popStyleColor()
        }

        ImGui.sameLine()
        if (ImGui.button("${Icons.GEAR} Reset", buttonSize, 30f)) {
            // Reset logic
            scene?.gameObjects?.find { it.name == "Skateboard" }?.let { skate ->
                skate.transform.translation.set(0f, 0.5f, 0f)
                skate.transform.rotation.set(0f, 0f, 0f)
                val rb = skate.getComponent<com.pafoid.skate.engine.physics3d.components.RigidBody3D>()
                // In Editor update, the RB syncs from transform, so we just reset velocities
                rb?.linearVelocity = Vector3f(0f, 0f, 0f)
                rb?.angularVelocity = Vector3f(0f, 0f, 0f)
            }
        }
        
        ImGui.endChild()
    }

    fun getHoveredObject(): GameObject? = hoveredGameObject

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

    fun getWantCaptureMouse(): Boolean {
        val mousePos = ImVec2()
        ImGui.getMousePos(mousePos)
        return mousePos.x >= imageScreenPosX && mousePos.x <= (imageScreenPosX + imageSizeX) && 
               mousePos.y >= imageScreenPosY && mousePos.y <= (imageScreenPosY + imageSizeY)
    }
}
