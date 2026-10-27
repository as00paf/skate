package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.engine.input.StickDeadZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputTestingModelTest {
    @Test
    fun InputTesting_EventLog_IsBoundedPausableAndClearable() {
        val log = InputEventLog(capacity = 3)
        (1..5).forEach { log.add("e$it") }
        assertEquals(listOf("e3", "e4", "e5"), log.lines)
        log.paused = true
        log.add("ignored")
        assertEquals(3, log.lines.size)
        log.clear()
        assertTrue(log.lines.isEmpty())
    }

    @Test
    fun InputTesting_ButtonChanges_ReportsPressAndRelease() {
        val changes = InputTestingModel.buttonChanges(booleanArrayOf(true, false), booleanArrayOf(false, true))
        assertEquals(listOf("A" to false, "B" to true), changes)
        assertTrue(InputTestingModel.buttonChanges(null, null).isEmpty())
    }

    @Test
    fun InputTesting_ReadStick_AppliesZoneAndFlipsY() {
        val zone = StickDeadZone(inner = 0.2f, outer = 0f)
        val inside = InputTestingModel.readStick(floatArrayOf(0.1f, 0.1f), 0, 1, zone)
        assertEquals(0f, inside.processedMagnitude)
        val up = InputTestingModel.readStick(floatArrayOf(0f, -1f), 0, 1, zone)
        assertEquals(1f, up.processed.y, 1e-4f)
    }

    @Test
    fun InputTesting_DeviceInfo_ListsAxesAndPressedButtons() {
        val info = InputTestingModel.deviceInfo(0, "Pad", floatArrayOf(0.5f), booleanArrayOf(true))
        assertTrue(info.contains("Gamepad 1: Pad"))
        assertTrue(info.contains("Left X = 0.500"))
        assertTrue(info.contains("A pressed"))
    }
}
