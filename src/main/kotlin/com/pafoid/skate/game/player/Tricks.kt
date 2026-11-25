package com.pafoid.skate.game.player

import com.pafoid.skate.game.skateboard.PreferredStance
import kotlinx.serialization.Serializable
import org.joml.Quaternionf
import kotlin.math.PI
import kotlin.math.abs

/**
 * The core trick set (GPL-21): every control scheme can do each of these. [nameKey] is the HUD / game string.
 * The board spins around its long axis for flips and around the vertical for shove-its.
 */
enum class TrickType(val nameKey: String) {
    OLLIE("trick.ollie"),
    KICKFLIP("trick.kickflip"),
    HEELFLIP("trick.heelflip"),
    SHOVE_IT("trick.shoveit"),
    THREE_SIXTY_SHOVE_IT("trick.360shoveit"),
}

/** How long each trick takes in the air, and how body spins and named grinds are read; part of [SkateControllerConfig]. */
@Serializable
data class TrickTuning(
    /** Seconds the board needs to finish a kickflip or heelflip; landing earlier is a bail. */
    val flipSec: Float = 0.4f,
    /** Seconds for a pop shove-it (180°). */
    val shoveItSec: Float = 0.3f,
    /** Seconds for a 360 shove-it. */
    val threeSixtyShoveItSec: Float = 0.5f,
    /** Steering stick held past this (left or right) at the pop starts a body spin (GPL-23). */
    val spinStickThreshold: Float = 0.5f,
    /** Air turn rate in rad/s while spinning, instead of [AirTuning.airTurnRate]; about a 360 in a full ollie. */
    val spinTurnRate: Float = 8f,
    /** Stick back / forward past this when a grind starts makes it a 5-0 / nosegrind, and a manual a nose manual. */
    val leanThreshold: Float = 0.5f,
    /** Board this far (degrees) across its travel when it locks on a rail: a boardslide instead of a grind. */
    val boardslideMinDeg: Float = 45f,
) {
    /** Air time [trick] needs before a clean landing (0 for an ollie). */
    fun duration(trick: TrickType): Float = when (trick) {
        TrickType.OLLIE -> 0f
        TrickType.KICKFLIP, TrickType.HEELFLIP -> flipSec
        TrickType.SHOVE_IT -> shoveItSec
        TrickType.THREE_SIXTY_SHOVE_IT -> threeSixtyShoveItSec
    }
}

/** Named grinds (GPL-23): picked by the lean on entry, or how the board sits on the rail. */
enum class GrindType(val nameKey: String) {
    FIFTY_FIFTY("trick.5050"),
    FIVE_O("trick.5_0"),
    NOSEGRIND("trick.nosegrind"),
    BOARDSLIDE("trick.boardslide");

    companion object {
        /** [lean] is the stick's forward axis on entry (+ = forward); [acrossDeg] the board's angle to its travel. */
        fun of(lean: Float, acrossDeg: Float, tuning: TrickTuning): GrindType = when {
            acrossDeg >= tuning.boardslideMinDeg && acrossDeg <= 180f - tuning.boardslideMinDeg -> BOARDSLIDE
            lean <= -tuning.leanThreshold -> FIVE_O
            lean >= tuning.leanThreshold -> NOSEGRIND
            else -> FIFTY_FIFTY
        }
    }
}

/** Manual on the back wheels, or on the front ones when the stick is forward on entry (GPL-23). */
enum class ManualType(val nameKey: String) {
    MANUAL("trick.manual"),
    NOSE_MANUAL("trick.nosemanual"),
}

/** Frontside or backside, from which way the rider turns and their stance. */
enum class SpinSide(val nameKey: String) { FRONTSIDE("trick.fs"), BACKSIDE("trick.bs") }

/** A body spin landed in the air, e.g. FS 180 (GPL-23). [degrees] is a multiple of 180. */
data class BodySpin(val side: SpinSide, val degrees: Int) {
    companion object {
        /**
         * The spin [turnRad] of accumulated air turn names, to the nearest 180° (null under 90°). Turning right
         * (negative yaw, like the steering) is backside for a regular rider and frontside for a goofy one.
         */
        fun of(turnRad: Float, stance: PreferredStance): BodySpin? {
            val deg = Math.toDegrees(turnRad.toDouble())
            val halves = Math.round(abs(deg) / 180.0).toInt()
            if (halves == 0) return null
            val right = deg < 0
            val frontside = right == (stance == PreferredStance.GOOFY)
            return BodySpin(if (frontside) SpinSide.FRONTSIDE else SpinSide.BACKSIDE, halves * 180)
        }
    }
}

/** Board rotation of a trick in the board's own frame (+Z = nose), from [progress] 0 (pop) to 1 (caught). */
object TrickSpin {
    fun of(trick: TrickType, progress: Float, out: Quaternionf = Quaternionf()): Quaternionf {
        val p = progress.coerceIn(0f, 1f)
        val turn = (2 * PI).toFloat()
        return when (trick) {
            TrickType.OLLIE -> out.identity()
            // Kickflip: the toe side goes up (roll one way around the long axis); heelflip the other.
            TrickType.KICKFLIP -> out.rotationZ(-turn * p)
            TrickType.HEELFLIP -> out.rotationZ(turn * p)
            TrickType.SHOVE_IT -> out.rotationY(turn / 2f * p)
            TrickType.THREE_SIXTY_SHOVE_IT -> out.rotationY(turn * p)
        }
    }
}
