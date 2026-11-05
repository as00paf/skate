package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.rhi.IntRect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PresentedViewportTest {
    private val viewport = PresentedViewport()

    @Test
    fun SceneFramebuffer_UsesTheWholeTarget() {
        assertNull(viewport.of(mockk<FrameBuffer>(), useFbo = true))
    }

    @Test
    fun BackBuffer_UsesTheFramebufferSize_CachedUntilItChanges() {
        val fb = mockk<FrameBuffer>()
        every { fb.width } returns 1280
        every { fb.height } returns 720
        val first = viewport.of(fb, useFbo = false)
        assertEquals(IntRect(0, 0, 1280, 720), first)
        assertSame(first, viewport.of(fb, useFbo = false))

        every { fb.width } returns 640
        val resized = viewport.of(fb, useFbo = false)
        assertNotSame(first, resized)
        assertEquals(IntRect(0, 0, 640, 720), resized)
    }
}
