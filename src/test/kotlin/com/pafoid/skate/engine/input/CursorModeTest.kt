package com.pafoid.skate.engine.input

import com.pafoid.skate.engine.input.listeners.MouseListener
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CursorModeTest {
    @Test
    fun CursorMode_RoundTripsGlfwValuesAndUnknownFallsBackToNormal() {
        CursorMode.entries.forEach { assertEquals(it, CursorMode.fromGlfw(it.glfwValue)) }
        assertEquals(CursorMode.NORMAL, CursorMode.fromGlfw(-1))
        assertEquals(3, CursorMode.entries.map { it.glfwValue }.toSet().size)
    }

    @Test
    fun CursorMode_FirstMoveAfterModeChangeProducesNoDelta() {
        val m = MouseListener()
        m.mousePosCallback(0, 100.0, 100.0)
        m.endFrame()
        m.discardNextDelta()
        m.mousePosCallback(0, 900.0, 700.0)
        assertEquals(0f, m.dx)
        assertEquals(0f, m.dy)
        m.mousePosCallback(0, 905.0, 700.0)
        assertEquals(5f, m.dx)
    }
}
