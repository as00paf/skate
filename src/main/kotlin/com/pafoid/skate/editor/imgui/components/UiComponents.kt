package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.EnumLabels
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.TrailingButtons
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UIListComponent
import com.pafoid.skate.engine.ui.UiAnchorPreset
import com.pafoid.skate.engine.ui.UiListItem
import com.pafoid.skate.engine.ui.UiProblem
import com.pafoid.skate.engine.ui.UiRequirements
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.engine.ecs.components.ComponentType
import imgui.ImGui
import imgui.type.ImInt
import imgui.type.ImString
import org.joml.Vector4f
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

/** True for the runtime UI components, which the generic reflective inspector cannot edit (strings, lists, nested data). */
fun Component.isUiComponent() = javaClass.name.startsWith("com.pafoid.skate.engine.ui.")

/** Inspector for every `engine.ui` component: field-by-field via reflection, labels from `ui.inspector.<field>`. */
fun Component.uiImgui(engine: Engine) {
    ImGui.pushID(javaClass.simpleName)
    requirementHint(engine)
    if (this is UIElementComponent) anchorPresetCombo(engine)
    drawObject(this, engine, this)
    if (this is UIListComponent) listItems(engine)
    ImGui.popID()
}

/** Explains why this object's UI cannot show yet, with a button that adds the missing component (undoable). */
private fun Component.requirementHint(engine: Engine) {
    val go = gameObject ?: return
    val problem = UiRequirements.problem(go) ?: return
    val strings = engine.stringManager
    val (message, fix) = when (problem) {
        UiProblem.NO_ELEMENT -> strings.getString("ui.hint.no_element") to ComponentType.UI_ELEMENT
        UiProblem.NO_CANVAS -> strings.getString("ui.hint.no_canvas") to ComponentType.UI_CANVAS
    }
    ImGui.pushTextWrapPos(0f) // EDT-100: wraps in a narrow inspector
    ImGui.textColored(1f, 0.75f, 0.2f, 1f, message)
    ImGui.popTextWrapPos()
    if (strings.tipped(MImGui.fitButton(strings.getString("ui.hint.fix")), "ui.hint.fix")) engine.eventSystem.publish(ViewportAction.AddComponent(go, fix))
}

private fun UIElementComponent.anchorPresetCombo(engine: Engine) {
    val presets = UiAnchorPreset.entries
    val names = presets.map { engine.stringManager.getString("ui.anchor.${it.name.lowercase()}") }.toTypedArray()
    val index = ImInt(-1)
    val picked = MImGui.field(engine.stringManager.getString("ui.inspector.anchorPreset"), "ui.inspector.anchorPreset") {
        ImGui.combo("##value", index, names, names.size)
    }
    if (picked && index.get() >= 0) presets[index.get()].apply(this)
}

private fun drawObject(target: Any, engine: Engine, owner: Component) {
    allFields(target.javaClass).forEach { field ->
        val mods = field.modifiers
        if (Modifier.isTransient(mods) || Modifier.isStatic(mods) || Modifier.isFinal(mods)) return@forEach
        field.isAccessible = true
        val label = engine.stringManager.getString("ui.inspector.${field.name}")
        ImGui.pushID(field.name)
        drawField(target, field, label, engine, owner)
        ImGui.popID()
    }
}

private fun allFields(c: Class<*>): List<Field> {
    val chain = generateSequence(c) { it.superclass }.takeWhile { it != Component::class.java && it != Any::class.java }.toList()
    return chain.reversed().flatMap { it.declaredFields.toList() }
}

