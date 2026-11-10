package com.pafoid.skate.editor.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ViewportCameraGateTest {

    @Test
    fun `fly keys act only while the viewport is focused`() {
        assertTrue(ViewportCameraGate.flyAllowed(viewportFocused = true, textInputActive = false, ctrlDown = false))
        assertFalse(ViewportCameraGate.flyAllowed(viewportFocused = false, textInputActive = false, ctrlDown = false))
    }

    @Test
    fun `fly keys are ignored while a text field takes input or Ctrl is held`() {
        assertFalse(ViewportCameraGate.flyAllowed(viewportFocused = true, textInputActive = true, ctrlDown = false))
        assertFalse(ViewportCameraGate.flyAllowed(viewportFocused = true, textInputActive = false, ctrlDown = true))
    }

    @Test
    fun `look and orbit need focus and the pointer over the image`() {
        assertTrue(ViewportCameraGate.lookAllowed(viewportFocused = true, pointerInsideImage = true))
        assertFalse(ViewportCameraGate.lookAllowed(viewportFocused = false, pointerInsideImage = true))
        assertFalse(ViewportCameraGate.lookAllowed(viewportFocused = true, pointerInsideImage = false))
    }

    @Test
    fun `wheel zoom needs hover, not focus`() {
        assertTrue(ViewportCameraGate.zoomAllowed(viewportHovered = true, pointerInsideImage = true))
        assertFalse(ViewportCameraGate.zoomAllowed(viewportHovered = false, pointerInsideImage = true))
        assertFalse(ViewportCameraGate.zoomAllowed(viewportHovered = true, pointerInsideImage = false))
    }

    @Test
    fun `focus report is consumed so a viewport that stops drawing reads as unfocused`() {
        val report = ViewportFocusReport()
        assertEquals(false to false, report.consume())
        report.report(focused = true, hovered = true)
        assertEquals(true to true, report.consume())
        assertEquals(false to false, report.consume())
    }
}
