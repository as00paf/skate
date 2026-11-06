package com.pafoid.skate.engine.ui

/**
 * A game-defined setting shown in the pause menu as a button that cycles through [values] (GPL-21; e.g. a control
 * scheme). The choice is saved in [GameSettings.options] under [id], so it survives restarts in the editor (project
 * settings file) and in the exported game.
 *
 * @param labelKey runtime UI string key of the button for a value, e.g. `"controls.$value"`
 * @param apply makes a value take effect; called on every scene load with the saved value and after each click
 */
class GameOption(
    val id: String,
    val values: List<String>,
    val default: String,
    val labelKey: (String) -> String,
    val apply: (String) -> Unit,
) {
    init {
        require(default in values) { "default '$default' of option '$id' is not one of its values" }
    }

    /** The value after [value] (wrapping); unknown values go to the first. */
    fun next(value: String): String = values[(values.indexOf(value) + 1).mod(values.size)]

    /** [saved] when it is still a valid value, else [default]. */
    fun valid(saved: String?): String = saved?.takeIf { it in values } ?: default
}

/** The options one engine's pause menu shows, in registration order. Hosts install the game's options at startup. */
class GameOptions {
    private val options = LinkedHashMap<String, GameOption>()

    /** Incremented on every change, so the pause menu rebuilds its rows. */
    var revision: Int = 0
        private set

    /** Adds [option], or replaces the option with the same id. */
    fun register(option: GameOption) {
        options[option.id] = option
        revision++
    }

    val all: List<GameOption> get() = options.values.toList()
}
