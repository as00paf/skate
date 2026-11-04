package com.pafoid.skate.editor.imgui.components

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
    ImGui.textColored(1f, 0.75f, 0.2f, 1f, message)
    if (ImGui.button(strings.getString("ui.hint.fix"))) engine.eventSystem.publish(ViewportAction.AddComponent(go, fix))
}

private fun UIElementComponent.anchorPresetCombo(engine: Engine) {
    val presets = UiAnchorPreset.entries
    val names = presets.map { engine.stringManager.getString("ui.anchor.${it.name.lowercase()}") }.toTypedArray()
    val index = ImInt(-1)
    if (ImGui.combo(engine.stringManager.getString("ui.inspector.anchorPreset"), index, names, names.size) && index.get() >= 0) {
        presets[index.get()].apply(this)
    }
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
    when {
        type == Int::class.javaPrimitiveType -> {
            val v = intArrayOf(value as Int)
            if (ImGui.dragInt(label, v, 1f)) field.set(target, v[0])
        }
        type == Float::class.javaPrimitiveType -> {
            val v = floatArrayOf(value as Float)
            if (ImGui.dragFloat(label, v, 0.5f)) field.set(target, v[0])
        }
        type == Boolean::class.javaPrimitiveType -> {
            if (ImGui.checkbox(label, value as Boolean)) field.set(target, !(value))
        }
        type == String::class.java -> {
            val s = ImString((value as? String) ?: "", 256)
            if (ImGui.inputText(label, s)) field.set(target, s.get())
        }
        type == Integer::class.java -> {
            // Nullable Int: a checkbox toggles presence.
            var present = value != null
            if (ImGui.checkbox("##present", present)) {
                present = !present
                field.set(target, if (present) 1 else null)
            }
            ImGui.sameLine()
            if (value != null) {
                val v = intArrayOf(value as Int)
                if (ImGui.dragInt(label, v, 1f)) field.set(target, v[0])
            } else {
                ImGui.textDisabled(label)
            }
        }
        value is Vector4f -> {
            val c = floatArrayOf(value.x, value.y, value.z, value.w)
            if (ImGui.colorEdit4(label, c)) value.set(c[0], c[1], c[2], c[3])
        }
        type.isEnum -> {
            val constants = type.enumConstants
            val names = constants.map { it.toString() }.toTypedArray()
            val idx = ImInt((value as Enum<*>).ordinal)
            if (ImGui.combo(label, idx, names, names.size)) field.set(target, constants[idx.get()])
        }
        value is List<*> && isStringList(field) -> stringList(target, field, label, engine, value)
        value != null && value !is List<*> && type.name.startsWith("com.pafoid.skate.engine.ui.") -> {
            if (ImGui.treeNode(label)) {
                drawObject(value, engine, owner)
                ImGui.treePop()
            }
        }
    }
}

private fun isStringList(field: Field): Boolean =
    ((field.genericType as? ParameterizedType)?.actualTypeArguments?.firstOrNull() as? Class<*>) == String::class.java

private fun stringList(target: Any, field: Field, label: String, engine: Engine, list: List<*>) {
    if (!ImGui.treeNode(label)) return
    val items = list.filterIsInstance<String>().toMutableList()
    var changed = false
    items.indices.forEach { i ->
        ImGui.pushID(i)
        val s = ImString(items[i], 256)
        if (ImGui.inputText("##item", s)) { items[i] = s.get(); changed = true }
        ImGui.sameLine()
        if (ImGui.button(engine.stringManager.getString("ui.inspector.remove"))) { items.removeAt(i); changed = true }
        ImGui.popID()
        if (changed) return@forEach
    }
    if (ImGui.button(engine.stringManager.getString("ui.inspector.add"))) { items.add(""); changed = true }
    if (changed) field.set(target, items.toList())
    ImGui.treePop()
}

private fun UIListComponent.listItems(engine: Engine) {
    if (!ImGui.treeNode(engine.stringManager.getString("ui.inspector.listItems"))) return
    val rows = items.toMutableList()
    var changed = false
    var i = 0
    while (i < rows.size) {
        ImGui.pushID(i)
        val s = ImString(rows[i].key, 256)
        if (ImGui.inputText("##key", s)) { rows[i] = UiListItem(s.get(), rows[i].args); changed = true }
        ImGui.sameLine()
        if (ImGui.button(engine.stringManager.getString("ui.inspector.remove"))) { rows.removeAt(i); changed = true; ImGui.popID(); continue }
        ImGui.popID()
        i++
    }
    if (ImGui.button(engine.stringManager.getString("ui.inspector.add"))) { rows.add(UiListItem("ui.default.text")); changed = true }
    if (changed) items = rows.toList()
    ImGui.treePop()
}
