package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.rhi.IntRect

/**
 * Viewport of a pass drawing into the presented target: null (the whole target) for the scene framebuffer, the
 * framebuffer's size when drawing straight to the back buffer. Cached, so a frame allocates nothing.
 */
internal class PresentedViewport {
    private var rect: IntRect? = null

    fun of(frameBuffer: FrameBuffer, useFbo: Boolean): IntRect? = if (useFbo) null else of(frameBuffer.width, frameBuffer.height)

    fun of(width: Int, height: Int): IntRect {
        val r = rect
        if (r != null && r.width == width && r.height == height) return r
        return IntRect(0, 0, width, height).also { rect = it }
    }
}