private fun drawField(target: Any, field: Field, label: String, engine: Engine, owner: Component) {
    val type = field.type
    val value = field.get(target)
    val tooltipKey = "ui.inspector.${field.name}${Tooltips.SUFFIX}"
    // EDT-100: each value in the shared row, its label on the left, so a narrow inspector stacks instead of clipping.
    fun row(control: () -> Unit) = MImGui.row(label, field.name) {
        control()
        Tooltips.showIfPresent(engine.stringManager, tooltipKey)
    }
    when {
        type == Int::class.javaPrimitiveType -> row {
            val v = intArrayOf(value as Int)
            if (ImGui.dragInt("##value", v, 1f)) field.set(target, v[0])
        }
        type == Float::class.javaPrimitiveType -> row {
            val v = floatArrayOf(value as Float)
            if (ImGui.dragFloat("##value", v, 0.5f)) field.set(target, v[0])
        }
        type == Boolean::class.javaPrimitiveType -> row {
            if (ImGui.checkbox("##value", value as Boolean)) field.set(target, !(value))
        }
        type == String::class.java -> row {
            val s = ImString((value as? String) ?: "", 256)
            if (ImGui.inputText("##value", s)) field.set(target, s.get())
        }
        type == Integer::class.java -> row {
            // Nullable Int: a checkbox toggles presence.
            var present = value != null
            if (ImGui.checkbox("##present", present)) {
                present = !present
                field.set(target, if (present) 1 else null)
            }
            if (value != null) {
                ImGui.sameLine()
                ImGui.setNextItemWidth(-1f)
                val v = intArrayOf(value as Int)
                if (ImGui.dragInt("##value", v, 1f)) field.set(target, v[0])
            }
        }
        value is Vector4f -> row {
            val c = floatArrayOf(value.x, value.y, value.z, value.w)
            if (ImGui.colorEdit4("##value", c)) value.set(c[0], c[1], c[2], c[3])
        }
        type.isEnum -> row {
            val constants = type.enumConstants
            val names = EnumLabels.all(engine.stringManager, constants.map { it as Enum<*> })
            val idx = ImInt((value as Enum<*>).ordinal)
            if (ImGui.combo("##value", idx, names, names.size)) field.set(target, constants[idx.get()])
        }
        value is List<*> && isStringList(field) -> stringList(target, field, label, engine, value)
        value != null && value !is List<*> && type.name.startsWith("com.pafoid.skate.engine.ui.") -> {
            if (ImGui.treeNode("$label###${field.name}")) {
                drawObject(value, engine, owner)
                ImGui.treePop()
            }
            Tooltips.showIfPresent(engine.stringManager, tooltipKey)
        }
    }
}

private fun isStringList(field: Field): Boolean =
    ((field.genericType as? ParameterizedType)?.actualTypeArguments?.firstOrNull() as? Class<*>) == String::class.java

private fun stringList(target: Any, field: Field, label: String, engine: Engine, list: List<*>) {
    if (!ImGui.treeNode("$label###${field.name}")) return
    val items = list.filterIsInstance<String>().toMutableList()
    var changed = false
    items.indices.forEach { i ->
        ImGui.pushID(i)
        val s = ImString(items[i], 256)
        val row = TrailingButtons.begin(engine.stringManager.getString("ui.inspector.remove"))
        if (ImGui.inputText("##item", s)) { items[i] = s.get(); changed = true }
        row.next()
        if (engine.stringManager.tipped(ImGui.button(engine.stringManager.getString("ui.inspector.remove")), "ui.inspector.remove")) { items.removeAt(i); changed = true }
        ImGui.popID()
        if (changed) return@forEach
    }
    if (engine.stringManager.tipped(ImGui.button(engine.stringManager.getString("ui.inspector.add")), "ui.inspector.add")) { items.add(""); changed = true }
    if (changed) field.set(target, items.toList())
    ImGui.treePop()
}

private fun UIListComponent.listItems(engine: Engine) {
    if (!ImGui.treeNode(engine.stringManager.label("ui.inspector.listItems"))) return
    val rows = items.toMutableList()
    var changed = false
    var i = 0
    while (i < rows.size) {
        ImGui.pushID(i)
        val s = ImString(rows[i].key, 256)
        val row = TrailingButtons.begin(engine.stringManager.getString("ui.inspector.remove"))
        if (ImGui.inputText("##key", s)) { rows[i] = UiListItem(s.get(), rows[i].args); changed = true }
        row.next()
        if (engine.stringManager.tipped(ImGui.button(engine.stringManager.getString("ui.inspector.remove")), "ui.inspector.remove")) { rows.removeAt(i); changed = true; ImGui.popID(); continue }
        ImGui.popID()
        i++
    }
    if (engine.stringManager.tipped(ImGui.button(engine.stringManager.getString("ui.inspector.add")), "ui.inspector.add")) { rows.add(UiListItem("ui.default.text")); changed = true }
    if (changed) items = rows.toList()
    ImGui.treePop()
}
