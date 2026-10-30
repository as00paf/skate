package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.audio.ReverbPreset
import com.pafoid.skate.engine.ecs.components.ReverbZone

data class ReverbZoneSettings(
    val preset: ReverbPreset,
    val radius: Float,
    val priority: Int,
    val sendLevel: Float,
    val lowPassGainHF: Float,
) {
    /** Clamps to the ranges the audio system accepts. */
    fun validated() = copy(
        radius = radius.coerceAtLeast(0f), sendLevel = sendLevel.coerceIn(0f, 1f), lowPassGainHF = lowPassGainHF.coerceIn(0f, 1f),
    )

    fun applyTo(zone: ReverbZone) {
        zone.preset = preset; zone.radius = radius; zone.priority = priority
        zone.sendLevel = sendLevel; zone.lowPassGainHF = lowPassGainHF
    }

    companion object {
        fun of(z: ReverbZone) = ReverbZoneSettings(z.preset, z.radius, z.priority, z.sendLevel, z.lowPassGainHF)
    }
}

class EditReverbZoneCommand(private val zone: ReverbZone, settings: ReverbZoneSettings) : Command {
    private val before = ReverbZoneSettings.of(zone)
    private val after = settings.validated()

    val isNoOp: Boolean get() = before == after

    override fun execute() = after.applyTo(zone)
    override fun undo() = before.applyTo(zone)
    override fun getDisplayName(): String = "Edit Reverb Zone"
    override fun getTargetName(): String = zone.name
}
