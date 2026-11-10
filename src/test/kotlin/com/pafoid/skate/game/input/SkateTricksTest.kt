package com.pafoid.skate.game.input

import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.game.player.SkateBehavior
import com.pafoid.skate.game.player.SkateControllerConfig
import com.pafoid.skate.game.player.TrickType
import org.joml.Vector2f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPL-21 acceptance: every control scheme performs the core trick set. Each trick is input the way its scheme says
 * (scripted sticks and buttons, one frame each), then the skater flies and lands; the trick must count as landed.
 */
class SkateTricksTest {

    private val dt = 1f / 60f
    private val config = SkateControllerConfig()

    /** One frame of input: the skate actions and the character's move stick. */
    private class Frame(val actions: FakeActions, val move: Vector2f = Vector2f())

    @AfterEach
    fun resetScheme() = SkateControls.select(ControlSchemes.DEFAULT.id)

    // --- Gestures -----------------------------------------------------------------------------------------------

    private fun hold(n: Int, frame: () -> Frame) = List(n) { frame() }

    /** Points on the rim from [fromDeg] to [toDeg] (0 = right, 90 = up), [n] samples. */
    private fun arc(fromDeg: Float, toDeg: Float, n: Int) = List(n) { i ->
        val a = Math.toRadians((fromDeg + (toDeg - fromDeg) * i / (n - 1f)).toDouble())
        Vector2f(cos(a).toFloat(), sin(a).toFloat())
    }

    private fun stick(p: Vector2f) = Frame(FakeActions.sticks(p.x, p.y))

    /** Pull back, then flick to ([x], [y]); or turn the trick stick from straight down by [turnDeg] (clockwise). */
    private fun flickIt(trick: TrickType): List<Frame> {
        val back = hold(6) { stick(Vector2f(0f, -1f)) }
        return when (trick) {
            TrickType.OLLIE -> back + stick(Vector2f()) + hold(3) { stick(Vector2f(0f, 1f)) }
            TrickType.KICKFLIP -> back + hold(3) { stick(Vector2f(-0.75f, 0.75f)) }
            TrickType.HEELFLIP -> back + hold(3) { stick(Vector2f(0.75f, 0.75f)) }
            TrickType.SHOVE_IT -> back + arc(-90f, -270f, 10).map(::stick) + hold(12) { stick(Vector2f(0f, 1f)) }
            TrickType.THREE_SIXTY_SHOVE_IT -> back + arc(-90f, -450f, 19).map(::stick) + hold(12) { stick(Vector2f(0f, -1f)) }
        }
    }

    /** Pop with the back foot (trick stick down) and flick the front foot; or scoop the back foot round. */
    private fun feet(trick: TrickType): List<Frame> {
        val pop = hold(3) { Frame(FakeActions.sticks(0f, -1f)) }
        fun front(x: Float, y: Float) = hold(3) { Frame(FakeActions.sticks(0f, -1f, x, y)) }
        return when (trick) {
            TrickType.OLLIE -> pop + front(0f, 1f)
            TrickType.KICKFLIP -> pop + front(-0.75f, 0.75f)
            TrickType.HEELFLIP -> pop + front(0.75f, 0.75f)
            TrickType.SHOVE_IT -> arc(-90f, -270f, 10).map(::stick) + hold(12) { stick(Vector2f(0f, 1f)) }
            TrickType.THREE_SIXTY_SHOVE_IT -> arc(-90f, -450f, 19).map(::stick) + hold(12) { stick(Vector2f(0f, -1f)) }
        }
    }

    /** Ollie button; flip button with a direction. */
    private fun buttons(trick: TrickType): List<Frame> {
        fun flip(x: Float, y: Float) = listOf(Frame(FakeActions(pressed = setOf(SkateActions.FLIP)), Vector2f(x, y)))
        return when (trick) {
            TrickType.OLLIE -> listOf(Frame(FakeActions(pressed = setOf(SkateActions.OLLIE))))
            TrickType.KICKFLIP -> flip(-1f, 0f)
            TrickType.HEELFLIP -> flip(1f, 0f)
            TrickType.SHOVE_IT -> flip(0f, -1f)
            TrickType.THREE_SIXTY_SHOVE_IT -> flip(0f, 1f)
        }
    }

    private fun gesture(scheme: ControlScheme, trick: TrickType) = when (scheme.style) {
        TrickStyle.FLICK_IT -> flickIt(trick)
        TrickStyle.FEET -> feet(trick)
        TrickStyle.BUTTONS -> buttons(trick)
    }

    // --- Ride ---------------------------------------------------------------------------------------------------

    private fun SkateBehavior.frame(f: Frame, grounded: Boolean = true): String =
        update(CharacterInput(move = f.move, grounded = grounded, horizontalSpeed = 3f, actions = f.actions), dt).state

    private val neutral get() = Frame(FakeActions())

    /** Rolls, inputs the [trick] the [scheme]'s way, flies 0.6 s and lands. Returns the landed trick. */
    private fun perform(scheme: ControlScheme, trick: TrickType): TrickType? {
        SkateControls.select(scheme.id)
        val rider = SkateBehavior(config).also { it.mounted = true }
        repeat(5) { rider.frame(neutral) }
        // Once the rider pops, the rest of the gesture happens in the air.
        var airborne = false
        val states = gesture(scheme, trick).map { f -> rider.frame(f, grounded = !airborne).also { if (it == "AIR") airborne = true } }
        assertEquals("AIR", states.last(), "${scheme.id} $trick: the gesture must pop (states $states)")
        repeat(36) { rider.frame(neutral, grounded = false) }
        repeat(10) { rider.frame(neutral) }
        assertEquals(1, rider.landedTricks, "${scheme.id} $trick lands once")
        return rider.lastLandedTrick
    }

