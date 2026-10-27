package com.pafoid.skate.game.player

import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.engine.gameplay.MountMath
import com.pafoid.skate.engine.gameplay.MountRequest
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.DefaultActions
import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MountTest {
    private val range = SkateControllerConfig().mount.range
    private fun press(distance: Float) = CharacterInput(mountPressed = true, mountDistance = distance)

    @Test
    fun Mount_RangeCheck() {
        assertTrue(MountMath.inRange(range, range))
        assertFalse(MountMath.inRange(range + 0.01f, range))
        assertFalse(MountMath.inRange(Float.NaN, range))
    }

    @Test
    fun Mount_TogglePressedInRangeMountsThenDismounts() {
        val b = SkateBehavior(SkateControllerConfig())
        assertFalse(b.mounted)

        val up = b.update(press(0.5f), 0.016f)
        assertEquals(MountRequest.MOUNT, up.mount)
        assertTrue(b.mounted)
        assertEquals(SkateState.IDLE.name, up.state)

        val down = b.update(press(0.5f), 0.016f)
        assertEquals(MountRequest.DISMOUNT, down.mount)
        assertFalse(b.mounted)
    }

    @Test
    fun Mount_OutOfRangeOrNoBoardIsRejected() {
        val b = SkateBehavior(SkateControllerConfig())
        assertEquals(MountRequest.NONE, b.update(press(range + 1f), 0.016f).mount)
        assertEquals(MountRequest.NONE, b.update(press(Float.NaN), 0.016f).mount)
        assertFalse(b.mounted)
    }

    @Test
    fun Mount_NoPressNoChangeAndDismountIgnoresDistance() {
        val b = SkateBehavior(SkateControllerConfig())
        assertEquals(MountRequest.NONE, b.update(CharacterInput(mountDistance = 0.1f), 0.016f).mount)
        b.mounted = true
        assertEquals(MountRequest.DISMOUNT, b.update(press(Float.NaN), 0.016f).mount)
    }

    @Test
    fun Mount_DismountedStillWalksWithStick() {
        val b = SkateBehavior(SkateControllerConfig())
        val motion = b.update(CharacterInput(move = Vector2f(0f, 1f)), 0.016f)
        assertTrue(motion.horizontalVelocity != null)
    }

    @Test
    fun Mount_DefaultActionIsKeyEAndGamepadY() {
        val map = DefaultActions.register(ActionMap())
        val bindings = map.bindings(DefaultActions.TOGGLE_MOUNT)
        assertTrue(bindings.any { it is ActionBinding.Key && it.key == 69 })
        assertTrue(bindings.any { it is ActionBinding.GamepadButton && it.button == 3 })
    }
}
