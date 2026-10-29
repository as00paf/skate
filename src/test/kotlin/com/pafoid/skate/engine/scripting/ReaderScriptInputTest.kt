package com.pafoid.skate.engine.scripting

import com.pafoid.skate.engine.input.InputReader
import com.pafoid.skate.engine.scripting.runtime.ReaderScriptInput
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderScriptInputTest {
    @Test
    fun ScriptInput_DelegatesToReaderAndDetectsRelease() {
        val reader = mockk<InputReader>()
        var down = true
        every { reader.isKeyDown(65) } answers { down }
        every { reader.wasKeyPressed(65) } returns true
        every { reader.actionValue("jump") } returns 0.75f
        val input = ReaderScriptInput(reader)

        assertTrue(input.isKeyDown(65))
        assertTrue(input.isKeyPressed(65))
        assertEquals(0.75f, input.action("jump"))
        assertFalse(input.isKeyReleased(65))
        down = false
        assertTrue(input.isKeyReleased(65))
        assertFalse(input.isKeyReleased(65))
    }
}
