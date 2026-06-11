package com.pafoid.skate.editor.imgui.systems

import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.systems.AudioSystem
import imgui.ImGui

fun AudioSystem.imgui(stringManager: StringManager) {
    // Master Volume
    val volumeArray = floatArrayOf(masterVolume)
    if (ImGui.dragFloat(stringManager.getString("lbl.audio.master_volume"), volumeArray, 0.01f, 0f, 1f)) {
        masterVolume = volumeArray[0]
    }

    // Mute toggle
    val isMuted = masterVolume <= 0.001f
    if (ImGui.button(if (isMuted) stringManager.getString("btn.audio.unmute") else stringManager.getString("btn.audio.mute"))) {
        if (isMuted) {
            masterVolume = lastNonMutedVolume
        } else {
            if (masterVolume > 0.001f) {
                lastNonMutedVolume = masterVolume
            }
            masterVolume = 0.0f
        }
    }

    ImGui.separator()

    // Status
    val status = if (audioEngine.isInitialized) {
        stringManager.getString("lbl.audio.status.initialized")
    } else {
        stringManager.getString("lbl.audio.status.not_initialized")
    }
    val color = if (audioEngine.isInitialized) floatArrayOf(0f, 1f, 0f, 1f) else floatArrayOf(0.5f, 0.5f, 0.5f, 1f)
    ImGui.textColored(color[0], color[1], color[2], color[3], stringManager.getString("lbl.audio.status", status))

    ImGui.separator()

    // Listener info
    ImGui.text(stringManager.getString("lbl.audio.listener_information"))
    ImGui.text(stringManager.getString("lbl.audio.volume", masterVolume))
}