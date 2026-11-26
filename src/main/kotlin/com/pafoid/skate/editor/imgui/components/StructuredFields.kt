package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.EnumLabels
import com.pafoid.skate.editor.imgui.InspectorLayout
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.Component
import imgui.ImGui
import imgui.flag.ImGuiDataType
import imgui.flag.ImGuiDir
import imgui.type.ImInt
import imgui.type.ImLong
import imgui.type.ImString
import kotlinx.serialization.json.JsonNull
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Type

/**
 * The generic inspector's widgets for saved fields that are not plain values (EDT-107): lists (add, remove, reorder),
 * `String`-keyed maps and nested `@Serializable` objects, drawn recursively. Values are edited in place; the inspector
 * edit tracker ([InspectorEditTracker]) turns each finished gesture into one undoable command, because
 * `ComponentValues` snapshots these fields as JSON. What [FieldKind] cannot draw shows a "not editable" note instead of
 * disappearing.
 */
object StructuredFields {
    /** A long list shows its first rows only (mesh colliders hold thousands of vertices). */
    const val MAX_ROWS = 50

    private val keyDrafts = HashMap<String, ImString>()

    /** Draws [field] of [owner] under [label]; [tooltipKey] is shown on the header. */
    fun field(engine: Engine, owner: Component, field: Field, label: String, tooltipKey: String) {
        field.isAccessible = true
        ImGui.pushID(field.name)
        value(engine, field.name, label, tooltipKey, field.genericType, field.get(owner)) { field.set(owner, it) }
        ImGui.popID()
    }

    /** Label key of [field] in [typeName]: `component.<Type>.<field>`, with a `.tooltip` sibling (EDT-05). */
    fun labelKey(typeName: String, field: String) = "component.$typeName.$field"

    /** What a new list item or map entry of [type] starts as, or null when the editor cannot construct one. */
    fun newValue(type: Type): Any? = when (val kind = FieldKind.of(type)) {
        FieldKind.IntField -> 0
        FieldKind.LongField -> 0L
        FieldKind.FloatField -> 0f
        FieldKind.BoolField -> false
        FieldKind.TextField -> ""
        FieldKind.Vec2Field -> Vector2f()
        FieldKind.Vec3Field -> Vector3f()
        FieldKind.Vec4Field -> Vector4f()
        FieldKind.JsonValue -> JsonNull
        is FieldKind.EnumValue -> kind.type.enumConstants.firstOrNull()
        is FieldKind.ListOf -> ArrayList<Any?>()
        is FieldKind.MapOf -> LinkedHashMap<String, Any?>()
        is FieldKind.Nested -> runCatching { kind.type.getDeclaredConstructor().newInstance() }.getOrNull()
        is FieldKind.Unsupported -> null
    }