    @Test
    fun everyScheme_LandsTheCoreTrickSet() {
        for (scheme in ControlSchemes.all) {
            for (trick in TrickType.entries) {
                assertEquals(trick, perform(scheme, trick), "${scheme.id} $trick")
            }
        }
    }

    // --- Interpreter details ------------------------------------------------------------------------------------

    private fun read(scheme: ControlScheme, frames: List<Frame>): List<TrickType?> {
        val input = SkateTrickInput()
        return frames.map { input.update(it.actions, it.move, scheme, dt).trick }
    }

    @Test
    fun flickIt_NeedsThePullBack() {
        val pushedUp = listOf(stick(Vector2f())) + hold(5) { stick(Vector2f(0f, 1f)) }
        assertEquals(List(6) { null }, read(ControlSchemes.EA_SKATE, pushedUp))
    }

    @Test
    fun aTrickFiresOnce_UntilTheSticksComeBackToTheCentre() {
        val twice = flickIt(TrickType.OLLIE) + hold(5) { stick(Vector2f(0f, 1f)) } + flickIt(TrickType.OLLIE)
        assertEquals(1, read(ControlSchemes.EA_SKATE, twice).count { it != null })

        val again = flickIt(TrickType.OLLIE) + stick(Vector2f()) + flickIt(TrickType.KICKFLIP)
        assertEquals(listOf(TrickType.OLLIE, TrickType.KICKFLIP), read(ControlSchemes.EA_SKATE, again).filterNotNull())
    }

    @Test
    fun aHalfCircleThatKeepsTurning_IsA360NotAShoveIt() {
        val tricks = read(ControlSchemes.EA_SKATE, flickIt(TrickType.THREE_SIXTY_SHOVE_IT)).filterNotNull()
        assertEquals(listOf(TrickType.THREE_SIXTY_SHOVE_IT), tricks)
    }

    @Test
    fun keyboardDiagonalFlick_IsAKickflip() {
        // IJKL keys jump straight from K (down) to I+J (up-left) with no sample in between.
        val keys = hold(4) { stick(Vector2f(0f, -1f)) } + hold(2) { stick(Vector2f(-1f, 1f)) }
        assertEquals(listOf(TrickType.KICKFLIP), read(ControlSchemes.EA_SKATE, keys).filterNotNull())
    }

    @Test
    fun feet_FrontFootWithoutAPop_IsNoTrick() {
        val lean = hold(6) { Frame(FakeActions.sticks(fx = -0.75f, fy = 0.75f)) }
        assertEquals(List(6) { null }, read(ControlSchemes.SESSION, lean))
    }

    @Test
    fun feetSchemes_SteerWithTheirOwnStick() {
        val input = SkateTrickInput()
        val session = input.update(FakeActions.sticks(fx = 0.5f, fy = 0.2f), Vector2f(), ControlSchemes.SESSION, dt)
        assertEquals(Vector2f(0.5f, 0.2f), session.move)

        val xl = FakeActions(values = mapOf(SkateActions.STEER to -0.8f, SkateActions.FRONT_Y to 0.3f))
        assertEquals(Vector2f(-0.8f, 0.3f), input.update(xl, Vector2f(), ControlSchemes.SKATER_XL, dt).move)
        assertNull(input.update(FakeActions(), Vector2f(1f, 0f), ControlSchemes.EA_SKATE, dt).move)
    }

    @Test
    fun landingBeforeTheFlipIsDone_IsABail() {
        SkateControls.select(ControlSchemes.THPS.id)
        val rider = SkateBehavior(config).also { it.mounted = true }
        rider.frame(neutral)
        buttons(TrickType.KICKFLIP).forEach { rider.frame(it) }
        // Touch down after 0.2 s: past the minimum air time, before the 0.4 s flip.
        repeat(12) { rider.frame(neutral, grounded = false) }

        assertEquals("BAIL", rider.frame(neutral))
        assertEquals(0, rider.landedTricks)
    }

    @Test
    fun thps_FlipAfterTheOllie_TurnsItIntoAFlipTrick() {
        SkateControls.select(ControlSchemes.THPS.id)
        val rider = SkateBehavior(config).also { it.mounted = true }
        rider.frame(neutral)
        buttons(TrickType.OLLIE).forEach { rider.frame(it) }
        assertEquals(TrickType.OLLIE, rider.trick)
        rider.frame(neutral, grounded = false)

        rider.frame(buttons(TrickType.HEELFLIP).single(), grounded = false)

        assertEquals(TrickType.HEELFLIP, rider.trick)
    }

    @Test
    fun boardSpinsThroughTheTrick() {
        SkateControls.select(ControlSchemes.THPS.id)
        val rider = SkateBehavior(config).also { it.mounted = true }
        rider.frame(neutral)
        assertNull(rider.mountSpin)
        buttons(TrickType.SHOVE_IT).forEach { rider.frame(it) }
        repeat(9) { rider.frame(neutral, grounded = false) }

        // 9 frames of a 0.3 s shove-it: half way, the board has turned 90 degrees around the vertical.
        val nose = rider.mountSpin!!.transform(org.joml.Vector3f(0f, 0f, 1f))
        assertEquals(1f, kotlin.math.abs(nose.x), 0.02f)
    }
}
