package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.ContextBlocking
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.InputContextStack
import com.pafoid.skate.engine.input.StickDeadZone
import com.pafoid.skate.engine.input.TriggerDeadZone
import org.joml.Vector2f

/** Standard GLFW gamepad layout (Xbox style) used to label axes and buttons. */
object GamepadLayout {
    val buttonNames = listOf(
        "A", "B", "X", "Y", "LB", "RB", "Back", "Start", "LS", "RS", "Guide", "DPad Up", "DPad Right", "DPad Down", "DPad Left",
    )
    val axisNames = listOf("Left X", "Left Y", "Right X", "Right Y", "Left Trigger", "Right Trigger")

    fun buttonName(index: Int): String = buttonNames.getOrNull(index) ?: "Button $index"
    fun axisName(index: Int): String = axisNames.getOrNull(index) ?: "Axis $index"

    /** Translated button name (`lbl.gamepad.button.<i>`); face/shoulder letters stay as printed on the pad. */
    fun buttonName(index: Int, strings: StringManager): String =
        if (index in buttonNames.indices) strings.getString("lbl.gamepad.button.$index") else strings.getString("lbl.gamepad.button.n", index)

    /** Translated axis name (`lbl.gamepad.axis.<i>`). */
    fun axisName(index: Int, strings: StringManager): String =
        if (index in axisNames.indices) strings.getString("lbl.gamepad.axis.$index") else strings.getString("lbl.gamepad.axis.n", index)
}

/** Raw and processed readout of one stick. */
data class StickReadout(val rawX: Float, val rawY: Float, val processed: Vector2f) {
    val rawMagnitude: Float get() = kotlin.math.sqrt(rawX * rawX + rawY * rawY)
    val processedMagnitude: Float get() = processed.length()
}

/** Bounded, pausable event log (Key Bindings › Test, EDT-51 §3.1). */
class InputEventLog<T>(private val capacity: Int = 200) {
    private val entries = ArrayDeque<T>()
    var paused = false

    val lines: List<T> get() = entries.toList()

    fun add(entry: T) {
        if (paused) return
        entries.addLast(entry)
        while (entries.size > capacity) entries.removeFirst()
    }

    fun clear() = entries.clear()
}

/** A bound action an input event fired; [live] is false when its context is inactive or the input is shadowed. */
data class InputLogAction(val actionId: String, val live: Boolean)

/** One event-log row: the input event (already formatted) and the actions it fired, for the Action column. */
data class InputLogEntry(val event: String, val actions: List<InputLogAction>) {
    /** Plain-text line for Copy log: `event -> action, action`. */
    fun line(actionName: (InputLogAction) -> String): String =
        if (actions.isEmpty()) event else "$event -> " + actions.joinToString(", ", transform = actionName)
}

object InputTestingModel {
    /**
     * The actions of [map] bound to the physical input of [source] (EDT-51 §3.1 Action column). With [stack], an
     * action is [InputLogAction.live] only when its context is readable and no higher context consumes that input,
     * as `ActionInputReader` decides; without a stack every match is live.
     */
    fun actionsFor(map: ActionMap?, stack: InputContextStack?, source: ActionBinding): List<InputLogAction> {
        if (map == null) return emptyList()
        val id = source.physicalSourceId()
        val matches = map.definitions().filter { d -> map.bindings(d.id).any { it.physicalSourceId() == id } }
        if (matches.isEmpty()) return emptyList()
        val hidden = stack?.resolve { ctx ->
            map.definitions(ctx).flatMapTo(HashSet()) { d -> map.bindings(d.id).map { it.physicalSourceId() } }
        }
        return matches.map { d -> InputLogAction(d.id, hidden == null || hidden[d.context]?.let { id !in it } == true) }
    }

    /** Raw Y is passed as reported by GLFW (up is negative); processed output is flipped so up is positive. */
    fun readStick(axes: FloatArray?, xIndex: Int, yIndex: Int, zone: StickDeadZone): StickReadout {
        val x = axes?.getOrNull(xIndex) ?: 0f
        val y = axes?.getOrNull(yIndex) ?: 0f
        return StickReadout(x, y, zone.apply(x, -y, Vector2f()))
    }

    fun readTrigger(axes: FloatArray?, index: Int, zone: TriggerDeadZone): Pair<Float, Float> {
        val raw = axes?.getOrNull(index) ?: 0f
        return raw to zone.apply(raw)
    }

    /** Names of buttons whose state differs between [previous] and [current], as `(name, pressed)`. */
    fun buttonChanges(previous: BooleanArray?, current: BooleanArray?): List<Pair<String, Boolean>> {
        if (current == null) return emptyList()
        return current.indices.mapNotNull { i ->
            val was = previous?.getOrNull(i) ?: false
            if (current[i] != was) GamepadLayout.buttonName(i) to current[i] else null
        }
    }

    fun deviceInfo(
        jid: Int, name: String?, axes: FloatArray?, buttons: BooleanArray?,
    ): String = buildString {
        appendLine("Gamepad ${jid + 1}: ${name ?: "unknown"}")
        appendLine("Axes: ${axes?.size ?: 0}, Buttons: ${buttons?.size ?: 0}")
        axes?.forEachIndexed { i, v -> appendLine("  ${GamepadLayout.axisName(i)} = %.3f".format(v)) }
        buttons?.forEachIndexed { i, v -> if (v) appendLine("  ${GamepadLayout.buttonName(i)} pressed") }
    }.trimEnd()
}

/** One line of the Key Bindings › Test context list (INP-10). */
data class InputContextRow(
    val context: InputContext,
    val priority: Int,
    val blocking: ContextBlocking,
    val owner: String,
    /** Below a [ContextBlocking.ALL] entry, so it reads nothing right now. */
    val silenced: Boolean,
)

object InputContextRows {
    /** [stack]'s entries, top first. */
    fun of(stack: InputContextStack): List<InputContextRow> {
        var silenced = false
        return stack.entries().map { e ->
            InputContextRow(e.context, e.priority, e.blocking, e.owner, silenced).also {
                if (e.blocking == ContextBlocking.ALL) silenced = true
            }
        }
    }
}
