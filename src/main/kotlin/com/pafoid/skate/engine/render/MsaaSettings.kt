package com.pafoid.skate.engine.render

/**
 * Global MSAA sample count for the editor's scene render target.
 *
 * The editor's graphics settings write it; [FrameBuffer] and [GlRenderTarget] read it at creation time.
 * Changed at runtime: the editor must recreate the frame buffer when this value changes.
 */
object MsaaSettings {
    /** JVM property that picks the starting sample count, e.g. `-Dskate.msaa=8`. */
    const val SAMPLES_PROPERTY = "skate.msaa"

    @Volatile
    var samples: Int = System.getProperty(SAMPLES_PROPERTY)?.toIntOrNull()?.coerceIn(0, 8)
        ?: 4

    /** Steps 1 -> 2 -> 4 -> 8 -> 1 and returns the new sample count. */
    fun cycle(): Int {
        val steps = intArrayOf(1, 2, 4, 8)
        val idx = steps.indexOf(samples).let { if (it < 0) 2 else it }
        samples = steps[(idx + 1) % steps.size]
        return samples
    }
}
