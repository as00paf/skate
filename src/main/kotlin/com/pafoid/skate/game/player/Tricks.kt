package com.pafoid.skate.game.player

import kotlinx.serialization.Serializable
import org.joml.Quaternionf
import kotlin.math.PI

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

/** How long each trick takes in the air and what it scores; part of [SkateControllerConfig]. */
@Serializable
data class TrickTuning(
    /** Seconds the board needs to finish a kickflip or heelflip; landing earlier is a bail. */
    val flipSec: Float = 0.4f,
    /** Seconds for a pop shove-it (180°). */
    val shoveItSec: Float = 0.3f,
    /** Seconds for a 360 shove-it. */
    val threeSixtyShoveItSec: Float = 0.5f,
    val olliePoints: Int = 100,
    val flipPoints: Int = 500,
    val shoveItPoints: Int = 300,
    val threeSixtyShoveItPoints: Int = 800,
) {
    /** Air time [trick] needs before a clean landing (0 for an ollie). */
    fun duration(trick: TrickType): Float = when (trick) {
        TrickType.OLLIE -> 0f
        TrickType.KICKFLIP, TrickType.HEELFLIP -> flipSec
        TrickType.SHOVE_IT -> shoveItSec
        TrickType.THREE_SIXTY_SHOVE_IT -> threeSixtyShoveItSec
    }

    fun points(trick: TrickType): Int = when (trick) {
        TrickType.OLLIE -> olliePoints
        TrickType.KICKFLIP, TrickType.HEELFLIP -> flipPoints
        TrickType.SHOVE_IT -> shoveItPoints
        TrickType.THREE_SIXTY_SHOVE_IT -> threeSixtyShoveItPoints
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
