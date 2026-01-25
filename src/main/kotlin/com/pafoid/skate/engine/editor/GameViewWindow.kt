package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.scenes.SceneManager
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import org.joml.Vector2f
import kotlin.math.roundToInt

class GameViewWindow {

    private var imageScreenPosX = 0f
    private var imageScreenPosY = 0f
    private var imageSizeX = 0f
    private var imageSizeY = 0f
    private var isPlaying = false
    private var hoveredGameObject: com.pafoid.skate.engine.scenes.GameObject? = null

    fun imgui() {
        ImGui.begin("Game Viewport", ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.MenuBar)

        val isPlaying = SceneManager.isPlaying()
        ImGui.beginMenuBar()
        if (ImGui.menuItem("Play", "", isPlaying, !isPlaying)) {
            SceneManager.setPlaying(true)
        }
        if (ImGui.menuItem("Stop", "", !isPlaying, isPlaying)) {
            SceneManager.setPlaying(false)
        }
        
        ImGui.text("World (${MouseListener.getWorldX().roundToInt()},${MouseListener.getWorldY().roundToInt()})")
        ImGui.endMenuBar()

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

        val texId = Window.getFrameBuffer().getTextureId()
        ImGui.image(texId.toLong(), windowSize.x, windowSize.y, 0f, 1f, 1f, 0f)

        MouseListener.setGameViewportPos(Vector2f(imageScreenPosX, imageScreenPosY))
        MouseListener.setGameViewportSize(Vector2f(imageSizeX, imageSizeY))

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

    fun getHoveredObject(): com.pafoid.skate.engine.scenes.GameObject? = hoveredGameObject

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
