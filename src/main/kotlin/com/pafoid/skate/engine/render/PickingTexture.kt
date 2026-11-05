package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RenderTargetDesc
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.Wrap
import com.pafoid.skate.engine.utils.EntityIdEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Entity-id target of mouse picking: RGB32F color (the encoded id in red) plus depth. */
class PickingTexture(
    width: Int,
    height: Int,
    private val device: () -> RhiDevice = RhiContext::require,
) {
    var width: Int = width
        private set
    var height: Int = height
        private set

    /** The RHI target the picking pass renders into. */
    val target: RhiRenderTarget = device().createRenderTarget(desc(width, height))

    private val pixel: ByteBuffer = ByteBuffer.allocateDirect(PixelFormat.RGB32F.bytesPerPixel).order(ByteOrder.nativeOrder())

    fun resize(width: Int, height: Int) {
        if (this.width == width && this.height == height) return
        this.width = width
        this.height = height
        target.resize(width, height)
    }

    /** Entity id at pixel ([x], [y]) (bottom-left origin); waits for the GPU. */
    fun readPixel(x: Int, y: Int): Int {
        pixel.clear()
        device().readPixels(target, 0, IntRect(x, y, 1, 1), PixelFormat.RGB32F, pixel)
        return EntityIdEncoder.decode(pixel.getFloat(0))
    }

    fun destroy() = target.close()

    companion object {
        fun desc(width: Int, height: Int) = RenderTargetDesc(
            width, height,
            colors = listOf(PixelFormat.RGB32F),
            depth = PixelFormat.DEPTH32F,
            colorSampler = SamplerDesc(Filter.NEAREST, Filter.NEAREST, Wrap.REPEAT),
        )
    }
}
