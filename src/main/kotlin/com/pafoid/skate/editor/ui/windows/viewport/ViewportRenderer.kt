package com.pafoid.skate.editor.ui.windows.viewport

import java.io.File
import com.pafoid.skate.editor.systems.ScreenshotFolder
import com.pafoid.skate.editor.imgui.EditorToast
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.utils.ScreenshotUtils
import imgui.ImGui
import imgui.ImVec2

class ViewportRenderer(
    private val engine: Engine,
    /** Folder for screenshots, resolved at capture time (EDT-42: the open project's, else the user-data one). */
    private val screenshotDir: () -> File = { ScreenshotFolder.resolve(null) },
) {
    var imageScreenPosX = 0f
    var imageScreenPosY = 0f
    var imageSizeX = 0f
    var imageSizeY = 0f

    fun render(windowSize: ImVec2) {
        val tempScreenPos = ImVec2()
        ImGui.getCursorScreenPos(tempScreenPos)
        imageScreenPosX = tempScreenPos.x
        imageScreenPosY = tempScreenPos.y
        imageSizeX = windowSize.x
        imageSizeY = windowSize.y

        val texId = engine.renderer.frameBuffer.getTextureId()
        ImGui.image(texId.toLong(), imageSizeX, imageSizeY, 0f, 1f, 1f, 0f)
    }

    /** Scale of the monitor hosting the viewport window (1 on standard DPI, 2 on HiDPI), refreshed each frame. */
    var dpiScale = 1f

    fun updateFramebuffer() {
        dpiScale = sanitizeDpiScale(ImGui.getWindowViewport().dpiScale)
        val fbWidth = framebufferExtent(imageSizeX, dpiScale)
        val fbHeight = framebufferExtent(imageSizeY, dpiScale)

        // Skip if dimensions are invalid or unchanged
        if (fbWidth <= 0 || fbHeight <= 0) return

        val currentFb = engine.renderer.frameBuffer
        if (currentFb.width != fbWidth || currentFb.height != fbHeight) {
            engine.resizeFrameBuffer(fbWidth, fbHeight)
        }
    }

    fun captureScreenshot() {
        val frameBuffer = engine.renderer.frameBuffer
        if (frameBuffer.width <= 0 || frameBuffer.height <= 0) {
            engine.logger.logEditor("Screenshot skipped: the viewport has no size yet", LogLevel.WARN)
            return
        }
        val dir = screenshotDir()
        runCatching { ScreenshotUtils.takeScreenshot(frameBuffer.width, frameBuffer.height, frameBuffer.fboId, dir, ::onScreenshotSaved) }
            .onSuccess { engine.logger.logEditor("Screenshot captured (${frameBuffer.width}x${frameBuffer.height})") }
            .onFailure { engine.logger.logEditor("Screenshot failed: ${it.message}", LogLevel.ERROR) }
    }

    /** Runs on the writer thread: the logger and the toast are both thread-safe. */
    internal fun onScreenshotSaved(result: Result<File>) {
        val strings = engine.stringManager
        result.onSuccess { file ->
            engine.logger.logEditor("Screenshot saved to ${file.absolutePath}", LogLevel.ACTION)
            EditorToast.show(strings.getString("toast.screenshot_saved", file.absolutePath))
        }.onFailure {
            engine.logger.logEditor("Screenshot failed: ${it.message}", LogLevel.ERROR)
            EditorToast.show(strings.getString("toast.screenshot_failed", it.message ?: ""))
        }
    }

    companion object {
        fun sanitizeDpiScale(scale: Float): Float = if (scale.isFinite() && scale > 0f) scale else 1f

        /** Physical pixel extent for a logical (ImGui) extent at the given DPI scale. */
        fun framebufferExtent(logical: Float, dpiScale: Float): Int =
            Math.round(logical * sanitizeDpiScale(dpiScale))
    }
}
