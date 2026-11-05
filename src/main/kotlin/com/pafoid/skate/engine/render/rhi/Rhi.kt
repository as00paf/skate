package com.pafoid.skate.engine.render.rhi

import java.nio.ByteBuffer
import java.nio.FloatBuffer

/*
 * Render hardware interface (REN-12, docs/design/REN-12.md). Backend-neutral: nothing in this package may use a
 * graphics API (RhiGlIsolationGuardTest). The OpenGL backend lives in `rhi/opengl/`.
 *
 * Migration status: step 5 (buffers, vertex inputs, textures, render targets, passes, readback, blit, shaders and
 * parameters). [RhiDevice] and [RhiCommandList] only declare what the backend implements; pipelines and draws (6) and
 * timers (7) are added by the step that migrates their callers. Everything runs on the main thread (§7.6).
 */

/** What the device supports; read once at creation. */
data class RhiCaps(
    val backend: String,
    val renderer: String,
    val version: String,
    val maxTextureSize: Int,
    val maxArrayLayers: Int,
    val maxAnisotropy: Float,
    val timerQueries: Boolean,
)

/** The graphics device of the window's context. */
interface RhiDevice : AutoCloseable {
    val caps: RhiCaps

    /** Start of a frame: forget cached state that code outside the RHI (ImGui) may have changed. */
    fun beginFrame()

    /** End of a frame: releases resources closed during it (deferred deletion, §6). */
    fun endFrame()

    /** A buffer of [BufferDesc.size] bytes with undefined contents. */
    fun createBuffer(desc: BufferDesc): RhiBuffer

    /** A buffer holding [data]. */
    fun createBuffer(usage: BufferUsage, update: BufferUpdate, data: FloatArray): RhiBuffer

    /** A buffer holding [data] (indices, joint indices). */
    fun createBuffer(usage: BufferUsage, update: BufferUpdate, data: IntArray): RhiBuffer

    fun createVertexLayout(desc: VertexLayoutDesc): RhiVertexLayout

    /**
     * Binds [vertexBuffers] (one per [VertexLayoutDesc.bindings] entry, same order) and an optional [indexBuffer] to
     * [layout]. GL: a VAO; Vulkan: the binding list recorded at draw time.
     */
    fun createVertexInput(layout: RhiVertexLayout, vertexBuffers: List<RhiBuffer>, indexBuffer: RhiBuffer? = null): RhiVertexInput

    /**
     * A texture object with [TextureDesc.sampler] applied and no contents yet: fill each level and layer with
     * [RhiTexture.upload] (cube faces are layers 0-5 in +X, -X, +Y, -Y, +Z, -Z order).
     */
    fun createTexture(desc: TextureDesc): RhiTexture

    /** A program from preprocessed stage sources; throws [ShaderCompileError] with the driver's log when one fails. */
    fun createShader(desc: ShaderDesc): RhiShader

    /** Color and depth attachments in one target; see [RenderTargetDesc]. */
    fun createRenderTarget(desc: RenderTargetDesc): RhiRenderTarget

    /**
     * Synchronously copies [rect] of color attachment [attachment] of [target] (null: the window's back buffer) into
     * [out] as tightly packed [format] pixels, bottom row first (GL order). Picking, thumbnails and screenshots only:
     * it waits for the GPU.
     */
    fun readPixels(target: RhiRenderTarget?, attachment: Int, rect: IntRect, format: PixelFormat, out: ByteBuffer)

    /** The frame's command list (GL: one shared immediate list). */
    fun commandList(): RhiCommandList
}

/**
 * The device of the current context, like GL's current context: set when a backend device is created, so code built
 * before the window (asset loaders) can reach it lazily. Main thread only.
 */
object RhiContext {
    var device: RhiDevice? = null

    fun require(): RhiDevice =
        device ?: error("No RHI device: create one (OpenGlDevice.create()) once the window's context is current")
}

/** A backend object. [close] releases it once (deferred to [RhiDevice.endFrame]); further calls do nothing. */
interface RhiResource : AutoCloseable

interface RhiBuffer : RhiResource {
    val usage: BufferUsage
    val update: BufferUpdate

    /** Current size in bytes. */
    val size: Long

    /** Writes [data] (whole array) at byte [offset]; must fit in [size]. */
    fun update(offset: Long, data: FloatArray)

    /** Writes [data]'s remaining floats at byte [offset]; must fit in [size]. */
    fun update(offset: Long, data: FloatBuffer)

    /** Writes [data]'s remaining bytes at byte [offset]; must fit in [size]. */
    fun update(offset: Long, data: ByteBuffer)

    /** New storage of [size] bytes with undefined contents (GL: orphans the old storage). */
    fun resize(size: Long)

    /** New storage holding exactly [data]. */
    fun replace(data: FloatArray)

