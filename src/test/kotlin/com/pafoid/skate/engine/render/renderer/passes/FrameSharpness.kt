package com.pafoid.skate.engine.render.renderer.passes

import kotlin.math.abs

/** Sharpness metric for RGBA8 frames (REN-24): a blur lowers it, edge-only anti-aliasing barely changes it. */
object FrameSharpness {
    /** Mean absolute luma difference between horizontal and vertical neighbours, in 0..255 units. */
    fun gradientEnergy(rgba: ByteArray, width: Int, height: Int): Double {
        require(rgba.size == width * height * 4) { "frame is ${rgba.size} bytes, expected ${width * height * 4}" }
        var sum = 0.0
        var n = 0L
        for (y in 0 until height - 1) {
            for (x in 0 until width - 1) {
                val l = luma(rgba, y * width + x)
                sum += abs(l - luma(rgba, y * width + x + 1)) + abs(l - luma(rgba, (y + 1) * width + x))
                n += 2
            }
        }
        return if (n == 0L) 0.0 else sum / n
    }

    private fun luma(rgba: ByteArray, p: Int): Double =
        0.299 * (rgba[p * 4].toInt() and 0xFF) + 0.587 * (rgba[p * 4 + 1].toInt() and 0xFF) + 0.114 * (rgba[p * 4 + 2].toInt() and 0xFF)
}
