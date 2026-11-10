package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.commands.objects.AudioComponentSettings
import com.pafoid.skate.editor.events.AudioAction
import com.pafoid.skate.editor.imgui.EnumLabels
import com.pafoid.skate.editor.imgui.ImGuiIds
import com.pafoid.skate.engine.audio.AttenuationModel
import com.pafoid.skate.engine.audio.BusId
import com.pafoid.skate.engine.audio.StreamMode
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.components.AudioComponent
import imgui.ImGui
import imgui.type.ImInt
import imgui.type.ImString
import java.io.File
import java.util.IdentityHashMap

/** Working copy while a widget is held; [frame] is the last ImGui frame the inspector drew it. */
private class AudioDraft(var settings: AudioComponentSettings, var frame: Int)

/**
 * Edits are made on a working copy while a widget is held, then committed as one command on release. Keyed by
 * identity (AudioComponent is compared by value).
 */
private val working = IdentityHashMap<AudioComponent, AudioDraft>()

/**
 * Once per frame after every window drew (EDT-40): a draft whose inspector was not drawn this [frame] (another object
 * selected mid-edit, window closed) is committed as one command instead of being lost.
 */
/** Test hook: an in-progress draft as the inspector would leave it after drawing it in [frame]. */
internal fun trackAudioDraft(component: AudioComponent, settings: AudioComponentSettings, frame: Int) {
    working[component] = AudioDraft(settings, frame)
}

fun flushAudioDrafts(events: EventSystem, frame: Int) {
    val it = working.entries.iterator()
    while (it.hasNext()) {
        val (component, draft) = it.next()
        if (draft.frame == frame) continue
        if (draft.settings != AudioComponentSettings.of(component)) events.publish(AudioAction.EditComponent(component, draft.settings))
        it.remove()
    }
}

private const val SOUND_PAYLOAD = "SOUND"

