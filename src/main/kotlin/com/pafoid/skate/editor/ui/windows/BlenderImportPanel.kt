package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.assetimport.blender.BlenderImportSettings
import com.pafoid.skate.editor.assetimport.blender.BlenderImportSettings.ActionMode
import com.pafoid.skate.editor.assetimport.blender.BlenderOutputs
import com.pafoid.skate.editor.assetimport.blender.BlenderSync
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.tools.external.BlenderAvailability
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt
import java.io.File

/**
 * Edits of one `.blend`'s import settings until Apply or Revert (EDT-66). Reloads the stored value when another file
 * is shown, or when the sidecar changes on disk while nothing is being edited. Pure.
 */
class BlenderSettingsDraft {
    var file: File? = null
        private set
    var saved = BlenderImportSettings()
        private set
    var value = BlenderImportSettings()

    val dirty: Boolean get() = value != saved

    fun show(file: File, stored: BlenderImportSettings) {
        if (file != this.file || !dirty) {
            this.file = file
            saved = stored
            value = stored
        }
    }

    fun revert() {
        value = saved
    }

    fun applied() {
        saved = value
    }
}

/**
 * The Properties window section for a selected `.blend` (EDT-66, from AST-19 §4.3): the `blender.*` import settings
 * with Revert and Apply (Apply saves them in the sidecar and re-imports), a Re-import button, the last import and a
 * "Needs Blender" badge when no usable Blender is installed.
 */
class BlenderImportPanel(
    private val strings: StringManager,
    private val sync: BlenderSync,
) : FileInspector {
    private val draft = BlenderSettingsDraft()
    private var cachedFor: Pair<File, Long>? = null
    private var cachedSettings = BlenderImportSettings()

    override fun handles(file: File): Boolean = BlenderOutputs.isBlend(file) && !BlenderOutputs.isBlenderSideFile(file)

    override fun render(file: File) {
        draft.show(file, stored(file))

        ImGui.text("${Icons.CUBE} ${file.name}")
        if (sync.availability == BlenderAvailability.Missing) {
            MImGui.warningText("${Icons.INFO} ${strings.getString("lbl.blender.needs_blender")}")
            Tooltips.show(strings, "lbl.blender.needs_blender.tooltip")
        }
        ImGui.separator()
        ImGui.spacing()

        var v = draft.value
        v = boolRow("lbl.blender.apply_modifiers", v.applyModifiers) { v.copy(applyModifiers = it) } ?: v
        v = boolRow("lbl.blender.only_visible", v.onlyVisible) { v.copy(onlyVisible = it) } ?: v
        v = boolRow("lbl.blender.export_actions", v.exportActions) { v.copy(exportActions = it) } ?: v
        val modes = ActionMode.entries.map { strings.getString("lbl.blender.action_mode.${it.name.lowercase()}") }.toTypedArray()
        val mode = ImInt(v.actionMode.ordinal)
        MImGui.propertyRow(strings.getString("lbl.blender.action_mode"), strings.getString("lbl.blender.action_mode.desc")) {
            ImGui.beginDisabled(!v.exportActions)
            if (ImGui.combo("##blender_action_mode", mode, modes, modes.size)) v = v.copy(actionMode = ActionMode.entries[mode.get()])
            ImGui.endDisabled()
        }
        v = boolRow("lbl.blender.auto_reimport", v.autoReimport) { v.copy(autoReimport = it) } ?: v
        draft.value = v

        ImGui.spacing()
        val record = sync.lastImport(file)
        MImGui.propertyRowReadOnly(
            strings.getString("lbl.blender.last_import"),
            record?.let { strings.getString("lbl.blender.last_import.value", it.importedAt, it.blenderVersion, it.outputs.size.toString()) }
                ?: strings.getString("lbl.blender.never_imported"),
        )

        ImGui.spacing()
        val importing = sync.isImporting(file)
        ImGui.beginDisabled(!draft.dirty)
        if (strings.tipped(ImGui.button(strings.label("btn.blender.revert", "${Icons.UNDO} ")), "btn.blender.revert")) draft.revert()
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("btn.blender.apply", "${Icons.CHECK} ")), "btn.blender.apply")) apply(file)
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.beginDisabled(importing || draft.dirty)
        if (strings.tipped(ImGui.button(strings.label("btn.blender.reimport", "${Icons.ARROW_ROTATE} ")), "btn.blender.reimport")) sync.reimport(file)
        ImGui.endDisabled()
        if (importing) MImGui.textDisabled(strings.getString("lbl.blender.importing"))
    }

    private fun apply(file: File) {
        if (sync.saveSettings(file, draft.value)) {
            draft.applied()
            cachedFor = null
            sync.reimport(file)
        }
    }

    /** The sidecar's settings, read again only when the sidecar changes on disk. */
    private fun stored(file: File): BlenderImportSettings {
        val key = file to AssetMeta.sidecarOf(file).lastModified()
        if (key != cachedFor) {
            cachedFor = key
            cachedSettings = sync.settingsOf(file)
        }
        return cachedSettings
    }

    /** A checkbox row; returns the changed settings, or null when untouched. */
    private fun boolRow(key: String, value: Boolean, change: (Boolean) -> BlenderImportSettings): BlenderImportSettings? {
        var result: BlenderImportSettings? = null
        MImGui.propertyRow(strings.getString(key), strings.getString("$key.desc")) {
            val flag = ImBoolean(value)
            if (ImGui.checkbox("##$key", flag)) result = change(flag.get())
        }
        return result
    }
}
