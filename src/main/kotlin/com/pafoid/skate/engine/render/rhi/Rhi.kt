package com.pafoid.skate.engine.render.rhi

import java.nio.ByteBuffer
import java.nio.FloatBuffer

/*
 * Render hardware interface (REN-12, docs/design/REN-12.md). Backend-neutral: nothing in this package may use a
 * graphics API (RhiGlIsolationGuardTest). The OpenGL backend lives in `rhi/opengl/`.
 *
 * Migration status: step 3 (buffers, vertex inputs, textures). [RhiDevice] and [RhiCommandList] only declare what
 * the backend implements; render targets and readback (step 4), shaders (5), pipelines and draws (6) and timers (7)
 * are added by the step that migrates their callers. Everything runs on the main thread (§7.6).
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

interface RhiRenderTarget : RhiResource {
    val desc: RenderTargetDesc
    val width: Int
    val height: Int
    fun resize(width: Int, height: Int)
    fun colorTexture(index: Int = 0): RhiTexture
    fun depthTexture(): RhiTexture?
}

/** Named shader parameters (today's `uName` uniforms); the backend uploads only changed values (§3). */
interface ShaderParams {
    fun set(name: String, value: Float)
    fun set(name: String, value: Int)
    fun set(name: String, x: Float, y: Float, z: Float)
    fun set(name: String, x: Float, y: Float, z: Float, w: Float)
    fun setMatrix4(name: String, values: FloatArray)
}

interface RhiShader : RhiResource {
    val desc: ShaderDesc
    fun parameters(): ShaderParams
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
 * Commands of a frame; immediate on GL, recorded on Vulkan. Grows with the migration: passes (`beginPass`/`endPass`
 * with [ClearValues]) and `blit` arrive at step 4, `setParams` at 5, `setPipeline`, `setVertexInput`, `setTexture`,
 * `setScissor` and the draws at 6, timers at 7 (docs/design/REN-12.md §3).
 */
interface RhiCommandList {
    /** Binds [buffer] (whole buffer when [size] is -1) to uniform block binding [binding]. */
    fun setUniformBuffer(binding: Int, buffer: RhiBuffer, offset: Long = 0, size: Long = -1)
}
