package com.pafoid.skate.editor.ui.windows

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
}

/** Raw and processed readout of one stick. */
data class StickReadout(val rawX: Float, val rawY: Float, val processed: Vector2f) {
    val rawMagnitude: Float get() = kotlin.math.sqrt(rawX * rawX + rawY * rawY)
    val processedMagnitude: Float get() = processed.length()
}

/** Bounded, pausable event log for the Input Testing window. */
class InputEventLog(private val capacity: Int = 200) {
    private val entries = ArrayDeque<String>()
    var paused = false

    val lines: List<String> get() = entries.toList()

    fun add(message: String) {
        if (paused) return
        entries.addLast(message)
        while (entries.size > capacity) entries.removeFirst()
    }

    fun clear() = entries.clear()
}

object InputTestingModel {
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
