package com.pafoid.skate.game.input

import com.pafoid.skate.engine.gameplay.ActionPresets
import com.pafoid.skate.engine.ui.GameOption
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionBinding.GamepadAxis
import com.pafoid.skate.engine.input.ActionBinding.GamepadButton
import com.pafoid.skate.engine.input.ActionBinding.Key
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.input.listeners.GamepadConstants as Pad

/** How a scheme turns input into tricks (see [SkateTrickInput]). */
enum class TrickStyle {
    /** EA Skate: pull the trick stick back, then flick it (direction and path pick the trick). */
    FLICK_IT,
    /** Session / Skater XL: pop with the back foot (trick stick down), flick with the front foot; scoop = shove-it. */
    FEET,
    /** THPS: ollie button; flip button + direction. */
    BUTTONS,
}

/**
 * A skateboarding control scheme (GPL-21): the default bindings of the [SkateActions] plus how tricks are read.
 *
 * @param steerWithAction turning comes from [SkateActions.STEER] (triggers) instead of the stick
 * @param frontFootSteers the front foot stick also steers and leans (manual balance), as the left stick does elsewhere
 */
data class ControlScheme(
    val id: String,
    /** Game string of the pause menu entry, e.g. "Controls: EA Skate". */
    val labelKey: String,
    val style: TrickStyle,
    val bindings: Map<String, List<ActionBinding>>,
    val steerWithAction: Boolean = false,
    val frontFootSteers: Boolean = false,
    val tricks: TrickInputSettings = TrickInputSettings(),
)

/**
 * The four shipped schemes. Every one can do the core trick set ([com.pafoid.skate.game.player.TrickType]) on a
 * gamepad and on the keyboard. Push, manual and the keyboard trick stick (IJKL) are shared where they fit.
 */
object ControlSchemes {
    private val push = listOf(Key(Keys.LEFT_SHIFT), GamepadAxis(Pad.AXIS_LEFT_TRIGGER))
    private val manual = listOf(Key(Keys.LEFT_CONTROL), GamepadButton(Pad.BUTTON_LB))
    private val ollie = listOf(Key(Keys.SPACE), GamepadButton(Pad.BUTTON_A))

    /** Right stick, or IJKL on the keyboard (I up, K down, J left, L right). Stick up reads negative raw, so -1. */
    private val trickStick = mapOf(
        SkateActions.TRICK_X to listOf(Key('L'.code), Key('J'.code, -1f), GamepadAxis(Pad.AXIS_RIGHT_X)),
        SkateActions.TRICK_Y to listOf(Key('I'.code), Key('K'.code, -1f), GamepadAxis(Pad.AXIS_RIGHT_Y, -1f)),
    )

    /** Left stick, or WASD. */
    private val frontFoot = mapOf(
        SkateActions.FRONT_X to listOf(Key(Keys.D), Key(Keys.A, -1f), GamepadAxis(Pad.AXIS_LEFT_X)),
        SkateActions.FRONT_Y to listOf(Key(Keys.W), Key(Keys.S, -1f), GamepadAxis(Pad.AXIS_LEFT_Y, -1f)),
    )

    /** Default: left stick steers and pushes, right stick flicks every trick; Space/A still ollies. */
    val EA_SKATE = ControlScheme(
        id = "ea_skate", labelKey = "controls.ea_skate", style = TrickStyle.FLICK_IT,
        bindings = mapOf(SkateActions.PUSH to push, SkateActions.MANUAL to manual, SkateActions.OLLIE to ollie) + trickStick,
        tricks = TrickInputSettings(flick = FlickSettings(windowSec = 0.45f)),
    )

    /** Each stick is a foot: left = front foot (also steers and leans), right = back foot. */
    val SESSION = ControlScheme(
        id = "session", labelKey = "controls.session", style = TrickStyle.FEET,
        bindings = mapOf(SkateActions.PUSH to push, SkateActions.MANUAL to manual) + frontFoot + trickStick,
        frontFootSteers = true,
        tricks = TrickInputSettings(flick = FlickSettings(windowSec = 0.45f)),
    )

    /** Sticks are feet; the triggers turn (U/O on the keyboard); A pushes. */
    val SKATER_XL = ControlScheme(
        id = "skater_xl", labelKey = "controls.skater_xl", style = TrickStyle.FEET,
        bindings = mapOf(
            SkateActions.PUSH to listOf(Key(Keys.LEFT_SHIFT), GamepadButton(Pad.BUTTON_A)),
            SkateActions.MANUAL to manual,
            SkateActions.STEER to listOf(
                Key('U'.code, -1f), Key('O'.code),
                GamepadAxis(Pad.AXIS_LEFT_TRIGGER, -1f), GamepadAxis(Pad.AXIS_RIGHT_TRIGGER),
            ),
        ) + frontFoot + trickStick,
        steerWithAction = true,
        tricks = TrickInputSettings(flick = FlickSettings(windowSec = 0.45f)),
    )

    /** Face buttons: A ollies, X (J) flips; the left stick direction picks the flip. */
    val THPS = ControlScheme(
        id = "thps", labelKey = "controls.thps", style = TrickStyle.BUTTONS,
        bindings = mapOf(
            SkateActions.PUSH to push, SkateActions.MANUAL to manual, SkateActions.OLLIE to ollie,
            SkateActions.FLIP to listOf(Key('J'.code), GamepadButton(Pad.BUTTON_X)),
        ),
    )

    val all: List<ControlScheme> = listOf(EA_SKATE, SESSION, SKATER_XL, THPS)
    val DEFAULT = EA_SKATE

    fun byId(id: String?): ControlScheme? = all.firstOrNull { it.id == id }
}

/** The selected scheme, a game setting chosen and saved through the pause menu ([SkateControls.option]). */
object SkateControls {
    @Volatile
    var scheme: ControlScheme = ControlSchemes.DEFAULT
        private set

    /** Id of the pause menu option and of its saved value in the game settings. */
    const val OPTION_ID = "skate.controls"

    /** The pause menu entry "Controls: <scheme>"; each click picks the next scheme and saves it. */
    fun option(): GameOption = GameOption(
        id = OPTION_ID,
        values = ControlSchemes.all.map { it.id },
        default = ControlSchemes.DEFAULT.id,
        labelKey = { id -> (ControlSchemes.byId(id) ?: ControlSchemes.DEFAULT).labelKey },
        apply = ::select,
    )

    /** Switches to [id] (unknown ids give the default); the skate actions are redefined on the next frame. */
    fun select(id: String?) {
        val next = ControlSchemes.byId(id) ?: ControlSchemes.DEFAULT
        if (next == scheme) return
        scheme = next
        ActionPresets.invalidate()
    }
}
