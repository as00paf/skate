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

    @Test
    fun Mount_SwitchesLocomotionAndCameraBothWays() {
        val b = SkateBehavior(SkateControllerConfig())
        val walk = b.cameraPreset
        assertEquals(SkateControllerConfig().mount.walkCamera.distance, walk.distance)

        b.update(press(0.5f), 0.016f)
        assertEquals(SkateControllerConfig().mount.rideCamera.distance, b.cameraPreset.distance)
        assertTrue(b.mounted)

        b.update(press(0.5f), 0.016f)
        assertEquals(walk, b.cameraPreset)
        assertFalse(b.mounted)
    }

    @Test
    fun Mount_BoardCarriedWhenRiddenAndFreeWhenWalking() {
        assertEquals(0, MountMath.mountMask(true))
        assertEquals(-1, MountMath.mountMask(false))
        val mask = MountMath.characterMask(-1)
        assertEquals(0, mask and MountMath.MOUNT_LAYER)
        assertEquals(1, mask and 1)
    }

    @Test
    fun Mount_RiderHoversOnDeckAndBoardSitsUnderFeet() {
        val mount = SkateControllerConfig().mount
        val h = mount.rideHeight
        // On the ground the rider is lifted to the deck, at the deck it holds, above it is airborne.
        assertTrue(MountMath.hoverVelocity(true, 0f, h)!! > 0f)
        assertEquals(0f, MountMath.hoverVelocity(true, h, h)!!, 1e-6f)
        assertTrue(MountMath.hoverVelocity(true, h + 0.2f, h) == null)
        assertTrue(MountMath.hoverVelocity(false, 0f, h) == null)
        // Board centre (half its 0.1 m thickness) is under the feet, so its top is the deck the feet stand on.
        assertEquals(-h / 2, mount.offsetY, 1e-6f)
        assertEquals(h, SkateBehavior(SkateControllerConfig()).rideHeight, 0f)
    }
}
