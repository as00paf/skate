package com.pafoid.skate.engine.input

import com.pafoid.skate.engine.input.listeners.MouseListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ViewportPointerMappingTest {

    // Editor window moved to desktop (2000, 300) on a second monitor; viewport image at (2100, 350), 800x450 logical.
    private val offset = ViewRect(2100f, 350f, 800f, 450f)

    @Test
    fun contains_usesTheRectWhereverItIsOnTheDesktop() {
        assertTrue(ViewportPointerMapping.contains(2100f, 350f, offset))
        assertTrue(ViewportPointerMapping.contains(2899f, 799f, offset))
        assertFalse(ViewportPointerMapping.contains(2900f, 500f, offset))
        assertFalse(ViewportPointerMapping.contains(500f, 500f, offset)) // window-space coordinates are outside
        assertFalse(ViewportPointerMapping.contains(0f, 0f, ViewRect()))
    }

    @Test
    fun normalized_isRelativeToTheRectAndClamped() {
        assertEquals(0.5f to 0.5f, ViewportPointerMapping.normalized(2500f, 575f, offset))
        assertEquals(0f to 1f, ViewportPointerMapping.normalized(2000f, 900f, offset))
        assertEquals(0f to 0f, ViewportPointerMapping.normalized(10f, 10f, ViewRect()))
    }

    @Test
    fun local_isOffsetFromTopLeftClamped() {
        assertEquals(400f to 0f, ViewportPointerMapping.local(2500f, 100f, offset))
    }

    @Test
    fun toTarget_absorbsOffsetAndDpi() {
        // 2x monitor: framebuffer 1600x900 behind an 800x450 logical image.
        assertEquals(800f to 450f, ViewportPointerMapping.toTarget(2500f, 575f, offset, 1600, 900))
        assertEquals(0f to 0f, ViewportPointerMapping.toTarget(2100f, 350f, offset, 1600, 900))
    }

    @Test
    fun toTarget_gameWindowStretchedBlitIdentityAndEmptyRect() {
        val window = ViewRect(0f, 0f, 2560f, 1440f)
        assertEquals(960f to 540f, ViewportPointerMapping.toTarget(1280f, 720f, window, 1920, 1080))
        assertEquals(10f to 20f, ViewportPointerMapping.toTarget(10f, 20f, ViewRect(0f, 0f, 1920f, 1080f), 1920, 1080))
        assertEquals(5f to 5f, ViewportPointerMapping.toTarget(15f, 15f, ViewRect(10f, 10f, 0f, 0f), 1920, 1080))
    }

    @Test
    fun toNdc_centreAndCornersAreIndependentOfPixelSize() {
        assertEquals(0f to 0f, ViewportPointerMapping.toNdc(2500f, 575f, offset))
        assertEquals(-1f to 1f, ViewportPointerMapping.toNdc(2100f, 350f, offset))
        assertEquals(1f to -1f, ViewportPointerMapping.toNdc(2900f, 800f, offset))
    }

    @Test
    fun deltaToTarget_scalesByTargetOverView() {
        assertEquals(20f to -10f, ViewportPointerMapping.deltaToTarget(10f, -5f, offset, 1600, 900))
    }

    @Test
    fun mouseListener_allViewQueriesUseThePointerSource() {
        val mouse = MouseListener()
        mouse.pointerSource = PointerSource { it[0] = 2500f; it[1] = 575f }
        mouse.setViewRect(offset)
        mouse.setTargetSize(1600, 900)
        // GLFW window cursor elsewhere: must not matter.
        mouse.mousePosCallback(0L, 10.0, 10.0)

        assertTrue(mouse.isInsideView())
        assertEquals(0.5f to 0.5f, mouse.viewNormalized())
        assertEquals(800f to 450f, mouse.viewTarget())
        assertEquals(0f to 0f, mouse.viewNdc())
    }

    @Test
    fun mouseListener_defaultSourceIsTheGlfwCursorForTheGame() {
        val mouse = MouseListener()
        mouse.setViewRect(ViewRect(0f, 0f, 1280f, 720f))
        mouse.setTargetSize(1920, 1080)
        mouse.mousePosCallback(0L, 640.0, 360.0)

        assertTrue(mouse.isInsideView())
        assertEquals(960f to 540f, mouse.viewTarget())
    }

    @Test
    fun mouseListener_ignoresEmptyRectAndTargetSize() {
        val mouse = MouseListener()
        mouse.setViewRect(offset)
        mouse.setViewRect(ViewRect(0f, 0f, 0f, 0f))
        mouse.setTargetSize(0, 0)
        assertEquals(offset, mouse.viewRect)
        assertEquals(1920, mouse.targetWidth)
    }
}
