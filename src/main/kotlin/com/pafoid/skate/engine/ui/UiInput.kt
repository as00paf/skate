package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionDefinition
import com.pafoid.skate.engine.input.DefaultActions
import com.pafoid.skate.engine.input.InputContext
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
    const val UP = DefaultActions.UI_UP
    const val DOWN = DefaultActions.UI_DOWN
    const val LEFT = DefaultActions.UI_LEFT
    const val RIGHT = DefaultActions.UI_RIGHT
    const val CONFIRM = DefaultActions.UI_CONFIRM
    const val CANCEL = DefaultActions.UI_CANCEL
    const val NEXT = "ui.next"
    const val PREVIOUS = "ui.prev"

    /**
     * Completes the UI context of [map]: registers the tab actions and adds left-stick navigation on top of the
     * keyboard/d-pad defaults of [DefaultActions]. Safe to call repeatedly; games may rebind afterwards.
     */
    fun bindDefaults(map: ActionMap) {
        if (map.definition(UP) == null) DefaultActions.register(map)
        val ui = InputContext.UI
        map.define(ActionDefinition(NEXT, ui, "Navigation", defaults = listOf(
            ActionBinding.Key(Keys.TAB), ActionBinding.GamepadButton(GamepadConstants.BUTTON_RB))))
        map.define(ActionDefinition(PREVIOUS, ui, "Navigation", defaults = listOf(
            ActionBinding.GamepadButton(GamepadConstants.BUTTON_LB))))
        val stick = mapOf(
            UP to ActionBinding.GamepadAxis(GamepadConstants.AXIS_LEFT_Y, -1f),
            DOWN to ActionBinding.GamepadAxis(GamepadConstants.AXIS_LEFT_Y, 1f),
            LEFT to ActionBinding.GamepadAxis(GamepadConstants.AXIS_LEFT_X, -1f),
            RIGHT to ActionBinding.GamepadAxis(GamepadConstants.AXIS_LEFT_X, 1f),
        )
        for ((id, binding) in stick) {
            val defaults = map.definition(id)?.defaults ?: continue
            if (defaults.none { it.sourceId() == binding.sourceId() }) {
                map.define(map.definition(id)!!.copy(defaults = defaults + binding))
            }
        }
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
