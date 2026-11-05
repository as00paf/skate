package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.events.ComponentFieldAction
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.GameObjectRef
import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import imgui.type.ImString
import kotlinx.serialization.serializerOrNull
import java.lang.reflect.Field
import java.util.WeakHashMap

/**
 * String fields in the generic inspector (EDT-36). Every saved `String` field gets a text field; the edit is applied
 * through [ComponentFieldAction.Set] (one undoable command) when the field loses focus or Enter is pressed. Fields
 * annotated [GameObjectRef] also get an object picker: a dropdown of the scene's object names and a drop target for
 * objects dragged from the hierarchy.
 */
object StringFields {

    private val savedNames = HashMap<Class<*>, Set<String>?>()

    /**
     * True when [field] of [type] is a String the scene file saves. `kotlinx.serialization.Transient` runtime state
     * (e.g. `PlayerController.state`) is not JVM-transient, so the serializer's element names are the reference.
     */
    fun isEditable(type: Class<*>, field: Field): Boolean {
        if (field.type != String::class.java) return false
        val names = savedNames.getOrPut(type) {
            runCatching { serializerOrNull(type)?.descriptor?.let { d -> (0 until d.elementsCount).map(d::getElementName).toSet() } }
                .getOrNull()
        }
        return names == null || field.name in names
    }

    fun isGameObjectRef(field: Field): Boolean = field.isAnnotationPresent(GameObjectRef::class.java)

    /** Distinct object names under [root] in hierarchy order (pre-order, any depth), for the picker. */
    fun objectNames(root: GameObject): List<String> {
        val out = LinkedHashSet<String>()
        fun walk(node: GameObject) {
            node.children.forEach { out.add(it.name); walk(it) }
        }
        walk(root)
        return out.toList()
    }

    /** Value to store for [text] typed into a field that held [old]: an emptied nullable (null) field stays null. */
    fun valueFor(old: String?, text: String): String? = if (old == null && text.isEmpty()) null else text

    private val drafts = WeakHashMap<Component, MutableMap<String, ImString>>()

    fun imgui(engine: Engine, component: Component, field: Field, label: String) {
        val strings = engine.stringManager
        val current = field.get(component) as String?
        val draft = drafts.getOrPut(component) { HashMap() }.getOrPut(field.name) { ImString(current.orEmpty(), 256) }
        val id = "##string.${field.name}"
        val isRef = isGameObjectRef(field)

        ImGui.text(label)
        if (isRef) ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.6f)
        ImGui.inputText(id, draft, ImGuiInputTextFlags.None)
        if (ImGui.isItemDeactivatedAfterEdit()) set(engine, component, field, valueFor(current, draft.get()))
        // Follow outside changes (undo, picker, drop) whenever the user is not typing in this field.
        if (!ImGui.isItemActive()) draft.set((field.get(component) as String?).orEmpty())
        if (!isRef) return

        acceptHierarchyDrop(engine, component, field)
        if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("inspector.string_field.drop_hint"))

        ImGui.sameLine()
        ImGui.setNextItemWidth(-1f)
        val scene = engine.sceneManager.currentScene
        val none = strings.getString("inspector.string_field.none")
        if (ImGui.beginCombo("##pick.${field.name}", strings.getString("inspector.string_field.pick"))) {
            if (ImGui.selectable("$none##pick.none", current.isNullOrEmpty())) set(engine, component, field, valueFor(current, ""))
            scene?.let(::objectNames)?.forEach { name ->
                if (ImGui.selectable("$name##pick.${field.name}.$name", name == current)) set(engine, component, field, name)
            }
            ImGui.endCombo()
        }
        if (!current.isNullOrEmpty() && scene != null && scene.findFirstByName(current) == null) {
            ImGui.textColored(1f, 0.6f, 0.2f, 1f, strings.getString("inspector.string_field.missing", current))
        }
    }

    private fun acceptHierarchyDrop(engine: Engine, component: Component, field: Field) {
        if (!ImGui.beginDragDropTarget()) return
        ImGui.acceptDragDropPayload<Int>("GAMEOBJECT_UID")?.let { uid ->
            engine.gameObjectManager.getGameObject(uid)?.let { set(engine, component, field, it.name) }
        }
        ImGui.endDragDropTarget()
    }

    private fun set(engine: Engine, component: Component, field: Field, value: String?) {
        drafts[component]?.get(field.name)?.set(value.orEmpty())
        engine.eventSystem.publish(ComponentFieldAction.Set(component, field, value))
    }
}