    private fun value(engine: Engine, id: String, label: String, tooltipKey: String?, type: Type, value: Any?, set: (Any?) -> Unit) {
        val strings = engine.stringManager
        fun row(control: () -> Unit) = MImGui.row(label, id) {
            control()
            tooltipKey?.let { Tooltips.showIfPresent(strings, it) }
        }
        when (val kind = FieldKind.of(type)) {
            FieldKind.IntField -> row {
                val v = intArrayOf((value as? Int) ?: 0)
                if (ImGui.dragInt("##value", v, 1f)) set(v[0])
            }
            FieldKind.LongField -> row {
                val v = ImLong((value as? Long) ?: 0L)
                if (ImGui.inputScalar("##value", ImGuiDataType.S64, v)) set(v.get())
            }
            FieldKind.FloatField -> row {
                val v = floatArrayOf((value as? Float) ?: 0f)
                if (ImGui.dragFloat("##value", v, 0.1f)) set(v[0])
            }
            FieldKind.BoolField -> row {
                if (ImGui.checkbox("##value", (value as? Boolean) ?: false)) set(!((value as? Boolean) ?: false))
            }
            FieldKind.TextField -> row {
                val text = ImString((value as? String).orEmpty(), 256)
                if (ImGui.inputText("##value", text)) set(text.get())
            }
            FieldKind.Vec2Field -> (value as? Vector2f)?.let { MImGui.drawVec2Control(label, it) }
            FieldKind.Vec3Field -> (value as? Vector3f)?.let { MImGui.drawVec3Control(label, it) }
            FieldKind.Vec4Field -> (value as? Vector4f)?.let { v ->
                val a = floatArrayOf(v.x, v.y, v.z, v.w)
                row { if (ImGui.dragFloat4("##value", a)) v.set(a[0], a[1], a[2], a[3]) }
            }
            is FieldKind.EnumValue -> row {
                val constants = kind.type.enumConstants.map { it as Enum<*> }
                val labels = EnumLabels.all(strings, constants)
                val index = ImInt(((value as? Enum<*>) ?: constants.first()).ordinal)
                if (ImGui.combo("##value", index, labels, labels.size)) set(constants[index.get()])
            }
            FieldKind.JsonValue -> row { ImGui.textDisabled(value.toString()) }
            is FieldKind.ListOf -> list(engine, id, label, tooltipKey, kind.element, value)
            is FieldKind.MapOf -> map(engine, id, label, tooltipKey, kind.value, value)
            is FieldKind.Nested -> nested(engine, id, label, tooltipKey, kind.type, value)
            is FieldKind.Unsupported -> row { ImGui.textDisabled(strings.getString("inspector.struct.unsupported")) }
        }
    }

    private fun header(engine: Engine, id: String, label: String, tooltipKey: String?, count: Int?): Boolean {
        val text = if (count == null) label else "$label ($count)"
        val room = ImGui.getContentRegionAvailX() - ImGui.getTreeNodeToLabelSpacing()
        val open = ImGui.treeNode("${InspectorLayout.fit(text, room) { ImGui.calcTextSize(it).x }}###$id")
        tooltipKey?.let { Tooltips.showIfPresent(engine.stringManager, it) }
        return open
    }

    private fun iconButton(engine: Engine, label: String, tooltipKey: String): Boolean =
        engine.stringManager.tippedWith(ImGui.button(label), tooltipKey)

    @Suppress("UNCHECKED_CAST")
    private fun list(engine: Engine, id: String, label: String, tooltipKey: String?, element: Type, value: Any?) {
        val items = value as? MutableList<Any?>
        if (!header(engine, id, label, tooltipKey, items?.size)) return
        val strings = engine.stringManager
        if (items.isNullOrEmpty()) ImGui.textDisabled(strings.getString("inspector.list.empty"))
        var remove = -1
        var swap = -1
        items?.take(MAX_ROWS)?.indices?.forEach { i ->
            ImGui.pushID(i)
            ImGui.textUnformatted("${i + 1}")
            MImGui.sameLineIfFits(ImGui.getFrameHeight())
            if (ImGui.arrowButton("##up", ImGuiDir.Up) && i > 0) swap = i - 1
            Tooltips.showIfPresent(strings, "inspector.list.up.tooltip")
            MImGui.sameLineIfFits(ImGui.getFrameHeight())
            if (ImGui.arrowButton("##down", ImGuiDir.Down) && i < items.size - 1) swap = i
            Tooltips.showIfPresent(strings, "inspector.list.down.tooltip")
            MImGui.sameLineIfFits(MImGui.buttonWidth("-"))
            if (iconButton(engine, "-##remove", "inspector.list.remove.tooltip")) remove = i
            value(engine, "item", "", null, element, items[i]) { items[i] = it }
            ImGui.popID()
        }
        if (items != null && items.size > MAX_ROWS) ImGui.textDisabled(strings.getString("inspector.list.more", items.size - MAX_ROWS))
        val canAdd = items != null && newValue(element) != null
        if (!canAdd) ImGui.beginDisabled()
        if (iconButton(engine, "+##add", "inspector.list.add.tooltip")) runCatching { items?.add(newValue(element)) }
        if (!canAdd) ImGui.endDisabled()
        runCatching {
            if (remove >= 0) items?.removeAt(remove)
            if (swap >= 0 && items != null) java.util.Collections.swap(items, swap, swap + 1)
        }
        ImGui.treePop()
    }

