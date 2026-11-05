package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.AudioComponentSettings
import com.pafoid.skate.editor.commands.objects.EditAudioComponentCommand
import com.pafoid.skate.editor.events.AudioAction
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.UndoRedoAction
import com.pafoid.skate.editor.project.GameplaySettings
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.settings.AudioSettings
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.AudioActionHandler
import com.pafoid.skate.engine.audio.AttenuationModel
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.audio.BusId
import com.pafoid.skate.engine.audio.FakeAudioBackend
import com.pafoid.skate.engine.audio.StreamMode
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.components.AudioComponent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-14: the audio source inspector and mixer edit through undoable commands and apply to the live mixer. */
class EditAudioComponentCommandTest {

    @Test
    fun EditAudioComponent_AppliesEveryFieldAndUndoes() {
        val component = AudioComponent("a.wav")
        val edited = AudioComponentSettings(
            "music.mp3", listOf("b.wav", "c.wav"), BusId.MUSIC, 0.5f, true, false, StreamMode.ALWAYS, 200, false,
            AttenuationModel.LINEAR, 2f, 30f, 0.5f, 0f,
        )
        val command = EditAudioComponentCommand(component, edited)
        command.execute()
        assertEquals(edited, AudioComponentSettings.of(component))
        command.undo()
        assertEquals(AudioComponentSettings.of(AudioComponent("a.wav")), AudioComponentSettings.of(component))
    }

    @Test
    fun EditAudioComponent_ClampsToRangesTheAudioSystemAccepts() {
        val component = AudioComponent()
        val bad = AudioComponentSettings.of(component).copy(
            clips = listOf("", "x.wav"), volume = 3f, priority = 999, minDistance = -1f, maxDistance = Float.NaN,
            rolloff = -2f, dopplerFactor = -1f,
        )
        EditAudioComponentCommand(component, bad).execute()
        assertEquals(listOf("x.wav"), component.clips)
        assertEquals(1f, component.volume)
        assertEquals(AudioComponentSettings.MAX_PRIORITY, component.priority)
        assertEquals(0f, component.minDistance)
        assertEquals(50f, component.maxDistance)
        assertEquals(0f, component.rolloff)
        assertEquals(0f, component.dopplerFactor)

        val inverted = AudioComponentSettings.of(component).copy(minDistance = 10f, maxDistance = 5f)
        EditAudioComponentCommand(component, inverted).execute()
        assertEquals(10f, component.maxDistance)
    }

    @Test
    fun EditAudioComponent_UnchangedSettingsAreANoOp() {
        val component = AudioComponent()
        assertTrue(EditAudioComponentCommand(component, AudioComponentSettings.of(component)).isNoOp)
        assertFalse(EditAudioComponentCommand(component, AudioComponentSettings.of(component).copy(loops = true)).isNoOp)
    }

    private class Harness {
        val events = EventSystem()
        val undo = UndoRedoManager(events, mockk(relaxed = true))
        val project = Project(name = "P", projectPath = "P/P.skateproj", defaultScene = "Main.scene", scenesPath = emptyList())
        val projects = mockk<ProjectManager>(relaxed = true).also {
            every { it.currentProject } returns project
            every { it.saveProject() } returns true
        }
        val audio = AudioEngine(mockk(relaxed = true), FakeAudioBackend().backend)

        init {
            AudioActionHandler(events, projects, undo, audio)
        }
    }

    @Test
    fun AudioActionHandler_InspectorEditIsOneUndoableStep() {
        val h = Harness()
        val component = AudioComponent()
        h.events.publish(AudioAction.EditComponent(component, AudioComponentSettings.of(component).copy(bus = BusId.MUSIC, dopplerFactor = 0f)))
        assertEquals(BusId.MUSIC, component.bus)
        assertEquals(0f, component.dopplerFactor)
        h.events.publish(UndoRedoAction.Undo)
        assertEquals(BusId.SFX, component.bus)
        assertEquals(1f, component.dopplerFactor)
        h.events.publish(UndoRedoAction.Redo)
        assertEquals(BusId.MUSIC, component.bus)
    }

    @Test
    fun AudioActionHandler_BusEditAppliesToTheLiveMixerSavesAndUndoes() {
        val h = Harness()
        h.events.publish(AudioAction.EditBuses(AudioSettings(musicVolume = 0.3f, sfxMuted = true)))
        assertEquals(0.3f, h.audio.mixer.bus(BusId.MUSIC).volume)
        assertTrue(h.audio.mixer.bus(BusId.SFX).muted)
        assertEquals(0.3f, h.project.gameplaySettings.audio.musicVolume)
        verify(exactly = 1) { h.projects.saveProject() }

        h.events.publish(UndoRedoAction.Undo)
        assertEquals(AudioSettings().musicVolume, h.audio.mixer.bus(BusId.MUSIC).volume)
        assertFalse(h.audio.mixer.bus(BusId.SFX).muted)
        verify(exactly = 2) { h.projects.saveProject() }
    }

    @Test
    fun AudioActionHandler_OpeningAProjectAppliesItsSavedMix() {
        val h = Harness()
        h.project.gameplaySettings = GameplaySettings(audio = AudioSettings(masterVolume = 0.4f, musicMuted = true))
        h.events.publish(ProjectEvent.Opened(h.project))
        assertEquals(0.4f, h.audio.mixer.bus(BusId.MASTER).volume)
        assertTrue(h.audio.mixer.bus(BusId.MUSIC).muted)
    }
}
