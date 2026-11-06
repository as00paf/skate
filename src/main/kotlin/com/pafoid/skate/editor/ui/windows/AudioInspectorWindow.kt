package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.components.imgui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.getComponent
import imgui.ImGui

/** The selected object's audio source (same inspector as the Properties window) and the project mixer (EDT-14). */
class AudioInspectorWindow(private val engine: Engine, private val projectManager: ProjectManager) : EditorWindow(NAME) {

    override val icon = Icons.MUSIC
    override val relatedWindows = listOf("window.properties", "window.asset_browser")

    private val stringManager = engine.stringManager
    private val sceneManager = engine.sceneManager
    private val mixerPanel = AudioMixerPanel(stringManager, engine.eventSystem, projectManager) { settings ->
        engine.audioEngine.applyConfig(settings.toConfig())
    }

    /** What the "Selected object" section shows (EDT-51 §2.5). */
    enum class SourceState { NO_SCENE, NOTHING_SELECTED, NO_AUDIO_COMPONENT, SOURCE }

    override fun imgui() {
        if (beginWindow(stringManager)) {
            header(stringManager)
            if (projectManager.currentProject == null) {
                EmptyState.noProject(stringManager, engine.eventSystem)
            } else {
                if (ImGui.collapsingHeader(stringManager.label("lbl.audio.selected_object"))) renderSelectedSource()
                if (ImGui.collapsingHeader(stringManager.label("lbl.audio.mixer"))) mixerPanel.render()
            }
        }
        ImGui.end()
    }

    private fun renderSelectedSource() {
        val scene = sceneManager.currentScene
        val selected = scene?.selectedGameObject
        val audio = selected?.getComponent<AudioComponent>()
        when (sourceState(scene != null, selected != null, audio != null)) {
            SourceState.NO_SCENE -> AudioMixerPanel.emptySection("##audio_no_scene") { EmptyState.noScene(stringManager) }
            SourceState.NOTHING_SELECTED -> AudioMixerPanel.emptySection("##audio_no_selection") {
                EmptyState.nothingSelected(stringManager, "lbl.audio.no_object_selected")
            }
            SourceState.NO_AUDIO_COMPONENT -> AudioMixerPanel.emptySection("##audio_no_component", withAction = true) {
                EmptyState.render(
                    stringManager, Icons.MUSIC, "lbl.audio.no_audio_component_selected",
                    actions = listOf(EmptyState.Action("btn.audio.add_component", Icons.PLUS) {
                        selected?.let { engine.eventSystem.publish(ViewportAction.AddComponent(it, ComponentType.AUDIO)) }
                    }),
                )
            }
            SourceState.SOURCE -> if (audio != null) {
                audio.imgui(engine)
                ImGui.sameLine()
                if (stringManager.tipped(ImGui.button("${stringManager.getString("btn.audio.remove_component")}##remove_audio"), "btn.audio.remove_component")) {
                    engine.eventSystem.publish(ViewportAction.RemoveComponent(audio))
                }
            }
        }
    }

    companion object {
        const val NAME = "window.audio_inspector"

        fun sourceState(hasScene: Boolean, hasSelection: Boolean, hasAudio: Boolean): SourceState = when {
            !hasScene -> SourceState.NO_SCENE
            !hasSelection -> SourceState.NOTHING_SELECTED
            !hasAudio -> SourceState.NO_AUDIO_COMPONENT
            else -> SourceState.SOURCE
        }
    }
}
