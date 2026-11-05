package com.pafoid.skate.editor.ui.windows.viewport

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
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

    private val logger = mockk<LoggerService>(relaxed = true)
    private val device = mockk<RhiDevice>()
    private val target = mockk<RhiRenderTarget>()

    @AfterEach
    fun tearDown() {
        unmockkObject(ScreenshotUtils)
    }

    @Test
    fun captureScreenshot_ValidFramebuffer_DelegatesToScreenshotUtils() {
        val engine = mockk<Engine>()
        every { engine.logger } returns logger
        val renderer = mockk<Renderer>()
        val frameBuffer = mockk<FrameBuffer>()
        every { renderer.frameBuffer } returns frameBuffer
        every { engine.renderer } returns renderer
        every { frameBuffer.width } returns 1920
        every { frameBuffer.height } returns 1080
        every { frameBuffer.target } returns target
        every { engine.device } returns device

        mockkObject(ScreenshotUtils)
        every { ScreenshotUtils.takeScreenshot(any(), any()) } just Runs

        ViewportRenderer(engine).captureScreenshot()

        verify(exactly = 1) {
            ScreenshotUtils.takeScreenshot(target, device)
        }
        verify { logger.logEditor(match { it.contains("Screenshot captured") && it.contains("1920x1080") }) }
    }

    @Test
    fun captureScreenshot_Failure_IsLoggedNotThrown() {
        val engine = mockk<Engine>()
        every { engine.logger } returns logger
        val renderer = mockk<Renderer>()
        val frameBuffer = mockk<FrameBuffer>()
        every { renderer.frameBuffer } returns frameBuffer
        every { engine.renderer } returns renderer
        every { frameBuffer.width } returns 640
        every { frameBuffer.height } returns 480
        every { frameBuffer.target } returns target
        every { engine.device } returns device

        mockkObject(ScreenshotUtils)
        every { ScreenshotUtils.takeScreenshot(any(), any()) } throws IllegalStateException("disk full")

        ViewportRenderer(engine).captureScreenshot()

        verify { logger.logEditor(match { it.contains("Screenshot failed") && it.contains("disk full") }, LogLevel.ERROR) }
    }

    @Test
    fun captureScreenshot_InvalidFramebufferDimensions_DoesNotCallScreenshotUtils() {
        val engine = mockk<Engine>()
        every { engine.logger } returns logger
        val renderer = mockk<Renderer>()
        val frameBuffer = mockk<FrameBuffer>()
        every { renderer.frameBuffer } returns frameBuffer
        every { engine.renderer } returns renderer
        every { frameBuffer.width } returns 0
        every { frameBuffer.height } returns 1080

        mockkObject(ScreenshotUtils)
        every { ScreenshotUtils.takeScreenshot(any(), any()) } just Runs

        ViewportRenderer(engine).captureScreenshot()

        verify(exactly = 0) {
            ScreenshotUtils.takeScreenshot(any(), any())
        }
        verify { logger.logEditor(match { it.contains("Screenshot skipped") }, LogLevel.WARN) }
    }
}
