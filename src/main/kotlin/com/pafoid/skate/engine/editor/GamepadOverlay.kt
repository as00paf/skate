package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.controls.JoystickListener
import com.pafoid.skate.engine.utils.SettingsManager
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1

class GamepadOverlay {
    private val controllerTexture: Texture by lazy {
        AssetPool.getTexture("assets/textures/xbox_controller.png")
    }

    fun imgui(gameViewPos: Vector2f, gameViewSize: Vector2f) {
        val settings = SettingsManager.settings
        val windowFlags = ImGuiWindowFlags.NoDecoration or
                         ImGuiWindowFlags.NoInputs or
                         ImGuiWindowFlags.AlwaysAutoResize or
                         ImGuiWindowFlags.NoFocusOnAppearing or
                         ImGuiWindowFlags.NoNav or
                         ImGuiWindowFlags.NoBackground

        // Target size: customizable percentage of viewport width/height
        val maxOverlayWidth = gameViewSize.x * settings.gamepadOverlaySize
        val maxOverlayHeight = gameViewSize.y * settings.gamepadOverlaySize
        
        // Calculate scale to fit within user-defined percentage of viewport
        val scaleX = maxOverlayWidth / controllerTexture.width
        val scaleY = maxOverlayHeight / controllerTexture.height
        val scale = Math.min(scaleX, scaleY)
        
        val displayWidth = controllerTexture.width * scale
        val displayHeight = controllerTexture.height * scale

        val padding = 10f
        val overlayPosX = gameViewPos.x + gameViewSize.x - displayWidth - padding
        val overlayPosY = gameViewPos.y + gameViewSize.y - displayHeight - padding

        ImGui.setNextWindowPos(overlayPosX, overlayPosY)
        ImGui.setNextWindowSize(displayWidth, displayHeight)
        ImGui.setNextWindowBgAlpha(0.0f)

        if (ImGui.begin("Gamepad Overlay", windowFlags)) {
            val drawList = ImGui.getWindowDrawList()
            val windowPos = ImGui.getWindowPos()

            // Draw Controller Background
            drawList.addImage(controllerTexture.texId.toLong(), 
                windowPos.x, windowPos.y, 
                windowPos.x + displayWidth, windowPos.y + displayHeight,
                0f, 0f, 1f, 1f,
                ImGui.getColorU32(1f, 1f, 1f, 0.7f))

            val axes = JoystickListener.getAxes(GLFW_JOYSTICK_1)
            val buttons = JoystickListener.getButtons(GLFW_JOYSTICK_1)

            // Dynamic Stick Highlights
            val lsPos = ImVec2(windowPos.x + displayWidth * 0.25f, windowPos.y + displayHeight * 0.275f)
            val rsPos = ImVec2(windowPos.x + displayWidth * 0.625f, windowPos.y + displayHeight * 0.525f)
            val stickRadius = 125f * scale

            if (axes != null && axes.size >= 4) {
                // Background of the stick area for better visibility
                drawList.addCircleFilled(lsPos.x, lsPos.y, stickRadius, ImGui.getColorU32(1f, 1f, 1f, 0.2f))
                drawList.addCircleFilled(rsPos.x, rsPos.y, stickRadius, ImGui.getColorU32(1f, 1f, 1f, 0.2f))

                // Left Stick - brighter red
                drawList.addCircleFilled(lsPos.x + axes[0] * stickRadius, lsPos.y + axes[1] * stickRadius, 10f * scale, ImGui.getColorU32(1f, 0.2f, 0.2f, 1.0f))
                
                // Right Stick - brighter red
                drawList.addCircleFilled(rsPos.x + axes[2] * stickRadius, rsPos.y + axes[3] * stickRadius, 10f * scale, ImGui.getColorU32(1f, 0.2f, 0.2f, 1.0f))
            }

            if (buttons != null) {
                // Adjusting the base for the new larger layout, scaling offsets
                val buttonBaseX = windowPos.x + displayWidth * 0.7375f
                val buttonBaseY = windowPos.y + displayHeight * 0.305f
                val bSize = 50f * scale

                val horizontalSpacing = bSize * 2.05f
                val verticalSpacing = bSize * 2f
                
                // Highlight pressed buttons with 50% transparency
                // A (Bottom)
                if (buttons.size > JoystickListener.BUTTON_A && buttons[JoystickListener.BUTTON_A]) {
                    drawList.addCircleFilled(
                        buttonBaseX,
                        buttonBaseY + verticalSpacing,
                        bSize,
                        ImGui.getColorU32(0f, 1f, 0f, 0.5f)
                    )
                }
                // B (Right)
                if (buttons.size > JoystickListener.BUTTON_B && buttons[JoystickListener.BUTTON_B]) {
                    drawList.addCircleFilled(
                        buttonBaseX + horizontalSpacing,
                        buttonBaseY,
                        bSize,
                        ImGui.getColorU32(1f, 0f, 0f, 0.5f)
                    )
                }
                // X (Left)
                if (buttons.size > JoystickListener.BUTTON_X && buttons[JoystickListener.BUTTON_X]) {
                    drawList.addCircleFilled(
                        buttonBaseX - horizontalSpacing,
                        buttonBaseY,
                        bSize,
                        ImGui.getColorU32(0.1f, 0.4f, 1f, 0.5f)
                    )
                }
                // Y (Top)
                if (buttons.size > JoystickListener.BUTTON_Y && buttons[JoystickListener.BUTTON_Y]) {
                    drawList.addCircleFilled(
                        buttonBaseX,
                        buttonBaseY - verticalSpacing,
                        bSize,
                        ImGui.getColorU32(1f, 1f, 0f, 0.5f)
                    )
                }
            }

            ImGui.end()
        }
    }
}
