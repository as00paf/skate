package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.events.TerrainAction
import com.pafoid.skate.editor.imgui.EnumLabels
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.terrain.TerrainTool
import com.pafoid.skate.editor.terrain.TerrainToolState
import com.pafoid.skate.engine.assets.terrain.TerrainAssets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.render.terrain.TerrainDebug
import com.pafoid.skate.engine.terrain.StampFootprint
import com.pafoid.skate.engine.terrain.StampMode
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainStamp
import imgui.ImGui
import imgui.type.ImInt

/**
 * Terrain inspector (EDT-92): the terrain's settings (reflected, undone by the inspector tracker), a note when the
 * sample spacing is capped, presets and reroll, the terrain asset, the sculpt / paint / stamp tools and the stamp list.
 * Biome, layer-rule, scatter-rule and climate inspectors are EDT-96. Every edit that is not a plain value goes
 * through [TerrainAction] to an undoable command.
 */
fun TerrainComponent.imgui(engine: Engine) {
    val strings = engine.stringManager
    val state = TerrainToolState.shared
    if (state.terrain !== this) {
        state.terrain = this
        state.selectedStamp = -1
        TerrainStampDraft.clear()
    }

    reflectedImgui(engine, skip = SKIPPED_FIELDS)
    if (spacingCapped()) {
        MImGui.wrappedText(strings.getString("component.TerrainComponent.spacingCapped", effectiveSpacing()))
        Tooltips.show(strings, "component.TerrainComponent.spacingCapped.tooltip")
    }
    presetRow(engine)
    MImGui.sameLineIfFits(MImGui.buttonWidth(strings.label("component.TerrainComponent.reroll")))
    if (strings.tipped(MImGui.fitButton(strings.label("component.TerrainComponent.reroll")), "component.TerrainComponent.reroll")) reroll()

    if (ImGui.collapsingHeader(strings.label("component.TerrainComponent.section.asset"))) assetSection(engine)
    if (ImGui.collapsingHeader(strings.label("component.TerrainComponent.section.tools"))) toolsSection(strings, state)
    if (ImGui.collapsingHeader(strings.label("component.TerrainComponent.section.stamps"))) stampsSection(engine, state)
}

/** Fields the terrain inspector draws itself (stamps), or that belong to EDT-96 (recipe, biomes, layers, climate). */
private val SKIPPED_FIELDS = setOf("asset", "biomeSource", "recipe", "stamps", "layers", "biomes", "climate")

/** Preset combo selection, per editor session (not saved). */
private val presetIndex = ImInt(TerrainPreset.ISLAND.ordinal)

private fun TerrainComponent.presetRow(engine: Engine) {
    val strings = engine.stringManager
    val names = TerrainPreset.entries.map { EnumLabels.of(strings, it) }.toTypedArray()
    MImGui.field(strings.getString("component.TerrainComponent.preset"), "component.TerrainComponent.preset") {
        ImGui.combo("##preset", presetIndex, names, names.size)
    }
    Tooltips.show(strings, "component.TerrainComponent.preset.tooltip")
    // Apply goes under the dropdown: side by side they overflowed a narrow dock (EDT-92 review)
    if (strings.tipped(MImGui.fitButton(strings.label("component.TerrainComponent.applyPreset")), "component.TerrainComponent.applyPreset")) {
        engine.eventSystem.publish(TerrainAction.ApplyPreset(this, TerrainPreset.entries[presetIndex.get()]))
    }
}

private fun TerrainComponent.assetSection(engine: Engine) {
    val strings = engine.stringManager
    if (asset.isBlank()) {
        MImGui.wrappedText(strings.getString("component.TerrainComponent.asset.none"))
        if (strings.tipped(MImGui.fitButton(strings.label("component.TerrainComponent.asset.create")), "component.TerrainComponent.asset.create")) {
            engine.eventSystem.publish(TerrainAction.CreateAsset(this))
        }
    } else {
        val name = runCatching { TerrainAssets.source.folder(asset)?.name }.getOrNull() ?: asset
        MImGui.wrappedText(strings.getString("component.TerrainComponent.asset.linked", name))
        Tooltips.show(strings, "component.TerrainComponent.asset.linked.tooltip")
    }
}

/** The tools in the order of the radio row; NONE gives the viewport back to the usual gizmos. */
private val TOOLS = listOf(TerrainTool.NONE, TerrainTool.RAISE, TerrainTool.LOWER, TerrainTool.SMOOTH, TerrainTool.FLATTEN, TerrainTool.PAINT_BIOME, TerrainTool.STAMP)

