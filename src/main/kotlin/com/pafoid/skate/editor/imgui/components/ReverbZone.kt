package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.ReverbZoneSettings
import com.pafoid.skate.editor.events.AudioAction
import com.pafoid.skate.engine.audio.ReverbPreset
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.ReverbZone
import imgui.ImGui
import imgui.type.ImInt
import java.util.WeakHashMap

private val working = WeakHashMap<ReverbZone, ReverbZoneSettings>()

fun ReverbZone.imgui(engine: Engine) {
    val strings = engine.stringManager
    fun s(key: String) = strings.getString("component.ReverbZone.$key")
    ImGui.pushID(System.identityHashCode(this))
    var draft = working[this] ?: ReverbZoneSettings.of(this)
    var commit = false
    var changed = false

    val presets = ReverbPreset.entries.map { it.name }.toTypedArray()
    val index = ImInt(draft.preset.ordinal)
    if (ImGui.combo(s("preset"), index, presets, presets.size)) {
        engine.eventSystem.publish(AudioAction.EditReverbZone(this, draft.copy(preset = ReverbPreset.entries[index.get()])))
    }

    val radius = floatArrayOf(draft.radius)
    if (ImGui.dragFloat(s("radius"), radius, 0.1f, 0f, 1000f)) { draft = draft.copy(radius = radius[0]); changed = true }
    if (ImGui.isItemDeactivatedAfterEdit()) commit = true
    val priority = intArrayOf(draft.priority)
    if (ImGui.dragInt(s("priority"), priority, 0.2f)) { draft = draft.copy(priority = priority[0]); changed = true }
    if (ImGui.isItemDeactivatedAfterEdit()) commit = true
    val send = floatArrayOf(draft.sendLevel)
    if (ImGui.sliderFloat(s("sendLevel"), send, 0f, 1f)) { draft = draft.copy(sendLevel = send[0]); changed = true }
    if (ImGui.isItemDeactivatedAfterEdit()) commit = true
    val low = floatArrayOf(draft.lowPassGainHF)
    if (ImGui.sliderFloat(s("lowPassGainHF"), low, 0f, 1f)) { draft = draft.copy(lowPassGainHF = low[0]); changed = true }
    if (ImGui.isItemDeactivatedAfterEdit()) commit = true

    if (commit) {
        working.remove(this)
        engine.eventSystem.publish(AudioAction.EditReverbZone(this, draft))
    } else if (changed) {
        working[this] = draft
    } else if (!ImGui.isAnyItemActive()) {
        working.remove(this)
    }
    ImGui.popID()
}
