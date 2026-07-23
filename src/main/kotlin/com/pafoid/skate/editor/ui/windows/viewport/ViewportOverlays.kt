package com.pafoid.skate.editor.ui.windows.viewport

import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.ui.windows.TrickUIWindow
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Renders viewport overlays including FPS, speedometer, and trick UI.
 *
 * This component handles:
 * - FPS counter (top-left)
 * - Speedometer (bottom-left, shows speed in km/h or mph)
 * - Trick UI (bottom-left, above speedometer)
 *
 * @param trickUIWindow The trick UI window to render
 * @param settingsManager For accessing unit system settings
 */
class ViewportOverlays(
    private val trickUIWindow: TrickUIWindow,
    private val settingsManager: SettingsManager
) {
    
    companion object {
        private const val OVERLAY_PADDING = 10f
        private const val FPS_OVERLAY_WIDTH = 80f
        private const val FPS_OVERLAY_HEIGHT = 30f
        private const val SPEED_OVERLAY_WIDTH = 120f
        private const val SPEED_OVERLAY_HEIGHT = 30f
        private const val TRICK_OVERLAY_WIDTH = 200f
        private const val TRICK_OVERLAY_HEIGHT = 30f
        private const val TOOLBAR_HEIGHT = 40f
    }
    
    private var trickUIInitialized = false
    
    /**
     * Renders all viewport overlays.
     * 
     * @param windowPos The window position for overlay placement
     * @param windowSize The window size for overlay placement
     * @param scene The current scene for speed calculation
     */
    fun render(windowPos: ImVec2, windowSize: ImVec2, scene: Scene?) {
        // Initialize TrickUIWindow with event subscriptions (once per scene)
        if (scene != null && !trickUIInitialized) {
            trickUIWindow.init(scene)
            trickUIInitialized = true
        }
        
        renderFpsOverlay(windowPos, windowSize)
        renderSpeedometerOverlay(windowPos, windowSize, scene)
        renderTrickOverlay(windowPos, windowSize)
    }
    
    private fun renderFpsOverlay(windowPos: ImVec2, windowSize: ImVec2) {
        // FPS Overlay (Top Left - inside game view)
        ImGui.setCursorPos(windowPos.x + OVERLAY_PADDING, windowPos.y + TOOLBAR_HEIGHT + OVERLAY_PADDING)
        ImGui.beginChild(
            "FPS_Overlay",
            FPS_OVERLAY_WIDTH,
            FPS_OVERLAY_HEIGHT,
            false,
            ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoDecoration
        )
        ImGui.textColored(0f, 1f, 0f, 1f, "FPS: ${ImGui.getIO().framerate.toInt()}")
        ImGui.endChild()
    }
    
    private fun renderSpeedometerOverlay(windowPos: ImVec2, windowSize: ImVec2, scene: Scene?) {
        // Speedometer Overlay (Bottom Left)
        val skateGo = scene?.gameObjects?.find { it.name == "Skateboard" }
        val rb = skateGo?.getComponent<RigidBody3D>()
        val velocity = rb?.linearVelocity

        if (velocity != null) {
            val speedMS = sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z).toFloat()
            val unitSystem = settingsManager.editor.unitSystem
            val speedDisplay: Float
            val unitLabel: String
            if (unitSystem == UnitSystem.METRIC) {
                speedDisplay = (speedMS * 3.6f).toFloat()
                unitLabel = "km/h"
            } else {
                speedDisplay = (speedMS * 2.23694f).toFloat()
                unitLabel = "mph"
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
    }
    
    private fun renderTrickOverlay(windowPos: ImVec2, windowSize: ImVec2) {
        // Trick UI Overlay (Bottom Left, above Speedometer)
        val trickX = windowPos.x + OVERLAY_PADDING
        val trickY = windowPos.y + windowSize.y - SPEED_OVERLAY_HEIGHT - TRICK_OVERLAY_HEIGHT - (OVERLAY_PADDING * 2)
        trickUIWindow.imgui(trickX, trickY, TRICK_OVERLAY_WIDTH, TRICK_OVERLAY_HEIGHT)
    }
    
    /**
     * Reset the trick UI initialization state.
     * Call this when scene changes.
     */
    fun resetTrickUI() {
        trickUIInitialized = false
    }
}
