package com.pafoid.skate.editor.ui.windows.input

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.events.InputSettingsAction
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.InputSettingsActionHandler
import com.pafoid.skate.editor.ui.windows.KeyBindingsWindow
import com.pafoid.skate.editor.ui.windows.PaletteCommands
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.input.DeadZoneMode
import com.pafoid.skate.engine.input.DeadZoneSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-52I: Project Settings › Input commits one undoable, saved change per dead-zone edit. */
class DeadZoneSaveTest {

    private val published = mutableListOf<InputSettingsAction.SetDeadZones>()
    private val session = DeadZoneEditSession { published += it }

    @Test
    fun DeadZoneEdit_OneGestureIsOneChangeWithBeforeAndAfter() {
        val zones = DeadZoneSettings()
        val start = zones.leftStick.inner
        session.begin(zones)
        zones.leftStick.inner = 0.3f
        session.begin(zones) // later frames of the same drag keep the first snapshot
        zones.leftStick.inner = 0.35f
        assertTrue(session.editing)
        session.end(zones, edited = true, label = "Left stick")

        val e = published.single()
        assertEquals(start, e.before.leftStick.inner)
        assertEquals(0.35f, e.after.leftStick.inner)
        assertEquals("Left stick", e.label)
        assertFalse(session.editing)
        zones.leftStick.inner = 0.9f
        assertEquals(0.35f, e.after.leftStick.inner, "the event holds a snapshot, not the live settings")
    }

    @Test
    fun DeadZoneEdit_ClickWithoutChangeOrStrayEndPublishesNothing() {
        val zones = DeadZoneSettings()
        session.begin(zones)
        session.end(zones, edited = false, label = "Left stick")
        session.end(zones, edited = true, label = "Left stick")
        assertTrue(published.isEmpty())
    }

    @Test
    fun DeadZoneEdit_ModeChangeCommitsImmediately() {
        val zones = DeadZoneSettings()
        val before = DeadZoneSettings()
        zones.rightStick.mode = if (zones.rightStick.mode == DeadZoneMode.AXIAL) DeadZoneMode.RADIAL else DeadZoneMode.AXIAL
        session.commit(before, zones, "Right stick")
        assertEquals(zones.rightStick.mode, published.single().after.rightStick.mode)
        assertEquals(before.rightStick.mode, published.single().before.rightStick.mode)
    }

    @Test
    fun DeadZoneSave_HandlerAppliesTheEditUndoablyAndSavesTheProject() {
        val events = EventSystem()
        val live = DeadZoneSettings()
        val projects = mockk<ProjectManager>(relaxed = true)
        every { projects.currentProject?.gameplaySettings?.inputMappings?.deadZones } returns live
        val undo = mockk<UndoRedoManager>(relaxed = true)
        val command = slot<Command>()
        every { undo.executeCommand(capture(command)) } answers { command.captured.execute() }
        InputSettingsActionHandler(events, projects, undo)

        val session = DeadZoneEditSession { events.publish(it) }
        val edited = DeadZoneSettings()
        session.begin(edited)
        edited.leftTrigger.pressThreshold = 0.8f
        session.end(edited, edited = true, label = "Left trigger")

        assertEquals(0.8f, live.leftTrigger.pressThreshold, "applied to the project's live settings")
        verify(exactly = 1) { projects.saveProject() }
        command.captured.undo()
        assertEquals(DeadZoneSettings().leftTrigger.pressThreshold, live.leftTrigger.pressThreshold)
    }

    @Test
    fun TestInput_OpensKeyBindingsOnItsTestTab() {
        assertEquals(KeyBindingsWindow.TEST_TAB, PaletteCommands.TEST_INPUT.window)
        assertTrue(KeyBindingsWindow.TEST_TAB.startsWith("window.keybindings"))
    }
}
