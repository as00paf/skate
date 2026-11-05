package com.pafoid.skate.engine.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*
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
     * Saves the [fboId] framebuffer as a PNG in [directory]. The file is written on a background thread, then
     * [onResult] runs there with the saved file, or the failure. By default a native message box reports it.
     */
    fun takeScreenshot(
        width: Int,
        height: Int,
        fboId: Int,
        directory: File = File(DEFAULT_DIRECTORY),
        onResult: (Result<File>) -> Unit = ::showResultPopup,
    ) {
        directory.mkdirs()
        val file = File(directory, buildScreenshotFileName())
        val filePath = file.path

        val pixels = BufferUtils.createByteBuffer(width * height * 4)
        
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)

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
