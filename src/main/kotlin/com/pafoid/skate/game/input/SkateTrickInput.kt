package com.pafoid.skate.game.input

import com.pafoid.skate.engine.gameplay.ActionQuery
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.engine.input.InputHistory
import com.pafoid.skate.game.player.TrickType
import kotlinx.serialization.Serializable
import org.joml.Vector2f
import kotlin.math.abs

/** Thresholds of [SkateTrickInput], per [ControlScheme]; serializable so a scheme can be tuned from a config. */
@Serializable
data class TrickInputSettings(
    val flick: FlickSettings = FlickSettings(),
    /** Flick-it: the trick stick must have been pulled back at least this far (y, 0..1) for a flick to count. */
    val preload: Float = 0.6f,
    /** Feet: the back foot pops when it goes this far down (y, 0..1). */
    val popThreshold: Float = 0.8f,
    /** Feet: seconds after the pop in which the front foot's flick picks the trick. */
    val popWindowSec: Float = 0.3f,
    /** A half circle waits until the stick has stopped turning for this long, to see whether it becomes a 360. */
    val shoveGraceSec: Float = 0.1f,
    /** The trick stick counts as still turning while its angle moves more than this per frame (degrees). */
    val turningDegPerFrame: Float = 3f,
    /** A turn of at least this many degrees is a 360 shove-it. */
    val fullTurnDeg: Float = 270f,
    /** Buttons: stick deflection that picks a flip direction. */
    val directionThreshold: Float = 0.5f,
)

/**
 * Reads the skate actions of the selected scheme into a trick request and, for the feet schemes, the stick that
 * steers (GPL-21). One per rider (it keeps a short [InputHistory] of the sticks); call [update] every frame.
 *
 * - [TrickStyle.FLICK_IT] (EA Skate): pull the trick stick back, then flick it: straight up ollie, up-left kickflip,
 *   up-right heelflip; a half circle is a shove-it, a full turn a 360 shove-it.
 * - [TrickStyle.FEET] (Session, Skater XL): pop with the back foot (trick stick down), then flick the front foot up
 *   (ollie), up-left (kickflip) or up-right (heelflip). Scooping the back foot round is a shove-it / 360 shove-it.
 * - [TrickStyle.BUTTONS] (THPS): the ollie button ollies; the flip button flips by the stick direction: left
 *   kickflip, right heelflip, down shove-it, up 360 shove-it, centred kickflip.
 *
 * A trick fires once; the sticks must come back to the centre before the next one.
 */
class SkateTrickInput {

    /** This frame's trick request (null: none) and the stick that steers and leans when the scheme moves it. */
    data class Frame(val trick: TrickType? = null, val move: Vector2f? = null)

    private val history = InputHistory(capacity = 64)
    private val recognizer = FlickRecognizer()
    private val trickStick = Vector2f()
    private val frontStick = Vector2f()
    private var time = 0f
    private var latched = false
    private var shoveUntil = -1f
    private var popTime = Float.NEGATIVE_INFINITY
    private var backWasDown = false
    private var lastAngle = Float.NaN
    private var turning = false

    init {
        history.track(TRICK, { trickStick.x }, { trickStick.y })
    }

    fun update(actions: ActionQuery, move: Vector2f, scheme: ControlScheme, dt: Float): Frame {
        val s = scheme.tricks
        recognizer.settings = s.flick
        time += dt.coerceAtLeast(0f)
        trickStick.set(actions.value(SkateActions.TRICK_X), actions.value(SkateActions.TRICK_Y))
        frontStick.set(actions.value(SkateActions.FRONT_X), actions.value(SkateActions.FRONT_Y))
        history.sample(time)
        trackTurning(s)
        // Read every frame, whatever the scheme: the reader tracks press edges from the first query on.
        val flipPressed = actions.wasPressed(SkateActions.FLIP)

        val trick = when (scheme.style) {
            TrickStyle.FLICK_IT -> flickIt(s)
            TrickStyle.FEET -> feet(s)
            TrickStyle.BUTTONS -> if (flipPressed) buttonFlip(move, s) else null
        }
        val steer = when {
            scheme.steerWithAction -> Vector2f(actions.value(SkateActions.STEER), frontStick.y)
            scheme.frontFootSteers -> Vector2f(frontStick)
            else -> null
        }
        return Frame(trick, steer)
    }

