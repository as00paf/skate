package com.pafoid.skate.engine.gameplay.camera

import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.DefaultActions
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.listeners.GamepadConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CameraCycleTest {

    @Test
    fun next_WrapsAroundAndStartsOutsideCamerasAtTheFirst() {
        assertEquals(listOf(1, 2, 0), (0..2).map { CameraCycle.next(it, 3) })
        assertEquals(0, CameraCycle.next(-1, 3))
        assertEquals(-1, CameraCycle.next(-1, 0))
    }

    @Test
    fun parseNames_TrimsAndDropsBlanks() {
        assertEquals(listOf("Camera", "FirstPersonCamera", "FixedCamera"), CameraCycle.parseNames(" Camera,FirstPersonCamera , ,FixedCamera,"))
        assertEquals(emptyList<String>(), CameraCycle.parseNames(""))
    }

    @Test
    fun bindDefaults_AddsAGameplayCameraActionOnCAndBackWithoutConflicts() {
        val map = DefaultActions.register(ActionMap())
        CameraCycle.bindDefaults(map)

        val def = map.definition(CameraCycle.ACTION)!!
        assertEquals(InputContext.GAMEPLAY, def.context)
        assertEquals("Camera", def.category)
        assertEquals(listOf(ActionBinding.Key('C'.code), ActionBinding.GamepadButton(GamepadConstants.BUTTON_BACK)), def.defaults)
        def.defaults.forEach { assertTrue(map.findConflicts(CameraCycle.ACTION, it).isEmpty(), "$it already used") }
    }

    @Test
    fun bindDefaults_IsIdempotentAndKeepsUserBindings() {
        val map = DefaultActions.register(ActionMap())
        CameraCycle.bindDefaults(map)
        map.rebind(CameraCycle.ACTION, 0, ActionBinding.Key('V'.code))
        val revision = map.revision

        CameraCycle.bindDefaults(map)

        assertEquals(revision, map.revision)
        assertEquals(ActionBinding.Key('V'.code), map.bindings(CameraCycle.ACTION).first())
    }
}
