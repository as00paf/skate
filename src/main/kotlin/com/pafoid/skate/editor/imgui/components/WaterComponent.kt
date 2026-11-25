package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.WaterWaveSettings
import com.pafoid.skate.editor.events.ComponentFieldAction
import com.pafoid.skate.editor.events.WaterAction
import com.pafoid.skate.editor.imgui.EnumLabels
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.ui.windows.assetBrowser.FileListTab
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.ecs.components.WaterMaterials
import com.pafoid.skate.engine.ecs.components.WaterPreset
import com.pafoid.skate.engine.environment.GerstnerWave
import com.pafoid.skate.engine.environment.WaterWaves
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import java.util.IdentityHashMap

/**
 * Open per-wave gestures (EDT-90), free of ImGui so it can be tested: the water's waves, flow and material from before
 * the first change of a drag, kept until the drag ends and becomes one [WaterAction.EditWaves].
 */
internal class WaterWaveGestures {
    private val open = IdentityHashMap<WaterComponent, WaterWaveSettings>()

    /** A widget changed [water]; [before] is its state at the start of this frame (kept only for the first change). */
    fun changed(water: WaterComponent, before: WaterWaveSettings) {
        open.putIfAbsent(water, before)
    }

    fun isOpen(water: WaterComponent): Boolean = water in open

    /** Ends [water]'s gesture: the edit to publish, or null when none was open. */
    fun finish(water: WaterComponent): WaterAction.EditWaves? =
        open.remove(water)?.let { WaterAction.EditWaves(water, it, WaterWaveSettings.of(water)) }

    companion object {
        val shared = WaterWaveGestures()
    }
}

private const val KEY = "component.WaterComponent"
private val SURFACE_FIELDS = setOf("sizeX", "sizeZ", "depth", "quadSize", "receiveShadows", "cutsOcean")
private val WAVE_FIELDS = setOf("windDrivenWaves")
private val FLOW_FIELDS = setOf("flowVelocity")
private val BUOYANCY_FIELDS = setOf("density", "linearDrag", "angularDrag")

/** A new wave for "Add wave": a small, gentle ripple. */
internal fun newWave() = GerstnerWave(directionDeg = 45f, wavelength = 4f, amplitude = 0.03f, steepness = 0.3f)

/**
 * Water inspector (EDT-90, REN-29 §9): surface size and depth, wave presets and a per-wave editor, Freeze time (the
 * scene's `EnvironmentComponent.animateInEditor`), flow and a material picker, and the buoyancy fields (PHY-16).
 * Plain fields go through the shared edit tracker; waves, presets and the material go through undoable commands.
 */
fun WaterComponent.imgui(engine: Engine) {
    val strings = engine.stringManager
    val events = engine.eventSystem
    val gestures = WaterWaveGestures.shared
    ImGui.pushID(System.identityHashCode(this))

    sectionHeader(strings.getString("$KEY.section.surface"))
    reflectedOnly(engine, SURFACE_FIELDS)

    sectionHeader(strings.getString("$KEY.waves"))
    Tooltips.showIfPresent(strings, "$KEY.waves${Tooltips.SUFFIX}")
    presetCombo(engine)
    reflectedOnly(engine, WAVE_FIELDS)
    freezeTime(engine)
    waveList(engine, gestures)

    sectionHeader(strings.getString("$KEY.section.flow"))
    reflectedOnly(engine, FLOW_FIELDS)
    materialPicker(engine)

    sectionHeader(strings.getString("$KEY.section.buoyancy"))
    reflectedOnly(engine, BUOYANCY_FIELDS)

    // A drag that ended without a deactivation (inspector collapsed, selection changed) still becomes one edit
    if (gestures.isOpen(this) && !ImGui.isAnyItemActive()) gestures.finish(this)?.let(events::publish)
    ImGui.popID()
}

/** The generic inspector rows of [only] (in declaration order), so field rows keep the shared look and edit tracking. */
private fun Component.reflectedOnly(engine: Engine, only: Set<String>) {
    val skip = Component.getCachedFields(javaClass).map { it.name }.filter { it !in only }.toSet()
    reflectedImgui(engine, skip)
}

internal fun WaterComponent.presetCombo(engine: Engine) {
    val strings = engine.stringManager
    val presets = WaterPreset.entries
    val labels = EnumLabels.all(strings, presets)
    val settings = WaterWaveSettings.of(this)
    val current = presets.indexOfFirst { settings == presetSettings(it) }
    val preview = if (current >= 0) labels[current] else strings.getString("$KEY.preset.custom")
    inspectorRow(strings, "water.preset", "$KEY.preset") {
        if (ImGui.beginCombo("##water.preset", preview)) {
            presets.forEachIndexed { i, preset ->
                if (ImGui.selectable(labels[i], i == current)) {
                    engine.eventSystem.publish(WaterAction.EditWaves(this, settings, presetSettings(preset)))
                }
            }
            ImGui.endCombo()
        }
    }
}

/** Each preset's waves, flow and material, built once (read-only: commands copy what they apply). */
private val PRESET_SETTINGS: Map<WaterPreset, WaterWaveSettings> by lazy {
    WaterPreset.entries.associateWith { WaterWaveSettings.of(WaterComponent().apply { applyPreset(it) }) }
}

internal fun presetSettings(preset: WaterPreset): WaterWaveSettings = PRESET_SETTINGS.getValue(preset)

