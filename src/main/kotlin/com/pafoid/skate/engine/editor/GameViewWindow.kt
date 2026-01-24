package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.controls.MouseListener
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import org.joml.Vector2f
import kotlin.math.roundToInt

class GameViewWindow {

    private var leftX = 0f
    private var rightX = 0f
    private var topY = 0f
    private var bottomY = 0f
    private var isPlaying = false

    fun imgui() {
        ImGui.begin("Game Viewport", ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.MenuBar)

        ImGui.beginMenuBar()
        if (ImGui.menuItem("Play", "", isPlaying, !isPlaying)) {
            isPlaying = true
        }
        if (ImGui.menuItem("Stop", "", !isPlaying, isPlaying)) {
            isPlaying = false
        }
        
        ImGui.text("World (${MouseListener.getWorldX().roundToInt()},${MouseListener.getWorldY().roundToInt()})")
        ImGui.endMenuBar()

        val windowSize = getLargestSizeForViewport()
        val windowPos = getCenteredPositionForViewport(windowSize)
        ImGui.setCursorPos(windowPos.x, windowPos.y)

        val viewportX = windowPos.x
        val viewportY = windowPos.y
        
        // We need to calculate the actual screen position of this viewport for MouseListener
        val screenPos = ImVec2()
        ImGui.getCursorScreenPos(screenPos)
        
        leftX = screenPos.x
        rightX = screenPos.x + windowSize.x
        topY = screenPos.y
        bottomY = screenPos.y + windowSize.y

        val texId = Window.getFrameBuffer().getTextureId()
        ImGui.image(texId.toLong(), windowSize.x, windowSize.y, 0f, 1f, 1f, 0f)

        MouseListener.setGameViewportPos(Vector2f(leftX, topY))
        MouseListener.setGameViewportSize(Vector2f(windowSize.x, windowSize.y))

        ImGui.end()
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

    fun getWantCaptureMouse(): Boolean {
        return MouseListener.getX() in leftX..rightX && MouseListener.getY() in topY..bottomY
    }
}