    private fun flickIt(s: TrickInputSettings): TrickType? {
        if (rearm(s, trickStick)) return null
        pendingShove(s)?.let { return fire(it) }
        if (shoveUntil >= 0f) return null
        val flick = recognizer.recognize(history, TRICK) ?: return null
        if (!preloaded(s)) return null
        return when (flick.shape) {
            FlickShape.LINE -> upFlick(flick.sector)?.let(::fire)
            FlickShape.QUARTER_CIRCLE -> null
            FlickShape.HALF_CIRCLE -> shove(flick, s)
        }
    }

    private fun feet(s: TrickInputSettings): TrickType? {
        val backDown = trickStick.y <= -s.popThreshold
        if (backDown && !backWasDown) popTime = time
        backWasDown = backDown
        if (rearm(s, trickStick, frontStick)) return null
        pendingShove(s)?.let { return fire(it) }
        if (shoveUntil >= 0f) return null
        recognizer.recognize(history, TRICK)?.takeIf { it.shape == FlickShape.HALF_CIRCLE }?.let { return shove(it, s) }
        if (time - popTime <= s.popWindowSec && frontStick.length() >= s.flick.endRadius) {
            return upFlick(recognizer.sector(frontStick.x, frontStick.y))?.let(::fire)
        }
        return null
    }

    private fun buttonFlip(move: Vector2f, s: TrickInputSettings): TrickType = when {
        move.x < -s.directionThreshold -> TrickType.KICKFLIP
        move.x > s.directionThreshold -> TrickType.HEELFLIP
        move.y < -s.directionThreshold -> TrickType.SHOVE_IT
        move.y > s.directionThreshold -> TrickType.THREE_SIXTY_SHOVE_IT
        else -> TrickType.KICKFLIP
    }

    /** Straight flick by end sector (0 up, 7 up-left, 1 up-right); other directions are no trick. */
    private fun upFlick(sector: Int): TrickType? = when (sector) {
        0 -> TrickType.OLLIE
        7 -> TrickType.KICKFLIP
        1 -> TrickType.HEELFLIP
        else -> null
    }

    /** A half circle: a 360 at once when it already turned that far, otherwise wait briefly for more. */
    private fun shove(flick: Flick, s: TrickInputSettings): TrickType? {
        if (abs(flick.sweepDeg) >= s.fullTurnDeg) return fire(TrickType.THREE_SIXTY_SHOVE_IT)
        shoveUntil = time + s.shoveGraceSec
        return null
    }

    /**
     * While a half circle waits: a 360 as soon as it has turned far enough; a shove-it once the stick has stopped
     * turning for [TrickInputSettings.shoveGraceSec] or was let go.
     */
    private fun pendingShove(s: TrickInputSettings): TrickType? {
        if (shoveUntil < 0f) return null
        if (turning) shoveUntil = time + s.shoveGraceSec
        val flick = recognizer.recognize(history, TRICK)
        return when {
            flick != null && abs(flick.sweepDeg) >= s.fullTurnDeg -> TrickType.THREE_SIXTY_SHOVE_IT
            time >= shoveUntil || trickStick.length() < s.flick.deadZone -> TrickType.SHOVE_IT
            else -> null
        }
    }

    /** Whether the trick stick's angle moved this frame (outside the dead zone). */
    private fun trackTurning(s: TrickInputSettings) {
        if (trickStick.length() < s.flick.deadZone) {
            lastAngle = Float.NaN
            turning = false
            return
        }
        val angle = kotlin.math.atan2(trickStick.y, trickStick.x)
        turning = !lastAngle.isNaN() && abs(Math.toDegrees(MotionMath.wrapAngle(angle - lastAngle).toDouble())) > s.turningDegPerFrame
        lastAngle = angle
    }

    private fun preloaded(s: TrickInputSettings): Boolean {
        var pulled = false
        history.forEachSample(TRICK, s.flick.windowSec) { _, _, y -> if (y <= -s.preload) pulled = true }
        return pulled
    }

    /** After a trick: true (keep waiting) until every stick is back in the centre. */
    private fun rearm(s: TrickInputSettings, vararg sticks: Vector2f): Boolean {
        if (!latched) return false
        if (sticks.all { it.length() < s.flick.deadZone }) {
            latched = false
            history.clear()
        }
        return latched
    }

    private fun fire(trick: TrickType): TrickType {
        latched = true
        shoveUntil = -1f
        popTime = Float.NEGATIVE_INFINITY
        return trick
    }

    private companion object {
        const val TRICK = "trick"
    }
}