fun toolKey(tool: TerrainTool): String = "component.TerrainComponent.tool.${tool.name.lowercase()}"

private fun TerrainComponent.toolsSection(strings: StringManager, state: TerrainToolState) {
    // Radio buttons flow onto as many lines as the dock needs (EDT-92 review: four per line overflowed)
    TOOLS.forEachIndexed { index, tool ->
        val label = strings.label(toolKey(tool))
        if (index > 0) MImGui.sameLineIfFits(radioWidth(label))
        if (toolRadio(strings, label, toolKey(tool), state.tool == tool)) state.tool = tool
    }
    if (state.tool.sculpt || state.tool == TerrainTool.PAINT_BIOME) {
        val radius = floatArrayOf(state.radius)
        MImGui.field(strings.getString("component.TerrainComponent.brush.radius"), "component.TerrainComponent.brush.radius") {
            if (ImGui.dragFloat("##radius", radius, 0.1f, TerrainToolState.MIN_RADIUS, TerrainToolState.MAX_RADIUS)) state.radius = radius[0]
        }
        Tooltips.show(strings, "component.TerrainComponent.brush.radius.tooltip")
    }
    if (state.tool.sculpt) {
        val strength = floatArrayOf(state.strength)
        MImGui.field(strings.getString("component.TerrainComponent.brush.strength"), "component.TerrainComponent.brush.strength") {
            if (ImGui.dragFloat("##strength", strength, 0.05f, TerrainToolState.MIN_STRENGTH, TerrainToolState.MAX_STRENGTH)) state.strength = strength[0]
        }
        Tooltips.show(strings, "component.TerrainComponent.brush.strength.tooltip")
    }
    if (state.tool == TerrainTool.PAINT_BIOME) {
        val names = biomes.map { it.name }.toTypedArray()
        if (names.isEmpty()) {
            MImGui.wrappedText(strings.getString("component.TerrainComponent.brush.noBiomes"))
        } else {
            val index = ImInt(state.biome.coerceIn(0, names.size - 1))
            MImGui.field(strings.getString("component.TerrainComponent.brush.biome"), "component.TerrainComponent.brush.biome") {
                if (ImGui.combo("##biome", index, names, names.size)) state.biome = index.get()
            }
            Tooltips.show(strings, "component.TerrainComponent.brush.biome.tooltip")
        }
    }
    if (state.tool != TerrainTool.NONE) {
        MImGui.wrappedText(strings.getString(if (state.tool == TerrainTool.STAMP) "component.TerrainComponent.tool.hint.stamp" else "component.TerrainComponent.tool.hint.brush"))
    }
    if (MImGui.checkbox(strings.getString("component.TerrainComponent.showBiomes"), TerrainDebug.showBiomes, "component.TerrainComponent.showBiomes")) {
        TerrainDebug.showBiomes = !TerrainDebug.showBiomes
    }
    Tooltips.show(strings, "component.TerrainComponent.showBiomes.tooltip")
}

/**
 * A tool radio button; when even a line of its own is too narrow for its label (French at 150 %), the circle is drawn
 * alone and the label follows as wrapped text that also selects the tool.
 */
private fun toolRadio(strings: StringManager, label: String, key: String, active: Boolean): Boolean {
    if (radioWidth(label) <= ImGui.getContentRegionAvailX()) return strings.tipped(ImGui.radioButton(label, active), key)
    var clicked = strings.tipped(ImGui.radioButton("##$key", active), key)
    ImGui.sameLine()
    MImGui.wrappedText(label.substringBefore("##"))
    if (ImGui.isItemClicked()) clicked = true
    Tooltips.show(strings, Tooltips.keyFor(key))
    return clicked
}

/** Width of a radio button labelled [label]: the circle, the inner spacing and the visible text. */
private fun radioWidth(label: String): Float =
    ImGui.getFrameHeight() + ImGui.getStyle().itemInnerSpacingX + ImGui.calcTextSize(label, true).x

/** A stamp being edited in the inspector: committed as one command when its widget is released (EDT-92 §4). */
internal object TerrainStampDraft {
    var terrain: TerrainComponent? = null
    var index = -1
    var stamp: TerrainStamp? = null

    fun of(terrain: TerrainComponent, index: Int, current: TerrainStamp): TerrainStamp =
        stamp?.takeIf { this.terrain === terrain && this.index == index } ?: current

    fun set(terrain: TerrainComponent, index: Int, value: TerrainStamp) {
        this.terrain = terrain; this.index = index; stamp = value
    }

    fun clear() { terrain = null; index = -1; stamp = null }
}

