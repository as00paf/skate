package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.ComponentValues
import com.pafoid.skate.editor.commands.objects.EditComponentFieldsCommand
import com.pafoid.skate.engine.ecs.components.CameraFocusComponent
import com.pafoid.skate.engine.ecs.components.CameraFocusTarget
import com.pafoid.skate.engine.ecs.components.HealthComponent
import com.pafoid.skate.engine.ecs.components.VehicleComponent
import com.pafoid.skate.engine.ecs.components.WheelConfig
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StructuredFieldsTest {

    @Test
    fun StructuredFields_ClassifiesListsMapsAndNestedObjects() {
        val wheels = VehicleComponent::class.java.getDeclaredField("wheels")
        assertEquals(FieldKind.ListOf(WheelConfig::class.java), FieldKind.of(wheels.genericType))
        val resistances = HealthComponent::class.java.getDeclaredField("resistances")
        assertEquals(FieldKind.MapOf(java.lang.Float::class.java), FieldKind.of(resistances.genericType))
        assertEquals(FieldKind.Nested(WheelConfig::class.java), FieldKind.of(WheelConfig::class.java))
    }

    @Test
    fun StructuredFields_NewValuesForListItems() {
        assertEquals(WheelConfig(), StructuredFields.newValue(WheelConfig::class.java))
        assertEquals(0f, StructuredFields.newValue(java.lang.Float::class.java))
        assertEquals(Vector3f(), StructuredFields.newValue(Vector3f::class.java))
        assertEquals(null, StructuredFields.newValue(Any::class.java))
    }

    @Test
    fun StructuredFields_ListEditIsOneUndoableChange() {
        val vehicle = VehicleComponent()
        val tracker = InspectorEditTracker { 0L }
        tracker.track(vehicle) {
            vehicle.wheels.add(WheelConfig(Vector3f(1f, 2f, 3f)))
            vehicle.wheels[0].radius = 0.9f
        }
        val edit = tracker.settle(anyItemActive = false).single()
        val change = edit.changes.entries.single { it.key.name == "wheels" }.value
        val command = EditComponentFieldsCommand(vehicle, mapOf(edit.changes.keys.single { it.name == "wheels" } to change))

        assertEquals(5, vehicle.wheels.size)
        command.undo()
        assertEquals(4, vehicle.wheels.size)
        assertEquals(0.35f, vehicle.wheels[0].radius)
        command.execute()
        assertEquals(5, vehicle.wheels.size)
        assertEquals(0.9f, vehicle.wheels[0].radius)
        assertEquals(Vector3f(1f, 2f, 3f), vehicle.wheels[4].offset)
    }

    @Test
    fun StructuredFields_MapEditUndoes() {
        val health = HealthComponent()
        val field = ComponentValues.fields(HealthComponent::class.java).first { it.name == "resistances" }
        val before = ComponentValues.capture(health, field)
        @Suppress("UNCHECKED_CAST")
        (health.resistances as MutableMap<String, Float>)["fire"] = 0.5f
        val after = ComponentValues.capture(health, field)
        assertFalse(before == after)
        ComponentValues.write(health, field, before)
        assertFalse("fire" in health.resistances)
        ComponentValues.write(health, field, after)
        assertEquals(0.5f, health.resistances["fire"])
    }

    @Test
    fun StructuredFields_UnchangedStructureRecordsNothing() {
        val camera = CameraFocusComponent().apply { targets.add(CameraFocusTarget(name = "A")) }
        val tracker = InspectorEditTracker { 0L }
        tracker.track(camera) { }
        assertTrue(tracker.settle(anyItemActive = false).isEmpty())
    }
}
