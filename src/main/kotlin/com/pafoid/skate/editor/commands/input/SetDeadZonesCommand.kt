package com.pafoid.skate.editor.commands.input

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.input.DeadZoneSettings
import com.pafoid.skate.engine.input.StickDeadZone
import com.pafoid.skate.engine.input.TriggerDeadZone

/** Deep copy of dead zone settings, used as before/after snapshots. */
fun DeadZoneSettings.snapshot(): DeadZoneSettings = DeadZoneSettings(
    leftStick.copy(), rightStick.copy(), leftTrigger.copy(), rightTrigger.copy(),
)

/** Copies every value of [from] into this instance, keeping the instance (and references to it) alive. */
fun DeadZoneSettings.copyFrom(from: DeadZoneSettings) {
    fun StickDeadZone.set(o: StickDeadZone) { mode = o.mode; inner = o.inner; outer = o.outer; curve = o.curve; sensitivity = o.sensitivity }
    fun TriggerDeadZone.set(o: TriggerDeadZone) { inner = o.inner; outer = o.outer; curve = o.curve; pressThreshold = o.pressThreshold }
    leftStick.set(from.leftStick)
    rightStick.set(from.rightStick)
    leftTrigger.set(from.leftTrigger)
    rightTrigger.set(from.rightTrigger)
}

/**
 * Replaces the live [target] dead zones with [after] (validated); undo restores [before].
 * Persistence is handled by the caller (the project is saved after execution).
 */
class SetDeadZonesCommand(
    private val target: DeadZoneSettings,
    before: DeadZoneSettings,
    after: DeadZoneSettings,
    private val label: String = "Dead Zones",
) : Command {
    private val before = before.snapshot()
    private val after = after.snapshot().also { it.validate() }

    override fun execute() = target.copyFrom(after)

    override fun undo() = target.copyFrom(before)

    override fun getDisplayName(): String = "Edit $label"

    override fun getTargetName(): String = "Input Settings"

    /** True when the edit changes nothing, so callers can skip pushing it. */
    fun isNoOp(): Boolean = before == after
}
