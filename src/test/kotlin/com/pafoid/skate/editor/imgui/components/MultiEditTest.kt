package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.ComponentValues
import com.pafoid.skate.editor.commands.objects.EditComponentFieldsCommand.Change
import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.editor.events.ComponentFieldAction
import com.pafoid.skate.editor.events.TransformAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.ComponentFieldActionHandler
import com.pafoid.skate.editor.ui.handlers.TransformActionHandler
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MultiEditTest {
    private fun obj(name: String, x: Float, light: Boolean) = GameObject(name).apply {
        addComponent(Transform(translation = Vector3f(x, 1f, 2f)))
        if (light) addComponent(PointLightComponent())
    }

    private val a = obj("A", 1f, light = true)
    private val b = obj("B", 5f, light = true)
    private val c = obj("C", 9f, light = false)

    private val events = EventSystem(mockk(relaxed = true))
    private val undo = UndoRedoManager(events, mockk(relaxed = true))

    init {
        ComponentFieldActionHandler(events, undo)
        TransformActionHandler(events, undo, mockk(relaxed = true))
    }

    @AfterEach
    fun clear() = MultiEdit.clear()

    @Test
    fun Shared_ListsOnlyTypesEveryObjectHasFromThePrimary() {
        assertEquals(listOf("PointLightComponent", "Transform"), MultiEdit.shared(listOf(a, b)).map { it.javaClass.simpleName })
        assertEquals(listOf("Transform"), MultiEdit.shared(listOf(a, b, c)).map { it.javaClass.simpleName })
        assertTrue(MultiEdit.shared(listOf(b, c)).all { it.gameObject === c })
    }

    @Test
    fun Inspect_NeedsTwoObjects() {
        MultiEdit.inspect(listOf(a))
        assertTrue(MultiEdit.mirrorsOf(a.getComponent<Transform>()!!).isEmpty())
        MultiEdit.inspect(listOf(a, b))
        assertEquals(listOf(a.getComponent<Transform>()), MultiEdit.mirrorsOf(b.getComponent<Transform>()!!))
    }

    @Test
    fun TransformEdit_CopiesOnlyTheChangedAxesToEveryObjectAsOneUndoStep() {
        MultiEdit.inspect(listOf(a, c, b))
        val t = b.getComponent<Transform>()!!
        val old = TransformCommand.snapshot(t)
        t.translation.y = 7f // the inspector writes live, then publishes the finished edit
        val entry = TransformCommand.Entry(b, old, t)
        events.publish(TransformAction.Edited(entry, MultiEdit.mirrorTransform(entry)))
        assertEquals(Vector3f(1f, 7f, 2f), a.getComponent<Transform>()!!.translation)
        assertEquals(Vector3f(9f, 7f, 2f), c.getComponent<Transform>()!!.translation)
        assertEquals(1, undo.getUndoCount())
        undo.undo()
        assertEquals(Vector3f(1f, 1f, 2f), a.getComponent<Transform>()!!.translation)
        assertEquals(Vector3f(5f, 1f, 2f), t.translation)
    }

    @Test
    fun FieldEdit_IsCopiedToTheSameComponentOfTheOtherObjects() {
        MultiEdit.inspect(listOf(a, b))
        val primary = b.getComponent<PointLightComponent>()!!
        val other = a.getComponent<PointLightComponent>()!!
        val field = ComponentValues.fields(PointLightComponent::class.java).first { it.name == "intensity" }
        val before = field.get(primary)
        ComponentValues.write(primary, field, 4.5f)
        val changes = mapOf(field to Change(before, 4.5f))
        events.publish(ComponentFieldAction.Edited(primary, changes, MultiEdit.mirrorFields(primary, changes)))
        assertEquals(4.5f, field.get(other))
        assertEquals(1, undo.getUndoCount())
        undo.undo()
        assertEquals(before, field.get(other))
        assertEquals(before, field.get(primary))
    }
}
