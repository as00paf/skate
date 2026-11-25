package com.pafoid.skate.game.ui

import com.pafoid.skate.engine.ui.GameOption
import com.pafoid.skate.game.input.ControlScheme
import com.pafoid.skate.game.player.SkateBehavior
import com.pafoid.skate.game.player.SkateState

/**
 * On-screen control hints (GPL-23 §6): what the rider can do now, in the selected scheme's terms. One game string per
 * scheme and situation, `hint.<scheme id>.<situation>`. On by default; the pause menu switches them off ([option]).
 */
object ControlHints {
    /** What the rider is doing, as far as the hints care. */
    enum class Situation(val id: String) { ROLL("ride"), AIR("air"), GRIND("grind"), MANUAL("manual") }

    /** Id of the pause menu option and of its saved value. */
    const val OPTION_ID = "skate.hints"
    const val ON = "on"
    const val OFF = "off"

    @Volatile
    var enabled: Boolean = true
        private set

    fun option(): GameOption = GameOption(
        id = OPTION_ID,
        values = listOf(ON, OFF),
        default = ON,
        labelKey = { value -> "hints.$value" },
        apply = { enabled = it != OFF },
    )

    /** The situation of [rider], or null when there is nothing to hint (on foot, bailing). */
    fun situation(rider: SkateBehavior): Situation? {
        if (!rider.mounted) return null
        return when (rider.machine.current) {
            SkateState.IDLE, SkateState.PUSH, SkateState.RIDE -> Situation.ROLL
            SkateState.AIR -> Situation.AIR
            SkateState.GRIND -> Situation.GRIND
            SkateState.MANUAL -> Situation.MANUAL
            SkateState.BAIL -> null
        }
    }

    /** Game string of the hint for [situation] in [scheme] ("" when hints are off or there is no situation). */
    fun key(scheme: ControlScheme, situation: Situation?, on: Boolean = enabled): String =
        if (!on || situation == null) "" else "hint.${scheme.id}.${situation.id}"
}
