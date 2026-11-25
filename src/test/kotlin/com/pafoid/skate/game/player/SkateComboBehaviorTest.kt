package com.pafoid.skate.game.player

import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.engine.gameplay.CharacterMotion
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.game.input.FakeActions
import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

/** GPL-23: the skate behavior feeds the combo chain, names spins, grinds and manuals, and lands 180s fakie. */
class SkateComboBehaviorTest {
    private val cfg = SkateControllerConfig()
    private val dt = 0.016f
    private val b = SkateBehavior(cfg).also { it.mounted = true }
    private var yaw = 0f

    private fun step(
        speed: Float = 3f,
        grounded: Boolean = true,
        jump: Boolean = false,
        manual: Boolean = false,
        stick: Vector2f = Vector2f(),
        velocityYaw: Float = Float.NaN,
        rail: Boolean = false,
        vertical: Float = 0f,
    ): CharacterMotion {
        val motion = b.update(
            CharacterInput(
                move = stick, actions = FakeActions.skate(manual = manual, ollie = jump), grounded = grounded,
                horizontalSpeed = speed, velocityYaw = velocityYaw, verticalSpeed = vertical, yaw = yaw,
                groundTag = if (rail) PrefabsGenerator.GRINDABLE_TAG else null,
            ),
            dt,
        )
        motion.targetYaw?.let { yaw = it }
        return motion
    }

    private fun air(frames: Int, stick: Vector2f = Vector2f()) = repeat(frames) { step(grounded = false, stick = stick, vertical = -1f) }

    private fun waitOutLinkWindow() = repeat(30) { step() }

    @Test
    fun anOllieIntoAGrindAndOut_ChainsThreeMoves_ThenLands() {
        step()
        assertEquals("AIR", step(jump = true).state)
        air(20)
        // Leaning back as the board locks on: a 5-0.
        assertEquals("GRIND", step(rail = true, vertical = -0.5f, stick = Vector2f(0f, -0.8f)).state)
        assertEquals(ComboMove.Grind(GrindType.FIVE_O), b.balanceMove)
        step(rail = true)
        assertEquals("AIR", step(rail = true, jump = true).state)
        air(20)
        assertEquals("RIDE", step().state)

        val chain = listOf(ComboMove.Air(TrickType.OLLIE), ComboMove.Grind(GrindType.FIVE_O), ComboMove.Air(TrickType.OLLIE))
        assertEquals(chain, b.combo.moves)
        waitOutLinkWindow()
        assertEquals(ComboResult(chain, ComboOutcome.LANDED), b.combo.lastResult)
    }

    @Test
    fun aBoardLandingAcrossARail_LocksIntoABoardslide_AndKeepsItsTravel() {
        step()
        step(jump = true)
        air(20)
        val across = (PI / 2).toFloat()
        val m = step(rail = true, vertical = -0.5f, velocityYaw = across)
        assertEquals("GRIND", m.state)
        assertEquals(ComboMove.Grind(GrindType.BOARDSLIDE), b.balanceMove)
        val slide = step(rail = true, velocityYaw = across)
        val travel = MotionMath.directionOfYaw(across)
        assertEquals(travel.x, slide.horizontalVelocity!!.x / slide.horizontalVelocity!!.length(), 1e-4f)
    }

    @Test
    fun aSpinHeldAtThePop_IsNamed_AndA180LandsFakie() {
        step()
        // Stick right at the pop: a backside spin for a regular rider.
        step(jump = true, stick = Vector2f(1f, 0f))
        air(25, Vector2f(1f, 0f))
        assertEquals(BodySpin(SpinSide.BACKSIDE, 180), b.airSpin)
        air(10)

        // Still travelling the old way (velocity yaw 0) but facing backward: not a bail, it rides fakie.
        val landing = step(velocityYaw = 0f)
        assertEquals("RIDE", landing.state)
        assertTrue(b.fakie)
        assertEquals(ComboMove.Air(TrickType.OLLIE, BodySpin(SpinSide.BACKSIDE, 180)), b.lastLanded)
        // The board keeps rolling the old way although the rider faces back.
        val v = step(velocityYaw = 0f).horizontalVelocity!!
        val forward = MotionMath.directionOfYaw(0f)
        assertTrue(v.dot(forward) > 0f, "rolls backward along its travel, got $v")
    }

    @Test
    fun withoutAStickAtThePop_TheAirTurnStaysSlow_AndNoSpinIsNamed() {
        step()
        step(jump = true)
        air(30, Vector2f(1f, 0f))
        // 0.48 s at the plain air turn rate is about 41°.
        assertEquals(null, b.airSpin)
        air(5)
        step(velocityYaw = yaw)
        assertEquals(ComboMove.Air(TrickType.OLLIE), b.lastLanded)
        assertFalse(b.fakie)
    }

    @Test
    fun aNoseManual_StartsWithTheStickForward_IsBalancedWithItBack_AndDoesNotBrake() {
        step()
        assertEquals("MANUAL", step(manual = true, stick = Vector2f(0f, 0.8f)).state)
        assertEquals(ComboMove.Manual(ManualType.NOSE_MANUAL), b.balanceMove)
        repeat(300) {
            val back = if (b.manualAngle > 0f) -1f else 1f
            val m = step(manual = true, stick = Vector2f(0f, back))
            // Stick back balances; it does not brake as it would in a manual.
            assertTrue(m.horizontalVelocity!!.length() > 3f - cfg.ride.brakeDecel * dt * 0.5f)
        }
        assertEquals("MANUAL", b.state)
        assertEquals(listOf<ComboMove>(ComboMove.Manual(ManualType.NOSE_MANUAL)), b.combo.moves)
    }

    @Test
    fun aBail_LosesTheCombo_AndRespawnClearsFakie() {
        step()
        step(jump = true)
        air(20)
        step()
        assertEquals(listOf<ComboMove>(ComboMove.Air(TrickType.OLLIE)), b.combo.moves)
        // Into a manual inside the link window, then it tips over.
        step(manual = true)
        var t = 0
        while (b.state == "MANUAL" && t++ < 1000) step(manual = true)
        assertEquals("BAIL", b.state)
        assertEquals(ComboOutcome.BAILED, b.combo.lastResult!!.outcome)
        assertEquals(2, b.combo.lastResult!!.moves.size)

        b.respawn()
        assertFalse(b.fakie)
        assertEquals(emptyList<ComboMove>(), b.combo.moves)
    }
}
