package com.pafoid.skate.engine.render.rhi.vulkan

import kotlin.math.abs

/**
 * Difference between two RGBA8 frames of the same size (alpha ignored), for backend parity tests (REN-13, VK-03).
 * A pixel's difference is its largest channel difference.
 */
class FrameDiff(a: ByteArray, b: ByteArray) {
    val pixels: Int = a.size / 4

    /** Mean absolute difference per colour channel, in 1/255 units. */
    val meanPerChannel: Double

    /** Largest channel difference over the frame. */
    val max: Int

    /** histogram[d] = pixels whose largest channel difference is d (0..255). */
    private val histogram = IntArray(256)

    /** Per pixel largest channel difference, for diff images. */
    val perPixel = IntArray(pixels)

    init {
        require(a.size == b.size && a.size % 4 == 0) { "frames differ in size: ${a.size} vs ${b.size}" }
        var sum = 0L
        var m = 0
        for (p in 0 until pixels) {
            var d = 0
            for (c in 0 until 3) {
                val x = abs((a[p * 4 + c].toInt() and 0xFF) - (b[p * 4 + c].toInt() and 0xFF))
                sum += x
                if (x > d) d = x
            }
            perPixel[p] = d
            histogram[d]++
            if (d > m) m = d
        }
        meanPerChannel = if (pixels == 0) 0.0 else sum.toDouble() / (pixels * 3L)
        max = m
    }

    /** Share (0..1) of pixels whose largest channel difference is at most [tolerance]. */
    fun within(tolerance: Int): Double {
        if (pixels == 0) return 1.0
        var n = 0L
        for (d in 0..tolerance.coerceIn(0, 255)) n += histogram[d]
        return n.toDouble() / pixels
    }

    /** True when the frames meet [t]. */
    fun meets(t: Tolerance): Boolean = meanPerChannel <= t.maxMean && within(t.channel) >= t.minShareWithin

    fun summary(): String = "mean %.3f, within 2: %.3f%%, within 8: %.3f%%, max %d".format(meanPerChannel, 100 * within(2), 100 * within(8), max)

    /**
     * Parity tolerance: mean channel difference at most [maxMean], and at least [minShareWithin] of pixels with no
     * channel off by more than [channel].
     */
    data class Tolerance(val maxMean: Double, val channel: Int, val minShareWithin: Double)

    companion object {
        /** Number of distinct colours sampled every [step] pixels: guards against comparing two flat frames. */
        fun distinctColours(rgba: ByteArray, step: Int = 97): Int {
            val seen = HashSet<Int>()
            for (p in 0 until rgba.size / 4 step step) {
                seen += ((rgba[p * 4].toInt() and 0xFF) shl 16) or ((rgba[p * 4 + 1].toInt() and 0xFF) shl 8) or (rgba[p * 4 + 2].toInt() and 0xFF)
            }
            return seen.size
        }
    }
}