    /** New storage holding exactly [data]. */
    fun replace(data: IntArray)
}

interface RhiTexture : RhiResource {
    val desc: TextureDesc

    /** Current sampler state; starts as [TextureDesc.sampler]. */
    val sampler: SamplerDesc

    /** (Re)specifies [level] of [layer] from [data] in [TextureDesc.format]; null allocates without contents. */
    fun upload(level: Int, layer: Int, data: ByteBuffer?)

    /** Builds levels 1.. from level 0. */
    fun generateMips()

    /** Changes filtering, wrapping or anisotropy (texture quality settings). */
    fun setSampler(sampler: SamplerDesc)

    /** Backend handle for ImGui and for draw code not yet on [RhiCommandList] (GL: texture name), see §7.2. */
    val nativeHandle: Long
}

/** See [RhiDevice.createRenderTarget]; render into it between [RhiCommandList.beginPass] and `endPass`. */
interface RhiRenderTarget : RhiResource {
    /** Current description; [resize] changes its size. */
    val desc: RenderTargetDesc
    val width: Int
    val height: Int

    /** New attachments of the given size with undefined contents; textures fetched before are closed. */
    fun resize(width: Int, height: Int)
    fun colorTexture(index: Int = 0): RhiTexture
    fun depthTexture(): RhiTexture?
}

/**
 * Named shader parameters (today's `uName` uniforms), one table per shader. The backend uploads only values that
 * changed (GL: at once, binding the program if needed; Vulkan: into the per-draw block at `setParams`). A name the
 * shader lacks is ignored.
 */
interface ShaderParams {
    fun set(name: String, value: Float)
    fun set(name: String, value: Int)
    fun set(name: String, x: Float, y: Float)
    fun set(name: String, x: Float, y: Float, z: Float)
    fun set(name: String, x: Float, y: Float, z: Float, w: Float)

    /** An int array (sampler arrays). */
    fun set(name: String, values: IntArray)

    /** A column-major 4x4 matrix (16 values) or an array of them. */
    fun setMatrix4(name: String, values: FloatArray)
}

interface RhiShader : RhiResource {
    val desc: ShaderDesc
    fun parameters(): ShaderParams

    /**
     * Binds uniform block [blockName] to [binding] for [RhiCommandList.setUniformBuffer] (GL 3.3 has no `binding`
     * qualifier; Vulkan declares it in the shader). Returns false when the shader has no such block.
     */
    fun bindUniformBlock(blockName: String, binding: Int): Boolean
}

interface RhiVertexLayout : RhiResource {
    val desc: VertexLayoutDesc
}

/** Vertex (and index) buffers bound to a layout; see [RhiDevice.createVertexInput]. Closing it keeps the buffers. */
interface RhiVertexInput : RhiResource {
    val layout: RhiVertexLayout

    /** Backend handle for draw code not yet on [RhiCommandList] (GL: VAO name); removed at step 6. */
    val nativeHandle: Long
}

interface RhiPipeline : RhiResource {
    val desc: PipelineDesc
}

/** Non-blocking GPU timer: results arrive a few frames later; [latestNs] is -1 until then. */
interface RhiTimer : RhiResource {
    val latestNs: Long
}

/**
 * Commands of a frame; immediate on GL, recorded on Vulkan. Grows with the migration: `setPipeline`,
 * `setVertexInput`, `setTexture`, `setScissor` and the draws arrive at step 6, timers at 7 (docs/design/REN-12.md §3).
 */
interface RhiCommandList {
    /** Makes [params]' shader the one later draws use and pushes its changed values. */
    fun setParams(params: ShaderParams)

    /**
     * Starts rendering into [target] (null: the window's back buffer, which needs an explicit [viewport]); [layer]
     * selects the layer of a layered target. [clear] clears the attachments it names whatever the depth-write and
     * scissor state (Vulkan load ops); [viewport] defaults to the whole target. Pair with [endPass].
     */
    fun beginPass(target: RhiRenderTarget?, layer: Int = 0, clear: ClearValues? = null, viewport: IntRect? = null)

    /** Ends the pass started by [beginPass] (GL: back to the default framebuffer). */
    fun endPass()

    /**
     * Copies color attachment 0 of [src] into [dst] (null: the back buffer, which needs [dstRect]), outside a pass.
     * Null rects are the whole target; [filter] (NEAREST or LINEAR) applies when the sizes differ.
     */
    fun blit(src: RhiRenderTarget, dst: RhiRenderTarget?, srcRect: IntRect? = null, dstRect: IntRect? = null, filter: Filter = Filter.NEAREST)

    /** Binds [buffer] (whole buffer when [size] is -1) to uniform block binding [binding]. */
    fun setUniformBuffer(binding: Int, buffer: RhiBuffer, offset: Long = 0, size: Long = -1)
}
