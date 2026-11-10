package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.components.imgui
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.getComponent
import imgui.ImGui

/** The selected object's audio source (same inspector as the Properties window) and the project mixer (EDT-14). */
class AudioInspectorWindow(private val engine: Engine, projectManager: ProjectManager) : EditorWindow("window.audio_inspector") {
    override val relatedWindows: List<String> = listOf("window.game_viewport")

    private val stringManager = engine.stringManager
    private val sceneManager = engine.sceneManager
    private val mixerPanel = AudioMixerPanel(stringManager, engine.eventSystem, projectManager) { settings ->
        engine.audioEngine.applyConfig(settings.toConfig())
    }

    override fun imgui() {
        ImGui.begin(stringManager.label("window.audio_inspector"))
        if (ImGui.collapsingHeader(stringManager.label("lbl.audio.selected_object"))) renderSelectedSource()
        if (ImGui.collapsingHeader(stringManager.label("lbl.audio.mixer"))) mixerPanel.render()
        ImGui.end()
    }

    private fun renderSelectedSource() {
        val selected = sceneManager.currentScene?.selectedGameObject
        if (selected == null) {
            MImGui.textDisabled(stringManager.getString("lbl.audio.no_object_selected"))
            return
        }
        val audio = selected.getComponent<AudioComponent>()
        if (audio == null) {
            MImGui.textDisabled(stringManager.getString("lbl.audio.no_audio_component_selected"))
            ImGui.separator()
            if (stringManager.tipped(ImGui.button("${stringManager.getString("btn.audio.add_component")}##add_audio"), "btn.audio.add_component")) {
                engine.eventSystem.publish(ViewportAction.AddComponent(selected, ComponentType.AUDIO))
            }
            return
        }
        audio.imgui(engine)
        ImGui.sameLine()
        if (stringManager.tipped(ImGui.button("${stringManager.getString("btn.audio.remove_component")}##remove_audio"), "btn.audio.remove_component")) {
            engine.eventSystem.publish(ViewportAction.RemoveComponent(audio))
        }
    }
}
