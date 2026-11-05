package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.components.StringFields
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.FollowCameraComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StringFieldsTest {

    private fun field(type: Class<*>, name: String) = type.getDeclaredField(name)

    @Test
    fun StringFields_OnlySavedStringsAreEditable() {
        val pc = PlayerController::class.java
        assertTrue(StringFields.isEditable(pc, field(pc, "behaviorId")))
        assertTrue(StringFields.isEditable(pc, field(pc, "configPath")))
        assertFalse(StringFields.isEditable(pc, field(pc, "state")), "runtime @Transient state must not be editable")
        val cam = FollowCameraComponent::class.java
        assertFalse(StringFields.isEditable(cam, field(cam, "distance")), "not a String")
    }

    @Test
    fun StringFields_GameObjectRefMarksObjectNames() {
        assertTrue(StringFields.isGameObjectRef(field(FollowCameraComponent::class.java, "targetName")))
        assertTrue(StringFields.isGameObjectRef(field(EnvironmentComponent::class.java, "fogCycleButton")))
        assertFalse(StringFields.isGameObjectRef(field(PlayerController::class.java, "configPath")))
    }

    @Test
    fun StringFields_PickerListsDistinctNamesAtAnyDepth() {
        val scene = Scene("s")
        val a = GameObject("A").also(scene::addChild)
        a.addChild(GameObject("Nested"))
        scene.addChild(GameObject("B"))
        scene.addChild(GameObject("A"))
        assertEquals(listOf("A", "Nested", "B"), StringFields.objectNames(scene))
    }

    @Test
    fun StringFields_EmptiedNullableStaysNull() {
        assertNull(StringFields.valueFor(null, ""))
        assertEquals("", StringFields.valueFor("x", ""))
        assertEquals("y", StringFields.valueFor(null, "y"))
    }

    @Test
    fun Scene_FindFirstByNameSearchesNestedAndSkipsExcluded() {
        val scene = Scene("s")
        val camera = GameObject("Target").also(scene::addChild)
        val rig = GameObject("Rig").also(scene::addChild)
        val nested = GameObject("Target").also(rig::addChild)
        assertEquals(camera, scene.findFirstByName("Target"))
        assertEquals(nested, scene.findFirstByName("Target", exclude = camera))
        assertNull(scene.findFirstByName("Missing"))
    }
}
