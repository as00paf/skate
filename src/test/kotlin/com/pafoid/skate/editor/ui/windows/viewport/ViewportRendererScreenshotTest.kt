package com.pafoid.skate.editor.ui.windows.viewport

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.core.Engine
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import com.pafoid.skate.editor.imgui.EditorToast
import com.pafoid.skate.editor.systems.ScreenshotFolder
import com.pafoid.skate.engine.core.StringManager
import java.io.File

class ViewportRendererScreenshotTest {

    private val logger = mockk<LoggerService>(relaxed = true)

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
        every { frameBuffer.fboId } returns 77

        mockkObject(ScreenshotUtils)
        every { ScreenshotUtils.takeScreenshot(any(), any(), any(), any(), any()) } just Runs

        val project = File("C:/Projects/Park")
        ViewportRenderer(engine) { ScreenshotFolder.resolve(project) }.captureScreenshot()

        // EDT-42: into the open project's Screenshots folder, never the working directory.
        verify(exactly = 1) {
            ScreenshotUtils.takeScreenshot(1920, 1080, 77, File(project, "Screenshots").absoluteFile, any())
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
        every { frameBuffer.fboId } returns 1

        mockkObject(ScreenshotUtils)
        every { ScreenshotUtils.takeScreenshot(any(), any(), any(), any(), any()) } throws IllegalStateException("disk full")

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
        every { ScreenshotUtils.takeScreenshot(any(), any(), any(), any(), any()) } just Runs

        ViewportRenderer(engine).captureScreenshot()

        verify(exactly = 0) {
            ScreenshotUtils.takeScreenshot(any(), any(), any(), any(), any())
        }
        verify { logger.logEditor(match { it.contains("Screenshot skipped") }, LogLevel.WARN) }
    }

    @Test
    fun Folder_IsTheProjectsOrTheUserDataOne() {
        val userData = File("C:/Users/me/AppData/Roaming/Skate")
        assertEquals(File("C:/Projects/Park/Screenshots").absoluteFile, ScreenshotFolder.resolve(File("C:/Projects/Park")) { userData })
        assertEquals(File(userData, "Screenshots").absoluteFile, ScreenshotFolder.resolve(null) { userData })
    }

    @Test
    fun SavedScreenshot_PathIsLoggedAndToasted() {
        val engine = mockk<Engine>()
        every { engine.logger } returns logger
        val strings = mockk<StringManager>()
        every { strings.getString("toast.screenshot_saved", *anyVararg()) } answers { "saved ${(args[1] as Array<*>)[0]}" }
        every { engine.stringManager } returns strings
        val file = File("C:/Projects/Park/Screenshots/shot.png")

        ViewportRenderer(engine).onScreenshotSaved(Result.success(file))

        verify { logger.logEditor(match { it.contains(file.absolutePath) }, LogLevel.ACTION) }
        assertEquals("saved ${file.absolutePath}", EditorToast.current())
    }

    @Test
    fun Toast_ExpiresAfterItsDuration() {
        EditorToast.show("hello", now = 1000L)
        assertEquals("hello", EditorToast.current(now = 1000L + EditorToast.DURATION_MS - 1))
        assertNull(EditorToast.current(now = 1000L + EditorToast.DURATION_MS))
    }
}