private fun TerrainComponent.stampsSection(engine: Engine, state: TerrainToolState) {
    val strings = engine.stringManager
    val events = engine.eventSystem
    if (state.selectedStamp !in stamps.indices) state.selectedStamp = -1
    stamps.forEachIndexed { i, s ->
        val text = strings.getString("component.TerrainComponent.stamp.row", i + 1, EnumLabels.of(strings, s.mode), EnumLabels.of(strings, s.footprint))
        // Full width, not the label's: a long row must not widen a narrow dock
        if (ImGui.selectable("$text##terrain_stamp_$i", state.selectedStamp == i, 0, ImGui.getContentRegionAvailX(), 0f)) {
            state.selectedStamp = i
            TerrainStampDraft.clear()
        }
    }
    if (strings.tipped(MImGui.fitButton(strings.label("component.TerrainComponent.stamp.add")), "component.TerrainComponent.stamp.add")) {
        val y = runtime.heightField?.heightAt(0f, 0f) ?: 0f
        events.publish(TerrainAction.SetStamps(this, stamps + TerrainStamp(y = y)))
        state.selectedStamp = stamps.size
    }
    val selected = stamps.getOrNull(state.selectedStamp) ?: return
    MImGui.sameLineIfFits(MImGui.buttonWidth(strings.label("component.TerrainComponent.stamp.remove")))
    if (strings.tipped(MImGui.fitButton(strings.label("component.TerrainComponent.stamp.remove")), "component.TerrainComponent.stamp.remove")) {
        events.publish(TerrainAction.SetStamps(this, stamps.filterIndexed { i, _ -> i != state.selectedStamp }))
        state.selectedStamp = -1
        TerrainStampDraft.clear()
        return
    }

    val index = state.selectedStamp
    var draft = TerrainStampDraft.of(this, index, selected)
    var commit = false
    fun enumCombo(key: String, values: Array<out Enum<*>>, current: Enum<*>, pick: (Int) -> TerrainStamp) {
        val names = values.map { EnumLabels.of(strings, it) }.toTypedArray()
        val i = ImInt(current.ordinal)
        MImGui.field(strings.getString(key), key) {
            if (ImGui.combo("##value", i, names, names.size)) { draft = pick(i.get()); commit = true }
        }
        Tooltips.show(strings, "$key.tooltip")
    }
    fun floatField(key: String, value: Float, speed: Float, apply: (Float) -> TerrainStamp) {
        val v = floatArrayOf(value)
        MImGui.field(strings.getString(key), key) {
            if (ImGui.dragFloat("##value", v, speed)) draft = apply(v[0])
        }
        Tooltips.show(strings, "$key.tooltip")
        if (ImGui.isItemDeactivatedAfterEdit()) commit = true
    }
    enumCombo("component.TerrainComponent.stamp.footprint", StampFootprint.entries.toTypedArray(), draft.footprint) { draft.copy(footprint = StampFootprint.entries[it]) }
    enumCombo("component.TerrainComponent.stamp.mode", StampMode.entries.toTypedArray(), draft.mode) { draft.copy(mode = StampMode.entries[it]) }
    floatField("component.TerrainComponent.stamp.x", draft.x, 0.1f) { draft.copy(x = it) }
    floatField("component.TerrainComponent.stamp.y", draft.y, 0.05f) { draft.copy(y = it) }
    floatField("component.TerrainComponent.stamp.z", draft.z, 0.1f) { draft.copy(z = it) }
    floatField("component.TerrainComponent.stamp.yaw", draft.yaw, 0.5f) { draft.copy(yaw = it) }
    if (draft.footprint == StampFootprint.CIRCLE) {
        floatField("component.TerrainComponent.stamp.radius", draft.radius, 0.05f) { draft.copy(radius = it.coerceAtLeast(0.25f)) }
    } else {
        floatField("component.TerrainComponent.stamp.width", draft.width, 0.1f) { draft.copy(width = it.coerceAtLeast(0.5f)) }
        floatField("component.TerrainComponent.stamp.depth", draft.depth, 0.1f) { draft.copy(depth = it.coerceAtLeast(0.5f)) }
    }
    floatField("component.TerrainComponent.stamp.falloff", draft.falloff, 0.05f) { draft.copy(falloff = it.coerceAtLeast(0f)) }

    if (commit) {
        TerrainStampDraft.clear()
        if (draft != selected) events.publish(TerrainAction.SetStamps(this, stamps.toMutableList().also { it[index] = draft }))
    } else if (draft != selected) {
        TerrainStampDraft.set(this, index, draft)
    }
}
