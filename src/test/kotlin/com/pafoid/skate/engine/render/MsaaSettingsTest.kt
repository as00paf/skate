package com.pafoid.skate.engine.render

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MsaaSettingsTest {

    private val initial = MsaaSettings.samples

    @AfterEach
    fun restore() {
        MsaaSettings.samples = initial
    }

    @Test
    fun `default samples is 4`() {
        MsaaSettings.samples = 4
        assertEquals(4, MsaaSettings.samples)
    }

    @Test
    fun `cycle steps through 1 2 4 8`() {
        MsaaSettings.samples = 1
        assertEquals(2, MsaaSettings.cycle())
        assertEquals(4, MsaaSettings.cycle())
        assertEquals(8, MsaaSettings.cycle())
        assertEquals(1, MsaaSettings.cycle())
    }

    @Test
    fun `cycle from 2`() {
        MsaaSettings.samples = 2
        assertEquals(4, MsaaSettings.cycle())
    }

    @Test
    fun `cycle from 8 wraps to 1`() {
        MsaaSettings.samples = 8
        assertEquals(1, MsaaSettings.cycle())
    }
}
