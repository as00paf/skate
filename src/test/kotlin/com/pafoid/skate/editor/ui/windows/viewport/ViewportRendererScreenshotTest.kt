package com.pafoid.skate.editor.ui.windows.viewport

import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.ScreenshotUtils
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ViewportRendererScreenshotTest {

    @AfterEach
    fun tearDown() {
        unmockkObject(ScreenshotUtils)
    }

    @Test
    fun `captureScreenshot_ValidFramebuffer_DelegatesToScreenshotUtils`() {
        val renderer = mockk<Renderer>()
        val frameBuffer = mockk<FrameBuffer>()
        every { renderer.frameBuffer } returns frameBuffer
        every { frameBuffer.width } returns 1920
        every { frameBuffer.height } returns 1080
        every { frameBuffer.getFboId() } returns 77

        mockkObject(ScreenshotUtils)
        every { ScreenshotUtils.takeScreenshot(any(), any(), any()) } just Runs

        ViewportRenderer(renderer).captureScreenshot()

        verify(exactly = 1) {
            ScreenshotUtils.takeScreenshot(1920, 1080, 77)
        }
    }

    @Test
    fun `captureScreenshot_InvalidFramebufferDimensions_DoesNotCallScreenshotUtils`() {
        val renderer = mockk<Renderer>()
        val frameBuffer = mockk<FrameBuffer>()
        every { renderer.frameBuffer } returns frameBuffer
        every { frameBuffer.width } returns 0
        every { frameBuffer.height } returns 1080

        mockkObject(ScreenshotUtils)
        every { ScreenshotUtils.takeScreenshot(any(), any(), any()) } just Runs

        ViewportRenderer(renderer).captureScreenshot()

        verify(exactly = 0) {
            ScreenshotUtils.takeScreenshot(any(), any(), any())
        }
    }
}