/** Inspector for [AudioComponent] (EDT-14): every saved field goes through `EditAudioComponentCommand`. */
fun AudioComponent.imgui(engine: Engine) {
    val strings = engine.stringManager
    fun s(key: String) = strings.getString("component.AudioComponent.$key")
    fun tip(key: String) { Tooltips.always(strings.getString("tooltip.audio.component.$key")) }
    ImGui.pushID(System.identityHashCode(this))

    val frame = ImGui.getFrameCount()
    working[this]?.frame = frame
    val draft0 = working[this]?.settings ?: AudioComponentSettings.of(this)
    var draft = draft0
    var changed = false
    var commit = false
    var publishNow: AudioComponentSettings? = null

    fun after() { if (ImGui.isItemDeactivatedAfterEdit()) commit = true }
    fun edit(next: AudioComponentSettings) { draft = next; changed = true }

    fun <E : Enum<E>> enumField(key: String, entries: List<E>, value: E, apply: (E) -> AudioComponentSettings) {
        val names = EnumLabels.all(strings, entries)
        val index = ImInt(value.ordinal)
        if (ImGui.combo(ImGuiIds.stable(s(key), key), index, names, names.size)) publishNow = apply(entries[index.get()])
        tip(key)
    }

    fun boolField(key: String, value: Boolean, apply: (Boolean) -> AudioComponentSettings) {
        if (ImGui.checkbox("${s(key)}##$key", value)) publishNow = apply(!value)
        tip(key)
    }

    fun floatField(key: String, value: Float, speed: Float, min: Float, max: Float, apply: (Float) -> AudioComponentSettings) {
        val a = floatArrayOf(value)
        if (ImGui.dragFloat("${s(key)}##$key", a, speed, min, max)) edit(apply(a[0]))
        after()
        tip(key)
    }

    // Sound file: type a path or drop a sound from the asset browser.
    val path = ImString(draft.soundFilePath, 1024)
    if (ImGui.inputText("${s("soundFilePath")}##soundFilePath", path)) edit(draft.copy(soundFilePath = path.get()))
    after()
    tip("soundFilePath")
    if (ImGui.beginDragDropTarget()) {
        ImGui.acceptDragDropPayload<String>(SOUND_PAYLOAD)?.let { publishNow = draft.copy(soundFilePath = it) }
        ImGui.endDragDropTarget()
    }

    // Random clips: drop sounds on the button to add; X removes.
    if (ImGui.treeNodeEx(ImGuiIds.stable("${s("clips")} (${draft.clips.size})", "clips"))) {
        draft.clips.forEachIndexed { i, clip ->
            if (ImGui.smallButton("X##clip$i")) publishNow = draft.copy(clips = draft.clips.filterIndexed { j, _ -> j != i })
            ImGui.sameLine()
            ImGui.textUnformatted(File(clip).name)
            Tooltips.always(clip)
        }
        strings.tipped(ImGui.button("${s("clips.drop")}##clips_drop"), "component.AudioComponent.clips.drop")
        if (ImGui.beginDragDropTarget()) {
            ImGui.acceptDragDropPayload<String>(SOUND_PAYLOAD)?.let { publishNow = draft.copy(clips = draft.clips + it) }
            ImGui.endDragDropTarget()
        }
        tip("clips")
        ImGui.treePop()
    }

    ImGui.separator()
    enumField("bus", BusId.entries, draft.bus) { draft.copy(bus = it) }
    val volume = floatArrayOf(draft.volume)
    if (ImGui.sliderFloat("${s("volume")}##volume", volume, 0f, 1f)) edit(draft.copy(volume = volume[0]))
    after()
    boolField("loops", draft.loops) { draft.copy(loops = it) }
    boolField("playOnAwake", draft.playOnAwake) { draft.copy(playOnAwake = it) }
    enumField("streamMode", StreamMode.entries, draft.streamMode) { draft.copy(streamMode = it) }
    val priority = intArrayOf(draft.priority)
    if (ImGui.dragInt("${s("priority")}##priority", priority, 0.5f, 0, AudioComponentSettings.MAX_PRIORITY)) {
        edit(draft.copy(priority = priority[0]))
    }
    after()
    tip("priority")

    ImGui.separator()
    boolField("is3D", draft.is3D) { draft.copy(is3D = it) }
    if (draft.is3D) {
        enumField("attenuationModel", AttenuationModel.entries, draft.attenuationModel) { draft.copy(attenuationModel = it) }
        floatField("minDistance", draft.minDistance, 0.05f, 0f, 10_000f) { draft.copy(minDistance = it) }
        floatField("maxDistance", draft.maxDistance, 0.1f, 0f, 10_000f) { draft.copy(maxDistance = it) }
        floatField("rolloff", draft.rolloff, 0.01f, 0f, 100f) { draft.copy(rolloff = it) }
        floatField("dopplerFactor", draft.dopplerFactor, 0.01f, 0f, 10f) { draft.copy(dopplerFactor = it) }
        if (draft.bus == BusId.MUSIC && draft.dopplerFactor > 0f) ImGui.textDisabled(s("dopplerFactor.music"))
    }

    // Preview: plays the sound in edit mode; not saved, so not a command.
    ImGui.separator()
    if (!isPlaying) {
        if (strings.tipped(ImGui.button("${strings.getString("btn.audio.play")}##preview_play"), "btn.audio.play")) play()
    } else if (strings.tipped(ImGui.button("${strings.getString("btn.audio.stop")}##preview_stop"), "btn.audio.stop")) {
        stopPlayback()
    }

    val pending = publishNow
    when {
        pending != null -> {
            working.remove(this)
            engine.eventSystem.publish(AudioAction.EditComponent(this, pending))
        }
        commit -> {
            working.remove(this)
            engine.eventSystem.publish(AudioAction.EditComponent(this, draft))
        }
        changed -> working[this] = AudioDraft(draft, frame)
        draft0 === draft && !ImGui.isAnyItemActive() -> working.remove(this)
    }
    ImGui.popID()
}
