package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.project.SaveConfigFileCommand
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.gameplay.ConfigProperties
import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImDouble
import imgui.type.ImString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

/** One editable value of a config file: its dotted [path], a readable [label] and the [section] it is grouped under. */
data class ConfigField(val path: String, val section: String, val label: String, val value: JsonPrimitive) {
    /** Every number is a [NUMBER]: a file cannot tell an integer setting from a float written as `5`. */
    enum class Kind { BOOL, NUMBER, TEXT }

    val kind: Kind
        get() = when {
            value.isString -> Kind.TEXT
            value.booleanOrNull != null -> Kind.BOOL
            else -> Kind.NUMBER
        }

    companion object {
        /**
         * [value] as JSON: a whole number is written without a fraction (`5`), so it still loads into an integer
         * setting, and any other value keeps its fraction (`2.5`), so a float written as `5` can become `2.5`.
         */
        fun number(value: Double): JsonPrimitive =
            if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
    }
}

/**
 * Edits of one config file (`*.character.json`, `*.controller.json`) until Apply or Revert (GPL-26). Every leaf
 * value is a [ConfigField]; array elements are labelled by their `name` when they have one (`states › idle › loop`).
 * Reloads the file when another one is shown, or when it changes on disk while nothing is being edited. Pure.
 */
class ConfigFileDraft {
    var file: File? = null
        private set
    var saved: JsonObject? = null
        private set
    var value: JsonObject? = null
        private set

    /** The file is not a JSON object (a parse error): nothing to edit. */
    var error: String? = null
        private set

    val dirty: Boolean get() = value != saved

    fun show(file: File, text: String) {
        if (file == this.file && dirty) return
        this.file = file
        val parsed = runCatching { Json.parseToJsonElement(text).jsonObject }
        error = parsed.exceptionOrNull()?.message
        saved = parsed.getOrNull()
        value = saved
    }

    val fields: List<ConfigField>
        get() {
            val root = value ?: return emptyList()
            return ConfigProperties.flatten(root).map { (path, v) ->
                val labels = labels(root, path)
                ConfigField(path, labels.first(), labels.drop(1).joinToString(" › ").ifEmpty { labels.first() }, v)
            }
        }

    /** Sets the leaf at [path]; ignored when the path does not exist or the draft has no document. */
    fun set(path: String, newValue: JsonPrimitive) {
        val root = value ?: return
        value = ConfigProperties.set(root, path, newValue)
    }

    fun revert() {
        value = saved
    }

    /** The edited document as the text to save (pretty-printed). */
    fun text(): String = PRETTY.encodeToString(JsonElement.serializer(), value ?: JsonObject(emptyMap()))

    fun applied() {
        saved = value
    }

    private fun labels(root: JsonObject, path: String): List<String> {
        var node: JsonElement? = root
        return path.split('.').map { segment ->
            val current = node
            val index = segment.toIntOrNull()
            if (current is JsonArray && index != null) {
                val element = current.getOrNull(index)
                node = element
                ((element as? JsonObject)?.get("name") as? JsonPrimitive)?.takeIf { it.isString }?.content ?: "#$index"
            } else {
                node = (current as? JsonObject)?.get(segment)
                segment
            }
        }
    }

    companion object {
        private val PRETTY = Json { prettyPrint = true }

        /** Files this draft edits: character configs and animator controllers. */
        fun handles(file: File): Boolean = file.name.lowercase().let { it.endsWith(".character.json") || it.endsWith(".controller.json") }
    }
}

/**
 * The Properties window section for a selected character config or animator controller (GPL-26): every value of
 * the file as a field, grouped by top-level section, with Revert and Apply (Apply saves the file). Users edit the
 * project's copy (`Assets/Data/skater.character.json`, `Assets/Animations/skater.controller.json`), which overrides
 * the engine default. Apply is a [SaveConfigFileCommand]: undoable, and a failed write shows an error, while the
 * edits stay in the draft. The Animator window (ANI-21) will add graph editing for controllers.
 */
class ConfigFileInspector(
    private val strings: StringManager,
    /** Runs the save as an undoable command; the editor's undo manager reports a failure in the error dialog. */
    private val execute: (Command) -> Unit = { it.execute() },
) : FileInspector {
    private val draft = ConfigFileDraft()
    private var loadedFor: Pair<File, Long>? = null

    override fun handles(file: File): Boolean = ConfigFileDraft.handles(file)

    override fun render(file: File) {
        val key = file to file.lastModified()
        if (key != loadedFor || file != draft.file) {
            loadedFor = key
            runCatching { file.readText() }.getOrNull()?.let { draft.show(file, it) }
        }

        ImGui.text("${Icons.SLIDERS} ${file.name}")
        MImGui.textDisabled(strings.getString("lbl.config.applies_on_play"))
        ImGui.separator()
        ImGui.spacing()
        draft.error?.let {
            MImGui.warningText("${Icons.INFO} ${strings.getString("lbl.config.invalid", it)}")
            return
        }

        draft.fields.groupBy { it.section }.forEach { (section, fields) ->
            if (ImGui.collapsingHeader("$section###cfg_section_$section")) {
                fields.forEach(::row)
            }
        }

        ImGui.spacing()
        ImGui.beginDisabled(!draft.dirty)
        if (strings.tipped(ImGui.button(strings.label("btn.config.revert", "${Icons.UNDO} ")), "btn.config.revert")) draft.revert()
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("btn.config.apply", "${Icons.CHECK} ")), "btn.config.apply")) apply(file)
        ImGui.endDisabled()
    }

    private fun apply(file: File) {
        val command = SaveConfigFileCommand(file, draft.text())
        execute(command)
        if (command.wasSuccessful()) {
            draft.applied()
            loadedFor = file to file.lastModified()
        }
    }

    private fun row(field: ConfigField) {
        MImGui.propertyRow(field.label) {
            val id = "##cfg_${field.path}"
            when (field.kind) {
                ConfigField.Kind.BOOL -> {
                    val flag = ImBoolean(field.value.content.toBoolean())
                    if (ImGui.checkbox(id, flag)) draft.set(field.path, JsonPrimitive(flag.get()))
                }
                ConfigField.Kind.NUMBER -> {
                    val number = ImDouble(field.value.content.toDoubleOrNull() ?: 0.0)
                    if (ImGui.inputDouble(id, number, 0.0, 0.0, "%.4g")) draft.set(field.path, ConfigField.number(number.get()))
                }
                ConfigField.Kind.TEXT -> {
                    val text = ImString(field.value.content, 256)
                    if (ImGui.inputText(id, text)) draft.set(field.path, JsonPrimitive(text.get()))
                }
            }
        }
    }
}
