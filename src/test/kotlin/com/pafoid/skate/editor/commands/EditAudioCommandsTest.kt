package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.EditAudioBusesCommand
import com.pafoid.skate.editor.commands.objects.EditReverbZoneCommand
import com.pafoid.skate.editor.commands.objects.ReverbZoneSettings
import com.pafoid.skate.editor.project.GameplaySettings
import com.pafoid.skate.editor.settings.AudioSettings
import com.pafoid.skate.engine.audio.ReverbPreset
import com.pafoid.skate.engine.ecs.components.ReverbZone
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditAudioCommandsTest {
    @Test
    fun EditReverbZone_AppliesClampedValuesAndUndoes() {
        val zone = ReverbZone()
        val command = EditReverbZoneCommand(zone, ReverbZoneSettings(ReverbPreset.CAVE, 25f, 3, 2f, -1f))
        command.execute()
        assertEquals(ReverbPreset.CAVE, zone.preset)
        assertEquals(25f, zone.radius)
        assertEquals(1f, zone.sendLevel)
        assertEquals(0f, zone.lowPassGainHF)
        command.undo()
        assertEquals(ReverbZone(), zone)
    }

    @Test
    fun EditAudioBuses_WritesToLiveMixerAndUndoes() {
        var stored = AudioSettings()
        val written = ArrayList<AudioSettings>()
        val command = EditAudioBusesCommand({ stored }, { stored = it; written.add(it) }, AudioSettings(sfxLowPass = 0.4f, sfxReverbSend = 0.5f))
        command.execute()
        assertEquals(0.4f, stored.sfxLowPass)
        command.undo()
        assertEquals(1f, stored.sfxLowPass)
        assertEquals(2, written.size)
    }

    @Test
    fun EditAudioBuses_SettingsPersistWithProjectGameplaySettings() {
        val json = Json { encodeDefaults = true }
        val text = json.encodeToString(GameplaySettings.serializer(), GameplaySettings(audio = AudioSettings(musicLowPass = 0.25f)))
        assertEquals(0.25f, json.decodeFromString(GameplaySettings.serializer(), text).audio.musicLowPass)
        assertTrue(EditAudioBusesCommand({ AudioSettings() }, {}, AudioSettings()).isNoOp)
    }
}
