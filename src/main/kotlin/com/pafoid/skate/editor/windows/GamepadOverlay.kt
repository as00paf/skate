package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.input.listeners.GamepadConstants
import com.pafoid.skate.engine.input.listeners.GamepadListener
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import org.joml.Vector2f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1
import kotlin.math.min

private const val CONTROLS_OVERLAY_BUTTON_SIZE = 50f

class GamepadOverlay : KoinComponent {
    private val resourceManager: ResourceManager by inject()
    private val joystickListener: GamepadListener by inject()
    private val settingsManager: SettingsManager by inject()
    
    private val controllerTexture: Texture by lazy {
        resourceManager.loadTextureSync(Assets.Textures.XBOX_CONTROLLER)
    }

    fun imgui(gameViewPos: Vector2f, gameViewSize: Vector2f) {
        val settings = settingsManager.settings
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
        val scale = min(scaleX, scaleY)
        
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

            val axes = joystickListener.getAxes(GLFW_JOYSTICK_1)
            val buttons = joystickListener.getButtons(GLFW_JOYSTICK_1)

            // Dynamic Stick Highlights
            val lsPos = ImVec2(windowPos.x + displayWidth * 0.245f, windowPos.y + displayHeight * 0.305f)
            val rsPos = ImVec2(windowPos.x + displayWidth * 0.615f, windowPos.y + displayHeight * 0.518f)
            val stickRadius = 75f * scale

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
                val bSize = CONTROLS_OVERLAY_BUTTON_SIZE * scale

                val horizontalSpacing = bSize * 2.05f
                val verticalSpacing = bSize * 2f
                
                // Highlight pressed buttons with 50% transparency
                // A (Bottom)
                if (buttons.size > GamepadConstants.BUTTON_A && buttons[GamepadConstants.BUTTON_A]) {
                    drawList.addCircleFilled(
                        buttonBaseX,
                        buttonBaseY + verticalSpacing,
                        bSize,
                        ImGui.getColorU32(0f, 1f, 0f, 0.5f)
                    )
                }
                // B (Right)
                if (buttons.size > GamepadConstants.BUTTON_B && buttons[GamepadConstants.BUTTON_B]) {
                    drawList.addCircleFilled(
                        buttonBaseX + horizontalSpacing,
                        buttonBaseY,
                        bSize,
                        ImGui.getColorU32(1f, 0f, 0f, 0.5f)
                    )
                }
                // X (Left)
                if (buttons.size > GamepadConstants.BUTTON_X && buttons[GamepadConstants.BUTTON_X]) {
                    drawList.addCircleFilled(
                        buttonBaseX - horizontalSpacing,
                        buttonBaseY,
                        bSize,
                        ImGui.getColorU32(0.1f, 0.4f, 1f, 0.5f)
                    )
                }
                // Y (Top)
                if (buttons.size > GamepadConstants.BUTTON_Y && buttons[GamepadConstants.BUTTON_Y]) {
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
