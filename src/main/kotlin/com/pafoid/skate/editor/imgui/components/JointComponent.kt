package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.JointSettings
import com.pafoid.skate.editor.events.JointAction
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.JointType
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.type.ImInt
import org.joml.Vector3f
import java.util.WeakHashMap

/** Edits are made on a working copy while a widget is held, then committed as one command on release. */
private val working = WeakHashMap<JointComponent, JointSettings>()

private fun bodies(root: GameObject?, out: MutableList<GameObject> = ArrayList()): List<GameObject> {
    root ?: return out
    if (root.getComponent<RigidBody3D>() != null) out.add(root)
    root.children.forEach { bodies(it, out) }
    return out
}

fun JointComponent.imgui(engine: Engine) {
    val strings = engine.stringManager
    fun s(key: String) = strings.getString("component.JointComponent.$key")
    ImGui.pushID(System.identityHashCode(this))

    val live = JointSettings.of(this)
    val state = working[this] ?: live
    var commit = false
    var edited = false
    val draft = state.deepCopy()
    var next = draft

    fun after() {
        if (ImGui.isItemDeactivatedAfterEdit()) commit = true
    }

    fun vec(label: String, v: Vector3f) {
        val a = floatArrayOf(v.x, v.y, v.z)
        if (MImGui.dragFloat3(s(label), a, 0.01f)) { v.set(a[0], a[1], a[2]); edited = true }
        after()
    }

    fun flt(label: String, get: Float, set: (Float) -> Unit) {
        val a = floatArrayOf(get)
        if (ImGui.dragFloat(s(label), a, 0.01f)) { set(a[0]); edited = true }
        after()
    }

    val types = JointType.entries.map { it.name }.toTypedArray()
    val typeIndex = ImInt(draft.jointType.ordinal)
    if (ImGui.combo(s("jointType"), typeIndex, types, types.size)) {
        next = draft.copy(jointType = JointType.entries[typeIndex.get()])
        working.remove(this)
        engine.eventSystem.publish(JointAction.Edit(this, next))
        ImGui.popID()
        return
    }

    val candidates = bodies(engine.sceneManager.currentScene).filter { it !== gameObject }
    val names = (listOf(s("worldAnchor")) + candidates.map { it.name }).toTypedArray()
    val currentIndex = candidates.indexOfFirst { it.uId == draft.connectedBodyId } + 1
    val connected = ImInt(currentIndex)
    if (ImGui.combo(s("connectedBody"), connected, names, names.size)) {
        val id = if (connected.get() == 0) -1 else candidates[connected.get() - 1].uId
        working.remove(this)
        engine.eventSystem.publish(JointAction.Edit(this, draft.copy(connectedBodyId = id)))
        ImGui.popID()
        return
    }

    vec("anchor", draft.anchor)
    if (draft.connectedBodyId != -1) vec("connectedAnchor", draft.connectedAnchor)
    when (draft.jointType) {
        JointType.FIXED -> {}
        JointType.HINGE, JointType.SLIDER -> {
            vec("axis", draft.axis)
            vec("connectedAxis", draft.connectedAxis)
            flt("limitMin", draft.limitMin) { next = next.copy(limitMin = it) }
            flt("limitMax", draft.limitMax) { next = next.copy(limitMax = it) }
        }
        JointType.CONE_TWIST -> {
            flt("swingSpan1", draft.swingSpan1) { next = next.copy(swingSpan1 = it) }
            flt("swingSpan2", draft.swingSpan2) { next = next.copy(swingSpan2 = it) }
            flt("twistSpan", draft.twistSpan) { next = next.copy(twistSpan = it) }
        }
        JointType.SIX_DOF -> {
            vec("linearLower", draft.linearLower)
            vec("linearUpper", draft.linearUpper)
            vec("angularLower", draft.angularLower)
            vec("angularUpper", draft.angularUpper)
        }
    }

    if (edited) working[this] = next
    else if (!commit && !ImGui.isAnyItemActive()) working.remove(this)
    if (commit) {
        working.remove(this)
        engine.eventSystem.publish(JointAction.Edit(this, next))
    }
    ImGui.popID()
}
