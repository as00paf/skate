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

    /** Folder used when the caller gives none (relative to the working directory). */
    const val DEFAULT_DIRECTORY = "screenshots"

    /**
     * Saves color attachment 0 of [target] as a PNG in [directory]: read now, written on a background thread. Then
     * [onResult] runs on that thread with the saved file, or the failure. By default a native message box reports it.
     */
    fun takeScreenshot(
        target: RhiRenderTarget,
        device: RhiDevice = RhiContext.require(),
        directory: File = File(DEFAULT_DIRECTORY),
        onResult: (Result<File>) -> Unit = ::showResultPopup,
    ) {
        val width = target.width
        val height = target.height
        directory.mkdirs()
        val file = File(directory, buildScreenshotFileName())
        val filePath = file.path

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

            val written = runCatching { stbi_write_png(filePath, width, height, 4, flippedPixels, width * 4) }.getOrDefault(false)
            onResult(if (written) Result.success(file) else Result.failure(IllegalStateException("Could not write $filePath")))
        }
    }

    private fun showResultPopup(result: Result<File>) {
        result.onSuccess {
            TinyFileDialogs.tinyfd_messageBox("Screenshot Saved", "Screenshot saved to:\n${it.path}", "ok", "info", true)
        }.onFailure {
            TinyFileDialogs.tinyfd_messageBox("Screenshot Failed", "Failed to save screenshot.", "ok", "error", true)
        }
    }

    internal fun buildScreenshotFileName(
        now: LocalDateTime = LocalDateTime.now()
    ): String {
        val timestamp = now.format(screenshotTimestampFormatter)
        val sequence = screenshotSequence.getAndIncrement()
        return "screenshot_${timestamp}_${sequence}.png"
    }
}