/** True when the water's waves, flow and material are exactly [preset]'s. */
internal fun WaterComponent.matches(preset: WaterPreset): Boolean = WaterWaveSettings.of(this) == presetSettings(preset)

internal fun freezeTime(engine: Engine) {
    val strings = engine.stringManager
    val environment = engine.sceneManager.currentScene?.getComponent<EnvironmentComponent>()
    if (environment == null) {
        wrappedHint("${Icons.INFO} ${strings.getString("$KEY.freezeTime.none")}")
        return
    }
    val frozen = !environment.animateInEditor
    inspectorRow(strings, "water.freezeTime", "$KEY.freezeTime") {
        if (ImGui.checkbox("##water.freezeTime", frozen)) {
            engine.eventSystem.publish(ComponentFieldAction.Set(environment, ANIMATE_IN_EDITOR, frozen))
        }
    }
}

private val ANIMATE_IN_EDITOR = EnvironmentComponent::class.java.getDeclaredField("animateInEditor").apply { isAccessible = true }
private val MATERIAL = WaterComponent::class.java.getDeclaredField("material").apply { isAccessible = true }

internal fun WaterComponent.waveList(engine: Engine, gestures: WaterWaveGestures = WaterWaveGestures.shared) {
    val strings = engine.stringManager
    val start = WaterWaveSettings.of(this)
    var removeAt = -1
    waves.forEachIndexed { i, wave ->
        ImGui.pushID(i)
        MImGui.textDisabled(strings.getString("$KEY.wave.title", i + 1))
        MImGui.sameLineIfFits(ImGui.calcTextSize(Icons.TRASH).x + ImGui.getStyle().framePaddingX * 2f)
        if (strings.tipped(ImGui.smallButton(Icons.TRASH), "$KEY.wave.remove")) removeAt = i
        waveField(engine, gestures, start, "direction", wave.directionDeg, -180f, 180f, 1f) { wave.directionDeg = it }
        waveField(engine, gestures, start, "wavelength", wave.wavelength, WaterWaveSettings.MIN_WAVELENGTH, 200f, 0.1f) { wave.wavelength = it }
        waveField(engine, gestures, start, "amplitude", wave.amplitude, 0f, 5f, 0.005f) { wave.amplitude = it }
        waveField(engine, gestures, start, "steepness", wave.steepness, 0f, 1f, 0.01f) { wave.steepness = it }
        ImGui.popID()
    }
    if (removeAt >= 0) {
        val after = start.copy(waves = start.waves.filterIndexed { i, _ -> i != removeAt })
        engine.eventSystem.publish(WaterAction.EditWaves(this, start, after))
    }
    val full = waves.size >= WaterWaves.MAX_WAVES
    if (full) ImGui.beginDisabled()
    if (strings.tipped(MImGui.fitButton(strings.label("$KEY.wave.add", "${Icons.PLUS} ")), "$KEY.wave.add")) {
        engine.eventSystem.publish(WaterAction.EditWaves(this, start, start.copy(waves = start.waves + newWave())))
    }
    if (full) ImGui.endDisabled()
}

/** One wave value: written live for the preview, published as one edit when the drag ends. */
private inline fun WaterComponent.waveField(
    engine: Engine,
    gestures: WaterWaveGestures,
    start: WaterWaveSettings,
    name: String,
    value: Float,
    min: Float,
    max: Float,
    speed: Float,
    crossinline write: (Float) -> Unit,
) {
    val strings = engine.stringManager
    val buffer = floatArrayOf(value)
    inspectorRow(strings, "water.wave.$name", "$KEY.wave.$name") {
        if (ImGui.dragFloat("##water.wave.$name", buffer, speed, min, max)) {
            gestures.changed(this, start)
            write(buffer[0].coerceIn(min, max))
        }
        if (ImGui.isItemDeactivatedAfterEdit()) gestures.finish(this)?.let(engine.eventSystem::publish)
    }
}

internal fun WaterComponent.materialPicker(engine: Engine) {
    val strings = engine.stringManager
    val bundled = WaterMaterials.ALL
    val names = bundled.map { strings.getString("$KEY.material.${it.substringAfterLast('/').substringBefore(".skmat").removePrefix("water_")}") }
    val current = bundled.indexOf(material)
    val preview = if (current >= 0) names[current] else strings.getString("$KEY.material.custom", material.substringAfterLast('/'))
    inspectorRow(strings, "water.material", "$KEY.material") {
        if (ImGui.beginCombo("##water.material", preview)) {
            bundled.forEachIndexed { i, reference ->
                if (ImGui.selectable(names[i], i == current) && reference != material) setMaterial(engine, reference)
            }
            ImGui.endCombo()
        }
        // A water .skmat dragged from the Asset Browser
        if (ImGui.beginDragDropTarget()) {
            ImGui.acceptDragDropPayload<String>(FileListTab.PAYLOAD)?.let { path ->
                if (isWaterMaterialPath(path) && path != material) setMaterial(engine, path)
            }
            ImGui.endDragDropTarget()
        }
    }
    wrappedHint(strings.getString("$KEY.material.drop"))
}

/** Only `.skmat` files can be water materials (their schema is checked when the renderer loads them). */
internal fun isWaterMaterialPath(path: String): Boolean = path.endsWith(".skmat", ignoreCase = true)

private fun WaterComponent.setMaterial(engine: Engine, reference: String) {
    engine.eventSystem.publish(ComponentFieldAction.Set(this, MATERIAL, reference))
}
