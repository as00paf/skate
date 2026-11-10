package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.AudioAction
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.settings.AudioSettings
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui

/**
 * Per-bus volume, mute, low-pass and reverb-send controls. Edits are committed as one command on release; while a
 * slider is held, [preview] applies the draft to the live mixer so the change is heard at once (EDT-14).
 */
class AudioMixerPanel(
    private val strings: StringManager,
    private val events: EventSystem,
    private val projectManager: ProjectManager,
    private val preview: (AudioSettings) -> Unit = {},
) {
    private var draft: AudioSettings? = null

    fun render() {
        val project = projectManager.currentProject
        if (project == null) {
            emptySection("##mixer_no_project", withAction = true) { EmptyState.noProject(strings, events) }
            return
        }
        var settings = draft ?: project.gameplaySettings.audio
        var changed = false
        var commit = false

        fun slider(label: String, tooltip: String, value: Float, apply: (Float) -> AudioSettings) {
            val a = floatArrayOf(value)
            if (ImGui.sliderFloat(label, a, 0f, 1f)) { settings = apply(a[0]); changed = true }
            if (ImGui.isItemDeactivatedAfterEdit()) commit = true
            if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString(tooltip))
        }

        fun mute(label: String, value: Boolean, apply: (Boolean) -> AudioSettings) {
            if (ImGui.checkbox(label, value)) { settings = apply(!value); changed = true; commit = true }
            if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("lbl.audio.mute.tooltip"))
        }

        fun header(key: String) { ImGui.separator(); ImGui.text(strings.getString(key)) }

        val s0 = settings
        header("lbl.audio.bus.master")
        slider("${strings.getString("lbl.audio.bus.volume")}##master", "tooltip.audio.bus.volume", s0.masterVolume) { settings.copy(masterVolume = it) }
        mute("${strings.getString("lbl.audio.mute")}##master", s0.masterMuted) { settings.copy(masterMuted = it) }
        slider("${strings.getString("lbl.audio.low_pass")}##master", "tooltip.audio.bus.low_pass", s0.masterLowPass) { settings.copy(masterLowPass = it) }
        header("lbl.audio.bus.music")
        slider("${strings.getString("lbl.audio.bus.volume")}##music", "tooltip.audio.bus.volume", s0.musicVolume) { settings.copy(musicVolume = it) }
        mute("${strings.getString("lbl.audio.mute")}##music", s0.musicMuted) { settings.copy(musicMuted = it) }
        slider("${strings.getString("lbl.audio.low_pass")}##music", "tooltip.audio.bus.low_pass", s0.musicLowPass) { settings.copy(musicLowPass = it) }
        slider("${strings.getString("lbl.audio.reverb_send")}##music", "tooltip.audio.bus.reverb_send", s0.musicReverbSend) { settings.copy(musicReverbSend = it) }
        header("lbl.audio.bus.sfx")
        slider("${strings.getString("lbl.audio.bus.volume")}##sfx", "tooltip.audio.bus.volume", s0.sfxVolume) { settings.copy(sfxVolume = it) }
        mute("${strings.getString("lbl.audio.mute")}##sfx", s0.sfxMuted) { settings.copy(sfxMuted = it) }
        slider("${strings.getString("lbl.audio.low_pass")}##sfx", "tooltip.audio.bus.low_pass", s0.sfxLowPass) { settings.copy(sfxLowPass = it) }
        slider("${strings.getString("lbl.audio.reverb_send")}##sfx", "tooltip.audio.bus.reverb_send", s0.sfxReverbSend) { settings.copy(sfxReverbSend = it) }
        header("lbl.audio.bus.ui")
        slider("${strings.getString("lbl.audio.bus.volume")}##ui", "tooltip.audio.bus.volume", s0.uiVolume) { settings.copy(uiVolume = it) }
        mute("${strings.getString("lbl.audio.mute")}##ui", s0.uiMuted) { settings.copy(uiMuted = it) }
        slider("${strings.getString("lbl.audio.low_pass")}##ui", "tooltip.audio.bus.low_pass", s0.uiLowPass) { settings.copy(uiLowPass = it) }
        slider("${strings.getString("lbl.audio.reverb_send")}##ui", "tooltip.audio.bus.reverb_send", s0.uiReverbSend) { settings.copy(uiReverbSend = it) }

        if (commit) {
            draft = null
            events.publish(AudioAction.EditBuses(settings))
        } else if (changed) {
            draft = settings
            preview(settings)
        } else if (draft != null && !ImGui.isAnyItemActive()) {
            // Released without an edit (or the edit was cancelled): back to the saved mix.
            draft = null
            preview(project.gameplaySettings.audio)
        }
    }

    companion object {
        /** Lines of height an empty section takes: message and hint, plus a button row [withAction]. */
        fun emptySectionLines(withAction: Boolean): Float = if (withAction) 5f else 3.5f

        /**
         * A shared empty state ([content], an `EmptyState` call) inside a collapsing section. `EmptyState` centres in
         * the space left, so a short child keeps it from pushing the next section to the bottom of the window.
         */
        fun emptySection(id: String, withAction: Boolean = false, content: () -> Unit) {
            ImGui.beginChild(id, 0f, ImGui.getTextLineHeightWithSpacing() * emptySectionLines(withAction), false)
            content()
            ImGui.endChild()
        }
    }
}
