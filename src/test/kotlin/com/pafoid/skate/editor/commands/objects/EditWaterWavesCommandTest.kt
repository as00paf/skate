package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.ecs.components.WaterMaterials
import com.pafoid.skate.engine.ecs.components.WaterPreset
import com.pafoid.skate.engine.environment.GerstnerWave
import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-90: water presets and wave edits are one undo step each. */
class EditWaterWavesCommandTest {

    @Test
    fun presetCommand_AppliesWavesFlowAndMaterial_AndUndoRestoresThem() {
        val water = WaterComponent()
        val before = WaterWaveSettings.of(water)
        val river = WaterWaveSettings.of(WaterComponent().apply { applyPreset(WaterPreset.RIVER) })
        val command = EditWaterWavesCommand(water, before, river)

        command.execute()
        assertEquals(river, WaterWaveSettings.of(water))
        assertEquals(Vector2f(1f, 0f), water.flowVelocity)

        command.undo()
        assertEquals(before, WaterWaveSettings.of(water))
        assertEquals(WaterMaterials.DEFAULT, water.material)
    }

    @Test
    fun liveEditedWaves_UndoToTheStateBeforeTheGesture() {
        val water = WaterComponent()
        val before = WaterWaveSettings.of(water)
        // The inspector writes into the wave while dragging
        water.waves[0].amplitude = 0.4f
        val command = EditWaterWavesCommand(water, before, WaterWaveSettings.of(water))

        command.execute()
        assertEquals(0.4f, water.waves[0].amplitude)
        command.undo()
        assertEquals(before.waves[0].amplitude, water.waves[0].amplitude)
        command.execute()
        assertEquals(0.4f, water.waves[0].amplitude)
    }

    @Test
    fun snapshots_AreDeepCopies() {
        val water = WaterComponent()
        val snapshot = WaterWaveSettings.of(water)
        water.waves[0].wavelength = 99f
        water.flowVelocity.set(5f, 5f)
        assertFalse(snapshot.waves[0].wavelength == 99f)
        assertEquals(Vector2f(), snapshot.flow)
    }

    @Test
    fun edits_AreClampedAndCappedAtFourWaves() {
        val water = WaterComponent()
        val wild = WaterWaveSettings(
            List(6) { GerstnerWave(wavelength = -1f, amplitude = -2f, steepness = 3f) }, Vector2f(), water.material,
        )
        EditWaterWavesCommand(water, WaterWaveSettings.of(water), wild).execute()

        assertEquals(4, water.waves.size)
        water.waves.forEach {
            assertEquals(WaterWaveSettings.MIN_WAVELENGTH, it.wavelength)
            assertEquals(0f, it.amplitude)
            assertEquals(1f, it.steepness)
        }
    }

    @Test
    fun sameBeforeAndAfter_IsANoOp() {
        val water = WaterComponent()
        assertTrue(EditWaterWavesCommand(water, WaterWaveSettings.of(water), WaterWaveSettings.of(water)).isNoOp)
    }
}
