package com.pafoid.skate.editor.commands.input

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.KeyedCommand
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
    /** Translated name of the edited zone (stick or trigger), or null for the whole dead-zone set. */
    private val label: String? = null,
) : Command, KeyedCommand {
    private val before = before.snapshot()
    private val after = after.snapshot().also { it.validate() }

    override fun execute() = target.copyFrom(after)

    override fun undo() = target.copyFrom(before)

    override val displayNameKey: String get() = if (label == null) "command.dead_zones.edit_all" else "command.dead_zones.edit"
    override val displayNameArgs: List<Any> get() = listOfNotNull(label)

    override fun getDisplayName(): String = displayNameKey

    /** String key, translated by [com.pafoid.skate.editor.commands.CommandNames.targetName]. */
    override fun getTargetName(): String = "command.target.input_settings"

    /** True when the edit changes nothing, so callers can skip pushing it. */
    fun isNoOp(): Boolean = before == after
}
