package com.pafoid.skate.engine.utils.testing

import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBImage.*
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

object VisualAssertion {

    data class ComparisonResult(
        val mismatchPercentage: Float,
        val pixelCount: Int,
        val mismatchedPixelCount: Int
    )

    /**
     * Compares two images and returns the mismatch percentage.
     * @param expectedPath Path to the gold master image.
     * @param actualPath Path to the current render.
     * @param diffOutputPath Optional path to save a difference image.
     * @param threshold Per-channel difference threshold (0-255).
     */
    fun compare(
        expectedPath: String,
        actualPath: String,
        diffOutputPath: String? = null,
        threshold: Int = 10
    ): ComparisonResult {
        val w1 = BufferUtils.createIntBuffer(1)
        val h1 = BufferUtils.createIntBuffer(1)
        val c1 = BufferUtils.createIntBuffer(1)
        val img1 = stbi_load(expectedPath, w1, h1, c1, 4) ?: throw IllegalArgumentException("Could not load $expectedPath")

        val w2 = BufferUtils.createIntBuffer(1)
        val h2 = BufferUtils.createIntBuffer(1)
        val c2 = BufferUtils.createIntBuffer(1)
        val img2 = stbi_load(actualPath, w2, h2, c2, 4) ?: throw IllegalArgumentException("Could not load $actualPath")

        val width = w1.get(0)
        val height = h1.get(0)
        
        if (width != w2.get(0) || height != h2.get(0)) {
            stbi_image_free(img1)
            stbi_image_free(img2)
            throw IllegalArgumentException("Image dimensions do not match: ${width}x${height} vs ${w2.get(0)}x${h2.get(0)}")
        }

        var mismatchedPixels = 0
        val diffBuffer: ByteBuffer? = if (diffOutputPath != null) MemoryUtil.memAlloc(width * height * 4) else null

        for (i in 0 until (width * height)) {
            val base = i * 4
            val r1 = img1.get(base).toInt() and 0xFF
            val g1 = img1.get(base + 1).toInt() and 0xFF
            val b1 = img1.get(base + 2).toInt() and 0xFF
            val a1 = img1.get(base + 3).toInt() and 0xFF

            val r2 = img2.get(base).toInt() and 0xFF
            val g2 = img2.get(base + 1).toInt() and 0xFF
            val b2 = img2.get(base + 2).toInt() and 0xFF
            val a2 = img2.get(base + 3).toInt() and 0xFF

            val isDifferent = abs(r1 - r2) > threshold ||
                              abs(g1 - g2) > threshold ||
                              abs(b1 - b2) > threshold ||
                              abs(a1 - a2) > threshold

            if (isDifferent) {
                mismatchedPixels++
                diffBuffer?.let {
                    it.put(base, 255.toByte())   // Red for difference
                    it.put(base + 1, 0.toByte())
                    it.put(base + 2, 0.toByte())
                    it.put(base + 3, 255.toByte())
                }
            } else {
                diffBuffer?.let {
                    // Dimmed version of original for context
                    it.put(base, (r1 / 4).toByte())
                    it.put(base + 1, (g1 / 4).toByte())
                    it.put(base + 2, (b1 / 4).toByte())
                    it.put(base + 3, 255.toByte())
                }
            }
        }

        if (diffOutputPath != null && diffBuffer != null) {
            stbi_write_png(diffOutputPath, width, height, 4, diffBuffer, width * 4)
            MemoryUtil.memFree(diffBuffer)
        }

        stbi_image_free(img1)
        stbi_image_free(img2)

        val totalPixels = width * height
        return ComparisonResult(
            mismatchPercentage = (mismatchedPixels.toFloat() / totalPixels.toFloat()) * 100f,
            pixelCount = totalPixels,
            mismatchedPixelCount = mismatchedPixels
        )
    }
}