    @Suppress("UNCHECKED_CAST")
    private fun map(engine: Engine, id: String, label: String, tooltipKey: String?, valueType: Type, value: Any?) {
        val entries = value as? MutableMap<String, Any?>
        if (!header(engine, id, label, tooltipKey, entries?.size)) return
        val strings = engine.stringManager
        if (entries.isNullOrEmpty()) ImGui.textDisabled(strings.getString("inspector.map.empty"))
        var remove: String? = null
        entries?.keys?.toList()?.take(MAX_ROWS)?.forEach { key ->
            ImGui.pushID(key)
            ImGui.textUnformatted(InspectorLayout.fit(key, ImGui.getContentRegionAvailX() - MImGui.buttonWidth("-") - ImGui.getStyle().itemSpacingX) { ImGui.calcTextSize(it).x })
            MImGui.sameLineIfFits(MImGui.buttonWidth("-"))
            if (iconButton(engine, "-##remove", "inspector.map.remove.tooltip")) remove = key
            value(engine, "entry", "", null, valueType, entries[key]) { entries[key] = it }
            ImGui.popID()
        }
        val draft = keyDrafts.getOrPut("${System.identityHashCode(entries)}.$id") { ImString(64) }
        val addWidth = MImGui.buttonWidth("+")
        val sideBySide = ImGui.getContentRegionAvailX() >= addWidth + ImGui.getFontSize() * 8f
        ImGui.setNextItemWidth(if (sideBySide) ImGui.getContentRegionAvailX() - addWidth - ImGui.getStyle().itemSpacingX else -1f)
        ImGui.inputTextWithHint("##key", strings.getString("inspector.map.key_hint"), draft)
        Tooltips.showIfPresent(strings, "inspector.map.key_hint.tooltip")
        if (sideBySide) ImGui.sameLine()
        val key = draft.get().trim()
        val canAdd = entries != null && key.isNotEmpty() && key !in entries && FieldKind.of(valueType) != FieldKind.JsonValue && newValue(valueType) != null
        if (!canAdd) ImGui.beginDisabled()
        if (iconButton(engine, "+##add", "inspector.map.add.tooltip")) runCatching {
            entries?.put(key, newValue(valueType))
            draft.set("")
        }
        if (!canAdd) ImGui.endDisabled()
        runCatching { remove?.let { entries?.remove(it) } }
        ImGui.treePop()
    }

    private fun nested(engine: Engine, id: String, label: String, tooltipKey: String?, type: Class<*>, value: Any?) {
        if (!header(engine, id, label, tooltipKey, null)) return
        if (value == null) {
            ImGui.textDisabled(engine.stringManager.getString("inspector.struct.unset"))
        } else {
            nestedFields(engine, type, value)
        }
        ImGui.treePop()
    }

    private fun nestedFields(engine: Engine, type: Class<*>, target: Any) {
        for (f in FieldKind.nestedFields(type)) {
            f.isAccessible = true
            val labelKey = labelKey(type.simpleName, f.name)
            val label = engine.stringManager.getString(labelKey)
            // A Kotlin `val` scalar cannot change; vectors, lists, maps and objects are edited in place.
            val readOnly = Modifier.isFinal(f.modifiers) && FieldKind.of(f.genericType).let {
                it is FieldKind.IntField || it is FieldKind.LongField || it is FieldKind.FloatField || it is FieldKind.BoolField ||
                    it is FieldKind.TextField || it is FieldKind.EnumValue
            }
            ImGui.pushID(f.name)
            if (readOnly) ImGui.beginDisabled()
            value(engine, f.name, label, labelKey + Tooltips.SUFFIX, f.genericType, f.get(target)) { f.set(target, it) }
            if (readOnly) ImGui.endDisabled()
            ImGui.popID()
        }
    }
}
