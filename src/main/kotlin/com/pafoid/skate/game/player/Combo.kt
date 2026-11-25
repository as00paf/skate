package com.pafoid.skate.game.player

import kotlinx.serialization.Serializable

/** One link of a combo chain (GPL-23): a trick landed in the air, a grind or a manual. */
sealed interface ComboMove {
    /** Board trick with its body spin, e.g. "FS 180 Kickflip" ([spin] null: no spin). */
    data class Air(val trick: TrickType, val spin: BodySpin? = null) : ComboMove

    data class Grind(val type: GrindType) : ComboMove

    data class Manual(val type: ManualType) : ComboMove

    /**
     * The display name, built from game strings: [string] resolves a key with format arguments. A spun ollie is named
     * by its spin alone ("FS 180").
     */
    fun label(string: (key: String, args: Array<out Any>) -> String): String = when (this) {
        is Air -> {
            val spinName = spin?.let { string(it.side.nameKey, arrayOf(it.degrees)) }
            when {
                spinName == null -> string(trick.nameKey, emptyArray())
                trick == TrickType.OLLIE -> spinName
                else -> "$spinName ${string(trick.nameKey, emptyArray())}"
            }
        }
        is Grind -> string(type.nameKey, emptyArray())
        is Manual -> string(type.nameKey, emptyArray())
    }
}

/** Combo tuning (GPL-23 §4); no points or score yet. */
@Serializable
data class ComboTuning(
    /** Seconds after a flat-ground landing to start a manual before the combo ends. */
    val linkWindowSec: Float = 0.3f,
)

/** How a combo ended: [LANDED] after the link window passed, [BAILED] when the rider fell. */
enum class ComboOutcome { LANDED, BAILED }

/** A finished combo, kept for the HUD. */
data class ComboResult(val moves: List<ComboMove>, val outcome: ComboOutcome)

/**
 * Chains the tricks done between two flat-ground landings (GPL-23 §4). Grinds and manuals keep the combo going. A
 * landing on flat ground opens the link window: a manual started in it continues the combo, otherwise the combo
 * ends ([ComboOutcome.LANDED]) when the window runs out. A bail loses it ([ComboOutcome.BAILED]). Pure logic: the
 * skate behavior reports what happens, the HUD reads [moves] and [lastResult].
 */
class ComboTracker(private val tuning: () -> ComboTuning = { ComboTuning() }) {
    private val chain = ArrayList<ComboMove>()

    /** The combo in progress, oldest first (empty: none). */
    val moves: List<ComboMove> get() = chain

    /** Seconds left in the link window, or null when it is not open. */
    var linkLeft: Float? = null
        private set

    /** The last combo that ended; [resultCount] grows by one each time one ends. */
    var lastResult: ComboResult? = null
        private set
    var resultCount = 0
        private set

    /** Adds [move] to the combo; inside the link window it continues the combo. */
    fun add(move: ComboMove) {
        linkLeft = null
        chain += move
    }

    /** The rider is rolling on flat ground again: the link window opens (nothing happens without a combo). */
    fun touchedFlat() {
        if (chain.isNotEmpty() && linkLeft == null) linkLeft = tuning().linkWindowSec
    }

    /** The rider fell: the combo is lost. */
    fun bail() {
        if (chain.isNotEmpty()) finish(ComboOutcome.BAILED)
    }

    /** Runs the link window; the combo ends when it runs out. */
    fun update(dt: Float) {
        val left = linkLeft ?: return
        val next = left - dt
        if (next > 0f) linkLeft = next else finish(ComboOutcome.LANDED)
    }

    /** Drops the combo in progress without a result (respawn, new run). */
    fun reset() {
        chain.clear()
        linkLeft = null
    }

    private fun finish(outcome: ComboOutcome) {
        lastResult = ComboResult(chain.toList(), outcome)
        resultCount++
        reset()
    }
}
