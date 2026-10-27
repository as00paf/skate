package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputReader
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.input.listeners.GamepadConstants

data class UiScreenSize(val width: Int, val height: Int)

/** Everything the UI needs from the input devices for one frame. Pointer is in screen pixels. */
class UiInputFrame(
    val pointerX: Float = 0f,
    val pointerY: Float = 0f,
    val pointerDown: Boolean = false,
    val pointerPressed: Boolean = false,
    val pointerReleased: Boolean = false,
    /** Directions that fired this frame (initial press or auto-repeat). */
    val navigate: Set<UiDirection> = emptySet(),
    val confirm: Boolean = false,
    val cancel: Boolean = false,
    val next: Boolean = false,
    val previous: Boolean = false,
    val scrollY: Float = 0f,
)

fun interface UiInputSource {
    fun frame(dt: Float): UiInputFrame
}

/** Fires a held direction once, then again every [repeatInterval] after [initialDelay]. */
class UiNavRepeater(private val initialDelay: Float = 0.4f, private val repeatInterval: Float = 0.12f) {
    private var heldFor = 0f
    private var nextFire = 0f
    private var active = false

    fun update(held: Boolean, dt: Float): Boolean {
        if (!held) {
            active = false
            return false
        }
        if (!active) {
            active = true
            heldFor = 0f
            nextFire = initialDelay
            return true
        }
        heldFor += dt
        if (heldFor >= nextFire) {
            nextFire += repeatInterval
            return true
        }
        return false
    }
}

object UiActions {
    const val UP = "ui_up"
    const val DOWN = "ui_down"
    const val LEFT = "ui_left"
    const val RIGHT = "ui_right"
    const val CONFIRM = "ui_confirm"
    const val CANCEL = "ui_cancel"
    const val NEXT = "ui_next"
    const val PREVIOUS = "ui_prev"

    /** Binds keyboard and gamepad defaults for the UI actions; games may rebind them afterwards. */
    fun bindDefaults(map: ActionMap) {
        listOf(UP, DOWN, LEFT, RIGHT, CONFIRM, CANCEL, NEXT, PREVIOUS).forEach(map::clear)
        map.bind(UP, ActionBinding.Key(Keys.UP))
            .bind(UP, ActionBinding.GamepadButton(GamepadConstants.BUTTON_DPAD_UP))
            .bind(UP, ActionBinding.GamepadAxis(GamepadConstants.AXIS_LEFT_Y, -1f))
            .bind(DOWN, ActionBinding.Key(Keys.DOWN))
            .bind(DOWN, ActionBinding.GamepadButton(GamepadConstants.BUTTON_DPAD_DOWN))
            .bind(DOWN, ActionBinding.GamepadAxis(GamepadConstants.AXIS_LEFT_Y, 1f))
            .bind(LEFT, ActionBinding.Key(Keys.LEFT))
            .bind(LEFT, ActionBinding.GamepadButton(GamepadConstants.BUTTON_DPAD_LEFT))
            .bind(LEFT, ActionBinding.GamepadAxis(GamepadConstants.AXIS_LEFT_X, -1f))
            .bind(RIGHT, ActionBinding.Key(Keys.RIGHT))
            .bind(RIGHT, ActionBinding.GamepadButton(GamepadConstants.BUTTON_DPAD_RIGHT))
            .bind(RIGHT, ActionBinding.GamepadAxis(GamepadConstants.AXIS_LEFT_X, 1f))
            .bind(CONFIRM, ActionBinding.Key(Keys.ENTER))
            .bind(CONFIRM, ActionBinding.GamepadButton(GamepadConstants.BUTTON_A))
            .bind(CANCEL, ActionBinding.Key(Keys.ESCAPE))
            .bind(CANCEL, ActionBinding.GamepadButton(GamepadConstants.BUTTON_B))
            .bind(NEXT, ActionBinding.Key(Keys.TAB))
            .bind(NEXT, ActionBinding.GamepadButton(GamepadConstants.BUTTON_RB))
            .bind(PREVIOUS, ActionBinding.GamepadButton(GamepadConstants.BUTTON_LB))
    }
}

/**
 * [UiInputSource] over an [InputReader] using the `ui_*` actions of [UiActions].
 * [pointerMapper] converts raw mouse coordinates to screen pixels of the UI render target.
 */
class ReaderUiInputSource(
    private val reader: InputReader,
    private val pointerMapper: (Float, Float) -> Pair<Float, Float> = { x, y -> x to y },
) : UiInputSource {
    private val repeaters = UiDirection.values().associateWith { UiNavRepeater() }
    private var wasDown = false

    override fun frame(dt: Float): UiInputFrame {
        val down = reader.isMouseButtonDown(0)
        val (px, py) = pointerMapper(reader.mouseX(), reader.mouseY())
        val fired = HashSet<UiDirection>()
        for (d in UiDirection.values()) {
            if (repeaters.getValue(d).update(reader.isActionDown(actionOf(d)), dt)) fired.add(d)
        }
        val frame = UiInputFrame(
            pointerX = px,
            pointerY = py,
            pointerDown = down,
            pointerPressed = down && !wasDown,
            pointerReleased = !down && wasDown,
            navigate = fired,
            confirm = reader.wasActionPressed(UiActions.CONFIRM),
            cancel = reader.wasActionPressed(UiActions.CANCEL),
            next = reader.wasActionPressed(UiActions.NEXT),
            previous = reader.wasActionPressed(UiActions.PREVIOUS),
            scrollY = reader.scrollY(),
        )
        wasDown = down
        return frame
    }

    private fun actionOf(d: UiDirection) = when (d) {
        UiDirection.UP -> UiActions.UP
        UiDirection.DOWN -> UiActions.DOWN
        UiDirection.LEFT -> UiActions.LEFT
        UiDirection.RIGHT -> UiActions.RIGHT
    }
}
