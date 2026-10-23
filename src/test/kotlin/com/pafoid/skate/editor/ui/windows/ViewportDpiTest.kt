package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ViewportDpiTest {
    @Test
    fun ViewportDpi_StandardDpi_KeepsLogicalSize() {
        assertEquals(800, ViewportRenderer.framebufferExtent(800f, 1f))
    }

    @Test
    fun ViewportDpi_HiDpi_ScalesFramebuffer() {
        assertEquals(1600, ViewportRenderer.framebufferExtent(800f, 2f))
        assertEquals(1200, ViewportRenderer.framebufferExtent(800f, 1.5f))
    }

    @Test
    fun ViewportDpi_InvalidScale_FallsBackToOne() {
        assertEquals(1f, ViewportRenderer.sanitizeDpiScale(0f))
        assertEquals(1f, ViewportRenderer.sanitizeDpiScale(Float.NaN))
        assertEquals(640, ViewportRenderer.framebufferExtent(640f, -2f))
    }

    @Test
    fun ViewportDpi_ZeroSize_StaysZero() {
        assertEquals(0, ViewportRenderer.framebufferExtent(0f, 2f))
    }
}
