package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AudioComponent
import imgui.ImGui
import imgui.type.ImString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Editor window for audio component inspector.
 * 
 * Features:
 * - Audio component inspector for selected objects
 * - Play/stop controls
 * - Volume and 3D settings
 */
class AudioInspectorWindow : KoinComponent {

    private val stringManager: StringManager by inject()

    /**
     * Renders the audio inspector window.
     */
    fun imgui(scene: Scene?) {
        ImGui.begin(stringManager.getString("window.audio_inspector"))

        if (scene != null) {
            renderAudioComponentInspector(scene)
        } else {
            ImGui.textColored(0.5f, 0.5f, 0.5f, 1.0f, "No scene loaded")
        }

        ImGui.end()
    }

    /**
     * Renders audio component inspector for selected object.
     */
    private fun renderAudioComponentInspector(scene: Scene) {
        if (ImGui.collapsingHeader(stringManager.getString("lbl.audio.selected_object"))) {
            val selectedObject = scene.getSelectedGameObject()

            if (selectedObject == null) {
                ImGui.textColored(0.5f, 0.5f, 0.5f, 1.0f, "No object selected")
                return
            }

            val audioComponent = selectedObject.getComponent<AudioComponent>()

            if (audioComponent == null) {
                ImGui.textColored(0.5f, 0.5f, 0.5f, 1.0f, "No AudioComponent on selected object")

                ImGui.separator()

                // Add audio component button
                if (ImGui.button(stringManager.getString("btn.audio.add_component"))) {
                    selectedObject.addComponent(AudioComponent())
                }
                return
            }

            // Sound file path
            val filePath = ImString(audioComponent.soundFilePath, 256)
            ImGui.inputText(
                stringManager.getString("lbl.audio.sound_file"),
                filePath
            )

            if (ImGui.beginDragDropTarget()) {
                val payload = ImGui.acceptDragDropPayload<String>("SOUND")
                if (payload != null) {
                    audioComponent.soundFilePath = payload
                }
                ImGui.endDragDropTarget()
            } else {
                audioComponent.soundFilePath = filePath.get()
            }

            ImGui.separator()

            // 3D toggle
            val is3D = audioComponent.is3D
            if (ImGui.checkbox(stringManager.getString("lbl.audio.is_3d"), is3D)) {
                audioComponent.apply3D(!is3D)
            }

            // Looping toggle
            val loops = audioComponent.loops
            if (ImGui.checkbox(stringManager.getString("lbl.audio.looping"), loops)) {
                audioComponent.applyLooping(!loops)
            }

            ImGui.separator()

            // Volume control
            val volumeArray = floatArrayOf(audioComponent.volume)
            if (ImGui.dragFloat(stringManager.getString("lbl.audio.volume"), volumeArray, 0.01f, 0f, 1f)) {
                audioComponent.applyVolume(volumeArray[0])
            }

            ImGui.separator()

            // Playback controls
            val isPlaying = audioComponent.isPlaying

            if (!isPlaying) {
                if (ImGui.button(stringManager.getString("btn.audio.play"))) {
                    audioComponent.play()
                }
            } else {
                if (ImGui.button(stringManager.getString("btn.audio.stop"))) {
                    audioComponent.stop()
                }
            }

            ImGui.sameLine()

            if (ImGui.button(stringManager.getString("btn.audio.remove_component"))) {
                selectedObject.removeComponent<AudioComponent>()
            }
        }
    }
}

// Extension function to get selected game object from scene
fun Scene.getSelectedGameObject(): com.pafoid.skate.engine.ecs.GameObject? {
    // This would need to be implemented based on how selection is tracked
    // For now, return null - the actual implementation would use a selection service
    return null
}
