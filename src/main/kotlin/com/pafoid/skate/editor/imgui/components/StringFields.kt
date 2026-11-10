package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.events.ComponentFieldAction
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.GameObjectRef
import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import imgui.type.ImString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.serializerOrNull
import java.lang.reflect.Field
import java.util.IdentityHashMap

/**
 * String fields in the generic inspector (EDT-36). Every saved `String` field gets a text field; the edit is applied
 * through [ComponentFieldAction.Set] (one undoable command) when the field loses focus or Enter is pressed. Fields
 * annotated [GameObjectRef] also get an object picker: a dropdown of the scene's object names and a drop target for
 * objects dragged from the hierarchy.
 *
 * A field being typed in that stops being drawn (another object selected, header collapsed, window closed) is
 * committed by [endFrame] instead of being lost (EDT-40).
 */
object StringFields {

    private val savedNames = HashMap<Class<*>, Set<String>?>()

    /**
     * True when [field] of [type] is a String the scene file saves. `kotlinx.serialization.Transient` runtime state
     * (e.g. `PlayerController.state`) is not JVM-transient, so the serializer's element names are the reference.
     */
    @OptIn(ExperimentalSerializationApi::class) // serializerOrNull(Class) is the only lookup for a runtime Class
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

    /** Text being edited for one field; [frame] is the last ImGui frame it was drawn in. */
    private class Draft(val field: Field, val text: ImString, var editing: Boolean = false, var frame: Int = -1)

    /** Keyed by identity: many components are data classes whose equals/hashCode change while edited. */
    private val drafts = IdentityHashMap<Component, HashMap<String, Draft>>()

    private fun read(component: Component, field: Field): String? = field.also { it.isAccessible = true }.get(component) as String?

    private fun draftFor(component: Component, field: Field): Draft =
        drafts.getOrPut(component) { HashMap() }
            .getOrPut(field.name) { Draft(field, ImString(read(component, field).orEmpty(), 256)) }

    /** Records what the inspector drew for [field] this [frame] ([text] typed so far, [editing] while focused). */
    internal fun track(component: Component, field: Field, text: String, editing: Boolean, frame: Int) {
        draftFor(component, field).apply { this.text.set(text); this.editing = editing; this.frame = frame }
    }

    /**
     * Once per frame after every window drew: a field still being typed in that was not drawn in [frame] is
     * committed (one command), then forgotten; undrawn idle drafts are dropped.
     */
    fun endFrame(events: EventSystem, frame: Int) {
        val byComponent = drafts.entries.iterator()
        while (byComponent.hasNext()) {
            val (component, fields) = byComponent.next()
            val byField = fields.values.iterator()
            while (byField.hasNext()) {
                val d = byField.next()
                if (d.frame == frame) continue
                if (d.editing) {
                    val old = read(component, d.field)
                    val value = valueFor(old, d.text.get())
                    if (value != old) events.publish(ComponentFieldAction.Set(component, d.field, value))
                }
                byField.remove()
            }
            if (fields.isEmpty()) byComponent.remove()
        }
    }

    fun imgui(engine: Engine, component: Component, field: Field, label: String) {
        val strings = engine.stringManager
        val current = field.get(component) as String?
        val d = draftFor(component, field)
        d.frame = ImGui.getFrameCount()
        val draft = d.text
        val id = "##string.${field.name}"
        val isRef = isGameObjectRef(field)

        ImGui.text(label)
        if (isRef) ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.6f)
        ImGui.inputText(id, draft, ImGuiInputTextFlags.None)
        if (ImGui.isItemDeactivatedAfterEdit()) {
            d.editing = false
            set(engine.eventSystem, component, field, valueFor(current, draft.get()))
        } else {
            d.editing = ImGui.isItemActive()
        }
        // Follow outside changes (undo, picker, drop) whenever the user is not typing in this field.
        if (!ImGui.isItemActive()) draft.set((field.get(component) as String?).orEmpty())
        if (!isRef) return

        acceptHierarchyDrop(engine, component, field)
        Tooltips.text(strings.getString("inspector.string_field.drop_hint"))

        ImGui.sameLine()
        ImGui.setNextItemWidth(-1f)
        val scene = engine.sceneManager.currentScene
        val none = strings.getString("inspector.string_field.none")
        if (ImGui.beginCombo("##pick.${field.name}", strings.getString("inspector.string_field.pick"))) {
            if (ImGui.selectable("$none##pick.none", current.isNullOrEmpty())) set(engine.eventSystem, component, field, valueFor(current, ""))
            scene?.let(::objectNames)?.forEach { name ->
                if (ImGui.selectable("$name##pick.${field.name}.$name", name == current)) set(engine.eventSystem, component, field, name)
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
            engine.gameObjectManager.getGameObject(uid)?.let { set(engine.eventSystem, component, field, it.name) }
        }
        ImGui.endDragDropTarget()
    }

    private fun set(events: EventSystem, component: Component, field: Field, value: String?) {
        drafts[component]?.get(field.name)?.text?.set(value.orEmpty())
        events.publish(ComponentFieldAction.Set(component, field, value))
    }
}
