package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.engine.assets.loaders.TextureLoader
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/** A browser tile: a small GPU copy of a texture plus the source image's size (AST-23). */
class TextureThumbnail(val texture: RhiTexture, val width: Int, val height: Int, val sourceWidth: Int, val sourceHeight: Int) {
    val texId: Long get() = texture.nativeHandle
}

/** RGBA8 pixels decoded and shrunk on a worker; [pixels] is `MemoryUtil` memory freed by the upload or [free]. */
class ThumbnailPixels(val width: Int, val height: Int, val sourceWidth: Int, val sourceHeight: Int, val pixels: ByteBuffer) {
    fun free() = MemoryUtil.memFree(pixels)
}

/** Fits images into [ThumbnailScaler.MAX] pixels with a box filter; pure, so it runs on IO workers. */
object ThumbnailScaler {
    const val MAX = 128

    /** Size of a [width]×[height] image scaled to fit [max]×[max], never upscaled, at least 1×1. */
    fun fit(width: Int, height: Int, max: Int = MAX): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= max) return width.coerceAtLeast(1) to height.coerceAtLeast(1)
        val scale = max.toDouble() / longest
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    /** Box-filters RGBA8 [src] (sw×sh) into [dst] (dw×dh, dw ≤ sw, dh ≤ sh); positions of both buffers are ignored. */
    fun downscale(src: ByteBuffer, sw: Int, sh: Int, dst: ByteBuffer, dw: Int, dh: Int) {
        val sum = IntArray(4)
        for (y in 0 until dh) {
            val y0 = y * sh / dh
            val y1 = max(y0 + 1, (y + 1) * sh / dh)
            for (x in 0 until dw) {
                val x0 = x * sw / dw
                val x1 = max(x0 + 1, (x + 1) * sw / dw)
                sum.fill(0)
                for (sy in y0 until y1) for (sx in x0 until x1) {
                    val i = (sy * sw + sx) * 4
                    for (c in 0 until 4) sum[c] += src.get(i + c).toInt() and 0xFF
                }
                val n = (y1 - y0) * (x1 - x0)
                val o = (y * dw + x) * 4
                for (c in 0 until 4) dst.put(o + c, (sum[c] / n).toByte())
            }
        }
    }

    /** Worker: decodes [file] with stb and shrinks it; the stb buffer is freed here. */
    fun decode(file: File, loader: TextureLoader = TextureLoader()): ThumbnailPixels {
        val data = loader.decodeFromFile(file.absolutePath)
        try {
            val (w, h) = fit(data.width, data.height)
            val out = MemoryUtil.memAlloc(w * h * 4)
            downscale(data.pixels, data.width, data.height, out, w, h)
            return ThumbnailPixels(w, h, data.width, data.height, out)
        } finally {
            loader.discard(data)
        }
    }

    /** Main thread: one small texture, no mips. Frees the pixels. */
    fun upload(pixels: ThumbnailPixels): TextureThumbnail = try {
        val texture = RhiContext.require().createTexture(
            TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, pixels.width, pixels.height, sampler = SAMPLER),
        )
        texture.upload(0, 0, pixels.pixels)
        TextureThumbnail(texture, pixels.width, pixels.height, pixels.sourceWidth, pixels.sourceHeight)
    } finally {
        pixels.free()
    }

    private val SAMPLER = SamplerDesc(minFilter = Filter.LINEAR, magFilter = Filter.LINEAR, wrap = Wrap.CLAMP_TO_EDGE)
}
