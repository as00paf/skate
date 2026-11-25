package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.environment.GerstnerWave
import com.pafoid.skate.engine.environment.WaterWaves
import org.joml.Vector2f

/**
 * The parts of a [WaterComponent] a preset or the per-wave editor changes (EDT-90): the waves (deep copies), the flow
 * and the material. The other fields are plain values the inspector's edit tracker already makes undoable.
 */
data class WaterWaveSettings(val waves: List<GerstnerWave>, val flow: Vector2f, val material: String) {

    /** At most [WaterWaves.MAX_WAVES] waves, each with a positive wavelength, non-negative amplitude and steepness in 0..1. */
    fun validated() = copy(
        waves = waves.take(WaterWaves.MAX_WAVES).map {
            it.copy(
                wavelength = it.wavelength.coerceAtLeast(MIN_WAVELENGTH),
                amplitude = it.amplitude.coerceAtLeast(0f),
                steepness = it.steepness.coerceIn(0f, 1f),
            )
        },
    )

    fun applyTo(water: WaterComponent) {
        water.waves.clear()
        water.waves.addAll(waves.map { it.copy() })
        water.flowVelocity.set(flow)
        water.material = material
    }

    companion object {
        const val MIN_WAVELENGTH = 0.05f

        fun of(water: WaterComponent) =
            WaterWaveSettings(water.waves.map { it.copy() }, Vector2f(water.flowVelocity), water.material)
    }
}

/**
 * Applies a water preset or a finished per-wave edit. The inspector previews edits live, so [before] is the state
 * from before the gesture and [after] the state it ended on.
 */
class EditWaterWavesCommand(
    private val water: WaterComponent,
    before: WaterWaveSettings,
    after: WaterWaveSettings,
) : Command {
    private val before = before.copy(waves = before.waves.map { it.copy() }, flow = Vector2f(before.flow))
    private val after = after.validated()

    val isNoOp: Boolean get() = before == after

    override fun execute() = after.applyTo(water)
    override fun undo() = before.applyTo(water)
    override fun getDisplayName(): String = "Edit Water Waves"
    override fun getTargetName(): String = water.name
}
