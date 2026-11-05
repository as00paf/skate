package com.pafoid.skate.engine.utils

import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.File
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

object ScreenshotUtils {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val screenshotSequence = AtomicInteger(0)
    private val screenshotTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")

    /** Saves color attachment 0 of [target] as a PNG in `screenshots/` (read now, written in the background). */
    fun takeScreenshot(target: RhiRenderTarget, device: RhiDevice = RhiContext.require()) {
        val width = target.width
        val height = target.height
        val screenshotsDir = "screenshots"
        File(screenshotsDir).mkdirs()
        val fileName = buildScreenshotFileName()
        val filePath = "$screenshotsDir/$fileName"

        val pixels = BufferUtils.createByteBuffer(width * height * 4)
        
        device.readPixels(target, 0, IntRect(0, 0, width, height), PixelFormat.RGBA8, pixels)

        // Offload the heavy lifting to a background thread
        scope.launch {
            // Flip the image vertically (OpenGL reads from bottom-left)
            val flippedPixels = BufferUtils.createByteBuffer(width * height * 4)
            for (y in 0 until height) {
                val originalRowIndex = y * width * 4
                val flippedRowIndex = (height - 1 - y) * width * 4
                for (x in 0 until width * 4) {
                    flippedPixels.put(flippedRowIndex + x, pixels.get(originalRowIndex + x))
                }
            }

            if (stbi_write_png(filePath, width, height, 4, flippedPixels, width * 4)) {
                // UI interaction must be carefully handled if TinyFileDialogs blocks or needs specific thread
                // TinyFileDialogs is usually thread-safe and creates its own window.
                showScreenshotPopup(filePath)
            } else {
                TinyFileDialogs.tinyfd_messageBox("Screenshot Failed", "Failed to save screenshot.", "ok", "error", true)
            }
        }
    }

    private fun showScreenshotPopup(filePath: String) {
        TinyFileDialogs.tinyfd_messageBox(
            "Screenshot Saved",
            "Screenshot saved to:\n$filePath",
            "ok",
            "info",
            true
        )
    }

    internal fun buildScreenshotFileName(
        now: LocalDateTime = LocalDateTime.now()
    ): String {
        val timestamp = now.format(screenshotTimestampFormatter)
        val sequence = screenshotSequence.getAndIncrement()
        return "screenshot_${timestamp}_${sequence}.png"
    }
}
