package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.TrickCompleted
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags

class TrickUIWindow(private val eventSystem: EventSystem) {

    private var lastCompletedTrick: String? = null
    private var lastScore: Int = 0
    private var lastStyle: Float = 1.0f
    private var trickDisplayTime: Float = 0f
    private val TRICK_DISPLAY_DURATION = 3.0f // Show trick for 3 seconds

    fun init(scene: Scene) {
        // Subscribe to trick completed events
        eventSystem.subscribe<TrickCompleted> { event ->
            lastCompletedTrick = event.trickName
            lastScore = event.score
            lastStyle = event.style
            trickDisplayTime = TRICK_DISPLAY_DURATION
        }
    }

    fun imgui(xPos: Float, yPos: Float, width: Float, height: Float) {
        // Update display timer
        if (trickDisplayTime > 0f) {
            trickDisplayTime -= ImGui.getIO().deltaTime
        }

        // Show trick name if recently completed
        val displayText = if (trickDisplayTime > 0f && lastCompletedTrick != null) {
            "$lastCompletedTrick (${lastScore} pts)"
        } else {
            ""
        }

        ImGui.setCursorPos(xPos, yPos)
        ImGui.beginChild("##TrickOverlay", width, height, false, ImGuiWindowFlags.NoDecoration or ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoSavedSettings or ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoInputs)
        ImGui.setWindowFontScale(1.5f)

        if (displayText.isNotEmpty()) {
            // Fade out effect based on remaining display time
            val alpha = (trickDisplayTime / TRICK_DISPLAY_DURATION).coerceIn(0f, 1f)
            ImGui.textColored(1f, 0.5f, 0f, alpha, displayText)
        }
        
        ImGui.endChild()
    }
}
